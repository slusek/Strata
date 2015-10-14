/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.calibration;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Doubles;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.market.ObservableValues;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.JacobianCalibrationMatrix;
import com.opengamma.strata.market.curve.definition.CurveGroupDefinition;
import com.opengamma.strata.market.curve.definition.CurveGroupEntry;
import com.opengamma.strata.market.curve.definition.CurveNode;
import com.opengamma.strata.market.curve.definition.CurveParameterSize;
import com.opengamma.strata.math.impl.function.Function1D;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionFactory;
import com.opengamma.strata.math.impl.matrix.CommonsMatrixAlgebra;
import com.opengamma.strata.math.impl.matrix.DoubleMatrix1D;
import com.opengamma.strata.math.impl.matrix.DoubleMatrix2D;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebra;
import com.opengamma.strata.math.impl.rootfinding.newton.BroydenVectorRootFinder;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Curve calibrator.
 * <p>
 * This calibrator takes an abstract curve definition and produces real curves.
 * <p>
 * Curves are calibrated in groups or one or more curves.
 * In addition, more than one group may be calibrated together.
 * <p>
 * Each curve is defined using two or more {@linkplain CurveNode nodes}.
 * Each node primarily defines enough information to produce a reference trade.
 * Calibration involves pricing, and re-pricing, these trades to find the best fit
 * using a root finder.
 * <p>
 * Once calibrated, the curves are then available for use.
 * Each node in the curve definition becomes a parameter in the matching output curve.
 */
public final class CurveCalibrator {

  /**
   * The default curve calibrator.
   * <p>
   * This uses the default tolerance of 1e-9, a maximum of 1000 steps and the
   * default {@link CalibrationMeasures} instance.
   */
  public static final CurveCalibrator DEFAULT = CurveCalibrator.of(1e-9, 1e-9, 1000, CalibrationMeasures.DEFAULT);

  /**
   * The matrix algebra used for matrix inversion.
   */
  private static final MatrixAlgebra MATRIX_ALGEBRA = new CommonsMatrixAlgebra();
  /**
   * The root finder used for curve calibration.
   */
  private final BroydenVectorRootFinder rootFinder;
  /**
   * The calibration measures.
   * This is used to compute the function for which the root is found.
   */
  private final CalibrationMeasures measures;

  //-------------------------------------------------------------------------
  /**
   * Obtains an instance, specifying tolerances and measures to use.
   * 
   * @param toleranceAbs  the absolute tolerance
   * @param toleranceRel  the relative tolerance
   * @param stepMaximum  the maximum steps
   * @param measures  the calibration measures, used to compute the function for which the root is found
   * @return the curve calibrator
   */
  public static CurveCalibrator of(
      double toleranceAbs,
      double toleranceRel,
      int stepMaximum,
      CalibrationMeasures measures) {

    return new CurveCalibrator(toleranceAbs, toleranceRel, stepMaximum, measures);
  }

  //-------------------------------------------------------------------------
  // restricted constructor
  private CurveCalibrator(
      double toleranceAbs,
      double toleranceRel,
      int stepMaximum,
      CalibrationMeasures measures) {

    this.rootFinder = new BroydenVectorRootFinder(
        toleranceAbs,
        toleranceRel,
        stepMaximum,
        DecompositionFactory.getDecomposition(DecompositionFactory.SV_COMMONS_NAME));
    this.measures = measures;
  }

  //-------------------------------------------------------------------------
  /**
   * Calibrates a single curve group, containing one or more curves.
   * <p>
   * The calibration is defined using {@link CurveGroupDefinition}.
   * Observable market data, time-series and FX are also needed to complete the calibration.
   * 
   * @param curveGroupDefn  the curve group definition
   * @param valuationDate  the validation date
   * @param marketData  the market data required to build a trade for the instrument
   * @param timeSeries  the time-series
   * @param fxMatrix  the FX matrix
   * @return the rates provider resulting from the calibration
   */
  public ImmutableRatesProvider calibrate(
      CurveGroupDefinition curveGroupDefn,
      LocalDate valuationDate,
      ObservableValues marketData,
      Map<Index, LocalDateDoubleTimeSeries> timeSeries,
      FxMatrix fxMatrix) {

    ImmutableRatesProvider knownData = ImmutableRatesProvider.builder()
        .valuationDate(valuationDate)
        .fxMatrix(fxMatrix)
        .timeSeries(timeSeries)
        .build();
    return calibrate(ImmutableList.of(curveGroupDefn), knownData, marketData);
  }

