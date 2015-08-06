/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.math.interpolation.FlatExtrapolator1D;
import com.opengamma.analytics.math.interpolation.LinearInterpolator1D;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.interpolator.CurveExtrapolator;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.basics.market.ObservableKey;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveParameterMetadata;
import com.opengamma.strata.market.curve.TenorCurveNodeMetadata;
import com.opengamma.strata.market.curve.config.CurveNode;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Utilities used in the calibration tests.
 */
public class CalibrationUtils {
  
  private static final CurveInterpolator INTERPOLATOR_LINEAR = new LinearInterpolator1D();
  private static final CurveExtrapolator EXTRAPOLATOR_FLAT = new FlatExtrapolator1D();
  private static final CalibrationCalculator CALIBRATION_CALCULATOR = DefaultCalibrationCalculator.DEFAULT;
  private static final double DEFAULT_TOL_ABS = 1.0E-9;
  private static final double DEFAULT_TOL_REL = 1.0E-9;
  private static final int DEFAULT_MAX_IT = 100;
  private static final CalibrationFunction CALIBRATION_FUNCTION = 
      new CalibrationFunction(DEFAULT_TOL_ABS, DEFAULT_TOL_REL, DEFAULT_MAX_IT, CALIBRATION_CALCULATOR);
  
  public static Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> calibration(
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
    Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> result =
        CALIBRATION_FUNCTION.calibrate(dataTotal, knownData, discountingNames, indexNames);
    return result;
  }

}
