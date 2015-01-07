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
import static com.opengamma.basics.date.DayCounts.THIRTY_U_360;
import static com.opengamma.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.basics.schedule.Frequency.P3M;
import static com.opengamma.basics.schedule.Frequency.P6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.financial.instrument.index.IndexIborMaster;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.calculator.discounting.PresentValueCurveSensitivityDiscountingCalculator;
import com.opengamma.analytics.financial.provider.calculator.generic.MarketQuoteSensitivityBlockCalculator;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.analytics.financial.provider.description.interestrate.ParameterProviderInterface;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MultipleCurrencyMulticurveSensitivity;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MultipleCurrencyParameterSensitivity;
import com.opengamma.analytics.financial.provider.sensitivity.parameter.ParameterSensitivityParameterCalculator;
import com.opengamma.analytics.financial.util.AssertSensitivityObjects;
import com.opengamma.basics.PayReceive;
import com.opengamma.basics.currency.MultiCurrencyAmount;
import com.opengamma.basics.date.BusinessDayAdjustment;
import com.opengamma.basics.date.DayCount;
import com.opengamma.basics.date.DayCounts;
import com.opengamma.basics.date.DaysAdjustment;
import com.opengamma.basics.date.HolidayCalendars;
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
import com.opengamma.platform.pricer.impl.ImmutableStoredPricingEnvironment;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3LD;
import com.opengamma.platform.pricer.results.ParameterSensitivityParameterCalculator3;
import com.opengamma.platform.pricer.results.ParameterSensitivityParameterCalculator3LD;
import com.opengamma.platform.pricer.swap.SwapLegPricerFn;
import com.opengamma.platform.pricer.swap.SwapPricerFn;
import com.opengamma.platform.source.id.StandardId;

/**
 * Test {@link DefaultSwapPricerFn}.
 */
