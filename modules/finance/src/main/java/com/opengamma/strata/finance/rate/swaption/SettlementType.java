package com.opengamma.strata.finance.rate.swaption;

/**
 * Settlement types for {@code Swaption}. 
 */
public enum SettlementType {
  /**
   * cash settlement
   * <p>
   * Cash amount is paid (by the short party to the long party) at the exercise date (or more exactly 
   * at the spot lag after the exercise and the actual swap is not entered into.
   */
  CASH,
  /**
   * physical delivery. 
   * <p>
   * The two parties enter into actual interest rate swap (the underlying swap) at the expiry date of the option. 
   */
  PHYSICAL,
}
