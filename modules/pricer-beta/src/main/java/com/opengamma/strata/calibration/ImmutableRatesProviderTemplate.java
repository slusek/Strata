/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.config.CurveConfig;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;

/**
 * Template of a rates provider based on existing {@link ImmutableRatesProvider} and new curves templates.
 */
public class ImmutableRatesProviderTemplate implements RatesProviderTemplate {
  
  /** The known data (curves and FXMatrix). */
  private final ImmutableRatesProvider knownProvider;
  /** The curve templates for the new curves to be generated. */
  private final List<CurveConfig> curveConfig;
  /** The map between curve name and currencies for discounting. The map should contains all the curve in the template
   * list but may have more names that the curve template list. Only the curves in the templates list are created.*/
  private final Map<CurveName, Currency> discountingNames;
  /** The map between curve name and indices for forward. The map should contains all the curve in the template
   * list but may have more names that the curve template list. Only the curves in the templates list are created.*/
  private final Map<CurveName, Set<Index>> forwardNames;

  public ImmutableRatesProviderTemplate(
      ImmutableRatesProvider knownProvider, 
      List<CurveConfig> curveTemplates, 
      Map<CurveName, Currency> discountingMap, 
      Map<CurveName, Set<Index>> forwardMap) {
    this.knownProvider = knownProvider;
    this.curveConfig = curveTemplates;
    this.discountingNames = discountingMap;
    this.forwardNames = forwardMap;
  }

  @Override
  public RatesProvider generate(double[] parameters) {
    int nbCurves = curveConfig.size();
    // Curves generation
    int nbPreviousParameters = 0;
    Curve[] curves = new Curve[nbCurves];
    for(int i=0; i<nbCurves; i++) {
      int nbParameters = curveConfig.get(i).getParameterCount();
      double[] curveParameters = new double[nbParameters];
      System.arraycopy(parameters, nbPreviousParameters, curveParameters, 0, nbParameters);
      curves[i] = curveConfig.get(i).createCurve(knownProvider.getValuationDate(), curveParameters);
      nbPreviousParameters += nbParameters;
    }
    // Map for constructor
    Map<Currency, Curve> discountingCurves = new HashMap<>();
    Map<Index, Curve> indexCurves = new HashMap<>();
    for(int i=0; i<nbCurves; i++) {
      CurveName name = curveConfig.get(i).getName();
      Currency ccy = discountingNames.get(name);
      if(ccy != null) {
        discountingCurves.put(ccy, curves[i]);
      }
      Set<Index> indices = forwardNames.get(name);
      for(Index index:indices) {
        indexCurves.put(index, curves[i]);
      }
    }
    // Build from existing provider
    discountingCurves.putAll(knownProvider.getDiscountCurves());
    indexCurves.putAll(knownProvider.getIndexCurves());
    return knownProvider.toBuilder().discountCurves(discountingCurves).indexCurves(indexCurves).build();
  }

}
