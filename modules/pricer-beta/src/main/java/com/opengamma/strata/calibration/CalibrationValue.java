/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.util.List;

import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.matrix.DoubleMatrix1D;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.pricer.rate.RatesProvider;

public class CalibrationValue extends Function1D<DoubleMatrix1D, DoubleMatrix1D> {

  private final CalibrationCalculator calculator;
  private final List<Trade> trades;
  private final int nbTrades;
  private final RatesProviderTemplate providerTemplate;

  public CalibrationValue(
      CalibrationCalculator calculator,
      List<Trade> trades,
      RatesProviderTemplate providerTemplate) {
    this.calculator = calculator;
    this.trades = trades;
    this.providerTemplate = providerTemplate;
    this.nbTrades = trades.size();
  }

  @Override
  public DoubleMatrix1D evaluate(DoubleMatrix1D x) {
    // Provider
    double[] data = x.getData();
    RatesProvider provider = providerTemplate.generate(data);
    // Results
    double[] measure = new double[nbTrades];
    for (int i = 0; i < nbTrades; i++) {
      measure[i] = calculator.value(trades.get(i), provider);
    }
    return new DoubleMatrix1D(measure);
  }

}
