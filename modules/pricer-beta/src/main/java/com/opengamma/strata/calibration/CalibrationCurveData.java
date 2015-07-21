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
  
  private final CurveTemplate template;
  private final List<Trade> trades;
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
