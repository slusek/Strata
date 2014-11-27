/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.rate;

import static com.opengamma.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.basics.index.RateIndices.USD_FED_FUND;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.analytics.financial.instrument.index.IndexON;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.RateIndices;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.platform.finance.rate.OvernightCompoundedRate;
import com.opengamma.platform.finance.rate.Rate;
import com.opengamma.platform.pricer.SwapInstrumentsDataSet;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;
import com.opengamma.util.tuple.Pair;

/**
 * Test {@link DefaultOvernightCompoundedRateProviderFn}.
 */
@Test
public class DefaultOvernightCompoundedRateProviderFnTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 11, 25);

  private static final IborIndex USD_LIBOR_1M = RateIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = RateIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = RateIndices.USD_LIBOR_6M;
  public static final double FIXING_YEST = 0.00123;
  public static final double FIXING_TODAY = 0.00234;
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_MISSINGDATA =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 20), 0.00121)
      .put(LocalDate.of(2014, 11, 21), 0.00122)
      .build();
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_MISSINGDBY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 19), 0.00120)
      .put(LocalDate.of(2014, 11, 20), 0.00121)
      .build();
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHOUTYEST =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 19), 0.00120)
      .put(LocalDate.of(2014, 11, 20), 0.00121)
      .put(LocalDate.of(2014, 11, 21), 0.00122)
      .build();
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHOUTTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 19), 0.00120)
      .put(LocalDate.of(2014, 11, 20), 0.00121)
      .put(LocalDate.of(2014, 11, 21), 0.00122)
      .put(LocalDate.of(2014, 11, 24), FIXING_YEST)
      .build();
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 19), 0.00120)
      .put(LocalDate.of(2014, 11, 20), 0.00121)
      .put(LocalDate.of(2014, 11, 21), 0.00122)
      .put(LocalDate.of(2014, 11, 24), FIXING_YEST)
      .put(LocalDate.of(2014, 11, 25), FIXING_TODAY)
      .build();
  private static final Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_OIS_PAIR = 
      StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();
  private static final ImmutablePricingEnvironment ENV_MISSINGDATA = env(TS_USDFEDFUND_MISSINGDATA);
  private static final ImmutablePricingEnvironment ENV_MISSINGDBY = env(TS_USDFEDFUND_MISSINGDBY);
  private static final ImmutablePricingEnvironment ENV_WITHOUTYEST = env(TS_USDFEDFUND_WITHOUTYEST);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY = env(TS_USDFEDFUND_WITHOUTTODAY);
  private static final ImmutablePricingEnvironment ENV_WITHTODAY = env(TS_USDFEDFUND_WITHTODAY);
  private static final IndexON USD_FED_FUND_OGA = ENV_WITHTODAY.convert(USD_FED_FUND);
  private static final OvernightCompoundedRate ON_CMP_RATE = OvernightCompoundedRate.of(USD_FED_FUND);
  
  private static final LocalDate[] FIXING_DATES_TESTED = new LocalDate[] 
      {LocalDate.of(2014, 11, 26), LocalDate.of(2014, 12, 2), LocalDate.of(2014, 12, 23), LocalDate.of(2015, 11, 25),
    LocalDate.of(2016, 11, 25)};
  private static final int NB_TESTS = FIXING_DATES_TESTED.length;
  private static final RateProviderFn<OvernightCompoundedRate> ON_CMP_RATE_PROVIDER = DefaultOvernightCompoundedRateProviderFn.DEFAULT;
  private static final RateProviderFn<Rate> RATE_PROVIDER = DefaultRateProviderFn.DEFAULT;
  private static final double TOLERANCE_RATE = 1.0E-10;

  @Test
  public void rateStartTodayWithoutFixingToday() {
    LocalDate fixingStartDate = VALUATION_DATE;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(VALUATION_DATE);
    double fixingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double rateExpectedWithoutFixing = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHTODAY.relativeTime(VALUATION_DATE, fixingStartDate),
        ENV_WITHTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingYearFraction);
    double rateWithoutFixingComputed = 
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
    assertEquals(rateExpectedWithoutFixing, rateWithoutFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");
  }

  @Test
  public void rateStartYestWithFixingPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingRemainingStartDate = VALUATION_DATE;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixedYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingRemainingStartDate);
    double fixingRemainingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingRemainingStartDate, fixingEndDate);
    double rateFwdExpectedWithFixing = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHTODAY.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateWithFixingExpected = ( (1.0d + fixedYearFraction * FIXING_YEST) * 
        (1.0d + fixingRemainingYearFraction * rateFwdExpectedWithFixing) - 1.0d ) / fixingYearFraction;
    double rateWithFixingComputed = 
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
    assertEquals(rateWithFixingExpected, rateWithFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");    
  }

  @Test
  public void rateStartYestWithoutFixingPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingRemainingStartDate = fixingStartDate;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingRemainingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingRemainingStartDate, fixingEndDate);
    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHTODAY.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateWithFixingExpected = rateFwdExpected;
    double rateWithFixingComputed = 
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
    assertEquals(rateWithFixingExpected, rateWithFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");    
  }

  @Test
  public void rateStartPastWithoutTodayFixingPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(7);
    LocalDate fixingRemainingStartDate = VALUATION_DATE;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixingRemainingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingRemainingStartDate, fixingEndDate);
    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateWithFixingExpected = ( (1.0 + 0.00119/360.0) * (1.0 + 0.00120/360.0) * (1.0 + 0.00121/360.0) 
        * (1.0 + 0.00122*3.0/360.0) * (1.0 + FIXING_YEST/360.0) 
        * (1.0d + fixingRemainingYearFraction * rateFwdExpected) - 1.0d ) / fixingYearFraction;
    double rateWithFixingComputed = 
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
    assertEquals(rateWithFixingExpected, rateWithFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");    
  }
  
  // TODO: add test with full period fixed
  
  //TODO: Add tests with publication lag 0 and with T/N (CHF)

  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartPastMissingFixingDFYPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(7);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    ON_CMP_RATE_PROVIDER.rate(ENV_MISSINGDBY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
  }

  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartPastMissingDataPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(7);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    ON_CMP_RATE_PROVIDER.rate(ENV_MISSINGDATA, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
  }
  
  @Test
  public void rateForward() {
    for(int i = 0; i < NB_TESTS ; i++) {
      LocalDate fixingStartDate = USD_LIBOR_3M.calculateEffectiveFromFixing(FIXING_DATES_TESTED[i]);
      LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
      double fixingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
      double rateExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
          ENV_WITHTODAY.relativeTime(VALUATION_DATE, fixingStartDate),
          ENV_WITHTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingYearFraction);
      double rateOnFixingComputed = 
          ON_CMP_RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
      assertEquals(rateExpected, rateOnFixingComputed, TOLERANCE_RATE,
          "DefaultIborRateProviderFn: rate forward");
      double rateGenFixingComputed = 
          RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
      assertEquals(rateGenFixingComputed, rateOnFixingComputed, TOLERANCE_RATE,
          "DefaultIborRateProviderFn: rate forward");
    }
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
            USD_LIBOR_3M, SwapInstrumentsDataSet.TS_USDLIBOR3M,
            USD_LIBOR_6M, SwapInstrumentsDataSet.TS_USDLIBOR6M,
            USD_FED_FUND, ts))
        .dayCount(ACT_ACT_ISDA)
        .build();
  }

}
