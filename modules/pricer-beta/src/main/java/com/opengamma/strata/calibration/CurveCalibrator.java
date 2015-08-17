/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlock;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.linearalgebra.DecompositionFactory;
import com.opengamma.analytics.math.matrix.CommonsMatrixAlgebra;
import com.opengamma.analytics.math.matrix.DoubleMatrix1D;
import com.opengamma.analytics.math.matrix.DoubleMatrix2D;
import com.opengamma.analytics.math.matrix.MatrixAlgebra;
import com.opengamma.analytics.math.rootfinding.newton.BroydenVectorRootFinder;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.market.ObservableKey;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.TenorCurveNodeMetadata;
import com.opengamma.strata.market.curve.config.CurveConfig;
import com.opengamma.strata.market.curve.config.CurveGroupConfig;
import com.opengamma.strata.market.curve.config.CurveGroupEntry;
import com.opengamma.strata.market.curve.config.CurveNode;
import com.opengamma.strata.market.curve.config.InterpolatedCurveConfig;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Class to calibrate curves to a set of trades.
 */
public class CurveCalibrator {

  /** The matrix algebra used for matrix inversion. */
  private static final MatrixAlgebra MATRIX_ALGEBRA = new CommonsMatrixAlgebra();
  /** The root finder used for curve calibration. */
  private final BroydenVectorRootFinder rootFinder;
  /** The calculator used to compute the function for which the root is found. */
  private final CalibrationCalculator calculator;
  
  public CurveCalibrator(
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
    DoubleMatrix1D initGuessMat = new DoubleMatrix1D(initGuess.toArray(new Double[initGuess.size()]));
    double[] parameters = rootFinder.getRoot(valueCalculator, derivativeCalculator, initGuessMat).getData();
    return (ImmutableRatesProvider) providerTemplate.generate(parameters);
  }

