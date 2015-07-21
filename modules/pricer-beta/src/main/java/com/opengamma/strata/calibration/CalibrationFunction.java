/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.linearalgebra.DecompositionFactory;
import com.opengamma.analytics.math.matrix.CommonsMatrixAlgebra;
import com.opengamma.analytics.math.matrix.DoubleMatrix1D;
import com.opengamma.analytics.math.matrix.DoubleMatrix2D;
import com.opengamma.analytics.math.matrix.MatrixAlgebra;
import com.opengamma.analytics.math.rootfinding.newton.BroydenVectorRootFinder;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

public class CalibrationFunction {

  /** The matrix algebra used for matrix inversion. */
  private static final MatrixAlgebra MATRIX_ALGEBRA = new CommonsMatrixAlgebra();
  /** The root finder used for curve calibration. */
  private final BroydenVectorRootFinder rootFinder;
  
  private final CalibrationCalculator calculator;
  
  public CalibrationFunction(
      double toleranceAbs, 
      double toleranceRel, 
      int stepMaximum,
      CalibrationCalculator calculator) {
    rootFinder = new BroydenVectorRootFinder(toleranceAbs, toleranceRel, stepMaximum,
        DecompositionFactory.getDecomposition(DecompositionFactory.SV_COMMONS_NAME));
    this.calculator = calculator;
  }
  
  private ImmutableRatesProvider makeGroup(
      List<Trade> trades,
      List<Double> initGuess,
      RatesProviderTemplate providerTemplate,
      List<Pair<CurveName, Integer>> curveOrder) {
    Function1D<DoubleMatrix1D, DoubleMatrix1D> valueCalculator = 
        new CalibrationValue(calculator, trades, providerTemplate);
    Function1D<DoubleMatrix1D, DoubleMatrix2D> derivativeCalculator =
        new CalibrationDerivative(calculator, trades, providerTemplate, curveOrder);
    double[] parameters = 
        rootFinder.getRoot(valueCalculator, derivativeCalculator, 
            new DoubleMatrix1D(initGuess.toArray(new Double[initGuess.size()]))).getData();
    return (ImmutableRatesProvider) providerTemplate.generate(parameters);
  }
  
  //TODO: updateBlockBundle
  
  public Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> calibrate(
      List<List<CalibrationCurveData>> dataTotal,
      ImmutableRatesProvider knownData,
      Map<CurveName, Currency> discountingNames,
      Map<CurveName, Index[]> forwardNames){
    int nbGroups = dataTotal.size();
    ImmutableRatesProvider knownSoFar = knownData;
    for(int loopgroup=0; loopgroup<nbGroups; loopgroup++) {
      List<CalibrationCurveData> dataGroup = dataTotal.get(loopgroup);
      List<Trade> tradesGroup = new ArrayList<>();
      List<Double> initialGuess = new ArrayList<>();
      List<CurveTemplate> curveTemplates = new ArrayList<>();
      List<Pair<CurveName, Integer>> curveOrder = new ArrayList<>();
      for(CalibrationCurveData d: dataGroup) {
        tradesGroup.addAll(d.getTrades());
        initialGuess.addAll(d.getInitialGuess());
        curveTemplates.add(d.getTemplate());
        curveOrder.add(Pair.of(d.getTemplate().getName(), d.getTemplate().getParameterCount()));
      }
      RatesProviderTemplate providerTemplate = 
          new ImmutableRatesProviderTemplate(knownSoFar, curveTemplates, discountingNames, forwardNames);
      knownSoFar = makeGroup(tradesGroup, initialGuess, providerTemplate, curveOrder);
      //TODO: blockBundle
    }
    // TODO: curve group
    return Pair.of(knownSoFar, null);
  }

}
