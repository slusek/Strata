/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.math.impl.statistics.leastsquare;

import java.util.List;
import java.util.Objects;

import com.opengamma.strata.math.impl.function.Function1D;
import com.opengamma.strata.math.impl.interpolation.BasisFunctionAggregation;
import com.opengamma.strata.math.impl.matrix.DoubleMatrix1D;
import com.opengamma.strata.math.impl.matrix.DoubleMatrix2D;

/**
 * 
 * @param <T> The type of the inputs to the basis functions
 */
public class GeneralizedLeastSquareResults<T> extends LeastSquareResults {

  private final Function1D<T, Double> _function;

  /**
   * Creates an instance
   * 
   * @param basisFunctions  the basis functions
   * @param chiSq  the chi-squared of the fit
   * @param parameters  the parameters that were fit
   * @param covariance  the covariance matrix of the result
   */
  public GeneralizedLeastSquareResults(
      List<Function1D<T, Double>> basisFunctions,
      double chiSq,
      DoubleMatrix1D parameters,
      DoubleMatrix2D covariance) {

    super(chiSq, parameters, covariance, null);

    _function = new BasisFunctionAggregation<>(basisFunctions, parameters.getData());
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the functions field.
   * @return the functions
   */
  public Function1D<T, Double> getFunction() {
    return _function;
  }

  @Override
  public int hashCode() {
    int prime = 31;
    int result = super.hashCode();
    result = prime * result + _function.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (!(obj instanceof GeneralizedLeastSquareResults)) {
      return false;
    }
    GeneralizedLeastSquareResults<?> other = (GeneralizedLeastSquareResults<?>) obj;
    if (!Objects.equals(_function, other._function)) {
      return false;
    }
    return true;
  }

}
