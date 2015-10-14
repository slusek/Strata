/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.math.impl.rootfinding.newton;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.math.impl.linearalgebra.Decomposition;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionResult;
import com.opengamma.strata.math.impl.matrix.DoubleMatrix1D;
import com.opengamma.strata.math.impl.matrix.DoubleMatrix2D;

/**
 * 
 */
public class JacobianDirectionFunction implements NewtonRootFinderDirectionFunction {

  private final Decomposition<?> _decomposition;

  public JacobianDirectionFunction(Decomposition<?> decomposition) {
    ArgChecker.notNull(decomposition, "decomposition");
    _decomposition = decomposition;
  }

  @Override
  public DoubleMatrix1D getDirection(DoubleMatrix2D estimate, DoubleMatrix1D y) {
    ArgChecker.notNull(estimate, "estimate");
    ArgChecker.notNull(y, "y");
    DecompositionResult result = _decomposition.evaluate(estimate);
    return result.solve(y);
  }

}
