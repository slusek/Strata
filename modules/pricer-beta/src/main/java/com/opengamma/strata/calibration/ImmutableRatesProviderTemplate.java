/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;

/**
 * Template of a rates provider based on existing {@link ImmutableRatesProvider} and new curves templates.
 */
public class ImmutableRatesProviderTemplate implements RatesProviderTemplate {
  
  private final ImmutableRatesProvider knownProvider;
  
  private final List<CurveTemplate> curveTemplates;
  
  private final Map<CurveName, Currency> discountingNames;
  
  private final Map<CurveName, Index[]> forwardNames;

  public ImmutableRatesProviderTemplate(
      ImmutableRatesProvider knownProvider, 
      List<CurveTemplate> curveTemplates, 
      Map<CurveName, Currency> discountingMap, 
      Map<CurveName, Index[]> forwardMap) {
    this.knownProvider = knownProvider;
    this.curveTemplates = curveTemplates;
    this.discountingNames = discountingMap;
    this.forwardNames = forwardMap;
  }

  @Override
  public RatesProvider generate(double[] parameters) {
    int nbCurves = curveTemplates.size();
    // Curves generation
    int nbPreviousParameters = 0;
    Curve[] curves = new Curve[nbCurves];
    for(int i=0; i<nbCurves; i++) {
      int nbParameters = curveTemplates.get(i).getParameterCount();
      double[] curveParameters = new double[nbParameters];
      System.arraycopy(parameters, nbPreviousParameters, curveParameters, 0, nbParameters);
      curves[i] = curveTemplates.get(i).generate(curveParameters);
      nbPreviousParameters += nbParameters;
    }
    // Map for constructor
    Map<Currency, Curve> discountingCurves = new HashMap<>();
    Map<Index, Curve> indexCurves = new HashMap<>();
    for(int i=0; i<nbCurves; i++) {
      CurveName name = curveTemplates.get(i).getName();
      Currency ccy = discountingNames.get(name);
      if(ccy != null) {
        discountingCurves.put(ccy, curves[i]);
      }
      Index[] indices = forwardNames.get(name);
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
