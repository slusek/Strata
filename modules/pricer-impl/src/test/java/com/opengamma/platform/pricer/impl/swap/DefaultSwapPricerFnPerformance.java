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
import com.opengamma.platform.finance.swap.FixedRateCalculation;
import com.opengamma.platform.finance.swap.IborRateCalculation;
import com.opengamma.platform.finance.swap.NotionalAmount;
import com.opengamma.platform.finance.swap.PaymentSchedule;
import com.opengamma.platform.finance.swap.RateSwapLeg;
import com.opengamma.platform.finance.swap.Swap;
import com.opengamma.platform.finance.swap.SwapLeg;
import com.opengamma.platform.pricer.CalendarUSD;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.SwapInstrumentsDataSet;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.platform.pricer.impl.ImmutableStoredPricingEnvironment;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3LD;
import com.opengamma.platform.pricer.results.ParameterSensitivityParameterCalculator3;
import com.opengamma.platform.pricer.results.ParameterSensitivityParameterCalculator3LD;
import com.opengamma.platform.pricer.swap.SwapPricerFn;

/**
 * Test {@link DefaultSwapPricerFn}.
 */
@Test
public class DefaultSwapPricerFnPerformance {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2014, 1, 22);
  
//  private static final DayCount DC = DayCounts.ACT_ACT_ISDA;
  private static final DayCount DC = DayCounts.ACT_365F;

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
  private static final com.opengamma.util.tuple.Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_OIS_PAIR =
      StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();
  private static final CurveBuildingBlockBundle BLOCK_OIS = MULTICURVE_OIS_PAIR.getSecond();
  private static final ImmutablePricingEnvironment ENV_WITHOUTTODAY = env(TS_USDLIBOR3M_WITHOUTTODAY);
  private static final ImmutableStoredPricingEnvironment ENV_FAST_WITHOUTTODAY = envStored(TS_USDLIBOR3M_WITHOUTTODAY);

  private static final SwapPricerFn SWAP_PRICER = DefaultSwapPricerFn.DEFAULT;

  /* Instrument */
  private static final BusinessDayAdjustment BDA_MF = BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CalendarUSD.NYC);
  private static final BusinessDayAdjustment BDA_P = BusinessDayAdjustment.of(PRECEDING, CalendarUSD.NYC);
  private static final NotionalAmount NOTIONAL = NotionalAmount.of(USD, 100_000_000);

  private static final LocalDate START_DATE_1 = LocalDate.of(2014, 9, 12);
  private static final LocalDate END_DATE_1 = LocalDate.of(2019, 9, 12);
  private static final double FIXED_RATE = 0.015;

  private static final PresentValueCurveSensitivityDiscountingCalculator PVCSDC =
      PresentValueCurveSensitivityDiscountingCalculator.getInstance();
  private static final ParameterSensitivityParameterCalculator<ParameterProviderInterface> PSC =
      new ParameterSensitivityParameterCalculator<>(PVCSDC);
  private static final MarketQuoteSensitivityBlockCalculator<ParameterProviderInterface> MQSBC = 
      new MarketQuoteSensitivityBlockCalculator<>(PSC);

  /* Constants */
  private static final double BP1 = 1.0E-4;

  @Test(enabled = true)
  public void performance() {

    long startTime, endTime;
    int nbTests = 100;
    int nbSwaps = 100;
    int nbReps = 10;

    // Performance note: OG-Analytics: construction and pv 430 ms
    // Performance note: OG-Analytics: pv 90 ms
    // Performance note: OG-Analytics: pv+pvcs 330 ms
    // Performance note: OG-Analytics: pv+ps 455 ms
    // Performance note: OG-Analytics: cstr+pv+ps 800 ms
    // Performance note: OG-Analytics: 2.x pcsv->ps: 125  / 3.0 pcsv->ps: 260 

    Swap[] swaps = new Swap[nbSwaps];
    for (int loops = 0; loops < nbSwaps; loops++) {
      double rate = FIXED_RATE - 0.0050 + loops * BP1;
      SwapLeg fixedLeg = fixedLeg(
          START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, rate, null).toExpanded();
      SwapLeg iborLeg = iborLeg(
          START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null).toExpanded();
      swaps[loops] = Swap.of(fixedLeg, iborLeg);
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          MultiCurrencyAmount pv = SWAP_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, swaps[loops]);
          pvTotal += pv.getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - pv (already expanded) " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: pv (already expanded): On Mac Book Pro 2.6 GHz Intel i7: 350 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
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
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - construction+pv " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: construction + pv: On Mac Book Pro 2.6 GHz Intel i7: 675 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          double rate = FIXED_RATE - 0.0050 + loops * BP1;
          SwapLeg fixedLeg = fixedLeg(
              START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, rate, null);
          SwapLeg iborLeg = iborLeg(
              START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null);
          Swap swap = Swap.of(fixedLeg, iborLeg);
          @SuppressWarnings("unused")
          MultiCurrencyAmount pv = SWAP_PRICER.presentValue(ENV_FAST_WITHOUTTODAY, VALUATION_DATE, swap);
          pvTotal += pv.getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - construction+pv(stored) " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: construction + pv: On Mac Book Pro 2.6 GHz Intel i7: 400 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
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
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - construction+pvcs " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: construction + pvcs: On Mac Book Pro 2.6 GHz Intel i7: 920 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          double rate = FIXED_RATE - 0.0050 + loops * BP1;
          SwapLeg fixedLeg = fixedLeg(
              START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, rate, null);
          SwapLeg iborLeg = iborLeg(
              START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null);
          Swap swap = Swap.of(fixedLeg, iborLeg);
          @SuppressWarnings("unused")
          Pair<MultiCurrencyAmount, MulticurveSensitivity3> pvcs =
              SWAP_PRICER.presentValueCurveSensitivity3(ENV_WITHOUTTODAY, VALUATION_DATE, swap);
          pvTotal += pvcs.getFirst().getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - construction+pvcs(3.0) " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: construction + pvcs3: On Mac Book Pro 2.6 GHz Intel i7: 750 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          double rate = FIXED_RATE - 0.0050 + loops * BP1;
          SwapLeg fixedLeg = fixedLeg(
              START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, rate, null);
          SwapLeg iborLeg = iborLeg(
              START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null);
          Swap swap = Swap.of(fixedLeg, iborLeg);
          @SuppressWarnings("unused")
          Pair<MultiCurrencyAmount, MulticurveSensitivity3LD> pvcs =
              SWAP_PRICER.presentValueCurveSensitivity3LD(ENV_FAST_WITHOUTTODAY, VALUATION_DATE, swap);
          pvTotal += pvcs.getFirst().getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - construction+pvcs(3.0LD) " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: construction + pvcs3LD: On Mac Book Pro 2.6 GHz Intel i7: 465 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          double rate = FIXED_RATE - 0.0050 + loops * BP1;
          SwapLeg fixedLeg = fixedLeg(
              START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, rate, null);
          SwapLeg iborLeg = iborLeg(
              START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null);
          Swap swap = Swap.of(fixedLeg, iborLeg);
          @SuppressWarnings("unused")
          Pair<MultiCurrencyAmount, MulticurveSensitivity3> pvcs =
              SWAP_PRICER.presentValueCurveSensitivity3(ENV_WITHOUTTODAY, VALUATION_DATE, swap);
          @SuppressWarnings("unused")
          MultipleCurrencyParameterSensitivity ps3 =
              ParameterSensitivityParameterCalculator3.pointToParameterSensitivity(pvcs.getSecond(), MULTICURVE_OIS);
          pvTotal += pvcs.getFirst().getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - construction+pvcs(3.0)+ps " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: construction + pvcs3 + ps: On Mac Book Pro 2.6 GHz Intel i7: 1010 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          double rate = FIXED_RATE - 0.0050 + loops * BP1;
          SwapLeg fixedLeg = fixedLeg(
              START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, rate, null);
          SwapLeg iborLeg = iborLeg(
              START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null);
          Swap swap = Swap.of(fixedLeg, iborLeg);
          @SuppressWarnings("unused")
          Pair<MultiCurrencyAmount, MulticurveSensitivity3LD> pvcs =
              SWAP_PRICER.presentValueCurveSensitivity3LD(ENV_FAST_WITHOUTTODAY, VALUATION_DATE, swap);
          @SuppressWarnings("unused")
          MultipleCurrencyParameterSensitivity ps3 =
              ParameterSensitivityParameterCalculator3LD.pointToParameterSensitivity(pvcs.getSecond(), ENV_FAST_WITHOUTTODAY, VALUATION_DATE);
          @SuppressWarnings("unused")
          MultipleCurrencyParameterSensitivity pvMarketQuoteSensi = MQSBC.fromParameterSensitivity(ps3, BLOCK_OIS);
          pvTotal += pvcs.getFirst().getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - construction+pvcs(3.0LD)+ps+mqs " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: construction + pvcs3LD + ps: On Mac Book Pro 2.6 GHz Intel i7: 995 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          MultiCurrencyAmount pv = SWAP_PRICER.presentValue(ENV_FAST_WITHOUTTODAY, VALUATION_DATE, swaps[loops]);
          pvTotal += pv.getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - pv(already expanded+stored) " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: pv(already expanded+stored): On Mac Book Pro 2.6 GHz Intel i7: 105 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          Pair<MultiCurrencyAmount, MulticurveSensitivity3LD> pvcs =
              SWAP_PRICER.presentValueCurveSensitivity3LD(ENV_FAST_WITHOUTTODAY, VALUATION_DATE, swaps[loops]);
          pvTotal += pvcs.getFirst().getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - pv+pvcs3.0LD(already expanded+stored) " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: pv+pvcs3.0LD(already expanded+stored): On Mac Book Pro 2.6 GHz Intel i7: 165 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          @SuppressWarnings("unused")
          Pair<MultiCurrencyAmount, MulticurveSensitivity3> pvcs =
              SWAP_PRICER.presentValueCurveSensitivity3(ENV_WITHOUTTODAY, VALUATION_DATE, swaps[loops]);
          @SuppressWarnings("unused")
          MultipleCurrencyParameterSensitivity ps3 =
              ParameterSensitivityParameterCalculator3.pointToParameterSensitivity(pvcs.getSecond(), MULTICURVE_OIS);
          pvTotal += pvcs.getFirst().getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - pv(already expanded)+pvcs(3.0)+ps " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: pv(already expanded)+pvcs(3.0)+ps: On Mac Book Pro 2.6 GHz Intel i7: 645 ms for 100x100 swaps.
    }

  }

  @Test(enabled = true)
  public void performanceArray() {

    long startTime, endTime;
    int nbTests = 100;
    int nbSwaps = 100;
    int nbReps = 10;
    
    PricingEnvironment[] pe = new PricingEnvironment[nbTests];
    for (int looptest = 0; looptest < nbTests; looptest++) {
      pe[looptest] = ENV_WITHOUTTODAY;
    }

    Swap[] swaps = new Swap[nbSwaps];
    for (int loops = 0; loops < nbSwaps; loops++) {
      double rate = FIXED_RATE - 0.0050 + loops * BP1;
      SwapLeg fixedLeg = fixedLeg(
          START_DATE_1, END_DATE_1, P6M, PAY, NOTIONAL, rate, null).toExpanded();
      SwapLeg iborLeg = iborLeg(
          START_DATE_1, END_DATE_1, P3M, RECEIVE, NOTIONAL, USD_LIBOR_3M, null).toExpanded();
      swaps[loops] = Swap.of(fixedLeg, iborLeg);
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int looptest = 0; looptest < nbTests; looptest++) {
        pvTotal = 0.0;
        for (int loops = 0; loops < nbSwaps; loops++) {
          MultiCurrencyAmount pv = SWAP_PRICER.presentValue(ENV_WITHOUTTODAY, VALUATION_DATE, swaps[loops]);
          pvTotal += pv.getAmount(USD).getAmount();
        }
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - pv (already expanded) " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: pv(expanded): On Mac Book Pro 2.6 GHz Intel i7: 350 ms for 100x100 swaps.
    }

    for (int looprep = 0; looprep < nbReps; looprep++) {
      double pvTotal = 0.0;
      startTime = System.currentTimeMillis();
      for (int loops = 0; loops < nbSwaps; loops++) {
        MultiCurrencyAmount[] pv = SWAP_PRICER.presentValue(pe, VALUATION_DATE, swaps[loops]);
        pvTotal += pv[0].getAmount(USD).getAmount();
      }
      endTime = System.currentTimeMillis();
      System.out.println("DefaultSwapPricerFn: " + nbTests + " x " + nbSwaps + " swaps (5Y/Q) - pv (already expanded + array PE) " +
          (endTime - startTime) + " ms - " + pvTotal);
      // Performance note: pv (expanded + array PE): On Mac Book Pro 2.6 GHz Intel i7: 95 ms for 100x100 swaps.
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
        .dayCount(DC)
        .build();
  }
  
  private static ImmutableStoredPricingEnvironment envStored(LocalDateDoubleTimeSeries ts) {
    long startTime, endTime;
    startTime = System.currentTimeMillis();
    Map<IborIndex, Map<LocalDate, Double>> iborRate = new HashMap<IborIndex, Map<LocalDate,Double>>();
    Map<LocalDate, Double> libor3MForward = new HashMap<LocalDate, Double>();
    LocalDate[] tsDates = ts.dates().toArray(LocalDate[]::new);
    for (LocalDate tsDate : tsDates) {
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