  private void updateBlockBundle(
      List<Trade> trades,
      ImmutableRatesProvider provider,
      List<Pair<CurveName, Integer>> curveOrderGroup,
      List<Pair<CurveName, Integer>> curveOrderBefore,
      CurveBuildingBlockBundle blockBundle) {
    LinkedHashMap<String, Pair<Integer, Integer>> mapBlockOut = new LinkedHashMap<>();
    // Curve names manipulation
    List<Pair<CurveName, Integer>> curveOrderAll = new ArrayList<Pair<CurveName, Integer>>(curveOrderBefore);
    curveOrderAll.addAll(curveOrderGroup);
    int nbCurvesBefore = curveOrderBefore.size();
    int nbCurvesGroup = curveOrderGroup.size();
    int nbParametersCurvesGroup = 0;
    int[] nbParametersGroup = new int[nbCurvesGroup];
    int[] startIndexGroup = new int[nbCurvesGroup];
    int loopc = 0;
    for (Pair<CurveName, Integer> pair : curveOrderGroup) {
      startIndexGroup[loopc] = nbParametersCurvesGroup;
      nbParametersGroup[loopc] = pair.getSecond();
      nbParametersCurvesGroup += nbParametersGroup[loopc];
      loopc++;
    }
    // Sensitivity to parameters
    int nbTrades = trades.size();
    double[][] res = new double[nbParametersCurvesGroup][];
    for (int looptrade = 0; looptrade < nbTrades; looptrade++) {
      res[looptrade] = calculator.derivative(trades.get(looptrade), provider, curveOrderAll);
      // The sensitivity is to all parameters in the stated order
    }
    int nbParametersCurvesAll = res[0].length;
    // Jacobian direct
    int nbParametersCurvesBefore = nbParametersCurvesAll - nbParametersCurvesGroup;
    double[][] direct = new double[nbParametersCurvesGroup][nbParametersCurvesGroup];
    for (int loopp = 0; loopp < nbTrades; loopp++) {
      System.arraycopy(res[loopp], nbParametersCurvesBefore, direct[loopp], 0, nbParametersCurvesGroup);
    }
    DoubleMatrix2D pDmCurrentMatrix = MATRIX_ALGEBRA.getInverse(new DoubleMatrix2D(direct));
    // Jacobian indirect: when nbCurvesBefore>0
    double[][] pDmBeforeArray = new double[0][0];
    if (nbParametersCurvesBefore > 0) {
      final double[][] nonDirect = new double[nbParametersCurvesGroup][nbParametersCurvesBefore];
      for (int loopp = 0; loopp < nbTrades; loopp++) {
        System.arraycopy(res[loopp], 0, nonDirect[loopp], 0, nbParametersCurvesBefore);
      }
      final DoubleMatrix2D pDpBeforeMatrix;
      pDpBeforeMatrix = (DoubleMatrix2D) MATRIX_ALGEBRA.scale(MATRIX_ALGEBRA.multiply(pDmCurrentMatrix, new DoubleMatrix2D(nonDirect)), -1.0);
      // All curves: order and size
      int[] nbParametersBefore = new int[nbCurvesBefore];
      int[] startIndexBefore = new int[nbCurvesBefore];
      int tempNbParam = 0;
      loopc = 0;
      for (Pair<CurveName, Integer> pair : curveOrderBefore) {
        nbParametersBefore[loopc] = pair.getSecond();
        startIndexBefore[loopc] = tempNbParam;
        tempNbParam += nbParametersBefore[loopc];
        loopc++;
      }
      // Transition Matrix: all curves before current
      final double[][] transition = new double[nbParametersCurvesBefore][nbParametersCurvesBefore];
      loopc = 0;
      int loopc2 = 0;
      for (Pair<CurveName, Integer> pair : curveOrderBefore) { // l
        final Pair<CurveBuildingBlock, DoubleMatrix2D> thisPair = blockBundle.getBlock(pair.getFirst().toString());
        final CurveBuildingBlock thisBlock = thisPair.getFirst();
        final Set<String> thisBlockCurves = thisBlock.getAllNames();
        final double[][] thisMatrix = thisPair.getSecond().getData();
        loopc2 = 0;
        for (Pair<CurveName, Integer> pair2 : curveOrderBefore) { // k
          if (thisBlockCurves.contains(pair2.getFirst().toString())) { // If not, the matrix stay with 0
            final Integer start = thisBlock.getStart(pair2.getFirst().toString());
            for (int loopp = 0; loopp < nbParametersBefore[loopc]; loopp++) {
              System.arraycopy(thisMatrix[loopp], start, transition[startIndexBefore[loopc] + loopp],
                  startIndexBefore[loopc2], thisBlock.getNbParameters(pair2.getFirst().toString()));
            }
          }
          loopc2++;
        }
        loopc++;
      }
      final DoubleMatrix2D transitionMatrix = new DoubleMatrix2D(transition);
      DoubleMatrix2D pDmBeforeMatrix;
      pDmBeforeMatrix = (DoubleMatrix2D) MATRIX_ALGEBRA.multiply(pDpBeforeMatrix, transitionMatrix);
      pDmBeforeArray = pDmBeforeMatrix.getData();
      loopc = 0;
      for (Pair<CurveName, Integer> pair : curveOrderBefore) {
        mapBlockOut.put(pair.getFirst().toString(), Pair.of(startIndexBefore[loopc], nbParametersBefore[loopc]));
        loopc++;
      }
    }
    loopc = 0;
    for (Pair<CurveName, Integer> pair : curveOrderGroup) {
      mapBlockOut.put(pair.getFirst().toString(), Pair.of(nbParametersCurvesBefore + startIndexGroup[loopc], nbParametersGroup[loopc]));
      loopc++;
    }
    final CurveBuildingBlock blockOut = new CurveBuildingBlock(mapBlockOut);
    final double[][] pDmCurrentArray = pDmCurrentMatrix.getData();
    loopc = 0;
    for (Pair<CurveName, Integer> pair : curveOrderGroup) {
      final double[][] pDmCurveArray = new double[nbParametersGroup[loopc]][nbParametersCurvesAll];
      for (int loopp = 0; loopp < nbParametersGroup[loopc]; loopp++) {
        System.arraycopy(pDmCurrentArray[startIndexGroup[loopc] + loopp], 0, pDmCurveArray[loopp], nbParametersCurvesBefore, nbParametersCurvesGroup);
      }
      if (nbParametersCurvesBefore > 0) {
        for (int loopp = 0; loopp < nbParametersGroup[loopc]; loopp++) {
          System.arraycopy(pDmBeforeArray[startIndexGroup[loopc] + loopp], 0, pDmCurveArray[loopp], 0, nbParametersCurvesBefore);
        }
      }
      final DoubleMatrix2D pDmCurveMatrix = new DoubleMatrix2D(pDmCurveArray);
      blockBundle.add(pair.getFirst().toString(), blockOut, pDmCurveMatrix);
      loopc++;
    }
  }
  
