/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.impl.bond;

import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.finance.rate.bond.FixedCouponBondPaymentPeriod;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.IssuerCurveDiscountFactors;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;

public class DiscountingFixedCouponBondPaymentPeriodPricer {

  public static final DiscountingFixedCouponBondPaymentPeriodPricer DEFAULT =
      new DiscountingFixedCouponBondPaymentPeriodPricer();

  public DiscountingFixedCouponBondPaymentPeriodPricer() {
  }

  public double presentValue(FixedCouponBondPaymentPeriod period, DiscountFactors discountFactor, double zSpread,
      boolean periodic, int periodPerYear) {
    double df = discountFactor.discountFactorWithSpread(period.getPaymentDate(), zSpread, periodic, periodPerYear);
    return period.getFixedRate() * period.getNotional() * df;
  }

  public double presentValue(FixedCouponBondPaymentPeriod period, DiscountFactors discountFactor) {
    double df = discountFactor.discountFactor(period.getPaymentDate());
    return period.getFixedRate() * period.getNotional() * df;
  }

  public double presentValue(FixedCouponBondPaymentPeriod period, StandardId standardId,
      LegalEntityDiscountingProvider provider) {
    IssuerCurveDiscountFactors discountFactor = provider.issuerCurveDiscountFactors(standardId, period.getCurrency());
    return presentValue(period, discountFactor.getDiscountFactors());
  }

  public PointSensitivityBuilder presentValueSensitivity(FixedCouponBondPaymentPeriod period, StandardId standardId,
      LegalEntityDiscountingProvider provider) {
    IssuerCurveDiscountFactors dsc = provider.issuerCurveDiscountFactors(standardId, period.getCurrency());
    PointSensitivityBuilder dscSensi = dsc.zeroRatePointSensitivity(period.getPaymentDate(), dsc.getCurrency());
    return dscSensi.multipliedBy(period.getFixedRate() * period.getNotional());
  }
}
