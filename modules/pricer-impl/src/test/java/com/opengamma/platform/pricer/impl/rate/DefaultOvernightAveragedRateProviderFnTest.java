/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.rate;

import static com.opengamma.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.basics.index.OvernightIndices.USD_FED_FUND;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.financial.instrument.index.IndexON;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.IborIndices;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.platform.finance.rate.OvernightCompoundedRate;
import com.opengamma.platform.finance.rate.Rate;
import com.opengamma.platform.pricer.SwapInstrumentsDataSet;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;
import com.opengamma.util.tuple.Pair;

/**
 * Test {@link DefaultOvernightAveragedRateProviderFn}.
 */
@Test
public class DefaultOvernightAveragedRateProviderFnTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 11, 25);

  private static final IborIndex USD_LIBOR_1M = IborIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = IborIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = IborIndices.USD_LIBOR_6M;
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
