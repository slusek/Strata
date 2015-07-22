/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import static com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;

import com.opengamma.analytics.financial.instrument.index.GeneratorAttribute;
import com.opengamma.analytics.financial.instrument.index.GeneratorAttributeIR;
import com.opengamma.analytics.financial.instrument.index.GeneratorInstrument;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.math.interpolation.FlatExtrapolator1D;
import com.opengamma.analytics.math.interpolation.LinearInterpolator1D;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.interpolator.CurveExtrapolator;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.basics.market.ObservableId;
import com.opengamma.strata.basics.market.ObservableKey;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.finance.rate.deposit.IborFixingDepositConvention;
import com.opengamma.strata.finance.rate.deposit.IborFixingDepositTemplate;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapTemplate;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveParameterMetadata;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.TenorCurveNodeMetadata;
import com.opengamma.strata.market.curve.config.CurveNode;
import com.opengamma.strata.market.curve.config.FixedIborSwapCurveNode;
import com.opengamma.strata.market.curve.config.IborFixingDepositCurveNode;
import com.opengamma.strata.market.key.QuoteKey;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

public class CalibrationDiscountingSimpleTest {
  
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 7, 21);
  
  private static final CurveInterpolator INTERPOLATOR_LINEAR = new LinearInterpolator1D();
  private static final CurveExtrapolator EXTRAPOLATOR_FLAT = new FlatExtrapolator1D();
  private static final DayCount CURVE_DC = ACT_365F;
  private static final LocalDateDoubleTimeSeries TS_EMTPY = LocalDateDoubleTimeSeries.empty();

  private static final String SCHEME = "CALIBRATION";
  
  /** Curve name */
  private static final String FWD3_NAME = "USD-ALL-LIBOR3M";
  private static final CurveName FWD3_CURVE_NAME = CurveName.of(FWD3_NAME);
  private static final Map<CurveName, Currency> DSC_NAMES = new HashMap<>();
  private static final Map<CurveName, Index[]> IDX_NAMES = new HashMap<>();
  private static final Map<Index, LocalDateDoubleTimeSeries> TS = new HashMap<>();
  static {
    DSC_NAMES.put(FWD3_CURVE_NAME, USD);
    IDX_NAMES.put(FWD3_CURVE_NAME, new Index[] {USD_LIBOR_3M});
    TS.put(USD_LIBOR_3M, TS_EMTPY);
  }

  /** Market values for the Fwd 3M USD curve */
  private static final double[] FWD3_MARKET_QUOTES = new double[] {
    0.0420, 0.0420, 0.0430, 0.0470, 0.0540,
    0.0570, 0.0600 };
  private static final int FWD3_NB_NODES = FWD3_MARKET_QUOTES.length;
  private static final String[] FWD3_ID_VALUE = new String[] {
    "Fixing", "Swap6M", "Swap1Y", "Swap2Y", "Swap3Y", "Swap5Y",
    "Swap7Y", "Swap10Y" };
  private static final Map<ObservableKey, Double> FWD3_QUOTES = new HashMap<>();
  static {
    for (int i = 0; i < FWD3_NB_NODES; i++) {
      FWD3_QUOTES.put(QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[i])), FWD3_MARKET_QUOTES[i]);
    }
  }
  /** Nodes for the Fwd 3M USD curve */
  private static final CurveNode[] FWD3_NODES = new CurveNode[FWD3_NB_NODES];
  private static final CurveParameterMetadata[] FWD3_METADATA = new CurveParameterMetadata[FWD3_NB_NODES];
  private static final double[] FWD3_TIMES = new double[FWD3_NB_NODES];
  private static final List<Trade> FWD3_TRADES = new ArrayList<>();
  private static final List<Double> FWD3_GUESS = new ArrayList<>();
  /** Tenors for the Fwd 3M USD swaps */
  private static final Period[] FWD3_SWAP_TENORS = new Period[] {
    Period.ofMonths(6), Period.ofYears(1), Period.ofYears(2), Period.ofYears(3), Period.ofYears(5),
    Period.ofYears(7), Period.ofYears(10) };
  static {
    FWD3_NODES[0] = IborFixingDepositCurveNode.of(IborFixingDepositTemplate.of(USD_LIBOR_3M),
        QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[0])));
    for (int i = 1; i < FWD3_NB_NODES; i++) {
      FWD3_NODES[i] = FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Period.ZERO, Tenor.of(FWD3_SWAP_TENORS[i - 1]), USD_FIXED_6M_LIBOR_3M),
          QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[i])));
    }
    for (int i = 0; i < FWD3_NB_NODES; i++) {
      FWD3_METADATA[i] = FWD3_NODES[i].metadata(VALUATION_DATE);
      FWD3_TIMES[i] = CURVE_DC.yearFraction(VALUATION_DATE, ((TenorCurveNodeMetadata) FWD3_METADATA[i]).getDate());
      FWD3_TRADES.add(FWD3_NODES[i].trade(VALUATION_DATE, FWD3_QUOTES));
      FWD3_GUESS.add(0.0d);
    }
  }
  private static final InterpolatedCurveTemplate FWD3_TEMPLATE = new InterpolatedCurveTemplate(
      DefaultCurveMetadata.of(FWD3_NAME), FWD3_TIMES, EXTRAPOLATOR_FLAT, INTERPOLATOR_LINEAR, EXTRAPOLATOR_FLAT);
  private static final CalibrationCurveData FWD3_DATA = new CalibrationCurveData(FWD3_TEMPLATE, FWD3_TRADES, FWD3_GUESS);
  
  @Test
  public void calibration() {
    List<List<CalibrationCurveData>> dataTotal = new ArrayList<>();
    List<CalibrationCurveData> dataGroup = new ArrayList<>();
    dataGroup.add(FWD3_DATA);
    dataTotal.add(dataGroup);
    ImmutableRatesProvider knownData = ImmutableRatesProvider.builder()
        .fxMatrix(FxMatrix.empty()).timeSeries(TS).build();
    // TODO: CalibrationCalculator
    Pair<ImmutableRatesProvider, CurveBuildingBlockBundle>
  }
  
}
