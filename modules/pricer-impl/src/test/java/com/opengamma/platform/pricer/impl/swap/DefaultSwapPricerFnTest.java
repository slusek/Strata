/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.swap;

import static com.opengamma.basics.PayReceive.PAY;
import static com.opengamma.basics.PayReceive.RECEIVE;
import static com.opengamma.basics.currency.Currency.USD;
import static com.opengamma.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.basics.date.BusinessDayConventions.PRECEDING;
import static com.opengamma.basics.date.DayCounts.ACT_360;
import static com.opengamma.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.basics.date.DayCounts.THIRTY_U_360;
import static com.opengamma.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.basics.schedule.Frequency.P3M;
import static com.opengamma.basics.schedule.Frequency.P6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MultipleCurrencyMulticurveSensitivity;
import com.opengamma.basics.PayReceive;
import com.opengamma.basics.currency.MultiCurrencyAmount;
import com.opengamma.basics.date.BusinessDayAdjustment;
import com.opengamma.basics.date.DaysAdjustment;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.IborIndices;
import com.opengamma.basics.schedule.Frequency;
import com.opengamma.basics.schedule.PeriodicSchedule;
import com.opengamma.basics.schedule.StubConvention;
import com.opengamma.basics.value.ValueSchedule;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.swap.ExpandedSwapLeg;
import com.opengamma.platform.finance.swap.FixedRateCalculation;
import com.opengamma.platform.finance.swap.IborRateCalculation;
import com.opengamma.platform.finance.swap.NotionalAmount;
import com.opengamma.platform.finance.swap.PaymentSchedule;
import com.opengamma.platform.finance.swap.RateSwapLeg;
import com.opengamma.platform.finance.swap.Swap;
import com.opengamma.platform.finance.swap.SwapLeg;
import com.opengamma.platform.finance.swap.SwapTrade;
import com.opengamma.platform.pricer.CalendarUSD;
import com.opengamma.platform.pricer.SwapInstrumentsDataSet;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.swap.SwapLegPricerFn;
import com.opengamma.platform.pricer.swap.SwapPricerFn;
import com.opengamma.platform.source.id.StandardId;

/**
 * Test {@link DefaultSwapPricerFn}.
 */
