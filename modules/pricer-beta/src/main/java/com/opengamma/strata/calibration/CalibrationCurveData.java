/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.util.List;

import com.opengamma.strata.finance.Trade;

/**
 * Data required to calibrate a curve.
 */
public class CalibrationCurveData {
  
  /** The template for the curve to calibrate*/
  private final CurveTemplate template;
  /** The trades to which the calibration will be done. */
  private final List<Trade> trades;
  /** The initial guess for the parameters of the curves. */
  private final List<Double> initialGuess;
  
  public CalibrationCurveData(CurveTemplate template, List<Trade> trades, List<Double> initialGuess) {
    this.template = template;
    this.trades = trades;
    this.initialGuess = initialGuess;
  }

  public CurveTemplate getTemplate() {
    return template;
  }

  public List<Trade> getTrades() {
    return trades;
  }

  public List<Double> getInitialGuess() {
    return initialGuess;
  }
    
}
