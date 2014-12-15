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
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.analytics.financial.instrument.index.IndexON;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.basics.date.BusinessDayConvention;
import com.opengamma.basics.date.BusinessDayConventions;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.IborIndices;
import com.opengamma.basics.index.ImmutableOvernightIndex;
import com.opengamma.basics.index.OvernightIndex;
import com.opengamma.basics.index.OvernightIndices;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.platform.finance.rate.OvernightAveragedRate;
import com.opengamma.platform.finance.rate.Rate;
import com.opengamma.platform.pricer.SwapInstrumentsDataSet;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;
import com.opengamma.util.tuple.Pair;

/**
 * Test {@link DefaultOvernightAveragedRateProviderFn} and {@link ApproximatedDiscountingOvernightAveragedRateProviderFn}
 */
@Test
public class DiscountingOvernightAveragedRateProviderFnTest {
  // TODO Tests for 0, -1 publication lags should be done with e.g., GBP, CHF.
  // Currently these cases are tested by modifying FedFund in order to make use of the setup in Platform 2.x

  private static final IborIndex USD_LIBOR_1M = IborIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = IborIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = IborIndices.USD_LIBOR_6M;
  private static final OvernightIndex USD_FED_FUND = OvernightIndices.USD_FED_FUND;
  private static final BusinessDayConvention MOD_FOL = BusinessDayConventions.MODIFIED_FOLLOWING;
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_21 =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_24 =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_25 =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .put(LocalDate.of(2014, 11, 25), 0.00124)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_26 =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .put(LocalDate.of(2014, 11, 25), 0.00124)
          .put(LocalDate.of(2014, 11, 26), 0.00125)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_28 =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .put(LocalDate.of(2014, 11, 25), 0.00124)
          .put(LocalDate.of(2014, 11, 26), 0.00125) // 27 holiday
          .put(LocalDate.of(2014, 11, 28), 0.00126)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_ALL =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .put(LocalDate.of(2014, 11, 25), 0.00124)
          .put(LocalDate.of(2014, 11, 26), 0.00125)
          .put(LocalDate.of(2014, 11, 28), 0.00126)
          .put(LocalDate.of(2014, 12, 1), 0.00127)
          .put(LocalDate.of(2014, 12, 2), 0.00128)
          .put(LocalDate.of(2014, 12, 3), 0.00129)
          .put(LocalDate.of(2014, 12, 4), 0.00130)
          .put(LocalDate.of(2014, 12, 5), 0.00131)
          .put(LocalDate.of(2014, 12, 8), 0.00132)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_ALL_TODAY =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .put(LocalDate.of(2014, 11, 25), 0.00124)
          .put(LocalDate.of(2014, 11, 26), 0.00125)
          .put(LocalDate.of(2014, 11, 28), 0.00126)
          .put(LocalDate.of(2014, 12, 1), 0.00127)
          .put(LocalDate.of(2014, 12, 2), 0.00128)
          .put(LocalDate.of(2014, 12, 3), 0.00129)
          .put(LocalDate.of(2014, 12, 4), 0.00130)
          .put(LocalDate.of(2014, 12, 5), 0.00131)
          .put(LocalDate.of(2014, 12, 8), 0.00132)
          .put(LocalDate.of(2014, 12, 9), 0.00133)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_ALL_MISSING_YEST =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .put(LocalDate.of(2014, 11, 25), 0.00124)
          .put(LocalDate.of(2014, 11, 26), 0.00125)
          .put(LocalDate.of(2014, 11, 28), 0.00126)
          .put(LocalDate.of(2014, 12, 1), 0.00127)
          .put(LocalDate.of(2014, 12, 2), 0.00128)
          .put(LocalDate.of(2014, 12, 3), 0.00129)
          .put(LocalDate.of(2014, 12, 4), 0.00130)
          .put(LocalDate.of(2014, 12, 5), 0.00131)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDFEDFUND_MISSINGDATA =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .put(LocalDate.of(2014, 11, 25), 0.00124) // Missing 26
          .put(LocalDate.of(2014, 11, 28), 0.00126)
          .put(LocalDate.of(2014, 12, 1), 0.00127)
          .put(LocalDate.of(2014, 12, 2), 0.00128)
          .put(LocalDate.of(2014, 12, 3), 0.00129)
          .put(LocalDate.of(2014, 12, 4), 0.00130)
          .build();
  private static final Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_OIS_PAIR =
      StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();