  //-------------------------------------------------------------------------
  /**
   * Calibrates a list of curve groups, each containing one or more curves.
   * <p>
   * The calibration is defined using a list of {@link CurveGroupDefinition}.
   * Observable market data and existing known data are also needed to complete the calibration.
   * <p>
   * A curve must only exist in one group.
   * 
   * @param allGroupsDefn  the curve group definitions
   * @param knownData  the starting data for the calibration
   * @param marketData  the market data required to build a trade for the instrument
   * @return the rates provider resulting from the calibration
   */
  public ImmutableRatesProvider calibrate(
      List<CurveGroupDefinition> allGroupsDefn,
      ImmutableRatesProvider knownData,
      ObservableValues marketData) {

    // perform calibration one group at a time, building up the result by mutating these variables
    ImmutableRatesProvider providerCombined = knownData;
    ImmutableList<CurveParameterSize> orderPrev = ImmutableList.of();
    ImmutableMap<CurveName, JacobianCalibrationMatrix> jacobians = ImmutableMap.of();
    for (CurveGroupDefinition groupDefn : allGroupsDefn) {
      // combine all data in the group into flat lists
      ImmutableList<Trade> trades = groupDefn.trades(knownData.getValuationDate(), marketData);
      ImmutableList<Double> initialGuesses = groupDefn.initialGuesses(knownData.getValuationDate(), marketData);
      ImmutableList<CurveParameterSize> orderGroup = toOrder(groupDefn);
      ImmutableList<CurveParameterSize> orderPrevAndGroup = ImmutableList.<CurveParameterSize>builder()
          .addAll(orderPrev)
          .addAll(orderGroup)
          .build();

      // calibrate
      RatesProviderGenerator providerGenerator = ImmutableRatesProviderGenerator.of(providerCombined, groupDefn);
      double[] calibratedGroupParams = calibrateGroup(providerGenerator, trades, initialGuesses, orderGroup);
      ImmutableRatesProvider calibratedProvider = providerGenerator.generate(calibratedGroupParams);

      // use calibration to build Jacobian matrices
      jacobians = updateJacobiansForGroup(
          calibratedProvider, trades, orderGroup, orderPrev, orderPrevAndGroup, jacobians);
      orderPrev = orderPrevAndGroup;

      // use Jacobians to build output curves
      providerCombined = providerGenerator.generate(calibratedGroupParams, jacobians);
    }
    // return the calibrated provider
    return providerCombined;
  }

  // converts a definition to the curve order list
  private static ImmutableList<CurveParameterSize> toOrder(CurveGroupDefinition groupDefn) {
    ImmutableList.Builder<CurveParameterSize> builder = ImmutableList.builder();
    for (CurveGroupEntry entry : groupDefn.getEntries()) {
      builder.add(entry.getCurveDefinition().toCurveParameterSize());
    }
    return builder.build();
  }

  //-------------------------------------------------------------------------
  // calibrates a single group
  private double[] calibrateGroup(
      RatesProviderGenerator providerGenerator,
      ImmutableList<Trade> trades,
      ImmutableList<Double> initialGuesses,
      ImmutableList<CurveParameterSize> curveOrder) {

    // setup for calibration
    Function1D<DoubleMatrix1D, DoubleMatrix1D> valueCalculator =
        new CalibrationValue(trades, measures, providerGenerator);
    Function1D<DoubleMatrix1D, DoubleMatrix2D> derivativeCalculator =
        new CalibrationDerivative(trades, measures, providerGenerator, curveOrder);

    // calibrate
    DoubleMatrix1D initGuessMatrix = new DoubleMatrix1D(Doubles.toArray(initialGuesses));
    return rootFinder.getRoot(valueCalculator, derivativeCalculator, initGuessMatrix).getData();
  }

