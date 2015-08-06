/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M;
import static com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static com.opengamma.strata.finance.rate.swap.type.FixedOvernightSwapConventions.EUR_FIXED_1Y_EONIA_OIS;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.market.ObservableKey;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.rate.deposit.IborFixingDepositTemplate;
import com.opengamma.strata.finance.rate.fra.FraTemplate;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapTemplate;
import com.opengamma.strata.finance.rate.swap.type.FixedOvernightSwapTemplate;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.config.CurveNode;
import com.opengamma.strata.market.curve.config.FixedIborSwapCurveNode;
import com.opengamma.strata.market.curve.config.FixedOvernightSwapCurveNode;
import com.opengamma.strata.market.curve.config.FraCurveNode;
import com.opengamma.strata.market.curve.config.IborFixingDepositCurveNode;
import com.opengamma.strata.market.key.QuoteKey;
import com.opengamma.strata.market.value.ValueType;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

public class CalibrationEurStandard {
  

  private static final DayCount CURVE_DC = ACT_365F;
  private static final LocalDateDoubleTimeSeries TS_EMTPY = LocalDateDoubleTimeSeries.empty();

  private static final String SCHEME = "CALIBRATION";
  
  /** Curve names */
  private static final String DSCON_NAME = "EUR_EONIA_EOD";
  public static final CurveName DSCON_CURVE_NAME = CurveName.of(DSCON_NAME);
  private static final String FWD3_NAME = "EUR_EURIBOR_3M";
  public static final CurveName FWD3_CURVE_NAME = CurveName.of(FWD3_NAME);
  private static final String FWD6_NAME = "EUR_EURIBOR_6M";
  public static final CurveName FWD6_CURVE_NAME = CurveName.of(FWD6_NAME);
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

