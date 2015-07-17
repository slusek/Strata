/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import com.opengamma.strata.market.curve.Curve;

/**
 * A template to generate a {@link Curve} from a set of parameters
 */
public interface CurveTemplate {
  
  /**
   * Generates a curve from a set of parameters.
   * 
   * @param parameters  the parameters describing the curve
   * @return the curve
   */
  public abstract Curve generate(double[] parameters);
  
  /**
   * Gets the number of parameters in the curve.
   * <p>
   * This returns the number of parameters in the curve.
   * 
   * @return the number of parameters
   */
  public abstract int getParameterCount();

}