  //-------------------------------------------------------------------------
  // calculates the Jacobian and builds the result, called once per group
  // this uses, but does not alter, data from previous groups
  private ImmutableMap<CurveName, JacobianCalibrationMatrix> updateJacobiansForGroup(
      ImmutableRatesProvider provider,
      ImmutableList<Trade> trades,
      ImmutableList<CurveParameterSize> orderGroup,
      ImmutableList<CurveParameterSize> orderPrev,
      ImmutableList<CurveParameterSize> orderAll,
      ImmutableMap<CurveName, JacobianCalibrationMatrix> jacobians) {

    // sensitivity to all parameters in the stated order
    int totalParamsGroup = orderGroup.stream().mapToInt(e -> e.getParameterCount()).sum();
    double[][] res = derivatives(trades, provider, orderAll, totalParamsGroup);

    // jacobian direct
    int nbTrades = trades.size();
    int totalParamsAll = res[0].length;
    int totalParamsPrevious = totalParamsAll - totalParamsGroup;
    DoubleMatrix2D pDmCurrentMatrix = jacobianDirect(res, nbTrades, totalParamsGroup, totalParamsPrevious);

    // jacobian indirect: when totalParamsPrevious > 0
    double[][] pDmPreviousArray = jacobianIndirect(
        res, pDmCurrentMatrix, nbTrades, totalParamsGroup, totalParamsPrevious, orderPrev, jacobians);

    // add to the map of jacobians, one entry for each curve in this group
    ImmutableMap.Builder<CurveName, JacobianCalibrationMatrix> jacobianBuilder = ImmutableMap.builder();
    jacobianBuilder.putAll(jacobians);
    double[][] pDmCurrentArray = pDmCurrentMatrix.getData();
    int startIndex = 0;
    for (CurveParameterSize order : orderGroup) {
      int paramCount = order.getParameterCount();
      double[][] pDmCurveArray = new double[paramCount][totalParamsAll];
      // copy data for previous groups
      if (totalParamsPrevious > 0) {
        for (int p = 0; p < paramCount; p++) {
          System.arraycopy(pDmPreviousArray[startIndex + p], 0, pDmCurveArray[p], 0, totalParamsPrevious);
        }
      }
      // copy data for this group
      for (int p = 0; p < paramCount; p++) {
        System.arraycopy(pDmCurrentArray[startIndex + p], 0, pDmCurveArray[p], totalParamsPrevious, totalParamsGroup);
      }
      // build final Jacobian matrix
      DoubleMatrix2D pDmCurveMatrix = new DoubleMatrix2D(pDmCurveArray);
      jacobianBuilder.put(order.getName(), JacobianCalibrationMatrix.of(orderAll, pDmCurveMatrix));
      startIndex += paramCount;
    }
    return jacobianBuilder.build();
  }

  // calculate the derivatives
  private double[][] derivatives(
      ImmutableList<Trade> trades,
      ImmutableRatesProvider provider,
      ImmutableList<CurveParameterSize> orderAll,
      int totalParamsGroup) {

    double[][] res = new double[totalParamsGroup][];
    for (int i = 0; i < trades.size(); i++) {
      res[i] = measures.derivative(trades.get(i), provider, orderAll);
    }
    return res;
  }

  // jacobian direct, for the current group
  private static DoubleMatrix2D jacobianDirect(
      double[][] res,
      int nbTrades,
      int totalParamsGroup,
      int totalParamsPrevious) {

    double[][] direct = new double[totalParamsGroup][totalParamsGroup];
    for (int i = 0; i < nbTrades; i++) {
      System.arraycopy(res[i], totalParamsPrevious, direct[i], 0, totalParamsGroup);
    }
    return MATRIX_ALGEBRA.getInverse(new DoubleMatrix2D(direct));
  }

  // jacobian indirect, merging groups
  private static double[][] jacobianIndirect(
      double[][] res,
      DoubleMatrix2D pDmCurrentMatrix,
      int nbTrades,
      int totalParamsGroup,
      int totalParamsPrevious,
      ImmutableList<CurveParameterSize> orderPrevious,
      ImmutableMap<CurveName, JacobianCalibrationMatrix> jacobiansPrevious) {

    double[][] pDmPreviousArray = new double[0][0];
    if (totalParamsPrevious > 0) {
      double[][] nonDirect = new double[totalParamsGroup][totalParamsPrevious];
      for (int i = 0; i < nbTrades; i++) {
        System.arraycopy(res[i], 0, nonDirect[i], 0, totalParamsPrevious);
      }
      DoubleMatrix2D pDpPreviousMatrix = (DoubleMatrix2D) MATRIX_ALGEBRA.scale(
          MATRIX_ALGEBRA.multiply(pDmCurrentMatrix, new DoubleMatrix2D(nonDirect)), -1d);
      // transition Matrix: all curves from previous groups
      double[][] transition = new double[totalParamsPrevious][totalParamsPrevious];
      int startIndexOuter = 0;
      for (CurveParameterSize order : orderPrevious) {  // l
        int paramCountOuter = order.getParameterCount();
        JacobianCalibrationMatrix thisInfo = jacobiansPrevious.get(order.getName());
        double[][] thisMatrix = thisInfo.getJacobianMatrix().getData();
        int startIndexInner = 0;
        for (CurveParameterSize order2 : orderPrevious) {  // k
          int paramCountInner = order2.getParameterCount();
          if (thisInfo.containsCurve(order2.getName())) { // If not, the matrix stay with 0
            for (int p = 0; p < paramCountOuter; p++) {
              System.arraycopy(
                  thisMatrix[p], startIndexOuter, transition[startIndexOuter + p], startIndexInner, paramCountInner);
            }
          }
          startIndexInner += paramCountInner;
        }
        startIndexOuter += paramCountOuter;
      }
      DoubleMatrix2D transitionMatrix = new DoubleMatrix2D(transition);
      DoubleMatrix2D pDmPreviousMatrix = (DoubleMatrix2D) MATRIX_ALGEBRA.multiply(pDpPreviousMatrix, transitionMatrix);
      pDmPreviousArray = pDmPreviousMatrix.getData();
    }
    return pDmPreviousArray;
  }

}
