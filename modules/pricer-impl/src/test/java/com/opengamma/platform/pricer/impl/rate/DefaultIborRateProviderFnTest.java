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
import com.opengamma.analytics.financial.instrument.index.IndexIborMaster;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.RateIndices;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.platform.finance.rate.IborRate;
import com.opengamma.platform.finance.rate.Rate;
import com.opengamma.platform.pricer.SwapInstrumentsDataSet;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;
import com.opengamma.util.tuple.Pair;

/**
 * Test {@link DefaultIborRateProviderFn}.
 */
@Test
public class DefaultIborRateProviderFnTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 11, 25);

  private static final IborIndex USD_LIBOR_1M = RateIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = RateIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = RateIndices.USD_LIBOR_6M;
  private static final com.opengamma.analytics.financial.instrument.index.IborIndex USD_LIBOR_3M_OGA =
      IndexIborMaster.getInstance().getIndex(IndexIborMaster.USDLIBOR3M);
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_WITHOUTTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 24), 0.00123)
      .build();
  public static final double FIXING_TODAY = 0.00234;
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_WITHTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 24), 0.00123)
      .put(LocalDate.of(2014, 11, 25), FIXING_TODAY)
      .build();
  private static final Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_OIS_PAIR = 
      StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY = env(TS_USDLIBOR3M_WITHOUTTODAY);
  private static final ImmutablePricingEnvironment ENV_WITHTODAY = env(TS_USDLIBOR3M_WITHTODAY);
  
  private static final LocalDate[] FIXING_DATES_TESTED = new LocalDate[] 
      {LocalDate.of(2014, 11, 26), LocalDate.of(2014, 12, 2), LocalDate.of(2014, 12, 23), LocalDate.of(2015, 11, 25),
    LocalDate.of(2016, 11, 25)};
  private static final int NB_TESTS = FIXING_DATES_TESTED.length;
  private static final RateProviderFn<IborRate> IBOR_RATE_PROVIDER = DefaultIborRateProviderFn.DEFAULT;
  private static final RateProviderFn<Rate> RATE_PROVIDER = DefaultRateProviderFn.DEFAULT;
  private static final IborRate IBOR_TODAY = IborRate.of(USD_LIBOR_3M, VALUATION_DATE);
  private static final double TOLERANCE_RATE = 1.0E-10;
  
  @Test
  public void rateTodayWithoutFixing() {
    double rateIborTodayWithoutFixingComputed = 
        IBOR_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, IBOR_TODAY, VALUATION_DATE, VALUATION_DATE);
    LocalDate fixingStartDate = IBOR_TODAY.getIndex().calculateEffectiveFromFixing(IBOR_TODAY.getFixingDate());
    LocalDate fixingEndDate = IBOR_TODAY.getIndex().calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = IBOR_TODAY.getIndex().getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double rateTodayWithoutFixingExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_LIBOR_3M_OGA,
        ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingStartDate),
        ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingYearFraction);
    assertEquals(rateTodayWithoutFixingExpected, rateIborTodayWithoutFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate on fixing date");
    double rateGenTodayWithoutFixingComputed = 
        RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, IBOR_TODAY, VALUATION_DATE, VALUATION_DATE);
    assertEquals(rateGenTodayWithoutFixingComputed, rateIborTodayWithoutFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate on fixing date");
  }

  @Test
  public void rateTodayWithFixing() {
    double rateIborTodayWithFixingComputed = 
        IBOR_RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_TODAY, VALUATION_DATE, VALUATION_DATE);
    double rateTodayWithFixingExpected = FIXING_TODAY;
    assertEquals(rateTodayWithFixingExpected, rateIborTodayWithFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate on fixing date");
    double rateGenTodayWithFixingComputed = 
        RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, IBOR_TODAY, VALUATION_DATE, VALUATION_DATE);
    assertEquals(rateGenTodayWithFixingComputed, rateIborTodayWithFixingComputed, TOLERANCE_RATE,
        "DefaultIborRateProviderFn: rate on fixing date");
  }

  @Test
  public void rateForward() {
    for(int i = 0; i < NB_TESTS ; i++) {
      IborRate ibor = IborRate.of(USD_LIBOR_3M, FIXING_DATES_TESTED[i]);
      LocalDate fixingStartDate = USD_LIBOR_3M.calculateEffectiveFromFixing(FIXING_DATES_TESTED[i]);
      LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
      double fixingYearFraction = USD_LIBOR_3M.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
      double rateExpected = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_LIBOR_3M_OGA,
          ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingStartDate),
          ENV_WITHOUTTODAY.relativeTime(VALUATION_DATE, fixingEndDate), fixingYearFraction);
      double rateIborWithoutFixingComputed = 
          IBOR_RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ibor, VALUATION_DATE, VALUATION_DATE);
      assertEquals(rateExpected, rateIborWithoutFixingComputed, TOLERANCE_RATE,
          "DefaultIborRateProviderFn: rate forward");
      double rateIborWithFixingComputed = 
          IBOR_RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, ibor, VALUATION_DATE, VALUATION_DATE);
      assertEquals(rateExpected, rateIborWithFixingComputed, TOLERANCE_RATE,
          "DefaultIborRateProviderFn: rate forward");
      double rateGenWithoutFixingComputed = 
          RATE_PROVIDER.rate(ENV_WITHOUTTODAY, VALUATION_DATE, ibor, VALUATION_DATE, VALUATION_DATE);
      assertEquals(rateGenWithoutFixingComputed, rateIborWithoutFixingComputed, TOLERANCE_RATE,
          "DefaultIborRateProviderFn: rate forward");
      double rateGenWithFixingComputed = 
          RATE_PROVIDER.rate(ENV_WITHTODAY, VALUATION_DATE, ibor, VALUATION_DATE, VALUATION_DATE);
      assertEquals(rateGenWithFixingComputed, rateIborWithFixingComputed, TOLERANCE_RATE,
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
            USD_LIBOR_3M, ts,
            USD_LIBOR_6M, SwapInstrumentsDataSet.TS_USDLIBOR6M,
            USD_FED_FUND, SwapInstrumentsDataSet.TS_USDON))
        .dayCount(ACT_ACT_ISDA)
        .build();
  }
  
}
