/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.index;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.collect.DoubleArrayMath;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.impl.rate.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.product.index.IborFuture;
import com.opengamma.strata.product.index.IborFutureTrade;

/**
 * Test {@link HullWhiteIborFutureTradePricer}.
 */
@Test
public class HullWhiteIborFutureTradePricerTest {
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteIborFutureDataSet.HULL_WHITE_PARAMETER_PROVIDER;
  private static final ImmutableRatesProvider RATE_PROVIDER = HullWhiteIborFutureDataSet.RATE_PROVIDER;
  private static final IborFutureTrade TRADE = HullWhiteIborFutureDataSet.IBOR_FUTURE_TRADE;
  private static final IborFuture PRODUCT = HullWhiteIborFutureDataSet.IBOR_FUTURE;
  private static final double LAST_PRICE = HullWhiteIborFutureDataSet.LAST_MARGIN_PRICE;
  private static final double NOTIONAL = HullWhiteIborFutureDataSet.NOTIONAL;
  private static final long QUANTITY = HullWhiteIborFutureDataSet.QUANTITY;

  private static final double TOL = 1.0e-13;
  private static final double TOL_FD = 1.0e-6;
  private static final HullWhiteIborFutureTradePricer PRICER = HullWhiteIborFutureTradePricer.DEFAULT;
  private static final HullWhiteIborFutureProductPricer PRICER_PRODUCT = HullWhiteIborFutureProductPricer.DEFAULT;
  private static final RatesFiniteDifferenceSensitivityCalculator FD_CAL =
      new RatesFiniteDifferenceSensitivityCalculator(TOL_FD);

