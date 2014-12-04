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
import com.opengamma.platform.finance.rate.IborInterpolatedRate;
import com.opengamma.platform.finance.rate.Rate;
import com.opengamma.platform.pricer.SwapInstrumentsDataSet;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;
import com.opengamma.util.tuple.Pair;

/**
 * Test {@link DefaultIborInterpolatedRateProviderFn}.
 */
@Test
public class DefaultIborInterpolatedRateProviderFnTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 11, 25);

  private static final IborIndex USD_LIBOR_1M = IborIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = IborIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = IborIndices.USD_LIBOR_6M;
  private static final com.opengamma.analytics.financial.instrument.index.IborIndex USD_LIBOR_3M_OGA =
      IndexIborMaster.getInstance().getIndex(IndexIborMaster.USDLIBOR3M);
  private static final com.opengamma.analytics.financial.instrument.index.IborIndex USD_LIBOR_6M_OGA =
      IndexIborMaster.getInstance().getIndex(IndexIborMaster.USDLIBOR6M);
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_WITHOUTTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 24), 0.00123)
      .build();
  public static final double FIXING_TODAY_3M = 0.00234;
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_WITHTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 24), 0.00123)
      .put(LocalDate.of(2014, 11, 25), FIXING_TODAY_3M)
      .build();
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR6M_WITHOUTTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 24), 0.00133)
      .build();
  public static final double FIXING_TODAY_6M = 0.00244;
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR6M_WITHTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 11, 24), 0.00133)
      .put(LocalDate.of(2014, 11, 25), FIXING_TODAY_6M)
      .build();
  private static final Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_OIS_PAIR = 
      StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();
  private static final ImmutablePricingEnvironment ENV_NONO = env(TS_USDLIBOR3M_WITHOUTTODAY, TS_USDLIBOR6M_WITHOUTTODAY);
  private static final ImmutablePricingEnvironment ENV_YESNO = env(TS_USDLIBOR3M_WITHTODAY, TS_USDLIBOR6M_WITHOUTTODAY);
  private static final ImmutablePricingEnvironment ENV_NOYES = env(TS_USDLIBOR3M_WITHOUTTODAY, TS_USDLIBOR6M_WITHTODAY);
  private static final ImmutablePricingEnvironment ENV_YESYES = env(TS_USDLIBOR3M_WITHTODAY, TS_USDLIBOR6M_WITHTODAY);
  
  private static final RateProviderFn<IborInterpolatedRate> IBOR_INT_RATE_PROVIDER = 
      DefaultIborInterpolatedRateProviderFn.DEFAULT;
  private static final RateProviderFn<Rate> RATE_PROVIDER = DefaultRateProviderFn.DEFAULT;
  private static final IborInterpolatedRate IBOR_INT_TODAY = 
      IborInterpolatedRate.of(USD_LIBOR_3M, USD_LIBOR_6M, VALUATION_DATE);
  private static final double TOLERANCE_RATE = 1.0E-10;
  
  @Test
  public void rateTodayNoFixing3MNoFixing6M() {
    LocalDate cpnStart = IBOR_INT_TODAY.getShortIndex().calculateEffectiveFromFixing(IBOR_INT_TODAY.getFixingDate());
    LocalDate cpnEnd = LocalDate.of(2015, 3, 25); // 4M
    double rateComputed = IBOR_INT_RATE_PROVIDER.rate(ENV_NONO, VALUATION_DATE, IBOR_INT_TODAY, cpnStart, cpnEnd);
    LocalDate fixingStartDate = IBOR_INT_TODAY.getShortIndex().calculateEffectiveFromFixing(IBOR_INT_TODAY.getFixingDate());
    LocalDate fixingEndDate3M = IBOR_INT_TODAY.getShortIndex().calculateMaturityFromEffective(fixingStartDate);
    LocalDate fixingEndDate6M = IBOR_INT_TODAY.getLongIndex().calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction3M = IBOR_INT_TODAY.getShortIndex().getDayCount().yearFraction(fixingStartDate, fixingEndDate3M);
    double fixingYearFraction6M = IBOR_INT_TODAY.getShortIndex().getDayCount().yearFraction(fixingStartDate, fixingEndDate6M);
    double rateToday3M = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_LIBOR_3M_OGA,
        ENV_NONO.relativeTime(VALUATION_DATE, fixingStartDate),
        ENV_NONO.relativeTime(VALUATION_DATE, fixingEndDate3M), fixingYearFraction3M);
    double rateToday6M = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_LIBOR_6M_OGA,
        ENV_NONO.relativeTime(VALUATION_DATE, fixingStartDate),
        ENV_NONO.relativeTime(VALUATION_DATE, fixingEndDate6M), fixingYearFraction6M);
    double days3M = fixingEndDate3M.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay(); //nb days in 3M fixing period
    double days6M = fixingEndDate6M.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay(); //nb days in 6M fixing period
    double daysCpn = cpnEnd.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay();
    double weight3M = (days6M - daysCpn) / (days6M - days3M);
    double weight6M = (daysCpn - days3M) / (days6M - days3M);
    double rateExpected = (weight3M * rateToday3M + weight6M * rateToday6M);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborInterpolatedRateProviderFn: rate on fixing date");
    double rateGenComputed = 
        RATE_PROVIDER.rate(ENV_NONO, VALUATION_DATE, IBOR_INT_TODAY, cpnStart, cpnEnd);
    assertEquals(rateGenComputed, rateComputed, TOLERANCE_RATE, "DefaultIborInterpolatedRateProviderFn: rate on fixing date");
  }
  
  @Test
  public void rateTodayFixing3MNoFixing6M() {
    LocalDate cpnStart = IBOR_INT_TODAY.getShortIndex().calculateEffectiveFromFixing(IBOR_INT_TODAY.getFixingDate());
    LocalDate cpnEnd = LocalDate.of(2015, 3, 25); // 4M
    double rateComputed = IBOR_INT_RATE_PROVIDER.rate(ENV_YESNO, VALUATION_DATE, IBOR_INT_TODAY, cpnStart, cpnEnd);
    LocalDate fixingStartDate = IBOR_INT_TODAY.getShortIndex().calculateEffectiveFromFixing(IBOR_INT_TODAY.getFixingDate());
    LocalDate fixingEndDate3M = IBOR_INT_TODAY.getShortIndex().calculateMaturityFromEffective(fixingStartDate);
    LocalDate fixingEndDate6M = IBOR_INT_TODAY.getLongIndex().calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction6M = IBOR_INT_TODAY.getShortIndex().getDayCount().yearFraction(fixingStartDate, fixingEndDate6M);
    double rateToday3M = FIXING_TODAY_3M;
    double rateToday6M = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_LIBOR_6M_OGA,
        ENV_YESNO.relativeTime(VALUATION_DATE, fixingStartDate),
        ENV_YESNO.relativeTime(VALUATION_DATE, fixingEndDate6M), fixingYearFraction6M);
    double days3M = fixingEndDate3M.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay(); //number of days in 3M fixing period
    double days6M = fixingEndDate6M.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay(); //number of days in 6M fixing period
    double daysCpn = cpnEnd.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay();
    double weight3M = (days6M - daysCpn) / (days6M - days3M);
    double weight6M = (daysCpn - days3M) / (days6M - days3M);
    double rateExpected = (weight3M * rateToday3M + weight6M * rateToday6M);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborInterpolatedRateProviderFn: rate on fixing date");
    double rateGenComputed = 
        RATE_PROVIDER.rate(ENV_YESNO, VALUATION_DATE, IBOR_INT_TODAY, cpnStart, cpnEnd);
    assertEquals(rateGenComputed, rateComputed, TOLERANCE_RATE, "DefaultIborInterpolatedRateProviderFn: rate on fixing date");
  }
  
  @Test
  public void rateTodayNoFixing3MFixing6M() {
    LocalDate cpnStart = IBOR_INT_TODAY.getShortIndex().calculateEffectiveFromFixing(IBOR_INT_TODAY.getFixingDate());
    LocalDate cpnEnd = LocalDate.of(2015, 3, 25); // 4M
    double rateComputed = IBOR_INT_RATE_PROVIDER.rate(ENV_NOYES, VALUATION_DATE, IBOR_INT_TODAY, cpnStart, cpnEnd);
    LocalDate fixingStartDate = IBOR_INT_TODAY.getShortIndex().calculateEffectiveFromFixing(IBOR_INT_TODAY.getFixingDate());
    LocalDate fixingEndDate3M = IBOR_INT_TODAY.getShortIndex().calculateMaturityFromEffective(fixingStartDate);
    LocalDate fixingEndDate6M = IBOR_INT_TODAY.getLongIndex().calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction3M = IBOR_INT_TODAY.getShortIndex().getDayCount().yearFraction(fixingStartDate, fixingEndDate3M);
    double rateToday3M = MULTICURVE_OIS.getSimplyCompoundForwardRate(USD_LIBOR_3M_OGA,
        ENV_NOYES.relativeTime(VALUATION_DATE, fixingStartDate),
        ENV_NOYES.relativeTime(VALUATION_DATE, fixingEndDate3M), fixingYearFraction3M);
    double rateToday6M = FIXING_TODAY_6M;
    double days3M = fixingEndDate3M.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay(); //number of days in 3M fixing period
    double days6M = fixingEndDate6M.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay(); //number of days in 6M fixing period
    double daysCpn = cpnEnd.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay();
    double weight3M = (days6M - daysCpn) / (days6M - days3M);
    double weight6M = (daysCpn - days3M) / (days6M - days3M);
    double rateExpected = (weight3M * rateToday3M + weight6M * rateToday6M);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborInterpolatedRateProviderFn: rate on fixing date");
    double rateGenComputed = 
        RATE_PROVIDER.rate(ENV_NOYES, VALUATION_DATE, IBOR_INT_TODAY, cpnStart, cpnEnd);
    assertEquals(rateGenComputed, rateComputed, TOLERANCE_RATE, "DefaultIborInterpolatedRateProviderFn: rate on fixing date");
  }
  
  @Test
  public void rateTodayFixing3MFixing6M() {
    LocalDate cpnStart = IBOR_INT_TODAY.getShortIndex().calculateEffectiveFromFixing(IBOR_INT_TODAY.getFixingDate());
    LocalDate cpnEnd = LocalDate.of(2015, 3, 25); // 4M
    double rateComputed = IBOR_INT_RATE_PROVIDER.rate(ENV_YESYES, VALUATION_DATE, IBOR_INT_TODAY, cpnStart, cpnEnd);
    LocalDate fixingStartDate = IBOR_INT_TODAY.getShortIndex().calculateEffectiveFromFixing(IBOR_INT_TODAY.getFixingDate());
    LocalDate fixingEndDate3M = IBOR_INT_TODAY.getShortIndex().calculateMaturityFromEffective(fixingStartDate);
    LocalDate fixingEndDate6M = IBOR_INT_TODAY.getLongIndex().calculateMaturityFromEffective(fixingStartDate);
    double rateToday3M = FIXING_TODAY_3M;
    double rateToday6M = FIXING_TODAY_6M;
    double days3M = fixingEndDate3M.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay(); //number of days in 3M fixing period
    double days6M = fixingEndDate6M.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay(); //number of days in 6M fixing period
    double daysCpn = cpnEnd.toEpochDay() - IBOR_INT_TODAY.getFixingDate().toEpochDay();
    double weight3M = (days6M - daysCpn) / (days6M - days3M);
    double weight6M = (daysCpn - days3M) / (days6M - days3M);
    double rateExpected = (weight3M * rateToday3M + weight6M * rateToday6M);
    assertEquals(rateExpected, rateComputed, TOLERANCE_RATE, "DefaultIborInterpolatedRateProviderFn: rate on fixing date");
    double rateGenComputed = 
        RATE_PROVIDER.rate(ENV_YESYES, VALUATION_DATE, IBOR_INT_TODAY, cpnStart, cpnEnd);
    assertEquals(rateGenComputed, rateComputed, TOLERANCE_RATE, "DefaultIborInterpolatedRateProviderFn: rate on fixing date");
  }

  /**
   * Create a pricing environment from the existing MulticurveProvider and Ibor fixing time series.
   * @param ts The time series for the USDLIBOR3M.
   * @return The pricing environment.
   */
  private static ImmutablePricingEnvironment env(LocalDateDoubleTimeSeries ts3, LocalDateDoubleTimeSeries ts6) {
    return ImmutablePricingEnvironment.builder()
        .multicurve(MULTICURVE_OIS)
        .timeSeries(ImmutableMap.of(
            USD_LIBOR_1M, SwapInstrumentsDataSet.TS_USDLIBOR1M,
            USD_LIBOR_3M, ts3,
            USD_LIBOR_6M, ts6,
            USD_FED_FUND, SwapInstrumentsDataSet.TS_USDON))
        .dayCount(ACT_ACT_ISDA)
        .build();
  }
  
}
