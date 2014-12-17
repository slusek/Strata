/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.rate;

import static com.opengamma.basics.currency.Currency.USD;
import static com.opengamma.basics.date.DayCounts.ACT_360;
import static com.opengamma.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.basics.date.HolidayCalendars.NYFD;
import static com.opengamma.basics.index.OvernightIndices.USD_FED_FUND;
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
import com.opengamma.basics.index.IborIndices;
import com.opengamma.basics.index.ImmutableOvernightIndex;
import com.opengamma.basics.index.OvernightIndex;
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
  // TODO Tests for 0, -1 publication lags should be done with e.g., GBP, CHF.
  // Currently these cases are tested by modifying FedFund in order to make use of the setup in Platform 2.x

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 11, 25);

  private static final IborIndex USD_LIBOR_1M = IborIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = IborIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = IborIndices.USD_LIBOR_6M;
  private static final double FIXING_YEST = 0.00123;
  private static final double FIXING_TODAY = 0.00234;
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_MISSINGDATA =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 20), 0.00121)
      .put(LocalDate.of(2014, 11, 21), 0.00122)
      .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_MISSINGDBY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 19), 0.00120)
      .put(LocalDate.of(2014, 11, 20), 0.00121)
      .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHOUTYEST =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 19), 0.00120)
      .put(LocalDate.of(2014, 11, 20), 0.00121)
      .put(LocalDate.of(2014, 11, 21), 0.00122)
      .build();
  /* today is set to be 2014/11/24 when using this set */
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHOUTYEST_1M =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 10, 23), 0.00119)
      .put(LocalDate.of(2014, 10, 24), 0.00122)
      .put(LocalDate.of(2014, 10, 27), 0.00119)
      .put(LocalDate.of(2014, 10, 28), 0.00119)
      .put(LocalDate.of(2014, 10, 29), 0.00119)
      .put(LocalDate.of(2014, 10, 30), 0.00119)
      .put(LocalDate.of(2014, 10, 31), 0.00122)
      .put(LocalDate.of(2014, 11, 3), 0.00119)
      .put(LocalDate.of(2014, 11, 4), 0.00119)
      .put(LocalDate.of(2014, 11, 5), 0.00119)
      .put(LocalDate.of(2014, 11, 6), 0.00119)
      .put(LocalDate.of(2014, 11, 7), 0.00122)
      .put(LocalDate.of(2014, 11, 10), 0.00121)
      .put(LocalDate.of(2014, 11, 12), 0.00119)
      .put(LocalDate.of(2014, 11, 13), 0.00119)
      .put(LocalDate.of(2014, 11, 14), 0.00122)
      .put(LocalDate.of(2014, 11, 17), 0.00119)
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 19), 0.00119)
      .put(LocalDate.of(2014, 11, 20), 0.00119)
      .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHOUTTODAY =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 17), 0.00117)
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 19), 0.00120)
      .put(LocalDate.of(2014, 11, 20), 0.00121)
      .put(LocalDate.of(2014, 11, 21), 0.00122)
      .put(LocalDate.of(2014, 11, 24), FIXING_YEST)
      .build();
  /* today is set to be 2014/11/24 when using this set */
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHOUTTODAY_1M =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 10, 23), 0.00119)
      .put(LocalDate.of(2014, 10, 24), 0.00122)
      .put(LocalDate.of(2014, 10, 27), 0.00119)
      .put(LocalDate.of(2014, 10, 28), 0.00119)
      .put(LocalDate.of(2014, 10, 29), 0.00119)
      .put(LocalDate.of(2014, 10, 30), 0.00119)
      .put(LocalDate.of(2014, 10, 31), 0.00122)
      .put(LocalDate.of(2014, 11, 3), 0.00119)
      .put(LocalDate.of(2014, 11, 4), 0.00119)
      .put(LocalDate.of(2014, 11, 5), 0.00119)
      .put(LocalDate.of(2014, 11, 6), 0.00119)
      .put(LocalDate.of(2014, 11, 7), 0.00122)
      .put(LocalDate.of(2014, 11, 10), 0.00121)
      .put(LocalDate.of(2014, 11, 12), 0.00119)
      .put(LocalDate.of(2014, 11, 13), 0.00119)
      .put(LocalDate.of(2014, 11, 14), 0.00122)
      .put(LocalDate.of(2014, 11, 17), 0.00119)
      .put(LocalDate.of(2014, 11, 18), 0.00119)
      .put(LocalDate.of(2014, 11, 19), 0.00119)
      .put(LocalDate.of(2014, 11, 20), 0.00119)
      .put(LocalDate.of(2014, 11, 21), 0.00122)
      .build();
  /* today is set to be 2014/11/24 when using this set */
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHOUTTODAY_1M_CUTOFF =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 10, 23), 0.00119)
      .put(LocalDate.of(2014, 10, 24), 0.00122)
      .put(LocalDate.of(2014, 10, 27), 0.00119)
      .put(LocalDate.of(2014, 10, 28), 0.00119)
      .put(LocalDate.of(2014, 10, 29), 0.00119)
      .put(LocalDate.of(2014, 10, 30), 0.00119)
      .put(LocalDate.of(2014, 10, 31), 0.00122)
      .put(LocalDate.of(2014, 11, 3), 0.00119)
      .put(LocalDate.of(2014, 11, 4), 0.00113)
      .put(LocalDate.of(2014, 11, 5), 0.00114)
      .put(LocalDate.of(2014, 11, 6), 0.00115)
      .put(LocalDate.of(2014, 11, 7), 0.00116)
      .put(LocalDate.of(2014, 11, 10), 0.00117)
      .put(LocalDate.of(2014, 11, 12), 0.00118)
      .put(LocalDate.of(2014, 11, 13), 0.00121)
      .put(LocalDate.of(2014, 11, 14), 0.00120)
      .put(LocalDate.of(2014, 11, 17), 0.00119)
      .put(LocalDate.of(2014, 11, 18), 0.00125)
      .put(LocalDate.of(2014, 11, 19), 0.00124)
      .put(LocalDate.of(2014, 11, 20), 0.00123)
      .put(LocalDate.of(2014, 11, 21), 0.00122)
      .build();
  /* today is set to be 2014/11/24 when using this set */
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHOUTYEST_1M_CUTOFF =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 10, 23), 0.00119)
      .put(LocalDate.of(2014, 10, 24), 0.00122)
      .put(LocalDate.of(2014, 10, 27), 0.00119)
      .put(LocalDate.of(2014, 10, 28), 0.00119)
      .put(LocalDate.of(2014, 10, 29), 0.00119)
      .put(LocalDate.of(2014, 10, 30), 0.00119)
      .put(LocalDate.of(2014, 10, 31), 0.00122)
      .put(LocalDate.of(2014, 11, 3), 0.00119)
      .put(LocalDate.of(2014, 11, 4), 0.00113)
      .put(LocalDate.of(2014, 11, 5), 0.00114)
      .put(LocalDate.of(2014, 11, 6), 0.00115)
      .put(LocalDate.of(2014, 11, 7), 0.00116)
      .put(LocalDate.of(2014, 11, 10), 0.00117)
      .put(LocalDate.of(2014, 11, 12), 0.00118)
      .put(LocalDate.of(2014, 11, 13), 0.00121)
      .put(LocalDate.of(2014, 11, 14), 0.00120)
      .put(LocalDate.of(2014, 11, 17), 0.00119)
      .put(LocalDate.of(2014, 11, 18), 0.00125)
      .put(LocalDate.of(2014, 11, 19), 0.00124)
      .put(LocalDate.of(2014, 11, 20), 0.00123)
      .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_WITHTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 17), 0.00117)
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

  /*
   * Tests for publication lag = +1.
   */
  private static final ImmutablePricingEnvironment ENV_MISSINGDATA = env(TS_USDFEDFUND_MISSINGDATA, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_MISSINGDBY = env(TS_USDFEDFUND_MISSINGDBY, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_WITHOUTYEST = env(TS_USDFEDFUND_WITHOUTYEST, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY = env(TS_USDFEDFUND_WITHOUTTODAY, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY_1M = env(TS_USDFEDFUND_WITHOUTTODAY_1M,
      USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_WITHTODAY = env(TS_USDFEDFUND_WITHTODAY, USD_FED_FUND);
  private static final IndexON USD_FED_FUND_OGA = ENV_WITHTODAY.convert(USD_FED_FUND);
  private static final OvernightCompoundedRate ON_CMP_RATE = OvernightCompoundedRate.of(USD_FED_FUND);
  private static final LocalDate[] FIXING_DATES_TESTED = new LocalDate[] 
      {LocalDate.of(2014, 11, 26), LocalDate.of(2014, 12, 2), LocalDate.of(2014, 12, 23), LocalDate.of(2015, 11, 25),
    LocalDate.of(2016, 11, 25)};
  private static final int NB_TESTS = FIXING_DATES_TESTED.length;
  private static final RateProviderFn<OvernightCompoundedRate> ON_CMP_RATE_PROVIDER = DefaultOvernightCompoundedRateProviderFn.DEFAULT;
  private static final RateProviderFn<Rate> RATE_PROVIDER = DefaultRateProviderFn.DEFAULT;
  private static final double TOLERANCE_RATE = 1.0E-10;

  /**
   * Period starts today without fixing today. 
   */
  @Test
  public void rateStartTodayWithoutFixingTodayPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(VALUATION_DATE);
    double fixingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double rateExpectedWithoutFixing = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingStartDate),
        ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingYearFraction);
    double rateWithoutFixingComputed = 
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
    assertEquals(rateExpectedWithoutFixing, rateWithoutFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts yesterday with fixing up to yesterday. 
   */
  @Test
  public void rateStartYestWithFixingPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingRemainingStartDate = VALUATION_DATE;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixedYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingRemainingStartDate);
    double fixingRemainingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingRemainingStartDate, fixingEndDate);
    double rateFwdExpectedWithFixing = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateWithFixingExpected = ( (1.0d + fixedYearFraction * FIXING_YEST) * 
        (1.0d + fixingRemainingYearFraction * rateFwdExpectedWithFixing) - 1.0d ) / fixingYearFraction;
    double rateWithFixingComputed = 
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
    assertEquals(rateWithFixingExpected, rateWithFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts yesterday without fixing yesterday, which is not yet published today.
   */
  @Test
  public void rateStartYestWithoutFixingPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingRemainingStartDate = fixingStartDate;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingRemainingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingRemainingStartDate, fixingEndDate);
    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTYEST.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHOUTYEST.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateWithFixingExpected = rateFwdExpected;
    double rateWithFixingComputed = 
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
    assertEquals(rateWithFixingExpected, rateWithFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");    
  }

  /**
   * Period starts before yesterday with fixing up to yesterday. 
   */
  @Test
  public void rateStartPastWithYestFixingPubLag1() {
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

  /**
   * Period start before yesterday without fixing yesterday, which is not yet published today. 
   */
  @Test
  public void rateStartPastWithoutYestFixingPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(7);
    LocalDate fixingRemainingStartDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixingRemainingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingRemainingStartDate, fixingEndDate);
    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTYEST.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHOUTYEST.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateWithFixingExpected = ( (1.0 + 0.00119/360.0) * (1.0 + 0.00120/360.0) * (1.0 + 0.00121/360.0) 
        * (1.0 + 0.00122*3.0/360.0) 
        * (1.0d + fixingRemainingYearFraction * rateFwdExpected) - 1.0d ) / fixingYearFraction;
    double rateWithFixingComputed = 
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
    assertEquals(rateWithFixingExpected, rateWithFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");    
  }
  
  /**
   * Period starts before yesterday and ends today with fixing up to yesterday. 
   */
  @Test
  public void rateStartPastEndTodayFixingPubLag1() {
    LocalDate valuationDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingStartDate = valuationDate.minusMonths(1);
    LocalDate fixingEndDate = USD_LIBOR_1M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double rateWithFixingExpected = (Math.pow((1.0 + 0.00119 / 360.0), 14) * (1.0 + 0.00121 * 2.0 / 360.0) *
        Math.pow((1.0 + 0.00122 * 3.0 / 360.0), 5) - 1.0) / fixingYearFraction;
    double rateWithFixingComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_1M, valuationDate, ON_CMP_RATE, fixingStartDate, fixingEndDate);
    assertEquals(rateWithFixingExpected, rateWithFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");
  }
  
  /**
   * Today's fixing is available for the publication leg +1 case, i.e., the time series is wrong. 
   * Implementation is to ignore the today's fixing rather than to return an error. 
   * Thus rate is produced "correctly."  
   */
  @Test
  public void rateStartTodayWithFixingToday1() {
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

  /**
   * Totally unfixed. 
   */
  @Test
  public void rateForward() {
    for (int i = 0; i < NB_TESTS; i++) {
      LocalDate fixingStartDate = USD_LIBOR_3M.calculateEffectiveFromFixing(FIXING_DATES_TESTED[i]);
      LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
      double fixingYearFraction = USD_FED_FUND.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
      double rateExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
          ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingStartDate),
          ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingYearFraction);
      double rateOnFixingComputed =
          ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
      assertEquals(rateExpected, rateOnFixingComputed, TOLERANCE_RATE,
          "DefaultIborRateProviderFn: rate forward");
      double rateGenFixingComputed =
          RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
      assertEquals(rateGenFixingComputed, rateOnFixingComputed, TOLERANCE_RATE,
          "DefaultIborRateProviderFn: rate forward");
    }
  }

  /**
   * Period starts before yesterday, missing fixing index rate in the past. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartPastMissingFixingDFYPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(7);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    ON_CMP_RATE_PROVIDER.rate(ENV_MISSINGDBY, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
  }

  /**
   * Period starts before yesterday, missing the latest index rate. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartPastMissingDataPubLag1() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(7);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    ON_CMP_RATE_PROVIDER.rate(ENV_MISSINGDATA, VALUATION_DATE, ON_CMP_RATE, fixingStartDate, fixingEndDate);
  }

  /*
   * Tests for publication lag = 0. 
   * 
   * Modify the index s.t. publication lag = 0. 
   * ImmutablePricingEnvironment and OvernightCompoundedRate should be also modified. 
   * Note that we continue to use "USD-FED-FUND" to pick up the curve in the multicurve. 
   */
  private static final OvernightIndex INDEX_PUB_LAG_ZERO = ImmutableOvernightIndex.builder().name("USD-FED-FUND")
      .currency(USD).fixingCalendar(NYFD).publicationDateOffset(0).effectiveDateOffset(0).dayCount(ACT_360).build();
  private static final OvernightCompoundedRate ON_CMP_RATE_ZERO = OvernightCompoundedRate.of(INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_WITHTODAY_ZERO = env(TS_USDFEDFUND_WITHTODAY, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY_ZERO = env(TS_USDFEDFUND_WITHOUTTODAY,
      INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_WITHOUTYEST_ZERO = env(TS_USDFEDFUND_WITHOUTYEST,
      INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_WITHOUTYEST_ZERO_1M = env(TS_USDFEDFUND_WITHOUTYEST_1M,
      INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY_ZERO_1M = env(TS_USDFEDFUND_WITHOUTTODAY_1M,
      INDEX_PUB_LAG_ZERO);

  /**
   * Period starts today, with fixing up to yesterday. 
   */
  @Test
  public void rateStartTodayWithFixingYestPubLag0() {
    LocalDate fixingStartDate = VALUATION_DATE;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(VALUATION_DATE);
    double fixingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingEndDate);

    double rateExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTTODAY_ZERO.relativeTime(VALUATION_DATE, fixingStartDate),
        ENV_WITHOUTTODAY_ZERO.relativeTime(VALUATION_DATE, fixingEndDate), fixingYearFraction);
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO,
        fixingStartDate, fixingEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts today, with fixing up to yesterday. 
   */
  @Test
  public void rateStartTodayWithFixingTodayPubLag0() {
    LocalDate fixingStartDate = VALUATION_DATE;
    LocalDate fixingRemainingStartDate = VALUATION_DATE.plusDays(1);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixedYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingRemainingStartDate);
    double fixingRemainingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingRemainingStartDate,
        fixingEndDate);

    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHTODAY_ZERO.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHTODAY_ZERO.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateExpected = ((1.0d + fixedYearFraction * FIXING_TODAY) *
        (1.0d + fixingRemainingYearFraction * rateFwdExpected) - 1.0d) / fixingYearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO,
        fixingStartDate, fixingEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /** 
   * Period starts today, without fixing yesterday, thus the input time series is not complete. 
   * However, an error is not returned because the rate fixed yesterday is not used. 
   */
  @Test
  public void rateStartTodayWithoutFixingYestPubLag0() {
    LocalDate fixingStartDate = VALUATION_DATE;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(VALUATION_DATE);
    double fixingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingEndDate);

    double rateExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTYEST_ZERO.relativeTime(VALUATION_DATE, fixingStartDate),
        ENV_WITHOUTYEST_ZERO.relativeTime(VALUATION_DATE, fixingEndDate), fixingYearFraction);
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO,
        fixingStartDate, fixingEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts yesterday with fixing up to today. 
   */
  @Test
  public void rateStartYestWithFixingTodayPubLag0() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingRemainingStartDate = VALUATION_DATE.plusDays(1);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixedYearFraction1 = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, VALUATION_DATE);
    double fixedYearFraction2 = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(VALUATION_DATE, fixingRemainingStartDate);
    double fixingRemainingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingRemainingStartDate,
        fixingEndDate);

    double rateFwdExpectedWithFixing = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHTODAY_ZERO.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHTODAY_ZERO.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateExpected = ((1.0d + fixedYearFraction1 * FIXING_YEST) * (1.0d + fixedYearFraction2 * FIXING_TODAY) *
        (1.0d + fixingRemainingYearFraction * rateFwdExpectedWithFixing) - 1.0d) / fixingYearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO,
        fixingStartDate, fixingEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts yesterday with fixing up to yesterday. 
   */
  @Test
  public void rateStartYestWithFixingYestPubLag0() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingRemainingStartDate = VALUATION_DATE;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixedYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingRemainingStartDate);
    double fixingRemainingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingRemainingStartDate,
        fixingEndDate);

    double rateFwdExpectedWithFixing = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTTODAY_ZERO.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHOUTTODAY_ZERO.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateExpected = ((1.0d + fixedYearFraction * FIXING_YEST) *
        (1.0d + fixingRemainingYearFraction * rateFwdExpectedWithFixing) - 1.0d) / fixingYearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO,
        fixingStartDate, fixingEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts yesterday without fixing yesterday. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartYestWithoutFixingYestPubLag0() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO, fixingStartDate, fixingEndDate);
  }

  /**
   * Period starts before yesterday with fixing up to today. 
   */
  @Test
  public void rateStartPastWitFixingTodayPubLag0() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(7);
    LocalDate fixingRemainingStartDate = VALUATION_DATE.plusDays(1);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixingRemainingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingRemainingStartDate,
        fixingEndDate);

    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHTODAY_ZERO.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHTODAY_ZERO.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateExpected = ((1.0 + 0.00119 / 360.0) * (1.0 + 0.00120 / 360.0) * (1.0 + 0.00121 / 360.0)
        * (1.0 + 0.00122 * 3.0 / 360.0) * (1.0 + FIXING_YEST / 360.0) * (1.0 + FIXING_TODAY / 360.0)
        * (1.0d + fixingRemainingYearFraction * rateFwdExpected) - 1.0d) / fixingYearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO,
        fixingStartDate, fixingEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts before yesterday with fixing up to yesterday. 
   */
  @Test
  public void rateStartPastWitFixingYestPubLag0() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(7);
    LocalDate fixingRemainingStartDate = VALUATION_DATE;
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixingRemainingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingRemainingStartDate,
        fixingEndDate);

    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTTODAY_ZERO.relativeTime(VALUATION_DATE, fixingRemainingStartDate),
        ENV_WITHOUTTODAY_ZERO.relativeTime(VALUATION_DATE, fixingEndDate), fixingRemainingYearFraction);
    double rateExpected = ((1.0 + 0.00119 / 360.0) * (1.0 + 0.00120 / 360.0) * (1.0 + 0.00121 / 360.0)
        * (1.0 + 0.00122 * 3.0 / 360.0) * (1.0 + FIXING_YEST / 360.0)
        * (1.0d + fixingRemainingYearFraction * rateFwdExpected) - 1.0d) / fixingYearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO,
        fixingStartDate, fixingEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts before yesterday without fixing yesterday. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartPastWithoutFixingYestPubLag0() {
    LocalDate fixingStartDate = VALUATION_DATE.minusDays(7);
    LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
    ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO, fixingStartDate, fixingEndDate);
  }

  /**
   * Period starts before yesterday and ends today with fixing up to yesterday. Thus all the rates are fixed. 
   */
  @Test
  public void rateStartPastEndTodayFixingPubLag0() {
    LocalDate valuationDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingStartDate = valuationDate.minusMonths(1);
    LocalDate fixingEndDate = USD_LIBOR_1M.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = INDEX_PUB_LAG_ZERO.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double rateExpected = (Math.pow((1.0 + 0.00119 / 360.0), 14) * (1.0 + 0.00121 * 2.0 / 360.0) *
        Math.pow((1.0 + 0.00122 * 3.0 / 360.0), 5) - 1.0) / fixingYearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO_1M, valuationDate, ON_CMP_RATE_ZERO,
        fixingStartDate, fixingEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts before yesterday and ends today without fixing yesterday. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartPastEndTodayWithoutFixingYestPubLag0() {
    LocalDate valuationDate = VALUATION_DATE.minusDays(1);
    LocalDate fixingStartDate = valuationDate.minusMonths(1);
    LocalDate fixingEndDate = USD_LIBOR_1M.calculateMaturityFromEffective(fixingStartDate);
    ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_ZERO_1M, valuationDate, ON_CMP_RATE_ZERO, fixingStartDate, fixingEndDate);
  }
  
  /*
   * Tests for publication lag = -1.
   * 
   * Modify the index s.t. publication lag = -1. 
   * ImmutablePricingEnvironment and OvernightCompoundedRate should be also modified. 
   * Note that we continue to use "USD-FED-FUND" to pick up the curve in the multicurve. 
   */
  private static final OvernightIndex INDEX_PUB_LAG_MINUS = ImmutableOvernightIndex.builder().name("USD-FED-FUND")
      .currency(USD).fixingCalendar(NYFD).publicationDateOffset(0).effectiveDateOffset(1).dayCount(ACT_360).build();
  private static final OvernightCompoundedRate ON_CMP_RATE_MINUS = OvernightCompoundedRate.of(INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_WITHTODAY_MINUS = env(TS_USDFEDFUND_WITHTODAY,
      INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY_MINUS = env(TS_USDFEDFUND_WITHOUTTODAY,
      INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_WITHOUTYEST_MINUS = env(TS_USDFEDFUND_WITHOUTYEST,
      INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_WITHOUTYEST_MINUS_1M = env(TS_USDFEDFUND_WITHOUTYEST_1M,
      INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY_MINUS_1M = env(TS_USDFEDFUND_WITHOUTTODAY_1M,
      INDEX_PUB_LAG_MINUS);

  /**
   * Period starts today with fixing up to today. 
   */
  @Test
  public void rateStartTodayWithFixingTodayPubLagM1() {
    LocalDate startDate = VALUATION_DATE;
    LocalDate remainingStartDate = VALUATION_DATE.plusDays(3); // US holiday on 27th
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    double yearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(startDate, endDate);
    double remainingYearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(remainingStartDate, endDate);

    double rateFwdExpectedWithFixing = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHTODAY_MINUS.relativeTime(VALUATION_DATE, remainingStartDate),
        ENV_WITHTODAY_MINUS.relativeTime(VALUATION_DATE, endDate), remainingYearFraction);
    double rateExpected = ((1.0d + FIXING_YEST / 360.0) * (1.0d + FIXING_TODAY / 180.0) *
        (1.0d + remainingYearFraction * rateFwdExpectedWithFixing) - 1.0d) / yearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS,
        startDate, endDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts today with fixing up to yesterday. 
   */
  @Test
  public void rateStartTodayWithFixingYestPubLagM1() {
    LocalDate startDate = VALUATION_DATE;
    LocalDate remainingStartDate = VALUATION_DATE.plusDays(1);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(VALUATION_DATE);
    double yearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(startDate, endDate);
    double remainingYearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(remainingStartDate, endDate);
    IndexON usdFedFundMod_oga = ENV_WITHOUTTODAY_MINUS.convert(INDEX_PUB_LAG_MINUS);

    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(usdFedFundMod_oga,
        ENV_WITHOUTTODAY_MINUS.relativeTime(VALUATION_DATE, remainingStartDate),
        ENV_WITHOUTTODAY_MINUS.relativeTime(VALUATION_DATE, endDate), remainingYearFraction);
    double rateExpected = ((1.0 + FIXING_YEST / 360.0)
        * (1.0d + remainingYearFraction * rateFwdExpected) - 1.0d) / yearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS,
        startDate, endDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /** 
   * Period starts today without fixing today. 
   * Rate fixed yesterday is used for the first sub-period, then exception is thrown. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartTodayWithoutFixingYestPubLagM1() {
    LocalDate startDate = VALUATION_DATE;
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(VALUATION_DATE);
    ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS, startDate, endDate);
  }

  /**
   * Period starts yesterday with fixing up to today. 
   */
  @Test
  public void rateStartYestWithFixingTodayPubLagM1() {
    LocalDate startDate = VALUATION_DATE.minusDays(1);
    LocalDate remainingStartDate = VALUATION_DATE.plusDays(3); // US holiday on 27th
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    double yearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(startDate, endDate);
    double remainingYearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(remainingStartDate, endDate);

    double rateFwdExpectedWithFixing = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHTODAY_MINUS.relativeTime(VALUATION_DATE, remainingStartDate),
        ENV_WITHTODAY_MINUS.relativeTime(VALUATION_DATE, endDate), remainingYearFraction);
    double rateExpected = ((1.0d + 0.00122 / 360.0) * (1.0d + FIXING_YEST / 360.0) *
        (1.0d + FIXING_TODAY / 180.0) * (1.0d + remainingYearFraction * rateFwdExpectedWithFixing) - 1.0d) /
        yearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS,
        startDate, endDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts yesterday with fixing up to yesterday. 
   */
  @Test
  public void rateStartYestWithFixingYestPubLagM1() {
    LocalDate startDate = VALUATION_DATE.minusDays(1);
    LocalDate remainingStartDate = VALUATION_DATE.plusDays(1);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    double yearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(startDate, endDate);
    double remainingYearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(remainingStartDate, endDate);

    double rateFwdExpectedWithFixing = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTTODAY_MINUS.relativeTime(VALUATION_DATE, remainingStartDate),
        ENV_WITHOUTTODAY_MINUS.relativeTime(VALUATION_DATE, endDate), remainingYearFraction);
    double rateExpected = ((1.0d + 0.00122 / 360.0) * (1.0d + FIXING_YEST / 360.0) *
        (1.0d + remainingYearFraction * rateFwdExpectedWithFixing) - 1.0d) / yearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS,
        startDate, endDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts yesterday without fixing yesterday. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartYestWithoutFixingYestPubLagM1() {
    LocalDate startDate = VALUATION_DATE.minusDays(1);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS, startDate, endDate);
  }

  /**
   * Period starts before yesterday with fixing up to today. 
   */
  @Test
  public void rateStartPastWitFixingTodayPubLagM1() {
    LocalDate startDate = VALUATION_DATE.minusDays(7);
    LocalDate remainingStartDate = VALUATION_DATE.plusDays(3);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    double yearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(startDate, endDate);
    double remainingYearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(remainingStartDate, endDate);

    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHTODAY_MINUS.relativeTime(VALUATION_DATE, remainingStartDate),
        ENV_WITHTODAY_MINUS.relativeTime(VALUATION_DATE, endDate), remainingYearFraction);
    double rateExpected = ((1.0 + 0.00117 / 360.0) * (1.0 + 0.00119 / 360.0) * (1.0 + 0.00120 / 360.0) *
        (1.0 + 0.00121 * 3.0 / 360.0) * (1.0 + 0.00122 / 360.0) * (1.0 + FIXING_YEST / 360.0) *
        (1.0 + FIXING_TODAY * 2.0 / 360.0) * (1.0d + remainingYearFraction * rateFwdExpected) - 1.0d) / yearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS,
        startDate, endDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts before yesterday with fixing up to yesterday. 
   */
  @Test
  public void rateStartPastWitFixingYestPubLagM1() {
    LocalDate startDate = VALUATION_DATE.minusDays(7);
    LocalDate remainingStartDate = VALUATION_DATE.plusDays(1);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    double yearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(startDate, endDate);
    double remainingYearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(remainingStartDate, endDate);

    double rateFwdExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_FED_FUND_OGA,
        ENV_WITHOUTTODAY_MINUS.relativeTime(VALUATION_DATE, remainingStartDate),
        ENV_WITHOUTTODAY_MINUS.relativeTime(VALUATION_DATE, endDate), remainingYearFraction);
    double rateExpected = ((1.0 + 0.00117 / 360.0) * (1.0 + 0.00119 / 360.0) * (1.0 + 0.00120 / 360.0) *
        (1.0 + 0.00121 * 3.0 / 360.0) * (1.0 + 0.00122 / 360.0) * (1.0 + FIXING_YEST / 360.0)
        * (1.0d + remainingYearFraction * rateFwdExpected) - 1.0d) / yearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE,
        ON_CMP_RATE_MINUS, startDate, endDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts before yesterday without fixing yesterday. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartPastWithoutFixingYestPubLagM1() {
    LocalDate startDate = VALUATION_DATE.minusDays(7);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS, startDate, endDate);
  }

  /**
   * Period starts before yesterday and ends today with fixing up to yesterday. 
   */
  @Test
  public void rateStartPastEndTodayFixingPubLagM1() {
    LocalDate valuationDate = VALUATION_DATE.minusDays(1);
    LocalDate startDate = valuationDate.minusMonths(1);
    LocalDate endDate = USD_LIBOR_1M.calculateMaturityFromEffective(startDate);
    double yearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(startDate, endDate);
    double rateExpected = (Math.pow((1.0 + 0.00119 / 360.0), 10) * (1.0 + 0.00121 / 360.0) *
        (1.0 + 0.00122 * 2.0 / 360.0) * Math.pow((1.0 + 0.00119 * 3.0 / 360.0), 5) *
        Math.pow((1.0 + 0.00122 / 360.0), 3) - 1.0) / yearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS_1M, valuationDate,
        ON_CMP_RATE_MINUS, startDate, endDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts before yesterday and ends today without fixing yesterday, thus the time series is not complete. 
   * However, the exception is not thrown because the last accrual is fixed one day before yesterday. 
   */
  @Test
  public void rateStartPastEndTodayWithoutFixingYestPubLagM1() {
    LocalDate valuationDate = VALUATION_DATE.minusDays(1);
    LocalDate startDate = valuationDate.minusMonths(1);
    LocalDate endDate = USD_LIBOR_1M.calculateMaturityFromEffective(startDate);
    double yearFraction = INDEX_PUB_LAG_MINUS.getDayCount().yearFraction(startDate, endDate);
    double rateExpected = (Math.pow((1.0 + 0.00119 / 360.0), 10) * (1.0 + 0.00121 / 360.0) *
        (1.0 + 0.00122 * 2.0 / 360.0) * Math.pow((1.0 + 0.00119 * 3.0 / 360.0), 5) *
        Math.pow((1.0 + 0.00122 / 360.0), 3) - 1.0) / yearFraction;
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_MINUS_1M, valuationDate,
        ON_CMP_RATE_MINUS, startDate, endDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /*
   * cutoff test
   */
  private static final OvernightCompoundedRate ON_CMP_RATE_CUT1 = OvernightCompoundedRate.of(USD_FED_FUND, 1);
  private static final OvernightCompoundedRate ON_CMP_RATE_ZERO_CUT1 = OvernightCompoundedRate
      .of(INDEX_PUB_LAG_ZERO, 1);
  private static final OvernightCompoundedRate ON_CMP_RATE_MINUS_CUT1 = OvernightCompoundedRate.of(INDEX_PUB_LAG_MINUS,
      1);
  private static final OvernightCompoundedRate ON_CMP_RATE_CUT2 = OvernightCompoundedRate.of(USD_FED_FUND, 2);
  private static final OvernightCompoundedRate ON_CMP_RATE_ZERO_CUT2 = OvernightCompoundedRate
      .of(INDEX_PUB_LAG_ZERO, 2);
  private static final OvernightCompoundedRate ON_CMP_RATE_MINUS_CUT2 = OvernightCompoundedRate.of(INDEX_PUB_LAG_MINUS,
      2);
  private static final OvernightCompoundedRate ON_CMP_RATE_CUT3 = OvernightCompoundedRate.of(USD_FED_FUND, 3);
  private static final OvernightCompoundedRate ON_CMP_RATE_ZERO_CUT3 = OvernightCompoundedRate
      .of(INDEX_PUB_LAG_ZERO, 3);
  private static final OvernightCompoundedRate ON_CMP_RATE_MINUS_CUT3 = OvernightCompoundedRate.of(INDEX_PUB_LAG_MINUS,
      3);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY_1M_CUTOFF = env(
      TS_USDFEDFUND_WITHOUTTODAY_1M_CUTOFF, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY_ZERO_1M_CUTOFF = env(
      TS_USDFEDFUND_WITHOUTTODAY_1M_CUTOFF, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY_MINUS_1M_CUTOFF = env(
      TS_USDFEDFUND_WITHOUTTODAY_1M_CUTOFF, INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_WITHOUTYEST_1M_CUTOFF = env(
      TS_USDFEDFUND_WITHOUTYEST_1M_CUTOFF, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_WITHOUTYEST_ZERO_1M_CUTOFF = env(
      TS_USDFEDFUND_WITHOUTYEST_1M_CUTOFF, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_WITHOUTYEST_MINUS_1M_CUTOFF = env(
      TS_USDFEDFUND_WITHOUTYEST_1M_CUTOFF, INDEX_PUB_LAG_MINUS);

  /**
   * Period starts tomorrow, rates unfixed, cutoff = 0, 1.
   */
  @Test
  public void rateStartTomorrow() {
    LocalDate startDate = VALUATION_DATE.plusDays(1);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    /* payment lag = 1*/
    double rateComputed0 =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE, startDate, endDate);
    double rateComputed1 =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE_CUT1, startDate, endDate);
    assertEquals(rateComputed0, rateComputed1, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = 0*/
    rateComputed0 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO,
        startDate, endDate);
    rateComputed1 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE,
        ON_CMP_RATE_ZERO_CUT1, startDate, endDate);
    assertEquals(rateComputed0, rateComputed1, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = -1*/
    rateComputed0 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS,
        startDate, endDate);
    rateComputed1 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE,
        ON_CMP_RATE_MINUS_CUT1, startDate, endDate);
    assertEquals(rateComputed0, rateComputed1, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts before yesterday, rates partially fixed, cutoff = 0, 1. 
   */
  @Test
  public void rateStartPast() {
    LocalDate startDate = VALUATION_DATE.minusDays(7);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    /* payment lag = 1*/
    double rateComputed0 =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE, startDate, endDate);
    double rateComputed1 =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE_CUT1, startDate, endDate);
    assertEquals(rateComputed0, rateComputed1, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = 0*/
    rateComputed0 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO,
        startDate, endDate);
    rateComputed1 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO_CUT1,
        startDate, endDate);
    assertEquals(rateComputed0, rateComputed1, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = -1*/
    rateComputed0 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS,
        startDate, endDate);
    rateComputed1 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS_CUT1,
        startDate, endDate);
    assertEquals(rateComputed0, rateComputed1, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period ends today, all rates fixed, cutoff = 0, 1. 
   */
  @Test
  public void rateEndToday() {
    LocalDate valuationDate = VALUATION_DATE.minusDays(1);
    LocalDate startDate = valuationDate.minusMonths(1);
    LocalDate endDate = USD_LIBOR_1M.calculateMaturityFromEffective(startDate);
    /* payment lag = 1*/
    double rateComputed0 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_1M_CUTOFF, valuationDate, ON_CMP_RATE,
        startDate, endDate);
    double rateComputed1 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_1M_CUTOFF, valuationDate,
        ON_CMP_RATE_CUT1, startDate, endDate);
    assertEquals(rateComputed0, rateComputed1, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = 0*/
    rateComputed0 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO_1M_CUTOFF, valuationDate,
        ON_CMP_RATE_ZERO, startDate, endDate);
    rateComputed1 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO_1M_CUTOFF, valuationDate,
        ON_CMP_RATE_ZERO_CUT1, startDate, endDate);
    assertEquals(rateComputed0, rateComputed1, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = -1*/
    rateComputed0 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS_1M_CUTOFF, valuationDate,
        ON_CMP_RATE_MINUS, startDate, endDate);
    rateComputed1 = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS_1M_CUTOFF, valuationDate,
        ON_CMP_RATE_MINUS_CUT1, startDate, endDate);
    assertEquals(rateComputed0, rateComputed1, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts tomorrow, rates unfixed, cutoff = 2
   */
  @Test
  public void rateStartTomorrowCutoff2() {
    LocalDate startDate = VALUATION_DATE.plusDays(1);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    LocalDate refEndDate = USD_FED_FUND.getFixingCalendar().previous(endDate);
    LocalDate refStartDate = USD_FED_FUND.getFixingCalendar().previous(refEndDate);
    /* payment lag = 1*/
    double rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE_CUT2, startDate,
        endDate);
    double rateExpected = computeCutoffRate(ENV_WITHOUTTODAY, ON_CMP_RATE, VALUATION_DATE, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = 0*/
    rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO_CUT2, startDate,
        endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_ZERO, ON_CMP_RATE_ZERO, VALUATION_DATE, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = -1*/
    rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS_CUT2, startDate,
        endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_MINUS, ON_CMP_RATE_MINUS, VALUATION_DATE, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts before yesterday, rates partially fixed, cutoff = 2. 
   */
  @Test
  public void rateStartPastCutoff2() {
    LocalDate startDate = VALUATION_DATE.minusDays(7);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    LocalDate refEndDate = USD_FED_FUND.getFixingCalendar().previous(endDate);
    LocalDate refStartDate = USD_FED_FUND.getFixingCalendar().previous(refEndDate);
    /* payment lag = 1*/
    double rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE_CUT2, startDate, endDate);
    double rateExpected =
        computeCutoffRate(ENV_WITHOUTTODAY, ON_CMP_RATE, VALUATION_DATE, startDate, endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    //    /* payment lag = 0*/
    rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO_CUT2, startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_ZERO, ON_CMP_RATE_ZERO, VALUATION_DATE, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    //    /* payment lag = -1*/
    rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS_CUT2, startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_MINUS, ON_CMP_RATE_MINUS, VALUATION_DATE, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period ends today, all rates fixed, cutoff = 2. 
   */
  @Test
  public void rateEndTodayCutoff2() {
    LocalDate valuationDate = VALUATION_DATE.minusDays(1);
    LocalDate startDate = valuationDate.minusMonths(1);
    LocalDate endDate = USD_LIBOR_1M.calculateMaturityFromEffective(startDate);
    LocalDate refEndDate = USD_FED_FUND.getFixingCalendar().previous(endDate);
    LocalDate refStartDate = USD_FED_FUND.getFixingCalendar().previous(refEndDate);
    /* payment lag = 1*/
    double rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_1M_CUTOFF, valuationDate, ON_CMP_RATE_CUT2, startDate, endDate);
    double rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_1M_CUTOFF, ON_CMP_RATE, valuationDate, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = 0*/
    rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO_1M_CUTOFF, valuationDate,
        ON_CMP_RATE_ZERO_CUT2, startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_ZERO_1M_CUTOFF, ON_CMP_RATE_ZERO, valuationDate, startDate,
        endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = -1*/
    rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS_1M_CUTOFF, valuationDate,
        ON_CMP_RATE_MINUS_CUT2, startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_MINUS_1M_CUTOFF, ON_CMP_RATE_MINUS, valuationDate, startDate,
        endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts tomorrow, rates unfixed, cutoff = 3
   */
  @Test
  public void rateStartTomorrowCutoff3() {
    LocalDate startDate = VALUATION_DATE.plusDays(1);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    LocalDate refEndDate = USD_FED_FUND.getFixingCalendar()
        .previous(USD_FED_FUND.getFixingCalendar().previous(endDate));
    LocalDate refStartDate = USD_FED_FUND.getFixingCalendar().previous(refEndDate);
    /* payment lag = 1*/
    double rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE_CUT3, startDate, endDate);
    double rateExpected =
        computeCutoffRate(ENV_WITHOUTTODAY, ON_CMP_RATE, VALUATION_DATE, startDate, endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate forward");
    /* payment lag = 0*/
    rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO_CUT3, startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_ZERO, ON_CMP_RATE_ZERO, VALUATION_DATE, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = -1*/
    rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS_CUT3, startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_MINUS, ON_CMP_RATE_MINUS, VALUATION_DATE, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period starts before yesterday, rates partially fixed, cutoff = 3. 
   */
  @Test
  public void rateStartPastCutoff3() {
    LocalDate startDate = VALUATION_DATE.minusDays(7);
    LocalDate endDate = USD_LIBOR_3M.calculateMaturityFromEffective(startDate);
    LocalDate refEndDate = USD_FED_FUND.getFixingCalendar()
        .previous(USD_FED_FUND.getFixingCalendar().previous(endDate));
    LocalDate refStartDate = USD_FED_FUND.getFixingCalendar().previous(refEndDate);
    /* payment lag = 1*/
    double rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ON_CMP_RATE_CUT3, startDate, endDate);
    double rateExpected =
        computeCutoffRate(ENV_WITHOUTTODAY, ON_CMP_RATE, VALUATION_DATE, startDate, endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = 0*/
    rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO, VALUATION_DATE, ON_CMP_RATE_ZERO_CUT3, startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_ZERO, ON_CMP_RATE_ZERO, VALUATION_DATE, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = -1*/
    rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS, VALUATION_DATE, ON_CMP_RATE_MINUS_CUT3, startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_MINUS, ON_CMP_RATE_MINUS, VALUATION_DATE, startDate, endDate,
        refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Period ends today, all rates fixed, cutoff = 3. 
   */
  @Test
  public void rateEndTodayCutoff3() {
    LocalDate valuationDate = VALUATION_DATE.minusDays(1);
    LocalDate startDate = valuationDate.minusMonths(1);
    LocalDate endDate = USD_LIBOR_1M.calculateMaturityFromEffective(startDate);
    LocalDate refEndDate = USD_FED_FUND.getFixingCalendar()
        .previous(USD_FED_FUND.getFixingCalendar().previous(endDate));
    LocalDate refStartDate = USD_FED_FUND.getFixingCalendar().previous(refEndDate);
    /* payment lag = 1*/
    double rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_1M_CUTOFF, valuationDate, ON_CMP_RATE_CUT3, startDate, endDate);
    double rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_1M_CUTOFF, ON_CMP_RATE, VALUATION_DATE, startDate,
        endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE * 1.0E1,
        "DefaultIborRateProviderFn: rate forward"); // tol increased due to accumulated errors
    //    /* payment lag = 0*/
    rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_ZERO_1M_CUTOFF, valuationDate, ON_CMP_RATE_ZERO_CUT3,
        startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_ZERO_1M_CUTOFF, ON_CMP_RATE_ZERO, VALUATION_DATE, startDate,
        endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE * 1.0E1,
        "DefaultIborRateProviderFn: rate forward"); // tol increased due to accumulated errors
    //    /* payment lag = -1*/
    rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTTODAY_MINUS_1M_CUTOFF, valuationDate,
        ON_CMP_RATE_MINUS_CUT3, startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTTODAY_MINUS_1M_CUTOFF, ON_CMP_RATE_MINUS, VALUATION_DATE, startDate,
        endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE * 1.0E1,
        "DefaultIborRateProviderFn: rate forward"); // tol increased due to accumulated errors
  }

  /**
   * Period ends in the future, all rates fixed except last interval, cut off = 2.
   * However, the forward curve is not used as the rate for the second last period is fixed. 
   */
  @Test
  public void secondLastRateFixedCutoffTest() {
    LocalDate valuationDate = VALUATION_DATE.minusDays(1);
    LocalDate startDate = valuationDate.minusMonths(1);
    LocalDate endDate = USD_LIBOR_1M.calculateMaturityFromEffective(startDate);
    LocalDate refEndDate = USD_FED_FUND.getFixingCalendar().previous(endDate);
    LocalDate refStartDate = USD_FED_FUND.getFixingCalendar().previous(refEndDate);
    /* payment lag = 1*/
    double rateComputed =
        ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_1M_CUTOFF, valuationDate, ON_CMP_RATE_CUT2, startDate, endDate);
    double rateExpected = computeCutoffRate(ENV_WITHOUTYEST_1M_CUTOFF, ON_CMP_RATE, valuationDate, startDate,
        endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = 0*/
    LocalDate valuationDate0 = USD_FED_FUND.getFixingCalendar().previous(valuationDate);
    rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_ZERO_1M_CUTOFF, valuationDate0, ON_CMP_RATE_ZERO_CUT2,
        startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTYEST_ZERO_1M_CUTOFF, ON_CMP_RATE_ZERO, valuationDate0, startDate,
        endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
    /* payment lag = -1*/
    LocalDate valuationDate1 = USD_FED_FUND.getFixingCalendar().previous(valuationDate0);
    rateComputed = ON_CMP_RATE_PROVIDER.rate(ENV_WITHOUTYEST_MINUS_1M_CUTOFF, valuationDate1, ON_CMP_RATE_MINUS_CUT2,
        startDate, endDate);
    rateExpected = computeCutoffRate(ENV_WITHOUTYEST_MINUS_1M_CUTOFF, ON_CMP_RATE_MINUS, valuationDate1, startDate,
        endDate, refStartDate, refEndDate);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborRateProviderFn: rate forward");
  }

  /**
   * Compute rate with cutoff from OvernightCompoundedRate without cutoff
   * @param env ImmutablePricingEnvironment
   * @param onRate OvernightCompoundedRate
   * @param valuationDate The valuation date
   * @param startDate Start date of the period
   * @param endDate End date of the period
   * @param refStartDate Start date of the reference interval
   * @param refEndDate End date of the reference interval
   * @return overnight rate
   */
  private double computeCutoffRate(ImmutablePricingEnvironment env, OvernightCompoundedRate onRate,
      LocalDate valuationDate, LocalDate startDate, LocalDate endDate, LocalDate refStartDate, LocalDate refEndDate) {
    double factorTotal = USD_FED_FUND.getDayCount().yearFraction(startDate, endDate);
    double factorToRefEnd = USD_FED_FUND.getDayCount().yearFraction(startDate, refEndDate);
    double factorLast = USD_FED_FUND.getDayCount().yearFraction(refEndDate, endDate);
    double rate = ON_CMP_RATE_PROVIDER.rate(env, valuationDate, onRate, startDate, refEndDate);
    double refRate = ON_CMP_RATE_PROVIDER.rate(env, valuationDate, onRate, refStartDate,
        refEndDate);
    return ((1. + rate * factorToRefEnd) * (1. + refRate * factorLast) - 1.) / factorTotal;
  }

  /**
   * Create a pricing environment from the existing MulticurveProvider and Ibor fixing time series.
   * @param ts The time series for the USDLIBOR3M.
   * @param index The overnight index. 
   * @return The pricing environment.
   */
  private static ImmutablePricingEnvironment env(LocalDateDoubleTimeSeries ts, OvernightIndex ois) {
    return ImmutablePricingEnvironment.builder()
        .multicurve(MULTICURVE_OIS)
        .timeSeries(ImmutableMap.of(
            USD_LIBOR_1M, SwapInstrumentsDataSet.TS_USDLIBOR1M,
            USD_LIBOR_3M, SwapInstrumentsDataSet.TS_USDLIBOR3M,
            USD_LIBOR_6M, SwapInstrumentsDataSet.TS_USDLIBOR6M,
            ois, ts))
        .dayCount(ACT_ACT_ISDA)
        .build();
  }
}
