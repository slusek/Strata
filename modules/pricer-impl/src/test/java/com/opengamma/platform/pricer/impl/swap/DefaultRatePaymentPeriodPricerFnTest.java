/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.swap;

import static com.opengamma.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.basics.index.OvernightIndices.USD_FED_FUND;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.basics.currency.Currency;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.IborIndices;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.platform.finance.rate.FixedRate;
import com.opengamma.platform.finance.rate.IborRate;
import com.opengamma.platform.finance.swap.CompoundingMethod;
import com.opengamma.platform.finance.swap.PaymentPeriod;
import com.opengamma.platform.finance.swap.RateAccrualPeriod;
import com.opengamma.platform.finance.swap.RatePaymentPeriod;
import com.opengamma.platform.pricer.SwapInstrumentsDataSet;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.impl.rate.DefaultRateProviderFn;
import com.opengamma.util.tuple.Pair;

/**
 * Test {@link DefaultRatePaymentPeriodPricerFn}.
 */
public class DefaultRatePaymentPeriodPricerFnTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 1, 22);

  private static final IborIndex USD_LIBOR_1M = IborIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = IborIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = IborIndices.USD_LIBOR_6M;
  public static final double FIXING_TODAY = 0.00234;
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_WITHTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 1, 21), 0.00123)
      .put(LocalDate.of(2014, 1, 22), FIXING_TODAY)
      .build();
  private static final Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_OIS_PAIR = 
      StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();
  private static final ImmutablePricingEnvironment ENV_WITHTODAY = env(TS_USDLIBOR3M_WITHTODAY);
  
  private static final DefaultPaymentPeriodPricerFn PAYMENT_PERIOD_PRICER = DefaultPaymentPeriodPricerFn.DEFAULT;
  private static final DefaultRateProviderFn RATE_PROVIDER = DefaultRateProviderFn.DEFAULT;
  
  private static final Currency USD = Currency.USD;
  private static final double RATE = 0.0125;
  private static final FixedRate FIXED_RATE = FixedRate.of(RATE);
  private static final LocalDate IBOR_FIXING_DATE_1 = LocalDate.of(2014, 1, 22);
  private static final IborRate IBOR_RATE_1 = IborRate.of(USD_LIBOR_3M, IBOR_FIXING_DATE_1);
  private static final LocalDate IBOR_FIXING_DATE_2 = LocalDate.of(2014, 2, 24);
  private static final IborRate IBOR_RATE_2 = IborRate.of(USD_LIBOR_3M, IBOR_FIXING_DATE_2);
  private static final LocalDate IBOR_FIXING_DATE_3 = LocalDate.of(2014, 3, 24);
  private static final IborRate IBOR_RATE_3 = IborRate.of(USD_LIBOR_3M, IBOR_FIXING_DATE_3);

  private static final double NOTIONAL_100 = 1.0E8;
  private static final LocalDate CPN_DATE_1 = LocalDate.of(2014, 1, 24);
  private static final LocalDate CPN_DATE_2 = LocalDate.of(2014, 2, 24);
  private static final LocalDate CPN_DATE_3 = LocalDate.of(2014, 3, 24);
  private static final LocalDate CPN_DATE_4 = LocalDate.of(2014, 4, 24);
  private static final double ACCRUAL_FACTOR_1 = 0.25;
  private static final double ACCRUAL_FACTOR_2 = 0.35;
  private static final LocalDate PAYMENT_DATE_1 = LocalDate.of(2014, 4, 26);
  private static final double GEARING = 2.0;
  private static final double SPREAD = 0.0025;

  private static final RateAccrualPeriod ACCRUAL_PERIOD_FIXED_1 = RateAccrualPeriod.builder().startDate(CPN_DATE_1)
      .unadjustedStartDate(CPN_DATE_1).endDate(CPN_DATE_2).unadjustedEndDate(CPN_DATE_2).yearFraction(ACCRUAL_FACTOR_1)
      .rate(FIXED_RATE).build();
  private static final RateAccrualPeriod ACCRUAL_PERIOD_IBOR_1 = RateAccrualPeriod.builder().startDate(CPN_DATE_1)
      .unadjustedStartDate(CPN_DATE_1).endDate(CPN_DATE_2).unadjustedEndDate(CPN_DATE_2).yearFraction(ACCRUAL_FACTOR_1)
      .rate(IBOR_RATE_1).build();
  private static final RateAccrualPeriod ACCRUAL_PERIOD_IBOR_2 = RateAccrualPeriod.builder().startDate(CPN_DATE_1)
      .unadjustedStartDate(CPN_DATE_1).endDate(CPN_DATE_2).unadjustedEndDate(CPN_DATE_2).yearFraction(ACCRUAL_FACTOR_1)
      .rate(IBOR_RATE_1).build();
  private static final RateAccrualPeriod ACCRUAL_PERIOD_IBOR_3 = RateAccrualPeriod.builder().startDate(CPN_DATE_2)
      .unadjustedStartDate(CPN_DATE_2).endDate(CPN_DATE_3).unadjustedEndDate(CPN_DATE_3).yearFraction(ACCRUAL_FACTOR_2)
      .rate(IBOR_RATE_2).build();
  private static final RateAccrualPeriod ACCRUAL_PERIOD_IBOR_2_SPREAD = RateAccrualPeriod.builder().startDate(CPN_DATE_1)
      .unadjustedStartDate(CPN_DATE_1).endDate(CPN_DATE_2).unadjustedEndDate(CPN_DATE_2).yearFraction(ACCRUAL_FACTOR_1)
      .rate(IBOR_RATE_1).spread(SPREAD).build();
  private static final RateAccrualPeriod ACCRUAL_PERIOD_IBOR_3_SPREAD = RateAccrualPeriod.builder().startDate(CPN_DATE_2)
      .unadjustedStartDate(CPN_DATE_2).endDate(CPN_DATE_3).unadjustedEndDate(CPN_DATE_3).yearFraction(ACCRUAL_FACTOR_2)
      .rate(IBOR_RATE_2).spread(SPREAD).build();
  private static final RateAccrualPeriod ACCRUAL_PERIOD_IBOR_4_SPREAD = RateAccrualPeriod.builder().startDate(CPN_DATE_3)
      .unadjustedStartDate(CPN_DATE_3).endDate(CPN_DATE_4).unadjustedEndDate(CPN_DATE_4).yearFraction(ACCRUAL_FACTOR_2)
      .rate(IBOR_RATE_3).spread(SPREAD).build();
  private static final RateAccrualPeriod ACCRUAL_PERIOD_IBOR_SPREAD_GEARING = RateAccrualPeriod.builder()
      .startDate(CPN_DATE_2).unadjustedStartDate(CPN_DATE_2).endDate(CPN_DATE_3).unadjustedEndDate(CPN_DATE_3)
      .yearFraction(ACCRUAL_FACTOR_2).rate(IBOR_RATE_2).gearing(GEARING).spread(SPREAD).build();

  private static final RatePaymentPeriod PAYMENT_PERIOD_1FIXED = RatePaymentPeriod.builder()
      .paymentDate(PAYMENT_DATE_1).currency(USD).notional(NOTIONAL_100)
      .accrualPeriods(ImmutableList.of(ACCRUAL_PERIOD_FIXED_1)).build();
  private static final RatePaymentPeriod PAYMENT_PERIOD_1IBOR = RatePaymentPeriod.builder().paymentDate(PAYMENT_DATE_1)
      .currency(USD).notional(NOTIONAL_100).accrualPeriods(ImmutableList.of(ACCRUAL_PERIOD_IBOR_1)).build();
  private static final RatePaymentPeriod PAYMENT_PERIOD_1IBOR_1FIXED = RatePaymentPeriod.builder()
      .paymentDate(PAYMENT_DATE_1).currency(USD).notional(NOTIONAL_100)
      .accrualPeriods(ImmutableList.of(ACCRUAL_PERIOD_IBOR_1, ACCRUAL_PERIOD_FIXED_1)).build();
  private static final RatePaymentPeriod PAYMENT_PERIOD_2IBOR = RatePaymentPeriod.builder().paymentDate(PAYMENT_DATE_1)
      .currency(USD).notional(NOTIONAL_100)
      .accrualPeriods(ImmutableList.of(ACCRUAL_PERIOD_IBOR_2, ACCRUAL_PERIOD_IBOR_3)).build();
  private static final RatePaymentPeriod PAYMENT_PERIOD_2IBOR_SPREAD_GEARING = RatePaymentPeriod.builder()
      .paymentDate(PAYMENT_DATE_1).currency(USD).notional(NOTIONAL_100)
      .accrualPeriods(ImmutableList.of(ACCRUAL_PERIOD_IBOR_2, ACCRUAL_PERIOD_IBOR_SPREAD_GEARING)).build();
  private static final RatePaymentPeriod PAYMENT_PERIOD_3IBOR_FLAT = RatePaymentPeriod.builder()
      .paymentDate(PAYMENT_DATE_1).currency(USD).notional(NOTIONAL_100).compoundingMethod(CompoundingMethod.FLAT)
      .accrualPeriods(ImmutableList.of(ACCRUAL_PERIOD_IBOR_2_SPREAD, ACCRUAL_PERIOD_IBOR_3_SPREAD, ACCRUAL_PERIOD_IBOR_4_SPREAD))
      .build();
  private static final RatePaymentPeriod PAYMENT_PERIOD_3IBOR_SPREAD_EXCLUSIVE = RatePaymentPeriod.builder()
      .paymentDate(PAYMENT_DATE_1).currency(USD).notional(NOTIONAL_100).compoundingMethod(CompoundingMethod.SPREAD_EXCLUSIVE)
      .accrualPeriods(ImmutableList.of(ACCRUAL_PERIOD_IBOR_2_SPREAD, ACCRUAL_PERIOD_IBOR_3_SPREAD, ACCRUAL_PERIOD_IBOR_4_SPREAD))
      .build();
  private static final RatePaymentPeriod PAYMENT_PERIOD_3IBOR_SPREAD_STRAIGHT = RatePaymentPeriod.builder()
      .paymentDate(PAYMENT_DATE_1).currency(USD).notional(NOTIONAL_100).compoundingMethod(CompoundingMethod.STRAIGHT)
      .accrualPeriods(ImmutableList.of(ACCRUAL_PERIOD_IBOR_2_SPREAD, ACCRUAL_PERIOD_IBOR_3_SPREAD, ACCRUAL_PERIOD_IBOR_4_SPREAD))
      .build();
  
  /* Constants */
  private static final double TOLERANCE_PV = 1.0E-2;
  
  /* No FX reset, no composition. */
  @Test
  public void futureValue1Fixed() {
    double fvExpected = RATE * ACCRUAL_FACTOR_1 * NOTIONAL_100;
    double fvComputed = PAYMENT_PERIOD_PRICER.futureValue(ENV_WITHTODAY, VALUATION_DATE, PAYMENT_PERIOD_1FIXED);    
    assertEquals(fvExpected, fvComputed, TOLERANCE_PV, "DefaultRatePaymentPeriodPricerFn: future value");
  }
  
  @Test
  public void futureValue1Ibor() {
    double forwardRate = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_1, CPN_DATE_1, CPN_DATE_2);
    double fvExpected = forwardRate * ACCRUAL_FACTOR_1 * NOTIONAL_100;
    double fvComputed = PAYMENT_PERIOD_PRICER.futureValue(ENV_WITHTODAY, VALUATION_DATE, PAYMENT_PERIOD_1IBOR);    
    assertEquals(fvExpected, fvComputed, TOLERANCE_PV, "DefaultRatePaymentPeriodPricerFn: future value");
  }
  
  @Test
  public void futureValue1Ibor1Fixed() {
    double forwardRate = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_1, CPN_DATE_1, CPN_DATE_2);
    double fvExpected = forwardRate * ACCRUAL_FACTOR_1 * NOTIONAL_100 + RATE * ACCRUAL_FACTOR_1 * NOTIONAL_100;
    double fvComputed = PAYMENT_PERIOD_PRICER.futureValue(ENV_WITHTODAY, VALUATION_DATE, PAYMENT_PERIOD_1IBOR_1FIXED);    
    assertEquals(fvExpected, fvComputed, TOLERANCE_PV, "DefaultRatePaymentPeriodPricerFn: future value");
  }
  
  @Test
  public void futureValue2Ibor() {
    double forwardRate1 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_1, CPN_DATE_1, CPN_DATE_2);
    double forwardRate2 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_2, CPN_DATE_2, CPN_DATE_3);
    double fvExpected = (forwardRate1 * ACCRUAL_FACTOR_1 + forwardRate2 * ACCRUAL_FACTOR_2) * NOTIONAL_100;
    double fvComputed = PAYMENT_PERIOD_PRICER.futureValue(ENV_WITHTODAY, VALUATION_DATE, PAYMENT_PERIOD_2IBOR);    
    assertEquals(fvExpected, fvComputed, TOLERANCE_PV, "DefaultRatePaymentPeriodPricerFn: future value");
  }
  
  @Test
  public void futureValue2IborSpreadGearing() {
    double forwardRate1 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_1, CPN_DATE_1, CPN_DATE_2);
    double forwardRate2 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_2, CPN_DATE_2, CPN_DATE_3);
    double fvExpected = (forwardRate1 * ACCRUAL_FACTOR_1 + (GEARING * forwardRate2 + SPREAD) * ACCRUAL_FACTOR_2)
        * NOTIONAL_100;
    double fvComputed = PAYMENT_PERIOD_PRICER.futureValue(ENV_WITHTODAY, VALUATION_DATE, PAYMENT_PERIOD_2IBOR_SPREAD_GEARING);    
    assertEquals(fvExpected, fvComputed, TOLERANCE_PV, "DefaultRatePaymentPeriodPricerFn: future value");
  }
  
  @Test
  public void presentValueNoFxResetNoComp() {
    presentValueTest(PAYMENT_PERIOD_1FIXED);
    presentValueTest(PAYMENT_PERIOD_1IBOR);
    presentValueTest(PAYMENT_PERIOD_1IBOR_1FIXED);
    presentValueTest(PAYMENT_PERIOD_2IBOR);
    presentValueTest(PAYMENT_PERIOD_2IBOR_SPREAD_GEARING);
  }
  
  /* No FX reset, composition. */
  
  @Test
  public void futureValue2IborFlat() {
    double rate1 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_1, CPN_DATE_1, CPN_DATE_2);
    double rate2 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_2, CPN_DATE_2, CPN_DATE_3);
    double rate3 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_3, CPN_DATE_3, CPN_DATE_4);
    double cpa1 = NOTIONAL_100 * (rate1 + SPREAD) * ACCRUAL_FACTOR_1;
    double cpa2 = cpa1 * rate2 * ACCRUAL_FACTOR_2 + NOTIONAL_100 * (rate2 + SPREAD) * ACCRUAL_FACTOR_2;
    double cpa3 = (cpa1 + cpa2) * rate3 * ACCRUAL_FACTOR_2 + NOTIONAL_100 * (rate3 + SPREAD) * ACCRUAL_FACTOR_2;
    double fvExpected = cpa1 + cpa2 + cpa3;
    double fvComputed = PAYMENT_PERIOD_PRICER.futureValue(ENV_WITHTODAY, VALUATION_DATE, PAYMENT_PERIOD_3IBOR_FLAT);
    assertEquals(fvExpected, fvComputed, TOLERANCE_PV, "DefaultRatePaymentPeriodPricerFn: future value");
  }
  
  @Test
  public void futureValue2IborSpreadExcl() {
    double rate1 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_1, CPN_DATE_1, CPN_DATE_2);
    double rate2 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_2, CPN_DATE_2, CPN_DATE_3);
    double rate3 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_3, CPN_DATE_3, CPN_DATE_4);
    double if1 = 1d + rate1 * ACCRUAL_FACTOR_1;
    double if2 = 1d + rate2 * ACCRUAL_FACTOR_2;
    double if3 = 1d + rate3 * ACCRUAL_FACTOR_2;
    double spreadAccrued = SPREAD * (ACCRUAL_FACTOR_1 + ACCRUAL_FACTOR_2 + ACCRUAL_FACTOR_2);
    double fvExpected = NOTIONAL_100 * (if1 * if2 * if3 - 1 + spreadAccrued);
    double fvComputed = PAYMENT_PERIOD_PRICER.futureValue(ENV_WITHTODAY, VALUATION_DATE, PAYMENT_PERIOD_3IBOR_SPREAD_EXCLUSIVE);
    assertEquals(fvExpected, fvComputed, TOLERANCE_PV, "DefaultRatePaymentPeriodPricerFn: future value");
  }
  
  @Test
  public void futureValue2IborComp() {
    double rate1 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_1, CPN_DATE_1, CPN_DATE_2);
    double rate2 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_2, CPN_DATE_2, CPN_DATE_3);
    double rate3 = RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_RATE_3, CPN_DATE_3, CPN_DATE_4);
    double if1 = 1d + (rate1 + SPREAD) * ACCRUAL_FACTOR_1;
    double if2 = 1d + (rate2 + SPREAD) * ACCRUAL_FACTOR_2;
    double if3 = 1d + (rate3 + SPREAD) * ACCRUAL_FACTOR_2;
    double fvExpected = NOTIONAL_100 * (if1 * if2 * if3 - 1);
    double fvComputed = PAYMENT_PERIOD_PRICER.futureValue(ENV_WITHTODAY, VALUATION_DATE, PAYMENT_PERIOD_3IBOR_SPREAD_STRAIGHT);
    assertEquals(fvExpected, fvComputed, TOLERANCE_PV, "DefaultRatePaymentPeriodPricerFn: future value");
  }
  
  @Test
  public void presentValueNoFxResetComp() {
    presentValueTest(PAYMENT_PERIOD_3IBOR_FLAT);
    presentValueTest(PAYMENT_PERIOD_3IBOR_SPREAD_EXCLUSIVE);
    presentValueTest(PAYMENT_PERIOD_3IBOR_SPREAD_STRAIGHT);
  }
  
  /* FX reset, no composition. */ // TODO
  
  // -------------------------------------------------------
  
  public void presentValueTest(PaymentPeriod paymentPeriod) {
    double fvComputed = PAYMENT_PERIOD_PRICER.futureValue(ENV_WITHTODAY, VALUATION_DATE, paymentPeriod);
    double df = ENV_WITHTODAY.discountFactor(USD, VALUATION_DATE, paymentPeriod.getPaymentDate());
    double pvExpected = fvComputed * df;
    double pvComputed = PAYMENT_PERIOD_PRICER.presentValue(ENV_WITHTODAY, VALUATION_DATE, paymentPeriod);
    assertEquals(pvExpected, pvComputed, TOLERANCE_PV, "DefaultRatePaymentPeriodPricerFn: future value");
  }

  /**
   * Create a pricing environment from the existing MulticurveProvider and Ibor fixing time series.
   * @param ts The time series for the USDLIBOR3M.
   * @return The pricing environment.
   */
  private static ImmutablePricingEnvironment env(LocalDateDoubleTimeSeries ts) {
    return ImmutablePricingEnvironment.builder()
        .multicurve(MULTICURVE_OIS)
        .timeSeries(ImmutableMap.of(
            USD_LIBOR_1M, SwapInstrumentsDataSet.TS_USDLIBOR1M,
            USD_LIBOR_3M, ts,
            USD_LIBOR_6M, SwapInstrumentsDataSet.TS_USDLIBOR6M,
            USD_FED_FUND, SwapInstrumentsDataSet.TS_USDON))
        .dayCount(ACT_ACT_ISDA)
        .build();
  }
  
}
