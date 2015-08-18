/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.util.List;

import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.market.curve.config.CurveConfig;

/**
 * Data required to calibrate a curve.
 */
public class CalibrationCurveData {
  
  /** The template for the curve to calibrate*/
  private final CurveConfig config;
  /** The trades to which the calibration will be done. */
  private final List<Trade> trades;
  /** The initial guess for the parameters of the curves. */
  private final List<Double> initialGuess;
  
  public CalibrationCurveData(CurveConfig config, List<Trade> trades, List<Double> initialGuess) {
    this.config = config;
    this.trades = trades;
    this.initialGuess = initialGuess;
  }

  public CurveConfig getConfig() {
    return config;
  }

  public List<Trade> getTrades() {
    return trades;
  }

  public List<Double> getInitialGuess() {
    return initialGuess;
  }
  
}