  public void test_price() {
    double computed = PRICER.price(TRADE, RATE_PROVIDER, HW_PROVIDER);
    double expected = PRICER_PRODUCT.price(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    assertEquals(computed, expected, TOL);
  }

  public void test_presentValue() {
    CurrencyAmount computed = PRICER.presentValue(TRADE, RATE_PROVIDER, HW_PROVIDER, LAST_PRICE);
    double price = PRICER_PRODUCT.price(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    double expected = (price - LAST_PRICE) * PRODUCT.getAccrualFactor() * NOTIONAL * QUANTITY;
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected, TOL * NOTIONAL * QUANTITY);
  }

  public void test_parSpread() {
    double computed = PRICER.parSpread(TRADE, RATE_PROVIDER, HW_PROVIDER, LAST_PRICE);
    CurrencyAmount pv = PRICER.presentValue(TRADE, RATE_PROVIDER, HW_PROVIDER, LAST_PRICE + computed);
    assertEquals(pv.getAmount(), 0d, TOL * NOTIONAL * QUANTITY);
  }

  public void test_presentValueSensitivity() {
    PointSensitivities point = PRICER.presentValueSensitivity(TRADE, RATE_PROVIDER, HW_PROVIDER);
    CurveCurrencyParameterSensitivities computed = RATE_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected =
        FD_CAL.sensitivity(RATE_PROVIDER, p -> PRICER.presentValue(TRADE, p, HW_PROVIDER, LAST_PRICE));
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * QUANTITY * TOL_FD));
  }

  public void test_presentValueSensitivityHullWhiteParameter() {
    DoubleArray computed = PRICER.presentValueSensitivityHullWhiteParameter(TRADE, RATE_PROVIDER, HW_PROVIDER);
    DoubleArray vols = HW_PROVIDER.getParameters().getVolatility();
    int size = vols.size();
    double[] expected = new double[size];
    for (int i = 0; i < size; ++i) {
      double[] volsUp = vols.toArray();
      double[] volsDw = vols.toArray();
      volsUp[i] += TOL_FD;
      volsDw[i] -= TOL_FD;
      HullWhiteOneFactorPiecewiseConstantParameters paramsUp = HullWhiteOneFactorPiecewiseConstantParameters.of(
          HW_PROVIDER.getParameters().getMeanReversion(), DoubleArray.copyOf(volsUp), HW_PROVIDER.getParameters()
              .getVolatilityTime().subArray(1, size));
      HullWhiteOneFactorPiecewiseConstantParameters paramsDw = HullWhiteOneFactorPiecewiseConstantParameters.of(
          HW_PROVIDER.getParameters().getMeanReversion(), DoubleArray.copyOf(volsDw), HW_PROVIDER.getParameters()
              .getVolatilityTime().subArray(1, size));
      HullWhiteOneFactorPiecewiseConstantParametersProvider provUp = HullWhiteOneFactorPiecewiseConstantParametersProvider
          .of(paramsUp, HW_PROVIDER.getDayCount(), HW_PROVIDER.getValuationDateTime());
      HullWhiteOneFactorPiecewiseConstantParametersProvider provDw = HullWhiteOneFactorPiecewiseConstantParametersProvider
          .of(paramsDw, HW_PROVIDER.getDayCount(), HW_PROVIDER.getValuationDateTime());
      double priceUp = PRICER.presentValue(TRADE, RATE_PROVIDER, provUp, LAST_PRICE).getAmount();
      double priceDw = PRICER.presentValue(TRADE, RATE_PROVIDER, provDw, LAST_PRICE).getAmount();
      expected[i] = 0.5 * (priceUp - priceDw) / TOL_FD;
    }
    assertTrue(DoubleArrayMath.fuzzyEquals(computed.toArray(), expected, NOTIONAL * QUANTITY * TOL_FD));
  }

  public void test_parSpreadSensitivity() {
    PointSensitivities point = PRICER.parSpreadSensitivity(TRADE, RATE_PROVIDER, HW_PROVIDER);
    CurveCurrencyParameterSensitivities computed = RATE_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected = FD_CAL.sensitivity(RATE_PROVIDER,
            p -> CurrencyAmount.of(EUR, PRICER.parSpread(TRADE, p, HW_PROVIDER, LAST_PRICE)));
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * QUANTITY * TOL_FD));
  }

  public void test_currencyExposure() {
    PointSensitivities point = PRICER.presentValueSensitivity(TRADE, RATE_PROVIDER, HW_PROVIDER);
    MultiCurrencyAmount expected = RATE_PROVIDER.currencyExposure(point)
        .plus(PRICER.presentValue(TRADE, RATE_PROVIDER, HW_PROVIDER, LAST_PRICE));
    MultiCurrencyAmount computed = PRICER.currencyExposure(TRADE, RATE_PROVIDER, HW_PROVIDER, LAST_PRICE);
    assertEquals(computed.size(), 1);
    assertEquals(computed.getAmount(EUR).getAmount(), expected.getAmount(EUR).getAmount(), NOTIONAL * QUANTITY * TOL);
  }

  //-------------------------------------------------------------------------
  public void regression_pv() {
    CurrencyAmount pv = PRICER.presentValue(TRADE, RATE_PROVIDER, HW_PROVIDER, LAST_PRICE);
    assertEquals(pv.getAmount(), 23383.551159035414, NOTIONAL * QUANTITY * TOL);
  }

  public void regression_pvSensi() {
    PointSensitivities point = PRICER.presentValueSensitivity(TRADE, RATE_PROVIDER, HW_PROVIDER);
    CurveCurrencyParameterSensitivities computed = RATE_PROVIDER.curveParameterSensitivity(point);
    double[] expected = new double[] {0.0, 0.0, 9.514709785770103E7, -1.939992074119211E8, 0.0, 0.0, 0.0, 0.0 };
    assertEquals(computed.size(), 1);
    assertTrue(DoubleArrayMath.fuzzyEquals(computed.getSensitivity(HullWhiteIborFutureDataSet.FWD3_NAME, EUR)
        .getSensitivity().toArray(), expected, NOTIONAL * QUANTITY * TOL));
  }
}