@Test
public class DefaultSwapPricerFnTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 1, 22);
  
  private static final DayCount DC = DayCounts.ACT_365F; // DayCounts.ACT_ACT_ISDA;

  private static final IborIndex USD_LIBOR_1M = IborIndices.USD_LIBOR_1M;
  private static final IborIndex USD_LIBOR_3M = IborIndices.USD_LIBOR_3M;
  private static final IborIndex USD_LIBOR_6M = IborIndices.USD_LIBOR_6M;
  private static final com.opengamma.analytics.financial.instrument.index.IborIndex USDLIBOR3M_OGA =
      IndexIborMaster.getInstance().getIndex(IndexIborMaster.USDLIBOR3M);
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
  private static final com.opengamma.util.tuple.Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> 
      MULTICURVE_OIS_PAIR = StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();
  private static final CurveBuildingBlockBundle BLOCK_OIS = MULTICURVE_OIS_PAIR.getSecond();
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY = env(TS_USDLIBOR3M_WITHOUTTODAY);
  private static final ImmutablePricingEnvironment ENV_WITHTODAY = env(TS_USDLIBOR3M_WITHTODAY);
  private static final ImmutableStoredPricingEnvironment ENV_FAST_WITHOUTTODAY = envStored(TS_USDLIBOR3M_WITHOUTTODAY);

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

  private static final PresentValueCurveSensitivityDiscountingCalculator PVCSDC =
      PresentValueCurveSensitivityDiscountingCalculator.getInstance();
  private static final ParameterSensitivityParameterCalculator<ParameterProviderInterface> PSC =
      new ParameterSensitivityParameterCalculator<>(PVCSDC);
  private static final MarketQuoteSensitivityBlockCalculator<ParameterProviderInterface> MQSBC = 
      new MarketQuoteSensitivityBlockCalculator<>(PSC);


  private static final SwapTrade SWAP_TRADE = SwapTrade.builder()
      .standardId(StandardId.of("OG-Trade", "1"))
      .tradeDate(LocalDate.of(2014, 9, 10))
      .swap(Swap.of(FIXED_LEG, IBOR_LEG))
      .build();

  /* Constants */
  private static final double TOLERANCE_PV = 1.0E-2;
  private static final double TOLERANCE_PV_DELTA = 1.0E+0;

  @SuppressWarnings("unused")
  @Test
  public void presentValue() {
    Swap swap = SWAP_TRADE.getSwap();
    double pvExpected = 0.0d;
    for (SwapLeg leg : swap.getLegs()) {
      pvExpected += LEG_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, leg.toExpanded());
    } // Single currency swap, no need for a currency dimension.
    MultiCurrencyAmount pvComputed = SWAP_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, swap);
    assertEquals(pvExpected, pvComputed.getAmount(USD).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: Present Value");
    MultiCurrencyAmount pvComputedFast = SWAP_PRICER.presentValue(ENV_FAST_WITHOUTTODAY, VALUATION_DATE, swap);
    assertEquals(pvExpected, pvComputedFast.getAmount(USD).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: Present Value");
  }

  @Test
  public void presentValueCurveSensitivity() {
    Pair<MultiCurrencyAmount, MultipleCurrencyMulticurveSensitivity> pvcsPair =
        SWAP_PRICER.presentValueCurveSensitivity(ENV_WITHOUTTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    Pair<MultiCurrencyAmount, MulticurveSensitivity3> pvcsPair3 =
        SWAP_PRICER.presentValueCurveSensitivity3(ENV_WITHOUTTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    Pair<MultiCurrencyAmount, MulticurveSensitivity3LD> pvcsPair3LD =
        SWAP_PRICER.presentValueCurveSensitivity3LD(ENV_WITHOUTTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    MulticurveSensitivity3LD pvcs3LD = pvcsPair3LD.getSecond();
    pvcs3LD.cleaned();
    pvcsPair3LD.getSecond().cleaned();
    MultiCurrencyAmount pv = SWAP_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, SWAP_TRADE.getSwap());
    MulticurveSensitivity3 pvcs3 = pvcsPair3.getSecond();
    assertEquals(pv.getAmount(USD).getAmount(), pvcsPair.getFirst().getAmount(USD).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: Present Value curve sensitivity");
    assertEquals(pv.getAmount(USD).getAmount(), pvcsPair3.getFirst().getAmount(USD).getAmount(), TOLERANCE_PV,
        "DefaultSwapPricerFn: Present Value curve sensitivity");
    assertEquals(20, pvcsPair3.getSecond().getForwardRateSensitivities().size(),
        "DefaultSwapPricerFn: Present Value curve sensitivity");
    assertEquals(30, pvcsPair3.getSecond().getZeroRateSensitivities().size(),
        "DefaultSwapPricerFn: Present Value curve sensitivity");
    pvcs3 = pvcs3.cleaned();
    assertEquals(20, pvcs3.getZeroRateSensitivities().size(),
        "DefaultSwapPricerFn: Present Value curve sensitivity");
    MultipleCurrencyParameterSensitivity ps = PSC.pointToParameterSensitivity(pvcsPair.getSecond(), MULTICURVE_OIS);
    MultipleCurrencyParameterSensitivity ps3 =
        ParameterSensitivityParameterCalculator3.pointToParameterSensitivity(pvcs3, MULTICURVE_OIS);
    AssertSensitivityObjects.assertEquals("DefaultSwapPricerFn: Present Value curve sensitivity",
        ps, ps3, TOLERANCE_PV_DELTA);
    MultipleCurrencyParameterSensitivity ps3LD =
        ParameterSensitivityParameterCalculator3LD.pointToParameterSensitivity(pvcs3LD, ENV_WITHOUTTODAY, VALUATION_DATE);
    AssertSensitivityObjects.assertEquals("DefaultSwapPricerFn: Present Value curve sensitivity",
        ps, ps3LD, TOLERANCE_PV_DELTA);
    @SuppressWarnings("unused")
    MultipleCurrencyParameterSensitivity pvMarketQuoteSensi = MQSBC.fromParameterSensitivity(ps3LD, BLOCK_OIS);
    @SuppressWarnings("unused")
    int t = 0;
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
        .dayCount(DC)
        .build();
  }
  
  private static ImmutableStoredPricingEnvironment envStored(LocalDateDoubleTimeSeries ts) {
    long startTime, endTime;
    startTime = System.currentTimeMillis();
    Map<IborIndex, Map<LocalDate, Double>> iborRate = new HashMap<IborIndex, Map<LocalDate,Double>>();
    Map<LocalDate, Double> libor3MForward = new HashMap<LocalDate, Double>();
    for(LocalDate tsDate: ts.dates()) {
      libor3MForward.put(tsDate, ts.get(tsDate).getAsDouble());
    }
    // ValuationDate
    OptionalDouble valuationFixing = ts.get(VALUATION_DATE);
    // 
    int nbDate = 12_500; // nbDate in the map ~ 50Y
    LocalDate fixingDate = valuationFixing.isPresent()?HolidayCalendars.GBLO.next(VALUATION_DATE):VALUATION_DATE;
    for(int loopFixing = 0 ; loopFixing<nbDate; loopFixing++) {
      LocalDate fixingStartDate = USD_LIBOR_3M.calculateEffectiveFromFixing(fixingDate);
      LocalDate fixingEndDate = USD_LIBOR_3M.calculateMaturityFromEffective(fixingStartDate);
      double fixingYearFraction = USD_LIBOR_3M.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
      libor3MForward.put(fixingDate, MULTICURVE_OIS.getSimplyCompoundForwardRate(USDLIBOR3M_OGA,
          DC.yearFraction(VALUATION_DATE, fixingStartDate), 
          DC.yearFraction(VALUATION_DATE, fixingEndDate),
          fixingYearFraction));
      fixingDate = HolidayCalendars.GBLO.next(fixingDate);
    }
    iborRate.put(USD_LIBOR_3M, libor3MForward);
    endTime = System.currentTimeMillis();
    System.out.println("PricingEnvironement: loading 50Y fixing 1 curve:" + (endTime - startTime) + " ms");
    return ImmutableStoredPricingEnvironment.builder()
        .multicurve(MULTICURVE_OIS)
        .timeSeries(ImmutableMap.of(
            USD_LIBOR_1M, SwapInstrumentsDataSet.TS_USDLIBOR1M,
            USD_LIBOR_3M, ts,
            USD_LIBOR_6M, SwapInstrumentsDataSet.TS_USDLIBOR6M,
            USD_FED_FUND, SwapInstrumentsDataSet.TS_USDON))
        .iborRate(iborRate)
        .dayCount(DC)
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
