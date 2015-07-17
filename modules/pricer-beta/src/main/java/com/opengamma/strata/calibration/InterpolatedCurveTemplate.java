/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import com.opengamma.strata.basics.interpolator.CurveExtrapolator;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;

/**
 * Generator of a curve based on interpolation between a number of nodal points.
 */
public class InterpolatedCurveTemplate implements CurveTemplate {

  /**
   * The curve metadata.
   * <p>
   * The metadata includes an optional list of parameter metadata.
   * If present, the size of the parameter metadata list will match the number of parameters of this curve.
   */
  private final CurveMetadata metadata;
  /**
   * The array of x-values, one for each point.
   * <p>
   * This array will contains at least two elements and be of the same length as y-values.
   */
  private final double[] xValues;
  /**
   * The extrapolator for x-values on the left, defaulted to 'Flat".
   * This is used for x-values smaller than the smallest known x-value.
   */
  private final CurveExtrapolator extrapolatorLeft;
  /**
   * The interpolator.
   * This is used for x-values between the smallest and largest known x-value.
   */
  private final CurveInterpolator interpolator;
  /**
   * The extrapolator for x-values on the right, defaulted to 'Flat".
   * This is used for x-values larger than the largest known x-value.
   */
  private final CurveExtrapolator extrapolatorRight;
  
  

  public InterpolatedCurveTemplate(
      CurveMetadata metadata, 
      double[] xValues, 
      CurveExtrapolator extrapolatorLeft, 
      CurveInterpolator interpolator, 
      CurveExtrapolator extrapolatorRight) {
    this.metadata = metadata;
    this.xValues = xValues;
    this.extrapolatorLeft = extrapolatorLeft;
    this.interpolator = interpolator;
    this.extrapolatorRight = extrapolatorRight;
  }

  @Override
  public Curve generate(double[] parameters) {
    return InterpolatedNodalCurve.builder()
        .metadata(metadata)
        .xValues(xValues)
        .yValues(parameters)
        .extrapolatorLeft(extrapolatorLeft)
        .interpolator(interpolator)
        .extrapolatorRight(extrapolatorRight).build();
  }

  @Override
  public int getParameterCount() {
    return xValues.length;
  }

}