  private static final RateProviderFn<OvernightAveragedRate> ON_AA_RATE_DEFAULT_PROVIDER =
      DefaultOvernightAveragedRateProviderFn.DEFAULT;
  private static final RateProviderFn<OvernightAveragedRate> ON_AA_RATE_APPROX_PROVIDER =
      ApproximatedDiscountingOvernightAveragedRateProviderFn.APPROXIMATED_DISCOUNTING;
  private static final RateProviderFn<Rate> RATE_PROVIDER = DefaultRateProviderFn.DEFAULT;
  private static final double TOLERANCE_RATE = 1.0E-10;
  private static final double TOLERANCE_RATE_APPROX = 1.0E-6; // Tolerance higher as only for approximation.

  private static final LocalDate START_DATE = LocalDate.of(2014, 11, 25);
  private static final LocalDate END_DATE = LocalDate.of(2014, 12, 9);
  // Creating the dates and accrual factors
  private static final List<LocalDate> FIXING_DATES = new ArrayList<>(); // Dates on which the fixing take place
  private static final List<LocalDate> PUBLICATION_DATES = new ArrayList<>(); // Dates on which the fixing is published
  private static final List<LocalDate> RATE_PERIOD_START_DATES = new ArrayList<>(); // Dates related to the underlying rate start periods.
  private static final List<LocalDate> RATE_PERIOD_END_DATES = new ArrayList<>(); // Dates related to the underlying rate end periods.
  private static final List<LocalDate> NO_CUTOFF_RATE_PERIOD_START_DATES = new ArrayList<>(); // Dates related to the underlying rate start periods.
  private static final List<LocalDate> NO_CUTOFF_RATE_PERIOD_END_DATES = new ArrayList<>(); // Dates related to the underlying rate end periods.
  private static final List<Double> NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR = new ArrayList<>();
  private static final List<Double> CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR = new ArrayList<>();
  private static final int NB_PERIODS;
  private static final double ACCRUAL_FACTOR_TOTAL;
  static {
    LocalDate currentStart = START_DATE;
    double accrualFactorTotal = 0.0d;
    while (currentStart.isBefore(END_DATE)) {
      LocalDate currentEnd = USD_FED_FUND.getFixingCalendar().next(currentStart);
      LocalDate fixingDate = currentStart;
      FIXING_DATES.add(fixingDate);
      PUBLICATION_DATES.add(currentEnd);
      RATE_PERIOD_START_DATES.add(currentStart);
      RATE_PERIOD_END_DATES.add(currentEnd);
      NO_CUTOFF_RATE_PERIOD_START_DATES.add(currentStart);
      NO_CUTOFF_RATE_PERIOD_END_DATES.add(currentEnd);
      double accrualFactor = USD_FED_FUND.getDayCount().yearFraction(currentStart, currentEnd);
      NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.add(accrualFactor);
      currentStart = currentEnd;
      accrualFactorTotal += accrualFactor;
    }
    CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.addAll(NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR);
    // dealing with Rate cutoff
    NB_PERIODS = NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.size();
    FIXING_DATES.set(NB_PERIODS - 1, FIXING_DATES.get(NB_PERIODS - 2));
    RATE_PERIOD_START_DATES.set(NB_PERIODS - 1, RATE_PERIOD_START_DATES.get(NB_PERIODS - 2));
    RATE_PERIOD_END_DATES.set(NB_PERIODS - 1, RATE_PERIOD_END_DATES.get(NB_PERIODS - 2));
    CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.set(NB_PERIODS - 1, NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(NB_PERIODS - 2));
    ACCRUAL_FACTOR_TOTAL = accrualFactorTotal;
  }

