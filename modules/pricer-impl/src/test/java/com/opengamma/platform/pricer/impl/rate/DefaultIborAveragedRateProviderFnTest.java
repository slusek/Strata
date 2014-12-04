/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.rate;

import static com.opengamma.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.basics.index.OvernightIndices.USD_FED_FUND;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.financial.instrument.index.IndexIborMaster;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.IborIndices;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.platform.finance.rate.IborAveragedFixing;
import com.opengamma.platform.finance.rate.IborAveragedRate;
import com.opengamma.platform.finance.rate.Rate;
import com.opengamma.platform.pricer.SwapInstrumentsDataSet;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;
import com.opengamma.util.tuple.Pair;

/**
 * Test {@link DefaultIborAveragedRateProviderFn}.
 */
public class DefaultIborAveragedRateProviderFnTest {

  private static final IborIndex USD_LIBOR_1M = IborIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = IborIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = IborIndices.USD_LIBOR_6M;
  private static final com.opengamma.analytics.financial.instrument.index.IborIndex USD_LIBOR_3M_OGA =
      IndexIborMaster.getInstance().getIndex(IndexIborMaster.USDLIBOR3M);
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_11_24 =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 24), 0.00123)
      .build();
  public static final double FIXING_11_25 = 0.00234;
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_11_25 =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 24), 0.00123)
      .put(LocalDate.of(2014, 11, 25), FIXING_11_25)
      .build();
  public static final double FIXING_12_2 = 0.0025;
  public static final double FIXING_12_9 = 0.003;
  public static final double FIXING_12_16 = 0.004;
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_12_16 =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 24), 0.00123)
      .put(LocalDate.of(2014, 11, 25), FIXING_11_25)
      .put(LocalDate.of(2014, 12, 2), FIXING_12_2)
      .put(LocalDate.of(2014, 12, 9), FIXING_12_9)
      .put(LocalDate.of(2014, 12, 16), FIXING_12_16)
      .build();
  private static final Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_OIS_PAIR = 
      StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();
  private static final ImmutablePricingEnvironment ENV_11_24 = env(TS_USDLIBOR3M_11_24);
  private static final ImmutablePricingEnvironment ENV_11_25 = env(TS_USDLIBOR3M_11_25);
  private static final ImmutablePricingEnvironment ENV_12_16 = env(TS_USDLIBOR3M_12_16);
  private static final int NB_FIXINGS_1 = 4;
  private static final IborAveragedFixing[] FIXINGS_1 = new IborAveragedFixing[NB_FIXINGS_1]; // 4 weekly fixing, same weight
  static {
    FIXINGS_1[0] = IborAveragedFixing.of(LocalDate.of(2014, 11, 25));
    FIXINGS_1[1] = IborAveragedFixing.of(LocalDate.of(2014, 12, 2));
    FIXINGS_1[2] = IborAveragedFixing.of(LocalDate.of(2014, 12, 9));
    FIXINGS_1[3] = IborAveragedFixing.of(LocalDate.of(2014, 12, 16));
  }
  private static final IborAveragedRate IBOR_AVERAGE_RATE = IborAveragedRate.of(USD_LIBOR_3M, FIXINGS_1);
  
  private static final RateProviderFn<IborAveragedRate> IBOR_AVERAGE_RATE_PROVIDER = DefaultIborAveragedRateProviderFn.DEFAULT;
  private static final RateProviderFn<Rate> RATE_PROVIDER = DefaultRateProviderFn.DEFAULT;
  private static final double TOLERANCE_RATE = 1.0E-10;


  @Test
  public void rateForward() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 4);
    double averageRateExpected = 0.0;
    double totalWeight = 0.0;
    for(int loopfix = 0 ; loopfix < NB_FIXINGS_1; loopfix++) {
      LocalDate fixingStartDate = USD_LIBOR_3M.calculateEffectiveFromFixing(FIXINGS_1[loopfix].getFixingDate());
      LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
      double fixingYearFraction = USD_LIBOR_3M.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
      double forwardRate = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_LIBOR_3M_OGA,
          ENV_11_24.relativeTime(valuationDate, fixingStartDate),
          ENV_11_24.relativeTime(valuationDate, fixingEndDate), fixingYearFraction);
      averageRateExpected += forwardRate * FIXINGS_1[loopfix].getWeight();
      totalWeight += FIXINGS_1[loopfix].getWeight();
    }
    averageRateExpected /= totalWeight;
    double averageRateComputed = 
        IBOR_AVERAGE_RATE_PROVIDER.rate(ENV_11_24, valuationDate, IBOR_AVERAGE_RATE, valuationDate, valuationDate);
    assertEquals(averageRateExpected, averageRateComputed, TOLERANCE_RATE,
        "DefaultIborAveragedRateProviderFn: rate forward");
    double averageRateComputedDefault = 
        RATE_PROVIDER.rate(ENV_11_24, valuationDate, IBOR_AVERAGE_RATE, valuationDate, valuationDate);
    assertEquals(averageRateComputed, averageRateComputedDefault, TOLERANCE_RATE,
        "DefaultIborAveragedRateProviderFn: rate forward");
  }


  @Test
  public void rateAtFix1NoFixing() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 25);
    double averageRateExpected = 0.0;
    double totalWeight = 0.0;
    for(int loopfix = 0 ; loopfix < NB_FIXINGS_1; loopfix++) {
      LocalDate fixingStartDate = USD_LIBOR_3M.calculateEffectiveFromFixing(FIXINGS_1[loopfix].getFixingDate());
      LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
      double fixingYearFraction = USD_LIBOR_3M.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
      double forwardRate = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_LIBOR_3M_OGA,
          ENV_11_24.relativeTime(valuationDate, fixingStartDate),
          ENV_11_24.relativeTime(valuationDate, fixingEndDate), fixingYearFraction);
      averageRateExpected += forwardRate * FIXINGS_1[loopfix].getWeight();
      totalWeight += FIXINGS_1[loopfix].getWeight();
    }
    averageRateExpected /= totalWeight;
    double averageRateComputed = 
        IBOR_AVERAGE_RATE_PROVIDER.rate(ENV_11_24, valuationDate, IBOR_AVERAGE_RATE, valuationDate, valuationDate);
    assertEquals(averageRateExpected, averageRateComputed, TOLERANCE_RATE,
        "DefaultIborAveragedRateProviderFn: rate forward");
    double averageRateComputedDefault = 
        RATE_PROVIDER.rate(ENV_11_24, valuationDate, IBOR_AVERAGE_RATE, valuationDate, valuationDate);
    assertEquals(averageRateComputed, averageRateComputedDefault, TOLERANCE_RATE,
        "DefaultIborAveragedRateProviderFn: rate forward");
  }


  @Test
  public void ratePostFix1() {
    LocalDate valuationDate = LocalDate.of(2014, 11, 26);
    double averageRateExpected = 0.0;
    averageRateExpected += FIXING_11_25 * FIXINGS_1[0].getWeight();
    double totalWeight = 0.0;
    totalWeight += FIXINGS_1[0].getWeight();
    for(int loopfix = 1 ; loopfix < NB_FIXINGS_1; loopfix++) {
      LocalDate fixingStartDate = USD_LIBOR_3M.calculateEffectiveFromFixing(FIXINGS_1[loopfix].getFixingDate());
      LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
      double fixingYearFraction = USD_LIBOR_3M.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
      double forwardRate = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_LIBOR_3M_OGA,
          ENV_11_25.relativeTime(valuationDate, fixingStartDate),
          ENV_11_25.relativeTime(valuationDate, fixingEndDate), fixingYearFraction);
      averageRateExpected += forwardRate * FIXINGS_1[loopfix].getWeight();
      totalWeight += FIXINGS_1[loopfix].getWeight();
    }
    averageRateExpected /= totalWeight;
    double averageRateComputed = 
        IBOR_AVERAGE_RATE_PROVIDER.rate(ENV_11_25, valuationDate, IBOR_AVERAGE_RATE, valuationDate, valuationDate);
    assertEquals(averageRateExpected, averageRateComputed, TOLERANCE_RATE,
        "DefaultIborAveragedRateProviderFn: rate forward");
    double averageRateComputedDefault = 
        RATE_PROVIDER.rate(ENV_11_25, valuationDate, IBOR_AVERAGE_RATE, valuationDate, valuationDate);
    assertEquals(averageRateComputed, averageRateComputedDefault, TOLERANCE_RATE,
        "DefaultIborAveragedRateProviderFn: rate forward");
  }

  @Test
  public void rateAllFixings() {
    LocalDate valuationDate = LocalDate.of(2014, 12, 17);
    double averageRateExpected = 0.0;
    averageRateExpected += FIXING_11_25 * FIXINGS_1[0].getWeight();
    averageRateExpected += FIXING_12_2 * FIXINGS_1[1].getWeight();
    averageRateExpected += FIXING_12_9 * FIXINGS_1[2].getWeight();
    averageRateExpected += FIXING_12_16 * FIXINGS_1[3].getWeight();
    double totalWeight = 0.0;
    for (int i = 0; i < 4; i++) {
      totalWeight += FIXINGS_1[i].getWeight();
    }
    averageRateExpected /= totalWeight;
    double averageRateComputed =
        IBOR_AVERAGE_RATE_PROVIDER.rate(ENV_12_16, valuationDate, IBOR_AVERAGE_RATE, valuationDate, valuationDate);
    assertEquals(averageRateExpected, averageRateComputed, TOLERANCE_RATE,
        "DefaultIborAveragedRateProviderFn: rate forward");
    double averageRateComputedDefault =
        RATE_PROVIDER.rate(ENV_12_16, valuationDate, IBOR_AVERAGE_RATE, valuationDate, valuationDate);
    assertEquals(averageRateComputed, averageRateComputedDefault, TOLERANCE_RATE,
      "DefaultIborAveragedRateProviderFn: rate forward");
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
