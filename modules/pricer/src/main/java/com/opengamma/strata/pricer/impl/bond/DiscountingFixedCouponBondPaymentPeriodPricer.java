/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.impl.bond;

import com.opengamma.strata.finance.rate.bond.FixedCouponBondPaymentPeriod;
import com.opengamma.strata.market.sensitivity.IssuerCurveZeroRateSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.ZeroRateSensitivity;
import com.opengamma.strata.market.value.IssuerCurveDiscountFactors;

public class DiscountingFixedCouponBondPaymentPeriodPricer {

  public static final DiscountingFixedCouponBondPaymentPeriodPricer DEFAULT =
      new DiscountingFixedCouponBondPaymentPeriodPricer();

  public DiscountingFixedCouponBondPaymentPeriodPricer() {
  }

  public double presentValue(FixedCouponBondPaymentPeriod period, IssuerCurveDiscountFactors discountFactor) {
    double df = discountFactor.discountFactor(period.getPaymentDate());
    return period.getFixedRate() * period.getNotional() * df;
  }

  public double presentValue(FixedCouponBondPaymentPeriod period, IssuerCurveDiscountFactors discountFactor,
      double zSpread, boolean periodic, int periodPerYear) {
    double df = discountFactor.getDiscountFactors()
        .discountFactorWithSpread(period.getPaymentDate(), zSpread, periodic, periodPerYear);
    return period.getFixedRate() * period.getNotional() * df;
  }

  //  public double presentValue(FixedCouponBondPaymentPeriod period, StandardId standardId,
  //      LegalEntityDiscountingProvider provider) {
  //    IssuerCurveDiscountFactors discountFactor = provider.issuerCurveDiscountFactors(standardId, period.getCurrency());
  //    return presentValue(period, discountFactor.getDiscountFactors());
  //  }

  //  public PointSensitivityBuilder presentValueSensitivity(FixedCouponBondPaymentPeriod period, StandardId standardId,
  //      LegalEntityDiscountingProvider provider) {
  //    IssuerCurveDiscountFactors dsc = provider.issuerCurveDiscountFactors(standardId, period.getCurrency());
  //    PointSensitivityBuilder dscSensi = dsc.zeroRatePointSensitivity(period.getPaymentDate(), dsc.getCurrency());
  //    return dscSensi.multipliedBy(period.getFixedRate() * period.getNotional());
  //  }

  //-------------------------------------------------------------------------
  public PointSensitivityBuilder presentValueSensitivity(FixedCouponBondPaymentPeriod period,
      IssuerCurveDiscountFactors discountFactor) {
    PointSensitivityBuilder dscSensi = discountFactor.zeroRatePointSensitivity(period.getPaymentDate());
    return dscSensi.multipliedBy(period.getFixedRate() * period.getNotional());
  }

  public PointSensitivityBuilder presentValueSensitivity(FixedCouponBondPaymentPeriod period,
      IssuerCurveDiscountFactors discountFactor, double zSpread, boolean periodic, int periodPerYear) {
    ZeroRateSensitivity zeroSensi = discountFactor.getDiscountFactors().zeroRatePointSensitivityWithSpread(
        period.getPaymentDate(), zSpread, periodic, periodPerYear);
    IssuerCurveZeroRateSensitivity dscSensi =
        IssuerCurveZeroRateSensitivity.of(zeroSensi, discountFactor.getLegalEntityGroup());
    return dscSensi.multipliedBy(period.getFixedRate() * period.getNotional());
  }
}