  public static Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> calibrateEurStandard(
      LocalDate valuationDate,
      double[] dscOisQuotes,
      Period[] dscOisTenors, 
      double fwd3FixingQuote,
      double[] fwd3FraQuotes,
      double[] fwd3IrsQuotes,
      Period[] fwd3FraTenors,
      Period[] fwd3IrsTenors,
      double fwd6FixingQuote,
      double[] fwd6FraQuotes,
      double[] fwd6IrsQuotes,
      Period[] fwd6FraTenors,
      Period[] fwd6IrsTenors) {
    /* Curve Discounting/EUR-EONIA */
    String[] dscIdValues = dscIdValues(dscOisTenors);
    /* Curve EUR-EURIBOR-3M */
    double[] fwd3MarketQuotes = fwdMarketQuotes(fwd3FixingQuote, fwd3FraQuotes, fwd3IrsQuotes);
    String[] fwd3IdValue = fwdIdValue(3, fwd3FixingQuote, fwd3FraQuotes, fwd3IrsQuotes, fwd3FraTenors, fwd3IrsTenors);
    /* Curve EUR-EURIBOR-6M */
    double[] fwd6MarketQuotes = fwdMarketQuotes(fwd6FixingQuote, fwd6FraQuotes, fwd6IrsQuotes);
    String[] fwd6IdValue = fwdIdValue(6, fwd6FixingQuote, fwd6FraQuotes, fwd6IrsQuotes, fwd6FraTenors, fwd6IrsTenors);
    /* All quotes for the curve calibration */
    Map<ObservableKey, Double> allQuotes = 
        allQuotes(dscOisQuotes, dscIdValues, fwd3MarketQuotes, fwd3IdValue, fwd6MarketQuotes, fwd6IdValue);
    /* All nodes by groups. */
    List<List<CurveNode[]>> curvesNodes = curvesNodes(
        dscOisTenors, dscIdValues, fwd3FraTenors, fwd3IrsTenors, fwd3IdValue, fwd6FraTenors, fwd6IrsTenors, fwd6IdValue);
    /* All metadata by groups */
    List<List<CurveMetadata>> curvesMetadata = new ArrayList<>();
    List<CurveMetadata> groupDscMeta = new ArrayList<>();
    groupDscMeta.add(DefaultCurveMetadata.builder().curveName(DSCON_CURVE_NAME).xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE).dayCount(CURVE_DC).build());
    curvesMetadata.add(groupDscMeta);
    List<CurveMetadata> groupFwd3Meta = new ArrayList<>();
    groupFwd3Meta.add(DefaultCurveMetadata.builder().curveName(FWD3_CURVE_NAME).xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE).dayCount(CURVE_DC).build());
    curvesMetadata.add(groupFwd3Meta);
    List<CurveMetadata> groupFwd6Meta = new ArrayList<>();
    groupFwd6Meta.add(DefaultCurveMetadata.builder().curveName(FWD6_CURVE_NAME).xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE).dayCount(CURVE_DC).build());
    curvesMetadata.add(groupFwd6Meta);
    /* Results */
    return CalibrationUtils.calibration(valuationDate, curvesMetadata, curvesNodes, allQuotes, DSC_NAMES, IDX_NAMES, TS);
  }

  public static String[] dscIdValues(Period[] dscOisTenors) {
    String[] dscIdValues = new String[dscOisTenors.length];
    for (int i = 0; i < dscOisTenors.length; i++) {
      dscIdValues[i] = "OIS" + dscOisTenors[i].toString();
    }
    return dscIdValues;
  }
  
  public static String[] fwdIdValue(
      int tenor,
      double fwdFixingQuote,
      double[] fwdFraQuotes,
      double[] fwdIrsQuotes,
      Period[] fwdFraTenors,
      Period[] fwdIrsTenors) {
    String[] fwdIdValue = new String[1 + fwdFraQuotes.length + fwdIrsQuotes.length];
    fwdIdValue[0] = "FIXING" + tenor + "M";
    for (int i = 0; i < fwdFraQuotes.length; i++) {
      fwdIdValue[i + 1] = "FRA" + fwdFraTenors[i].toString() + "x" + fwdFraTenors[i].plusMonths(tenor).toString();
    }
    for (int i = 0; i < fwdIrsQuotes.length; i++) {
      fwdIdValue[i + 1 + fwdFraQuotes.length] = "IRS" + fwdIrsTenors[i].toString();
    }
    return fwdIdValue;
  }

  public static double[] fwdMarketQuotes(
      double fwdFixingQuote,
      double[] fwdFraQuotes,
      double[] fwdIrsQuotes) {
    int fwdNbFraNodes = fwdFraQuotes.length;
    int fwdNbIrsNodes = fwdIrsQuotes.length;
    int fwdNbNodes = 1 + fwdNbFraNodes + fwdNbIrsNodes;
    double[] fwdMarketQuotes = new double[fwdNbNodes];
    fwdMarketQuotes[0] = fwdFixingQuote;
    System.arraycopy(fwdFraQuotes, 0, fwdMarketQuotes, 1, fwdNbFraNodes);
    System.arraycopy(fwdIrsQuotes, 0, fwdMarketQuotes, 1 + fwdNbFraNodes, fwdNbIrsNodes);
    return fwdMarketQuotes;
  }
  
  public static List<List<CurveNode[]>> curvesNodes(
      Period[] dscOisTenors, 
      String[] dscIdValues,
      Period[] fwd3FraTenors, 
      Period[] fwd3IrsTenors, 
      String[] fwd3IdValues,
      Period[] fwd6FraTenors, 
      Period[] fwd6IrsTenors, 
      String[] fwd6IdValues){
    CurveNode[] dscNodes = new CurveNode[dscOisTenors.length];
    for (int i = 0; i < dscOisTenors.length; i++) {
      dscNodes[i] = FixedOvernightSwapCurveNode.of(
          FixedOvernightSwapTemplate.of(Period.ZERO, Tenor.of(dscOisTenors[i]), EUR_FIXED_1Y_EONIA_OIS),
          QuoteKey.of(StandardId.of(SCHEME, dscIdValues[i])));
    }
    CurveNode[] fwd3Nodes = new CurveNode[fwd3IdValues.length];
    fwd3Nodes[0] = IborFixingDepositCurveNode.of(IborFixingDepositTemplate.of(EUR_EURIBOR_3M),
        QuoteKey.of(StandardId.of(SCHEME, fwd3IdValues[0])));
    for (int i = 0; i < fwd3FraTenors.length; i++) {
      fwd3Nodes[i + 1] = FraCurveNode.of(FraTemplate.of(fwd3FraTenors[i], EUR_EURIBOR_3M),
          QuoteKey.of(StandardId.of(SCHEME, fwd3IdValues[i + 1])));
    }
    for (int i = 0; i < fwd3IrsTenors.length; i++) {
      fwd3Nodes[i + 1 + fwd3FraTenors.length] = FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Period.ZERO, Tenor.of(fwd3IrsTenors[i]), EUR_FIXED_1Y_EURIBOR_3M),
          QuoteKey.of(StandardId.of(SCHEME, fwd3IdValues[i + 1])));
    }
    CurveNode[] fwd6Nodes = new CurveNode[fwd6IdValues.length];
    fwd6Nodes[0] = IborFixingDepositCurveNode.of(IborFixingDepositTemplate.of(EUR_EURIBOR_6M),
        QuoteKey.of(StandardId.of(SCHEME, fwd6IdValues[0])));
    for (int i = 0; i < fwd6FraTenors.length; i++) {
      fwd6Nodes[i + 1] = FraCurveNode.of(FraTemplate.of(fwd6FraTenors[i], EUR_EURIBOR_6M),
          QuoteKey.of(StandardId.of(SCHEME, fwd6IdValues[i + 1])));
    }
    for (int i = 0; i < fwd6IrsTenors.length; i++) {
      fwd6Nodes[i + 1 + fwd6FraTenors.length] = FixedIborSwapCurveNode.of(
          FixedIborSwapTemplate.of(Period.ZERO, Tenor.of(fwd6IrsTenors[i]), EUR_FIXED_1Y_EURIBOR_6M),
          QuoteKey.of(StandardId.of(SCHEME, fwd6IdValues[i + 1])));
    }    
    List<List<CurveNode[]>> curvesNodes = new ArrayList<>();
    List<CurveNode[]> groupDsc = new ArrayList<>();
    groupDsc.add(dscNodes);
    curvesNodes.add(groupDsc);
    List<CurveNode[]> groupFwd3 = new ArrayList<>();
    groupFwd3.add(fwd3Nodes);
    curvesNodes.add(groupFwd3);
    List<CurveNode[]> groupFwd6 = new ArrayList<>();
    groupFwd6.add(fwd6Nodes);
    curvesNodes.add(groupFwd6);    
    return curvesNodes;
  }
  
  public static Map<ObservableKey, Double> allQuotes(
      double[] dscOisQuotes,
      String[] dscIdValues,
      double[] fwd3MarketQuotes,
      String[] fwd3IdValue,
      double[] fwd6MarketQuotes,
      String[] fwd6IdValue) {
    /* All quotes for the curve calibration */
    Map<ObservableKey, Double> allQuotes = new HashMap<>();
    for (int i = 0; i < dscOisQuotes.length; i++) {
      allQuotes.put(QuoteKey.of(StandardId.of(SCHEME, dscIdValues[i])), dscOisQuotes[i]);
    }
    for (int i = 0; i < fwd3MarketQuotes.length; i++) {
      allQuotes.put(QuoteKey.of(StandardId.of(SCHEME, fwd3IdValue[i])), fwd3MarketQuotes[i]);
    }
    for (int i = 0; i < fwd6MarketQuotes.length; i++) {
      allQuotes.put(QuoteKey.of(StandardId.of(SCHEME, fwd6IdValue[i])), fwd6MarketQuotes[i]);
    }
    return allQuotes;
  }

}
