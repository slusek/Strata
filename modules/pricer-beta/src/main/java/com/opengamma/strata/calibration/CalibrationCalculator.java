/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.util.List;
import java.util.function.BiFunction;

import com.google.common.collect.ImmutableList;
import com.opengamma.analytics.util.ArrayUtils;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.finance.rate.deposit.IborFixingDepositTrade;
import com.opengamma.strata.finance.rate.fra.FraTrade;
import com.opengamma.strata.finance.rate.swap.SwapTrade;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.CurveUnitParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveUnitParameterSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.rate.RatesProvider;

/**
 * The calculator of the measure on which the curve calibration is based. 
 * <p>
 * The most often used measures are par spread and converted present value.
 */
public class CalibrationCalculator {

  /** The measure for {@link IborFixingDepositTrade}. */
  BiFunction<IborFixingDepositTrade, RatesProvider, Double> fixingValue;
  /** The measure for {@link FraTrade}. */
  BiFunction<FraTrade, RatesProvider, Double> fraValue;
  /** The measure for {@link SwapTrade}. */
  BiFunction<SwapTrade, RatesProvider, Double> swapValue;

  /** The measure for {@link IborFixingDepositTrade}. */
  BiFunction<IborFixingDepositTrade, RatesProvider, PointSensitivities> fixingSensitivity;
  /** The measure for {@link FraTrade}. */
  BiFunction<FraTrade, RatesProvider, PointSensitivities> fraSensitivity;
  /** The measure for {@link SwapTrade}. */
  BiFunction<SwapTrade, RatesProvider, PointSensitivities> swapSensitivity;

  public double value(Trade trade, RatesProvider provider) {
    if (trade instanceof IborFixingDepositTrade) {
      return fixingValue.apply((IborFixingDepositTrade) trade, provider);
    }
    if (trade instanceof FraTrade) {
      return fraValue.apply((FraTrade) trade, provider);
    }
    if (trade instanceof SwapTrade) {
      return swapValue.apply((SwapTrade) trade, provider);
    }
    throw new IllegalArgumentException("Trade type " + trade.getClass() + " not supported for calibration");
  }

  /**
   * Calculate the sensitivity with respect to the curve provider as a long vector. The vector is composed 
   * of the curve sensitivities concatenated.
   * @param trade  the trade
   * @param provider  the rates provider
   * @return the sensitivity
   */
  public double[] derivative(Trade trade, RatesProvider provider, List<Pair<CurveName, Integer>> curveOrder) {
    PointSensitivities pts = null;
    if (trade instanceof IborFixingDepositTrade) {
      pts = fixingSensitivity.apply((IborFixingDepositTrade) trade, provider);
    } else if (trade instanceof FraTrade) {
      pts = fraSensitivity.apply((FraTrade) trade, provider);
    } else if (trade instanceof SwapTrade) {
      pts = swapSensitivity.apply((SwapTrade) trade, provider);
    } else {
      throw new IllegalArgumentException("Trade type " + trade.getClass() + " not supported for calibration");
    }
    CurveCurrencyParameterSensitivities ps = provider.curveParameterSensitivity(pts);
    // Ignore currency
    ImmutableList<CurveCurrencyParameterSensitivity> ps2 = ps.getSensitivities();
    CurveUnitParameterSensitivities ups = CurveUnitParameterSensitivities.empty();
    for (CurveCurrencyParameterSensitivity ccps : ps2) {
      ups.combinedWith(CurveUnitParameterSensitivity.of(ccps.getMetadata(), ccps.getSensitivity()));
    }
    // Expand to vector
    double[] result = new double[0];
    for (Pair<CurveName, Integer> nameNb : curveOrder) {
      CurveUnitParameterSensitivity s = ups.getSensitivity(nameNb.getFirst());
      if (s != null) {
        result = ArrayUtils.addAll(result, s.getSensitivity());
      } else {
        result = ArrayUtils.addAll(result, new double[nameNb.getSecond()]);
      }
    }
    return result;
  }

}
