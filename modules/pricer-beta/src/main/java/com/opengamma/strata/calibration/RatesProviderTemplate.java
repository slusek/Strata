/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import com.opengamma.strata.pricer.rate.RatesProvider;

/**
 * A template to generate a {@link RatesProvider} from a set of parameters
 */
public interface RatesProviderTemplate {
  
  /**
   * Generates a rates provider from a set of parameters.
   * 
   * @param parameters  the parameters describing the provider
   * @return the provider
   */
  public abstract RatesProvider generate(double[] parameters);

}
