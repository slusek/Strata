/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.index;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.DoubleArrayMath;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.impl.rate.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.product.index.IborFuture;

/**
 * Test {@link HullWhiteIborFutureProductPricer}.
 */
@Test
public class HullWhiteIborFutureProductPricerTest {
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteIborFutureDataSet.HULL_WHITE_PARAMETER_PROVIDER;
  private static final ImmutableRatesProvider RATE_PROVIDER = HullWhiteIborFutureDataSet.RATE_PROVIDER;
  private static final IborFuture PRODUCT = HullWhiteIborFutureDataSet.IBOR_FUTURE;

  private static final double TOL = 1.0e-13;
  private static final double TOL_FD = 1.0e-6;
  private static final HullWhiteIborFutureProductPricer PRICER = HullWhiteIborFutureProductPricer.DEFAULT;
  private static final DiscountingIborFutureProductPricer PRICER_DSC = DiscountingIborFutureProductPricer.DEFAULT;
  private static final RatesFiniteDifferenceSensitivityCalculator FD_CAL =
      new RatesFiniteDifferenceSensitivityCalculator(TOL_FD);

  public void test_price() {
    double computed = PRICER.price(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    LocalDate start = EUR_EURIBOR_3M.calculateEffectiveFromFixing(PRODUCT.getFixingDate());
    LocalDate end = EUR_EURIBOR_3M.calculateMaturityFromEffective(start);
    double fixingYearFraction = EUR_EURIBOR_3M.getDayCount().yearFraction(start, end);
    double convexity = HW_PROVIDER.futuresConvexityFactor(PRODUCT.getLastTradeDate(), start, end);
    double forward = RATE_PROVIDER.iborIndexRates(EUR_EURIBOR_3M).rate(PRODUCT.getFixingDate());
    double expected = 1d - convexity * forward + (1d - convexity) / fixingYearFraction;
    assertEquals(computed, expected, TOL);
  }

  public void test_parRate() {
    double computed = PRICER.parRate(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    double price = PRICER.price(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    assertEquals(computed, 1d - price, TOL);
  }

  public void test_convexityAdjustment() {
    double computed = PRICER.convexityAdjustment(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    double priceHw = PRICER.price(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    double priceDsc = PRICER_DSC.price(PRODUCT, RATE_PROVIDER); // no convexity adjustment
    assertEquals(priceDsc + computed, priceHw, TOL);
  }

  public void test_priceSensitivity() {
    PointSensitivities point = PRICER.priceSensitivity(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    CurveCurrencyParameterSensitivities computed = RATE_PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expected =
        FD_CAL.sensitivity(RATE_PROVIDER, (p) -> CurrencyAmount.of(EUR, PRICER.price(PRODUCT, (p), HW_PROVIDER)));
    assertTrue(computed.equalWithTolerance(expected, TOL_FD));
  }

  public void test_priceSensitivityHullWhiteParameter() {
    DoubleArray computed = PRICER.priceSensitivityHullWhiteParameter(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
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
      double priceUp = PRICER.price(PRODUCT, RATE_PROVIDER, provUp);
      double priceDw = PRICER.price(PRODUCT, RATE_PROVIDER, provDw);
      expected[i] = 0.5 * (priceUp - priceDw) / TOL_FD;
    }
    assertTrue(DoubleArrayMath.fuzzyEquals(computed.toArray(), expected, TOL_FD));
  }

  //-------------------------------------------------------------------------
  public void regression_value() {
    double price = PRICER.price(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    assertEquals(price, 0.9802338355115904, TOL);
    double parRate = PRICER.parRate(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    assertEquals(parRate, 0.01976616448840962, TOL);
    double adjustment = PRICER.convexityAdjustment(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    assertEquals(adjustment, -1.3766374738599652E-4, TOL);
  }

  public void regression_sensitivity() {
    PointSensitivities point = PRICER.priceSensitivity(PRODUCT, RATE_PROVIDER, HW_PROVIDER);
    CurveCurrencyParameterSensitivities computed = RATE_PROVIDER.curveParameterSensitivity(point);
    double[] expected = new double[] {0.0, 0.0, 0.9514709785770106, -1.9399920741192112, 0.0, 0.0, 0.0, 0.0 };
    assertEquals(computed.size(), 1);
    assertTrue(DoubleArrayMath.fuzzyEquals(computed.getSensitivity(HullWhiteIborFutureDataSet.FWD3_NAME, EUR)
        .getSensitivity().toArray(), expected, TOL));
  }
}
