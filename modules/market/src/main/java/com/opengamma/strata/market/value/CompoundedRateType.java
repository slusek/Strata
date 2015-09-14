package com.opengamma.strata.market.value;

import org.joda.convert.FromString;

import com.opengamma.strata.collect.ArgChecker;

/**
 * A compounded rate type. 
 * <p>
 * Compounded rate is continuously compounded rate or periodically compounded rate.
 * The main application of this is z-spread computation under a specific way of compounding. See {@link DiscountFactors}.
 */
public enum CompoundedRateType {

  /**
   * Periodic compounding. 
   * <p>
   * In this case number of times the interest is compounded per year should be specified in addition.
   */
  PERIODIC,

  /**
   * Continuous compounding.
   */
  CONTINUOUS;

  //-------------------------------------------------------------------------
  /**
   * Obtains the type of compounded rate from a unique name.
   * 
   * @param uniqueName  the unique name
   * @return the compounded rate type
   * @throws IllegalArgumentException if the name is not known
   */
  @FromString
  public static CompoundedRateType of(String uniqueName) {
    ArgChecker.notNull(uniqueName, "uniqueName");
    return valueOf(uniqueName);
  }

}