@Test
public class DefaultSwapPricerFnTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 1, 22);

  private static final IborIndex USD_LIBOR_1M = IborIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = IborIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = IborIndices.USD_LIBOR_6M;
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_WITHOUTTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 1, 21), 0.00123)
      .build();
  public static final double FIXING_TODAY = 0.00234;
  public static final LocalDateDoubleTimeSeries TS_USDLIBOR3M_WITHTODAY =
      LocalDateDoubleTimeSeries.builder()
      .put(LocalDate.of(2014, 1, 21), 0.00123)
      .put(LocalDate.of(2014, 1, 22), FIXING_TODAY)
      .build();
  private static final com.opengamma.util.tuple.Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_OIS_PAIR = 
      StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY = env(TS_USDLIBOR3M_WITHOUTTODAY);
  private static final ImmutablePricingEnvironment ENV_WITHTODAY = env(TS_USDLIBOR3M_WITHTODAY);
  

  private static final SwapPricerFn SWAP_PRICER = DefaultSwapPricerFn.DEFAULT;
  private static final SwapLegPricerFn<ExpandedSwapLeg> LEG_PRICER = DefaultExpandedSwapLegPricerFn.DEFAULT;
  
  /* Instrument */
  private static final BusinessDayAdjustment BDA_MF = BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CalendarUSD.NYC);
  private static final BusinessDayAdjustment BDA_P = BusinessDayAdjustment.of(PRECEDING, CalendarUSD.NYC);
  private static final NotionalAmount NOTIONAL = NotionalAmount.of(USD, 100_000_000);

  private static final LocalDate START_DATE_1 = LocalDate.of(2014, 9, 12);
  private static final LocalDate END_DATE_1 = LocalDate.of(2019, 9, 12);
  private static final double FIXED_RATE = 0.015;
  private static final RateSwapLeg FIXED_LEG = fixedLeg(
      START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, FIXED_RATE, null);
  private static final RateSwapLeg IBOR_LEG = iborLeg(
      START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null);
        
  private static final SwapTrade SWAP_TRADE = SwapTrade.builder()
      .standardId(StandardId.of("OG-Trade", "1"))
      .tradeDate(LocalDate.of(2014, 9, 10))
      .swap(Swap.of(FIXED_LEG, IBOR_LEG))
      .build();
  
  /* Constants */
  private static final double TOLERANCE_PV = 1.0E-2;
  private static final double BP1 = 1.0E-4;
  
  @SuppressWarnings("unused")
  @Test
  public void presentValue() {
    Swap swap = SWAP_TRADE.getSwap();
    double pvExpected = 0.0d;
    for(SwapLeg leg: swap.getLegs()) {
      pvExpected += LEG_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, leg.toExpanded());
    } // Single currency swap, no need for a currency dimension.
    MultiCurrencyAmount pvComputed = SWAP_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, swap);
    assertEquals(pvExpected, pvComputed.getAmount(USD).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: Present Value");
  }
  
  @Test
  public void presentValueCurveSensitivity() {
    Pair<MultiCurrencyAmount, MultipleCurrencyMulticurveSensitivity> pvcs = 
        SWAP_PRICER.presentValueCurveSensitivity(ENV_WITHOUTTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    MultiCurrencyAmount pv = SWAP_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    assertEquals(pv.getAmount(USD).getAmount(), pvcs.getFirst().getAmount(USD).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: Present Value curve sensitivity");
  }
  
  @Test
  public void parRate() {
    double parRateWithoutFixingComputed = SWAP_PRICER.parRate(ENV_WITHOUTTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    SwapLeg fixedLeg0WithoutFixing = fixedLeg(
        START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, parRateWithoutFixingComputed, null);
    Swap swap0WithoutFixing = Swap.of(fixedLeg0WithoutFixing, IBOR_LEG);
    MultiCurrencyAmount pv0WithoutFixing = SWAP_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, swap0WithoutFixing);
    assertEquals(0.0d, pv0WithoutFixing.getAmount(NOTIONAL.getCurrency()).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: parRate");
    double parRateWithFixingComputed = SWAP_PRICER.parRate(ENV_WITHTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    SwapLeg fixedLeg0WithFixing = fixedLeg(
        START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, parRateWithFixingComputed, null);
    Swap swap0WithFixing = Swap.of(fixedLeg0WithFixing, IBOR_LEG);
    MultiCurrencyAmount pv0WithFixing = SWAP_PRICER.presentValue(ENV_WITHTODAY, VALUATION_DATE, swap0WithFixing);
    assertEquals(0.0d, pv0WithFixing.getAmount(NOTIONAL.getCurrency()).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: parRate");
  }
  
  @Test
  public void parSpread() {
    double parSpreadWithoutFixingComputed = SWAP_PRICER.parSpread(ENV_WITHOUTTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    SwapLeg fixedLeg0WithoutFixing = fixedLeg(
        START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, FIXED_RATE + parSpreadWithoutFixingComputed, null);
    Swap swap0WithoutFixing = Swap.of(fixedLeg0WithoutFixing, IBOR_LEG);
    MultiCurrencyAmount pv0WithoutFixing = SWAP_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, swap0WithoutFixing);
    assertEquals(0.0d, pv0WithoutFixing.getAmount(NOTIONAL.getCurrency()).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: parRate");
    double parSpreadWithFixingComputed = SWAP_PRICER.parSpread(ENV_WITHTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    SwapLeg fixedLeg0WithFixing = fixedLeg(
        START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, FIXED_RATE + parSpreadWithFixingComputed, null);
    Swap swap0WithFixing = Swap.of(fixedLeg0WithFixing, IBOR_LEG);
    MultiCurrencyAmount pv0WithFixing = SWAP_PRICER.presentValue(ENV_WITHTODAY, VALUATION_DATE, swap0WithFixing);
    assertEquals(0.0d, pv0WithFixing.getAmount(NOTIONAL.getCurrency()).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: parRate");
  }
  

  @Test(enabled = false)
  public void performance() {

    long startTime, endTime;
    int nbTest = 100;
    int nbSwap = 100;
    int nbRep = 10;

    for (int looprep = 0; looprep < nbRep; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTest; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwap; loops++) {
          double rate = FIXED_RATE - 0.0050 + loops * BP1;
          SwapLeg fixedLeg = fixedLeg(
              START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, rate, null);
          SwapLeg iborLeg = iborLeg(
              START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null);
          Swap swap = Swap.of(fixedLeg, iborLeg);
          @SuppressWarnings("unused")
          MultiCurrencyAmount pv = SWAP_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, swap);
          pvTotal += pv.getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTest + " x " + nbSwap + " swaps (5Y/Q) - construction+pv " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: construction + pv: On Mac Book Pro 2.6 GHz Intel i7: 605 ms for 100x100 swaps.
      // Performance note: OG-Analytics: 434 ms
    }

    for (int looprep = 0; looprep < nbRep; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTest; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwap; loops++) {
          double rate = FIXED_RATE - 0.0050 + loops * BP1;
          SwapLeg fixedLeg = fixedLeg(
              START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, rate, null);
          SwapLeg iborLeg = iborLeg(
              START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null);
          Swap swap = Swap.of(fixedLeg, iborLeg);
          @SuppressWarnings("unused")
          Pair<MultiCurrencyAmount, MultipleCurrencyMulticurveSensitivity> pvcs = 
          SWAP_PRICER.presentValueCurveSensitivity(ENV_WITHOUTTODAY, VALUATION_DATE, swap);
          pvTotal += pvcs.getFirst().getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTest + " x " + nbSwap + " swaps (5Y/Q) - construction+pvcs " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: construction + pv: On Mac Book Pro 2.6 GHz Intel i7: 875 ms for 100x100 swaps.
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

  //-------------------------------------------------------------------------
  // fixed rate leg
  private static RateSwapLeg fixedLeg(
      LocalDate start, LocalDate end, Frequency frequency,
      PayReceive payReceive, NotionalAmount notional, double fixedRate, StubConvention stubConvention) {
    
    return RateSwapLeg.builder()
        .payReceive(payReceive)
        .accrualPeriods(PeriodicSchedule.builder()
            .startDate(start)
            .endDate(end)
            .frequency(frequency)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(stubConvention)
            .build())
        .paymentPeriods(PaymentSchedule.builder()
            .paymentFrequency(frequency)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notional(notional)
        .calculation(FixedRateCalculation.builder()
            .dayCount(THIRTY_U_360)
            .rate(ValueSchedule.of(fixedRate))
            .build())
        .build();
  }
  

  // fixed rate leg
  private static RateSwapLeg iborLeg(
      LocalDate start, LocalDate end, Frequency frequency,
      PayReceive payReceive, NotionalAmount notional, IborIndex index, StubConvention stubConvention) {
    return RateSwapLeg.builder()
        .payReceive(payReceive)
        .accrualPeriods(PeriodicSchedule.builder()
            .startDate(start)
            .endDate(end)
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(stubConvention)
            .build())
        .paymentPeriods(PaymentSchedule.builder()
            .paymentFrequency(P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notional(notional)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(index)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();
  }
  
}
