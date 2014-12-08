/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.rate;

import static com.opengamma.basics.date.DayCounts.ACT_ACT_ISDA;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.IborIndices;
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
 * Test {@link DefaultOvernightAveragedRateProviderFn}.
 * Test done only for USD_FED_FUND. This rate type is used only for USD Fed Fund swaps.
 */
@Test
public class DefaultOvernightAveragedRateProviderFnTest {

  private static final IborIndex USD_LIBOR_1M = IborIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = IborIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = IborIndices.USD_LIBOR_6M;
  private static final OvernightIndex USD_FED_FUND = OvernightIndices.USD_FED_FUND;
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_21 =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .build();
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_24 =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .build();
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_25 =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 11, 18), 0.00119)
          .put(LocalDate.of(2014, 11, 19), 0.00120)
          .put(LocalDate.of(2014, 11, 20), 0.00121)
          .put(LocalDate.of(2014, 11, 21), 0.00122)
          .put(LocalDate.of(2014, 11, 24), 0.00123)
          .put(LocalDate.of(2014, 11, 25), 0.00124)
          .build();
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_28 =
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
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_ALL =
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
  public static final LocalDateDoubleTimeSeries TS_USDFEDFUND_MISSINGDATA =
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
  private static final ImmutablePricingEnvironment ENV_21 = env(TS_USDFEDFUND_21);
  private static final ImmutablePricingEnvironment ENV_24 = env(TS_USDFEDFUND_24);
  private static final ImmutablePricingEnvironment ENV_25 = env(TS_USDFEDFUND_25);
  private static final ImmutablePricingEnvironment ENV_28 = env(TS_USDFEDFUND_28);
  private static final ImmutablePricingEnvironment ENV_ALL = env(TS_USDFEDFUND_ALL);
  private static final ImmutablePricingEnvironment ENV_MISSINGDATA = env(TS_USDFEDFUND_MISSINGDATA);
  //  private static final IndexON USD_FED_FUND_OGA = ENV_25.convert(USD_FED_FUND);
  private static final OvernightAveragedRate ON_AA_RATE = OvernightAveragedRate.of(USD_FED_FUND, 2);

  private static final RateProviderFn<OvernightAveragedRate> ON_AA_RATE_PROVIDER =
      DefaultOvernightAveragedRateProviderFn.DEFAULT;
  private static final RateProviderFn<Rate> RATE_PROVIDER = DefaultRateProviderFn.DEFAULT;
  private static final double TOLERANCE_RATE = 1.0E-10;

  private static final LocalDate START_DATE = LocalDate.of(2014, 11, 25);
  private static final LocalDate END_DATE = LocalDate.of(2014, 12, 9);
  // Creating the dates and accrual factors
  private static final List<LocalDate> FIXING_DATES = new ArrayList<>(); // Dates on which the fixing take place
  private static final List<LocalDate> PUBLICATION_DATES = new ArrayList<>(); // Dates on which the fixing is published
  private static final List<LocalDate> RATE_PERIOD_START_DATES = new ArrayList<>(); // Dates related to the underlying rate start periods.
  private static final List<LocalDate> RATE_PERIOD_END_DATES = new ArrayList<>(); // Dates related to the underlying rate end periods.
  private static final List<Double> RATE_PERIOD_ACCRUAL_FACTOR = new ArrayList<>();
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
      double accrualFactor = USD_FED_FUND.getDayCount().yearFraction(currentStart, currentEnd);
      RATE_PERIOD_ACCRUAL_FACTOR.add(accrualFactor);
      currentStart = currentEnd;
      accrualFactorTotal += accrualFactor;
    }
    // dealing with Rate cutoff
    NB_PERIODS = RATE_PERIOD_ACCRUAL_FACTOR.size();
    FIXING_DATES.set(NB_PERIODS - 1, FIXING_DATES.get(NB_PERIODS - 2));
    RATE_PERIOD_START_DATES.set(NB_PERIODS - 1, RATE_PERIOD_START_DATES.get(NB_PERIODS - 2));
    RATE_PERIOD_END_DATES.set(NB_PERIODS - 1, RATE_PERIOD_END_DATES.get(NB_PERIODS - 2));
    ACCRUAL_FACTOR_TOTAL = accrualFactorTotal;
  }

  @Test(enabled = false)
  public void rateForward() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_21.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_21.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_21.getMulticurve().getSimplyCompoundForwardRate(
          ENV_21.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_PROVIDER.rate(ENV_21, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
    double rateGenComputed = RATE_PROVIDER.rate(ENV_21, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateGenComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  @Test(enabled = false)
  public void rateStart1Fixing0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 24);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_24.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_24.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_24.getMulticurve().getSimplyCompoundForwardRate(
          ENV_24.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_PROVIDER.rate(ENV_24, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  @Test(enabled = false)
  public void rateStart1Fixing1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00124;
    for (int i = 1; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_25.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_25.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_25.getMulticurve().getSimplyCompoundForwardRate(
          ENV_25.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_PROVIDER.rate(ENV_25, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  @Test(enabled = false)
  public void rateStart3Fixing3() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 1);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00124;
    accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(1) * 0.00125;
    accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(2) * 0.00126;
    for (int i = 3; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_28.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_28.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_28.getMulticurve().getSimplyCompoundForwardRate(
          ENV_28.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_PROVIDER.rate(ENV_28, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  @Test(enabled = false)
  public void rateStart4Fixing3() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 2);
    // Forward rates
    double accruedUnitNotional = 0d;
    accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(0) * 0.00124;
    accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(1) * 0.00125;
    accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(2) * 0.00126;
    for (int i = 3; i < NB_PERIODS; i++) {
      double ratePeriodStartTime = ENV_28.relativeTime(valuationDate, RATE_PERIOD_START_DATES.get(i));
      double ratePeriodendTime = ENV_28.relativeTime(valuationDate, RATE_PERIOD_END_DATES.get(i));
      double forwardRate = ENV_28.getMulticurve().getSimplyCompoundForwardRate(
          ENV_28.convert(USD_FED_FUND), ratePeriodStartTime, ratePeriodendTime, RATE_PERIOD_ACCRUAL_FACTOR.get(i));
      accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(i) * forwardRate;
    }
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_PROVIDER.rate(ENV_28, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  @Test
  public void rateAllFixed() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 9);
    // Forward rates
    double accruedUnitNotional = 0d;
    for (int i = 0; i < NB_PERIODS - 1; i++) {
      accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(i) * (0.00124 + i * 0.00001);
    }
    accruedUnitNotional += RATE_PERIOD_ACCRUAL_FACTOR.get(NB_PERIODS - 1) * 0.00131; // Cut-off part
    // final rate
    double rateExpected = accruedUnitNotional / ACCRUAL_FACTOR_TOTAL;
    double rateComputed = ON_AA_RATE_PROVIDER.rate(ENV_ALL, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
    double rateGenComputed = RATE_PROVIDER.rate(ENV_ALL, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
    assertEquals(rateExpected, rateGenComputed, TOLERANCE_RATE,
        "DefaultOvernightAveragedRateProviderFn: rate");
  }

  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateStart2Missing0() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 27);
    ON_AA_RATE_PROVIDER.rate(ENV_24, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
  }

  @Test(expectedExceptions = OpenGammaRuntimeException.class)
  public void rateMissingData() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 5);
    ON_AA_RATE_PROVIDER.rate(ENV_MISSINGDATA, valuationDate, ON_AA_RATE, START_DATE, END_DATE);
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
