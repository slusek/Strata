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
import com.opengamma.strata.pricer.rate.swap.DiscountingSwapProductPricer;

/**
 * Default calibration calculator used for curve calibration.
 */
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

  /** The DEFAULT instance. Computing par spread for IborFixingDeposit, Fra and Swap by discounting. */
  public static final DefaultCalibrationCalculator DEFAULT = new DefaultCalibrationCalculator(
      (fixing, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.parSpread(fixing.getProduct(), p), 
      (fra, p) -> DiscountingFraProductPricer.DEFAULT.parSpread(fra.getProduct(), p),
      (swap, p) -> DiscountingSwapProductPricer.DEFAULT.parSpread(swap.getProduct(), p),
      (fixing, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.parSpreadSensitivity(fixing.getProduct(), p),  
      (fra, p) -> DiscountingFraProductPricer.DEFAULT.parSpreadCurveSensitivity(fra.getProduct(), p), // TODO: Change name in pricer
      (swap, p) -> DiscountingSwapProductPricer.DEFAULT.parSpreadSensitivity(swap.getProduct(), p).build());

}
