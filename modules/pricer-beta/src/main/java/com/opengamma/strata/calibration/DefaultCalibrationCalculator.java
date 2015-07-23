/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.calibration;

import java.util.function.BiFunction;

import com.opengamma.strata.finance.rate.deposit.IborFixingDepositTrade;
import com.opengamma.strata.finance.rate.fra.FraTrade;
import com.opengamma.strata.finance.rate.swap.SwapTrade;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.rate.deposit.DiscountingIborFixingDepositProductPricer;
import com.opengamma.strata.pricer.rate.fra.DiscountingFraProductPricer;

public class DefaultCalibrationCalculator extends CalibrationCalculator {
  
  private DefaultCalibrationCalculator(
      BiFunction<IborFixingDepositTrade, RatesProvider, Double> fixingValue, 
      BiFunction<FraTrade, RatesProvider, Double> fraValue,
      BiFunction<SwapTrade, RatesProvider, Double> swapValue, 
      BiFunction<IborFixingDepositTrade, RatesProvider, PointSensitivities> fixingSensitivity,
      BiFunction<FraTrade, RatesProvider, PointSensitivities> fraSensitivity, 
      BiFunction<SwapTrade, RatesProvider, PointSensitivities> swapSensitivity) {
    super(fixingValue, fraValue, swapValue, fixingSensitivity, fraSensitivity, swapSensitivity);
  }

  public static final DefaultCalibrationCalculator DEFAULT = new DefaultCalibrationCalculator(
      (fixing, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.parSpread(fixing.getProduct(), p), 
      (fra, p) -> DiscountingFraProductPricer.DEFAULT.parSpread(fra.getProduct(), p),
      (swap, p) -> 0.0,
      (fixing, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.parSpreadSensitivity(fixing.getProduct(), p),  
      (fra, p) -> DiscountingFraProductPricer.DEFAULT.parSpreadCurveSensitivity(fra.getProduct(), p), // TODO: Change name in pricer
      (swap, p) -> PointSensitivities.empty());

}
