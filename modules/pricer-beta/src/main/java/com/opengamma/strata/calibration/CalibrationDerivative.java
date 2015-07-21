/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.util.List;

import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.matrix.DoubleMatrix1D;
import com.opengamma.analytics.math.matrix.DoubleMatrix2D;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.pricer.rate.RatesProvider;

public class CalibrationDerivative extends Function1D<DoubleMatrix1D, DoubleMatrix2D> {

  private final CalibrationCalculator calculator;
  private final List<Trade> trades;
  private final int nbTrades;
  private final RatesProviderTemplate providerTemplate;
  /** Provide the order in which the curves appear in the long vector result. The expected number of 
   * parameter for each curve is also provided. */
  private final List<Pair<CurveName, Integer>> curveOrder;

  public CalibrationDerivative(
      CalibrationCalculator calculator,
      List<Trade> trades,
      RatesProviderTemplate providerTemplate,
      List<Pair<CurveName, Integer>> curveOrder) {
    this.calculator = calculator;
    this.trades = trades;
    this.providerTemplate = providerTemplate;
    this.nbTrades = trades.size();
    this.curveOrder = curveOrder;
  }

  @Override
  public DoubleMatrix2D evaluate(DoubleMatrix1D x) {
    // Provider
    double[] data = x.getData();
    RatesProvider provider = providerTemplate.generate(data);
    // Results
    double[][] measure = new double[nbTrades][nbTrades];
    for (int i = 0; i < nbTrades; i++) {
      measure[i] = calculator.derivative(trades.get(i), provider, curveOrder);
    }
    return new DoubleMatrix2D(measure);
  }

}