  /**
   * Calibrate curves to a set of instruments.
   * @param dataTotal The data for all the curves. The calibration is done by groups. 
   * The List of lists correspond to a list of calibration group, each calibration group represented by a list of curves.
   * @param knownData The starting data for the calibration. 
   * @param discountingNames The names of the discounting curves, as a map between curve names and currencies.
   * @param forwardNames The names of the forward curves, as a map between curve names and indices.
   * @return The rates provider resulting from the calibration.
   */
  public Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> calibrate(
      List<List<CalibrationCurveData>> dataTotal,
      ImmutableRatesProvider knownData,
      Map<CurveName, Currency> discountingNames,
      Map<CurveName, Set<Index>> forwardNames) {
    int nbGroups = dataTotal.size();
    CurveBuildingBlockBundle blockIncremental = new CurveBuildingBlockBundle();
    ImmutableRatesProvider providerIncremental = knownData;
    List<Pair<CurveName, Integer>> curveOrderIncremental = new ArrayList<>();
    for (int loopgroup = 0; loopgroup < nbGroups; loopgroup++) {
      List<CalibrationCurveData> dataGroup = dataTotal.get(loopgroup);
      List<Trade> tradesGroup = new ArrayList<>();
      List<Double> initialGuess = new ArrayList<>();
      List<CurveTemplate> curveTemplates = new ArrayList<>();
      List<Pair<CurveName, Integer>> curveOrderGroup = new ArrayList<>();
      for (CalibrationCurveData d : dataGroup) {
        tradesGroup.addAll(d.getTrades());
        initialGuess.addAll(d.getInitialGuess());
        curveTemplates.add(d.getTemplate());
        curveOrderGroup.add(Pair.of(d.getTemplate().getName(), d.getTemplate().getParameterCount()));
      }
      RatesProviderTemplate providerTemplate =
          new ImmutableRatesProviderTemplate(providerIncremental, curveTemplates, discountingNames, forwardNames);
      providerIncremental = makeGroup(tradesGroup, initialGuess, providerTemplate, curveOrderGroup);
      updateBlockBundle(tradesGroup, providerIncremental, curveOrderGroup, curveOrderIncremental, blockIncremental);
      curveOrderIncremental.addAll(curveOrderGroup);
    }
    return Pair.of(providerIncremental, blockIncremental);
  }
  
  public Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> calibrate(
      LocalDate valuationDate,
      CurveGroupConfig curveGroupConfig,
      Map<ObservableKey, Double> quotes,
      Map<Index, LocalDateDoubleTimeSeries> timeSeries,
      FxMatrix fxMatrix){
    List<List<CalibrationCurveData>> dataTotal = new ArrayList<>();
    List<CalibrationCurveData> dataGroup = new ArrayList<>();    
    ImmutableList<CurveGroupEntry> groupEntries = curveGroupConfig.getEntries();
    Map<CurveName, Currency> discountingNames = new HashMap<>();
    Map<CurveName, Set<Index>> indexNames = new HashMap<>();    
    for(CurveGroupEntry entry: groupEntries) {
      CurveConfig curveConfig = entry.getCurveConfig();
      ArgChecker.isTrue(curveConfig instanceof InterpolatedCurveConfig, 
          "CurveConfig should be of the type InterpolatedCurveConfig");
      InterpolatedCurveConfig interpolatedCurveConfig = (InterpolatedCurveConfig) curveConfig;
      CurveMetadata curveMetadata = curveConfig.metadata(valuationDate);
      List<CurveNode> nodes = entry.getCurveConfig().getNodes();
      int nbNodes = nodes.size();
      double[] curveTimes = new double[nbNodes];
      List<Trade> curveTrades = new ArrayList<>();
      List<Double> curveGuesses = new ArrayList<>();
      for (int i = 0; i < nbNodes; i++) {        
        curveTimes[i] = curveMetadata.getDayCount().get()
            .yearFraction(valuationDate, 
                ((TenorCurveNodeMetadata) curveMetadata.getParameterMetadata().get().get(i)).getDate());
        curveTrades.add(nodes.get(i).trade(valuationDate, quotes));
        curveGuesses.add(0.0d);
      }
      InterpolatedCurveTemplate template = new InterpolatedCurveTemplate(
          curveMetadata, curveTimes, interpolatedCurveConfig.getLeftExtrapolator(), 
          interpolatedCurveConfig.getInterpolator(), interpolatedCurveConfig.getRightExtrapolator());
      CalibrationCurveData data = new CalibrationCurveData(template, curveTrades, curveGuesses);
      dataGroup.add(data);
      Optional<Currency> ccy = entry.getDiscountingCurrency();
      if(ccy.isPresent()) {
        discountingNames.put(curveConfig.getName(), ccy.get());
      }
      Set<Index> indices = new HashSet<>();
      indices.addAll(entry.getIborIndices());
      indices.addAll(entry.getOvernightIndices());
      indexNames.put(curveConfig.getName(), indices);
    }
    dataTotal.add(dataGroup);
    ImmutableRatesProvider knownData = ImmutableRatesProvider.builder()
        .valuationDate(valuationDate)
        .fxMatrix(fxMatrix).timeSeries(timeSeries).build();
    Pair<ImmutableRatesProvider, CurveBuildingBlockBundle> result =
        calibrate(dataTotal, knownData, discountingNames, indexNames);
    return result;
  }

}