  /*
   * Tests for publication lag = +1.
   */
  private static final ImmutablePricingEnvironment ENV_21 = env(TS_USDFEDFUND_21, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_24 = env(TS_USDFEDFUND_24, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_25 = env(TS_USDFEDFUND_25, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_28 = env(TS_USDFEDFUND_28, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_ALL = env(TS_USDFEDFUND_ALL, USD_FED_FUND);
  private static final ImmutablePricingEnvironment ENV_MISSINGDATA = env(TS_USDFEDFUND_MISSINGDATA, USD_FED_FUND);
  private static final OvernightAveragedRate ON_AA_RATE = OvernightAveragedRate.of(USD_FED_FUND, 2);

  /**
   * Period starts tomorrow, totally not fixed.
   */
  @Test
  public void rateForwardPubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_21.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_21.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_21.getMulticurve().getSimplyCompoundForwardRate(
          ENV_21.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_21, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
    double rateGenComputed = RATE_PROVIDER.rate(ENV_21, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateGenComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Test approximated rate for future start.
   */
  @Test
  public void rateForwardApproxVDetailedPubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    double rateComputedDetailed =
        ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_21, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    double rateComputedApproxim =
        ON_AA_RATE_APPROX_PROVIDER.rate(ENV_21, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateComputedDetailed, rateComputedApproxim, TOLERANCE_RATE_APPROX,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts tomorrow, totally not fixed.
   */
  @Test
  public void rateStart1Fixing0PubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_24.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_24.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_24.getMulticurve().getSimplyCompoundForwardRate(
          ENV_24.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_24, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");

    /* Test redundant time series is not used */
    double rateComputedRed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateComputed, rateComputedRed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts yesterday with fixing yesterday. 
   */
  @Test
  public void rateStart1Fixing1PubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00124;
    for (int i = 1; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_25.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_25.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_25.getMulticurve().getSimplyCompoundForwardRate(
          ENV_25.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_25, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");

    /* Test redundant time series is not used */
    double rateComputedRed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateComputed, rateComputedRed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts before yesterday with fixing up to yesterday.
   */
  @Test
  public void rateStart3Fixing3PubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 1);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00124;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(1) * 0.00125;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(2) * 0.00126;
    for (int i = 3; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_28.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_28.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_28.getMulticurve().getSimplyCompoundForwardRate(
          ENV_28.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Test approximated rate for past start.  
   */
  @Test
  public void rateStart3Fixing3ApproxVDetailedPubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 1);
    double rateDetailed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    double rateApproxim = ON_AA_RATE_APPROX_PROVIDER.rate(ENV_28, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateDetailed, rateApproxim, TOLERANCE_RATE_APPROX,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts before yesterday with fixing up to the day before yesterday
   */
  @Test
  public void rateStart4Fixing3PubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 2);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00124;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(1) * 0.00125;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(2) * 0.00126;
    for (int i = 3; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_28.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_28.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_28.getMulticurve().getSimplyCompoundForwardRate(
          ENV_28.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * All fixed.
   */
  @Test
  public void rateAllFixedPubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 9);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS - 1; i++) {
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * (0.00124 + i * 0.00001);
    }
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(NB_PERIODS - 1) * 0.00131; // Cut-off part
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_ALL, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
    double rateGenComputed = RATE_PROVIDER.rate(ENV_ALL, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateGenComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
    double rateComputedApprox = ON_AA_RATE_APPROX_PROVIDER.rate(ENV_ALL, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputedApprox, TOLERANCE_RATE,
        "ApproximatedDiscountingOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Yesterday's fix is not available yet, allowed for publication lag = +1.
   */
  @Test
  public void rateStart2MissingYestPubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_24.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_24.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_24.getMulticurve().getSimplyCompoundForwardRate(
          ENV_24.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime,
          CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputedApprox = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_24, valuationDate, ON_AA_RATE, START_DATE,
        END_DATE);
    assertEquals(rateExpected, rateComputedApprox, TOLERANCE_RATE,
        "ApproximatedDiscountingOvernightAveragedRateProviderFn: rate");
  }

  /**
   * The latest fixing is missing
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStart2Missing0PubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 27);
    ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_24, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
  }

  /**
   * The latest fixing is missing
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateMissingDataPubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 5);
    ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_MISSINGDATA, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
  }

  /**
   * Fixing in the past is missing for the approximated rate. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStart2Missing0ApproxPubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 27);
    ON_AA_RATE_APPROX_PROVIDER.rate(ENV_24, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
  }

  /**
   * Fixing in the past is missing for the approximated rate. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateMissingDataApproxPubLag1() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 5);
    ON_AA_RATE_APPROX_PROVIDER.rate(ENV_MISSINGDATA, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
  }

  /*
   * Tests for publication lag = 0. 
   * 
   * Modify the index s.t. publication lag = 0. 
   * ImmutablePricingEnvironment and OvernightAveragedRate should be also modified. 
   * Note that we continue to use "USD-FED-FUND" to pick up the curve in the multicurve. 
   */
  private static final OvernightIndex INDEX_PUB_LAG_ZERO = ImmutableOvernightIndex.builder().name("USD-FED-FUND")
      .currency(USD).fixingCalendar(NYFD).publicationDateOffset(0).effectiveDateOffset(0).dayCount(ACT_360).build();
  private static final OvernightAveragedRate ON_AA_RATE_ZERO = OvernightAveragedRate.of(INDEX_PUB_LAG_ZERO);
  private static final IndexON CONVERTED_ON = ENV_21.convert(USD_FED_FUND);// used to specify a curve in the multicurve
  private static final ImmutablePricingEnvironment ENV_21_ZERO = env(TS_USDFEDFUND_21, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_24_ZERO = env(TS_USDFEDFUND_24, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_25_ZERO = env(TS_USDFEDFUND_25, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_26_ZERO = env(TS_USDFEDFUND_26, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_28_ZERO = env(TS_USDFEDFUND_28, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_ALL_ZERO = env(TS_USDFEDFUND_ALL, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_ALL_TODAY_ZERO = env(TS_USDFEDFUND_ALL_TODAY, INDEX_PUB_LAG_ZERO);
  private static final ImmutablePricingEnvironment ENV_ALL_MISSING_YEST_ZERO = env(TS_USDFEDFUND_ALL_MISSING_YEST,
      INDEX_PUB_LAG_ZERO);

  /**
   * Period starts tomorrow. 
   */
  @Test
  public void rateStartTomoPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_21_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_21_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_21_ZERO.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime, NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_21_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
    double rateGenComputed = RATE_PROVIDER.rate(ENV_21_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateExpected, rateGenComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");

    /* Test redundant time series is not used*/
    double rateGenComputedRed = RATE_PROVIDER.rate(ENV_28_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateGenComputed, rateGenComputedRed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Test approximated rate for future start.
   */
  @Test
  public void rateForwardApproxVDetailedPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    double rateComputedDetailed =
        ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_21_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    double rateComputedApproxim =
        ON_AA_RATE_APPROX_PROVIDER.rate(ENV_21_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateComputedDetailed, rateComputedApproxim, TOLERANCE_RATE_APPROX,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts today with fixing today. 
   */
  @Test
  public void rateStartTodyWithFixingTodayPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 25);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00124;
    for (int i = 1; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_25_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_25_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_25_ZERO.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime,
          NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_25_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");

    /* Test redundant time series is not used*/
    double rateComputedWithRed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_28_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateComputed, rateComputedWithRed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts today with fixing yesterday. 
   */
  @Test
  public void rateStartTodyWithFixingYestPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 25);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_24_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_24_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_24_ZERO.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime,
          NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_24_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts today without fixing yesterday, that is, the time series is not complete. 
   * However, an error is not returned because the the first sub-period uses the rate today. 
   */
  @Test
  public void rateStartTodyWithoutFixingYestPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 25);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_21_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_21_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_21_ZERO.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime,
          NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_21_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts yesterday with fixing up to today. 
   */
  @Test
  public void rateStartYestWithFixingTodayPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00124;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(1) * 0.00125;
    for (int i = 2; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_26_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_26_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_26_ZERO.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime,
          NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_26_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");

    /* Test redundant time series is not used*/
    double rateComputedRed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_28_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateComputed, rateComputedRed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts yesterday with fixing up to yesterday. 
   */
  @Test
  public void rateStartYestWithFixingYestPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00124;
    for (int i = 1; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_25_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_25_ZERO.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_25_ZERO.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime,
          NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_25_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts yesterday without fixing yesterday.
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartYestWithoutFixingYestPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_24_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
  }

  /**
   * Period starts before yesterday and ends today with fixing up to yesterday.
   */
  @Test
  public void rateStartPastEndTodayWithFixingYestPubLag0() {
    LocalDate valuationDate = END_DATE;
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * (0.00124 + i * 0.00001);
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_ALL_TODAY_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
    double rateComputedYest = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_ALL_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
    assertEquals(rateComputed, rateComputedYest, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts before yesterday and ends today without fixing yesterday.  
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartPastEndTodayWithoutFixingYestPubLag0() {
    LocalDate valuationDate = END_DATE;
    ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_ALL_MISSING_YEST_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE, END_DATE);
  }

  /**
   * Test approximated rate for past start.  
   */
  @Test
  public void rateStart3Fixing3ApproxVDetailedPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 1);
    double rateDetailed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE,
        END_DATE);
    double rateApproxim = ON_AA_RATE_APPROX_PROVIDER.rate(ENV_28_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE,
        END_DATE);
    assertEquals(rateDetailed, rateApproxim, TOLERANCE_RATE_APPROX,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Test approximated rate for all fixed.
   */
  @Test
  public void rateAllFixedPubLag0() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 9);
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_ALL_ZERO, valuationDate, ON_AA_RATE_ZERO, START_DATE,
        END_DATE);
    double rateComputedApprox = ON_AA_RATE_APPROX_PROVIDER.rate(ENV_ALL_ZERO, valuationDate, ON_AA_RATE_ZERO,
        START_DATE, END_DATE);
    assertEquals(rateComputed, rateComputedApprox, TOLERANCE_RATE,
        "ApproximatedDiscountingOvernightAveragedRateProviderFn: rate");
  }

  /*
   * Tests for publication lag = -1
   * 
   * Modify the index s.t. publication lag = -1. 
   * ImmutablePricingEnvironment and OvernightAveragedRate should be also modified. 
   * Note that we continue to use "USD-FED-FUND" to pick up the curve in the multicurve. 
   */
  private static final OvernightIndex INDEX_PUB_LAG_MINUS = ImmutableOvernightIndex.builder().name("USD-FED-FUND")
      .currency(USD).fixingCalendar(NYFD).publicationDateOffset(0).effectiveDateOffset(1).dayCount(ACT_360).build();
  private static final OvernightAveragedRate ON_AA_RATE_MINUS = OvernightAveragedRate.of(INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_21_MINUS = env(TS_USDFEDFUND_21, INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_24_MINUS = env(TS_USDFEDFUND_24, INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_25_MINUS = env(TS_USDFEDFUND_25, INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_26_MINUS = env(TS_USDFEDFUND_26, INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_28_MINUS = env(TS_USDFEDFUND_28, INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_ALL_MINUS = env(TS_USDFEDFUND_ALL, INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_ALL_TODAY_MINUS = env(TS_USDFEDFUND_ALL_TODAY,
      INDEX_PUB_LAG_MINUS);
  private static final ImmutablePricingEnvironment ENV_ALL_MISSING_YEST_MINUS = env(TS_USDFEDFUND_ALL_MISSING_YEST,
      INDEX_PUB_LAG_MINUS);

  /**
   * Period starts tomorrow with fixing up to today. 
   */
  @Test
  public void rateStartTomoWithFixingTodayPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00123;
    for (int i = 1; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_24_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_24_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_24_MINUS.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime, NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_24_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
    double rateGenComputed = RATE_PROVIDER.rate(ENV_24_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
    assertEquals(rateExpected, rateGenComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");

    /* Test redundant date is not used */
    double rateGenComputedRed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28_MINUS, valuationDate, ON_AA_RATE_MINUS,
        START_DATE, END_DATE);
    assertEquals(rateExpected, rateGenComputedRed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts tomorrow with fixing up to yesterday. 
   */
  @Test
  public void rateStartTomoWithFixingYestPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_21_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_21_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_21_MINUS.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime, NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_21_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE,
        END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Test approximated rate for future start.
   */
  @Test
  public void rateForwardApproxVDetailedPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    double rateComputedDetailed =
        ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_21_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
    double rateComputedApproxim =
        ON_AA_RATE_APPROX_PROVIDER.rate(ENV_21_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
    assertEquals(rateComputedDetailed, rateComputedApproxim, TOLERANCE_RATE_APPROX,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts today with fixing up to today. 
   */
  @Test
  public void rateStartTodyWithFixingTodayPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 25);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00123;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(1) * 0.00124;
    for (int i = 2; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_25_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_25_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_25_MINUS.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime, NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_25_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE,
        END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");

    /* Test redundant date is not used */
    double rateGenComputedRed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28_MINUS, valuationDate, ON_AA_RATE_MINUS,
        START_DATE, END_DATE);
    assertEquals(rateExpected, rateGenComputedRed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts today with fixing up to yesterday.
   */
  @Test
  public void rateStartTodyWithFixingYestPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 25);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00123;
    for (int i = 1; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_24_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_24_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_24_MINUS.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime, NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_24_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts today without fixing yesterday, that is the time series is not complete. 
   * Because the first sub-period uses the rate fixed yesterday, the exception is thrown.
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartTodyWithoutFixingYestPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 25);
    ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_21_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
  }

  /**
   * Period starts yesterday with fixing up to today. 
   */
  @Test
  public void rateStartYestWithFixingTodayPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00123;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(1) * 0.00124;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(2) * 0.00125;
    for (int i = 3; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_26_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_26_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_26_MINUS.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime, NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_26_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");

    /* Test redundant date is not used */
    double rateGenComputedRed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28_MINUS, valuationDate, ON_AA_RATE_MINUS,
        START_DATE, END_DATE);
    assertEquals(rateExpected, rateGenComputedRed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts yesterday with fixing up to yesterday. 
   */
  @Test
  public void rateStartYestWithFixingYestPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00123;
    accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(1) * 0.00124;
    for (int i = 2; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_25_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_25_MINUS.relativeTime(valuationDate, NO_CUTOFF_RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_25_MINUS.getMulticurve().getSimplyCompoundForwardRate(
          CONVERTED_ON, ratePeriodStartTime, ratePeriodendTime, NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_25_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts yesterday without fixing yesterday. 
   */
  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStartYestWithoutFixingYestPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_24_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
  }

  /**
   * Period starts before yesterday and ends today with fixing up to today.
   */
  @Test
  public void rateStartPastEndTodayWithFixingTodayPubLagM1() {
    LocalDate valuationDate = END_DATE;
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * (0.00123 + i * 0.00001);
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_ALL_TODAY_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");
    double rateComputedYest = ON_AA_RATE_DEFAULT_PROVIDER
        .rate(ENV_ALL_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE, END_DATE);
    assertEquals(rateComputed, rateComputedYest, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Period starts before yesterday and ends today without fixing yesterday, incomplete time series. 
   * However, the exception is not thrown because the last sub-period is fixed one day before yesterday. 
   */
  @Test
  public void rateStartPastEndTodayWithoutFixingYestPubLagM1() {
    LocalDate valuationDate = END_DATE;
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      accruedUnitNotional += NO_CUTOFF_RATE_PERIOD_ACCRUAL_FACTOR.get(i) * (0.00123 + i * 0.00001);
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_ALL_MISSING_YEST_MINUS, valuationDate, ON_AA_RATE_MINUS,
        START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Test approximated rate for past start.  
   */
  @Test
  public void rateStart3Fixing3ApproxVDetailedPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 1);
    double rateDetailed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_28_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE,
        END_DATE);
    double rateApproxim = ON_AA_RATE_APPROX_PROVIDER.rate(ENV_28_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE,
        END_DATE);
    assertEquals(rateDetailed, rateApproxim, TOLERANCE_RATE_APPROX,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Test approximated rate for all fixed.
   */
  @Test
  public void rateAllFixedPubLagM1() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 9);
    double rateComputed = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_ALL_MINUS, valuationDate, ON_AA_RATE_MINUS, START_DATE,
        END_DATE);
    double rateComputedApprox = ON_AA_RATE_APPROX_PROVIDER.rate(ENV_ALL_MINUS, valuationDate, ON_AA_RATE_MINUS,
        START_DATE, END_DATE);
    assertEquals(rateComputed, rateComputedApprox, TOLERANCE_RATE,
        "ApproximatedDiscountingOvernightAveragedRateProviderFn: rate");
  }

  /**
   * Performance test, to be turned off when pushed. 
   */
  @Test(enabled = false)
  public void performance() {

    long startTime, endTime;
    int nbTest = 100;    
    int nbRep = 10;
    int nbPeriod = 40; // 10Y x Quarterly
    LocalDate[] periodDates = new LocalDate[nbPeriod+1];
    periodDates[0] = START_DATE;
    for(int loopperiod = 1; loopperiod <= nbPeriod; loopperiod++) {
      periodDates[loopperiod] = MOD_FOL.adjust(periodDates[0].plus(Period.ofMonths(loopperiod*3)), USD_FED_FUND.getFixingCalendar());
    }
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    
    for (int looprep = 0; looprep < nbRep; looprep++) {
      double rateAverage = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTest; looptest++) {
        rateAverage = 0.0;
        for(int loopperiod = 0; loopperiod < nbPeriod; loopperiod++) {
          double rate = ON_AA_RATE_DEFAULT_PROVIDER.rate(ENV_ALL, valuationDate, ON_AA_RATE, 
              periodDates[loopperiod], periodDates[loopperiod+1]);
          rateAverage += rate;
        }
      }
      endTime = System.currentTimeMillis();
      rateAverage /= nbPeriod;
      System.out.println("OvernightAveragedRateProviderFn: " + nbTest + " x " + nbPeriod + " rate OAA detailed - construction/pv " +
          (endTime - startTime) + " ms - " + rateAverage);
      // Performance note: construction + rate / detailed: On Mac Book Pro 2.6 GHz Intel i7: 146 ms for 100x40 rates.
    }
    
    for (int looprep = 0; looprep < nbRep; looprep++) {
      double rateAverage = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTest; looptest++) {
        rateAverage = 0.0;
        for(int loopperiod = 0; loopperiod < nbPeriod; loopperiod++) {
          double rate = ON_AA_RATE_APPROX_PROVIDER.rate(ENV_ALL, valuationDate, ON_AA_RATE, 
              periodDates[loopperiod], periodDates[loopperiod+1]);
          rateAverage += rate;
        }
      }
      endTime = System.currentTimeMillis();
      rateAverage /= nbPeriod;
      System.out.println("OvernightAveragedRateProviderFn: " + nbTest + " x " + nbPeriod + " rate OAA approx - construction/pv " +
          (endTime - startTime) + " ms - " + rateAverage);
      // Performance note: construction + rate / approx: On Mac Book Pro 2.6 GHz Intel i7: (all dates) 88 / (improved) 5 ms for 100x40 rates.
    }

  }

  /**
   * Create a pricing environment from the existing MulticurveProvider and Ibor fixing time series.
   * @param ts The time series for the USDLIBOR3M.
   * @param index The overnight index
   * @return The pricing environment.
   */
  private static ImmutablePricingEnvironment env(LocalDateDoubleTimeSeries ts, OvernightIndex index) {
    return ImmutablePricingEnvironment.builder()
        .multicurve(MULTICURVE_OIS)
        .timeSeries(ImmutableMap.of(
            USD_LIBOR_1M, SwapInstrumentsDataSet.TS_USDLIBOR1M,
            USD_LIBOR_3M, SwapInstrumentsDataSet.TS_USDLIBOR3M,
            USD_LIBOR_6M, SwapInstrumentsDataSet.TS_USDLIBOR6M,
            index, ts))
        .dayCount(ACT_ACT_ISDA)
        .build();
  }
}
