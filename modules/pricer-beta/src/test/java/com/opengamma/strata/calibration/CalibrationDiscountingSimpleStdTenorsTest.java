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

import static com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M;
import static com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static com.opengamma.strata.finance.rate.swap.type.FixedOvernightSwapConventions.EUR_FIXED_1Y_EONIA_OIS;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static org.testng.Assert.assertEquals;

import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.math.interpolation.FlatExtrapolator1D;
import com.opengamma.analytics.math.interpolation.LinearInterpolator1D;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.interpolator.CurveExtrapolator;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.basics.market.ObservableKey;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.finance.rate.swap.SwapTrade;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapTemplate;
import com.opengamma.strata.finance.rate.swap.type.FixedOvernightSwapTemplate;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveParameterMetadata;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.TenorCurveNodeMetadata;
import com.opengamma.strata.market.curve.config.CurveNode;
import com.opengamma.strata.market.curve.config.FixedIborSwapCurveNode;
import com.opengamma.strata.market.curve.config.FixedOvernightSwapCurveNode;
import com.opengamma.strata.market.key.QuoteKey;
import com.opengamma.strata.market.value.ValueType;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.swap.DiscountingSwapProductPricer;

public class CalibrationDiscountingSimpleStdTenorsTest {
  
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 7, 24);
  
  private static final CurveInterpolator INTERPOLATOR_LINEAR = new LinearInterpolator1D();
  private static final CurveExtrapolator EXTRAPOLATOR_FLAT = new FlatExtrapolator1D();
  private static final DayCount CURVE_DC = ACT_365F;
  private static final LocalDateDoubleTimeSeries TS_EMTPY = LocalDateDoubleTimeSeries.empty();

  private static final String SCHEME = "CALIBRATION";
  
  /** Curve names */
  private static final String DSCON_NAME = "EUR_EONIA_EOD";
  private static final CurveName DSCON_CURVE_NAME = CurveName.of(DSCON_NAME);
  private static final String FWD3_NAME = "EUR_EURIBOR_3M";
  private static final CurveName FWD3_CURVE_NAME = CurveName.of(FWD3_NAME);
  private static final String FWD6_NAME = "EUR_EURIBOR_6M";
  private static final CurveName FWD6_CURVE_NAME = CurveName.of(FWD6_NAME);
  /** Curves associations to currencies and indices. */
  private static final Map<CurveName, Currency> DSC_NAMES = new HashMap<>();
  private static final Map<CurveName, Index[]> IDX_NAMES = new HashMap<>();
  private static final Map<Index, LocalDateDoubleTimeSeries> TS = new HashMap<>();
  static {
    DSC_NAMES.put(DSCON_CURVE_NAME, EUR);
    IDX_NAMES.put(DSCON_CURVE_NAME, new Index[] {EUR_EONIA});
    IDX_NAMES.put(FWD3_CURVE_NAME, new Index[] {EUR_EURIBOR_3M});
    IDX_NAMES.put(FWD6_CURVE_NAME, new Index[] {EUR_EURIBOR_6M});
    TS.put(EUR_EURIBOR_3M, TS_EMTPY);
    TS.put(EUR_EURIBOR_6M, TS_EMTPY);
    TS.put(EUR_EONIA, TS_EMTPY);
  }

  /** Data for EUR-DSCON curve */
  /* Market values */
  private static final double[] DSC_MARKET_QUOTES = new double[] {
    -0.0010787505441382185, 0.0016443214916477351, 0.00791319942756944, 0.014309183236345927 };
  private static final int DSC_NB_NODES = DSC_MARKET_QUOTES.length;
  private static final String[] DSC_ID_VALUE = new String[] {
    "OIS2Y", "OIS5Y", "OIS10Y", "OIS30Y"};
  /* Nodes */
  private static final CurveNode[] DSC_NODES = new CurveNode[DSC_NB_NODES];
  /* Tenors */
  private static final Period[] DSC_OIS_TENORS = new Period[] {
    Period.ofYears(2), Period.ofYears(5), Period.ofYears(10), Period.ofYears(30) };
  private static final int DSC_NB_OIS_NODES = DSC_OIS_TENORS.length;
  static {
    for (int i = 0; i < DSC_NB_OIS_NODES; i++) {
      DSC_NODES[i] = FixedOvernightSwapCurveNode.of(
          FixedOvernightSwapTemplate.of(Period.ZERO, Tenor.of(DSC_OIS_TENORS[i]), EUR_FIXED_1Y_EONIA_OIS),
          QuoteKey.of(StandardId.of(SCHEME, DSC_ID_VALUE[i])));
    }
  }
  
  /** Data for EUR-LIBOR3M curve */
  /* Market values */
  private static final double[] FWD3_MARKET_QUOTES = new double[] {
    0.00013533281680009178, 0.0031298573232152152, 0.009328861288116275, 0.015219571759282416 };
  private static final int FWD3_NB_NODES = FWD3_MARKET_QUOTES.length;
  private static final String[] FWD3_ID_VALUE = new String[] {
    "IRS3M_2Y", "IRS3M_5Y", "IRS3M_10Y", "IRS3M_30Y" };
  /* Nodes */
  private static final CurveNode[] FWD3_NODES = new CurveNode[FWD3_NB_NODES];
  /* Tenors */
  private static final Period[] FWD3_IRS_TENORS = new Period[] {
    Period.ofYears(2), Period.ofYears(5), Period.ofYears(10), Period.ofYears(30) };
  private static final int FWD3_NB_IRS_NODES = FWD3_IRS_TENORS.length;
  static {
    for (int i = 0; i < FWD3_NB_IRS_NODES; i++) {
      FWD3_NODES[i ] = FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Period.ZERO, Tenor.of(FWD3_IRS_TENORS[i]), EUR_FIXED_1Y_EURIBOR_3M),
          QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[i])));
    }
  }
  
  /** Data for EUR-EURIBOR6M curve */
  /* Market values */
  private static final double[] FWD6_MARKET_QUOTES = new double[] {
    0.00013533281680009178, 0.0031298573232152152, 0.009328861288116275, 0.015219571759282416 };
  private static final int FWD6_NB_NODES = FWD3_MARKET_QUOTES.length;
  private static final String[] FWD6_ID_VALUE = new String[] {
    "IRS6M_2Y", "IRS6M_5Y", "IRS6M_10Y", "IRS6M_30Y" };
  /* Nodes */
  private static final CurveNode[] FWD6_NODES = new CurveNode[FWD3_NB_NODES];
  /* Tenors */
  private static final Period[] FWD6_IRS_TENORS = new Period[] {
    Period.ofYears(2), Period.ofYears(5), Period.ofYears(10), Period.ofYears(30) };
  private static final int FWD6_NB_IRS_NODES = FWD6_IRS_TENORS.length;
  static {
    for (int i = 0; i < FWD6_NB_IRS_NODES; i++) {
      FWD6_NODES[i ] = FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Period.ZERO, Tenor.of(FWD6_IRS_TENORS[i]), EUR_FIXED_1Y_EURIBOR_6M),
          QuoteKey.of(StandardId.of(SCHEME, FWD6_ID_VALUE[i])));
    }
  }

  /** All quotes for the curve calibration */
  private static final Map<ObservableKey, Double> ALL_QUOTES = new HashMap<>();
  static {
    for (int i = 0; i < DSC_NB_NODES; i++) {
      ALL_QUOTES.put(QuoteKey.of(StandardId.of(SCHEME, DSC_ID_VALUE[i])), DSC_MARKET_QUOTES[i]);
    }
    for (int i = 0; i < FWD3_NB_NODES; i++) {
      ALL_QUOTES.put(QuoteKey.of(StandardId.of(SCHEME, FWD3_ID_VALUE[i])), FWD3_MARKET_QUOTES[i]);
    }
    for (int i = 0; i < FWD6_NB_NODES; i++) {
      ALL_QUOTES.put(QuoteKey.of(StandardId.of(SCHEME, FWD6_ID_VALUE[i])), FWD6_MARKET_QUOTES[i]);
    }
  }
  
  /** All nodes by groups. */
  private static final List<List<CurveNode[]>> CURVES_NODES = new ArrayList<>();
  static {
    List<CurveNode[]> groupDsc = new ArrayList<>();
    groupDsc.add(DSC_NODES);
    CURVES_NODES.add(groupDsc);
    List<CurveNode[]> groupFwd3 = new ArrayList<>();
    groupFwd3.add(FWD3_NODES);
    CURVES_NODES.add(groupFwd3);
    List<CurveNode[]> groupFwd6 = new ArrayList<>();
    groupFwd6.add(FWD6_NODES);
    CURVES_NODES.add(groupFwd6);
  }
  
  /** All metadata by groups */
  private static final List<List<CurveMetadata>> CURVES_METADATA = new ArrayList<>();
  static {
    List<CurveMetadata> groupDsc = new ArrayList<>();
    groupDsc.add(DefaultCurveMetadata.builder().curveName(DSCON_CURVE_NAME).xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE).dayCount(CURVE_DC).build());
    CURVES_METADATA.add(groupDsc);
    List<CurveMetadata> groupFwd3 = new ArrayList<>();
    groupFwd3.add(DefaultCurveMetadata.builder().curveName(FWD3_CURVE_NAME).xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE).dayCount(CURVE_DC).build());
    CURVES_METADATA.add(groupFwd3);
    List<CurveMetadata> groupFwd6 = new ArrayList<>();
    groupFwd6.add(DefaultCurveMetadata.builder().curveName(FWD6_CURVE_NAME).xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE).dayCount(CURVE_DC).build());
    CURVES_METADATA.add(groupFwd6);
  }
  
  private static final DiscountingSwapProductPricer SWAP_PRICER =
      DiscountingSwapProductPricer.DEFAULT;
  
  // Constants
  private static final double TOLERANCE_PV = 1.0E-6;
  
  @SuppressWarnings("unused")
  @Test
  public void calibration_present_value() {
    
    Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> result = 
        calibration(VALUATION_DATE, CURVES_METADATA, CURVES_NODES, ALL_QUOTES, DSC_NAMES, IDX_NAMES, TS);
    // Test PV Dsc
    CurveNode[] dscNodes = CURVES_NODES.get(0).get(0);
    List<Trade> dscTrades = new ArrayList<>();
    for (int i = 0; i < dscNodes.length; i++) {
      dscTrades.add(dscNodes[i].trade(VALUATION_DATE, ALL_QUOTES));
    }
    // OIS
    for (int i = 0; i < DSC_NB_OIS_NODES; i++) {
      MultiCurrencyAmount pvIrs = SWAP_PRICER
          .presentValue(((SwapTrade) dscTrades.get(i)).getProduct(), result.getFirst());
      assertEquals(pvIrs.getAmount(EUR).getAmount(), 0.0, TOLERANCE_PV);
    }
    // Test PV Fwd3
    CurveNode[] fwd3Nodes = CURVES_NODES.get(1).get(0);
    List<Trade> fwd3Trades = new ArrayList<>();
    for (int i = 0; i < fwd3Nodes.length; i++) {
      fwd3Trades.add(fwd3Nodes[i].trade(VALUATION_DATE, ALL_QUOTES));
    }
    // IRS
    for (int i = 0; i < FWD3_NB_IRS_NODES; i++) {
      MultiCurrencyAmount pvIrs = SWAP_PRICER
          .presentValue(((SwapTrade) fwd3Trades.get(i)).getProduct(), result.getFirst());
      assertEquals(pvIrs.getAmount(EUR).getAmount(), 0.0, TOLERANCE_PV);
    }
    // Test PV Fwd6
    CurveNode[] fwd6Nodes = CURVES_NODES.get(2).get(0);
    List<Trade> fwd6Trades = new ArrayList<>();
    for (int i = 0; i < fwd6Nodes.length; i++) {
      fwd6Trades.add(fwd6Nodes[i].trade(VALUATION_DATE, ALL_QUOTES));
    }
    // IRS
    for (int i = 0; i < FWD6_NB_IRS_NODES; i++) {
      MultiCurrencyAmount pvIrs = SWAP_PRICER
          .presentValue(((SwapTrade) fwd6Trades.get(i)).getProduct(), result.getFirst());
      assertEquals(pvIrs.getAmount(EUR).getAmount(), 0.0, TOLERANCE_PV);
    }
  }

  private Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> calibration(
      LocalDate valuationDate,
      List<List<CurveMetadata>> curvesMetadata,
      List<List<CurveNode[]>> curvesNodes, 
      Map<ObservableKey, Double> quotes,
      Map<CurveName, Currency> discountingNames,
      Map<CurveName, Index[]> indexNames,
      Map<Index, LocalDateDoubleTimeSeries> timeSeries) {
    List<List<CalibrationCurveData>> dataTotal = new ArrayList<>();
    for (int loopgroup = 0; loopgroup < curvesMetadata.size(); loopgroup++) {
      List<CurveMetadata> groupMetadata = curvesMetadata.get(loopgroup);
      List<CalibrationCurveData> dataGroup = new ArrayList<>();
      for (int loopcurve = 0; loopcurve < groupMetadata.size(); loopcurve++) {
        CurveMetadata curveMetadata = groupMetadata.get(loopcurve);
        CurveNode[] curveNodes = curvesNodes.get(loopgroup).get(loopcurve);
        int nbNodes = curveNodes.length;
        double[] curveTimes = new double[nbNodes];
        CurveParameterMetadata[] curveParamMetadata = new CurveParameterMetadata[nbNodes];
        List<Trade> curveTrades = new ArrayList<>();
        List<Double> curveGuesses = new ArrayList<>();
        for (int i = 0; i < nbNodes; i++) {
          curveParamMetadata[i] = curveNodes[i].metadata(valuationDate);
          curveTimes[i] = curveMetadata.getDayCount().get()
              .yearFraction(valuationDate, ((TenorCurveNodeMetadata) curveParamMetadata[i]).getDate());
          curveTrades.add(curveNodes[i].trade(valuationDate, quotes));
          curveGuesses.add(0.0d);
        }
        InterpolatedCurveTemplate template = new InterpolatedCurveTemplate(
            curveMetadata, curveTimes, EXTRAPOLATOR_FLAT, INTERPOLATOR_LINEAR, EXTRAPOLATOR_FLAT);
        CalibrationCurveData data = new CalibrationCurveData(template, curveTrades, curveGuesses);
        dataGroup.add(data);
      }
      dataTotal.add(dataGroup);
    }
    ImmutableRatesProvider knownData = ImmutableRatesProvider.builder()
        .valuationDate(valuationDate)
        .fxMatrix(FxMatrix.empty()).timeSeries(timeSeries).build();
    CalibrationCalculator calculator = DefaultCalibrationCalculator.DEFAULT;
    CalibrationFunction function = new CalibrationFunction(1.0E-9, 1.0E-9, 100, calculator);
    Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> result =
        function.calibrate(dataTotal, knownData, discountingNames, indexNames);
    return result;
  }
  
}
