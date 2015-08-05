package com.opengamma.strata.pricer.rate.bond;

import java.time.LocalDate;
import java.util.function.BiFunction;

import com.opengamma.analytics.convention.daycount.AccruedInterestCalculator;
import com.opengamma.analytics.convention.daycount.DayCount;
import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.rootfinding.BracketRoot;
import com.opengamma.analytics.math.rootfinding.BrentSingleRootFinder;
import com.opengamma.analytics.math.rootfinding.RealSingleRootFinder;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.finance.rate.FixedRateObservation;
import com.opengamma.strata.finance.rate.bond.Bond;
import com.opengamma.strata.finance.rate.swap.PaymentEvent;
import com.opengamma.strata.finance.rate.swap.PaymentPeriod;
import com.opengamma.strata.finance.rate.swap.RatePaymentPeriod;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.rate.DecoratedRatesProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.rate.swap.PaymentEventPricer;
import com.opengamma.strata.pricer.rate.swap.PaymentPeriodPricer;

public class DiscountingBondProductPricer {

  public static final DiscountingBondProductPricer DEFAULT = new DiscountingBondProductPricer(
      PaymentPeriodPricer.instance(),
      PaymentEventPricer.instance());

  /**
   * Pricer for {@link PaymentPeriod}.
   */
  private final PaymentPeriodPricer<PaymentPeriod> paymentPeriodPricer;
  /**
   * Pricer for {@link PaymentEvent}.
   */
  private final PaymentEventPricer<PaymentEvent> paymentEventPricer;

  /**
   * The root bracket used for yield finding.
   */
  private static final BracketRoot BRACKETER = new BracketRoot();
  /**
   * The root finder used for yield finding.
   */
  private static final RealSingleRootFinder ROOT_FINDER = new BrentSingleRootFinder();
  /**
   * Brackets a root
   */
  private static final BracketRoot ROOT_BRACKETER = new BracketRoot();

  public DiscountingBondProductPricer(
      PaymentPeriodPricer<PaymentPeriod> paymentPeriodPricer,
      PaymentEventPricer<PaymentEvent> paymentEventPricer) {
    this.paymentPeriodPricer = ArgChecker.notNull(paymentPeriodPricer, "paymentPeriodPricer");
    this.paymentEventPricer = ArgChecker.notNull(paymentEventPricer, "paymentEventPricer");
  }

  // TODO some methods are fixed coupon bond specific.

  //-------------------------------------------------------------------------
  public CurrencyAmount presentValue(Bond bond, RatesProvider provider) {
    DecoratedRatesProvider decoratedProvider =
        DecoratedRatesProvider.of(provider, bond.getCurrency(), bond.getLegalEntity());
    double pv = presentValuePeriodsInternal(bond, decoratedProvider) +
        presentValueEventsInternal(bond, decoratedProvider);
    return CurrencyAmount.of(bond.getCurrency(), pv);
  }

  public double dirtyPriceFromCurves(Bond bond, RatesProvider provider) {
    CurrencyAmount pv = presentValue(bond, provider);
    double df = provider.discountFactor(bond.getCurrency(), bond.getSettlementDate());
    double notional = ((RatePaymentPeriod) bond.getPaymentPeriods().get(0)).getNotional();
    return pv.getAmount() / df / notional;
  }

  public double dirtyPriceFromCleanPrice(Bond bond, double cleanPrice) {
    double notional = ((RatePaymentPeriod) bond.getPaymentPeriods().get(0)).getNotional();
    double accruedInterest = accruedInterest(bond);
    return cleanPrice + accruedInterest / notional;
  }

  public CurrencyAmount presentValueFromZSpread(Bond bond, RatesProvider provider, final double zSpread) { // TODO how to add shift to curve?
  //    IssuerProviderInterface issuerShifted = new IssuerProviderIssuerDecoratedSpreadContinuous(issuerMulticurves,
  //        bond.getIssuerEntity(), zSpread);
    RatesProvider providerShifted = null;
    return presentValue(bond, providerShifted);
  }

  public CurrencyAmount presentValueFromZSpread(Bond bond, RatesProvider provider, double zSpread, boolean periodic,  // TODO how to add shift to curve?
      int periodPerYear) {
    if (periodic) {
      //      IssuerProviderInterface issuerShifted = new IssuerProviderIssuerDecoratedSpreadPeriodic(issuerMulticurves,
      //          bond.getIssuerEntity(), zSpread, periodPerYear);
      RatesProvider providerShifted = null;
      return presentValue(bond, providerShifted);
    }
    return presentValueFromZSpread(bond, provider, zSpread);
  }

  public double zSpreadFromCurvesAndPV(Bond bond, RatesProvider provider, CurrencyAmount pv, boolean periodic,
      int periodPerYear) {
    Currency ccy = bond.getCurrency();

    final Function1D<Double, Double> residual = new Function1D<Double, Double>() {
      @Override
      public Double evaluate(final Double z) {
        return presentValueFromZSpread(bond, provider, z, periodic, periodPerYear).getAmount() -
            pv.getAmount();
      }
    };

    double[] range = ROOT_BRACKETER.getBracketedPoints(residual, -0.01, 0.01); // Starting range is [-1%, 1%]
    return ROOT_FINDER.getRoot(residual, range[0], range[1]);
  }

  //-------------------------------------------------------------------------
  public PointSensitivityBuilder presentValueSensitivity(
      Bond bond,
      RatesProvider provider) {
    DecoratedRatesProvider decoratedProvider =
        DecoratedRatesProvider.of(provider, bond.getCurrency(), bond.getLegalEntity());

    PointSensitivityBuilder builder = PointSensitivityBuilder.none();
    BiFunction<PaymentPeriod, RatesProvider, PointSensitivityBuilder> periodFn = paymentPeriodPricer::presentValueSensitivity;
    for (PaymentPeriod period : bond.getPaymentPeriods()) {
      if (!period.getPaymentDate().isBefore(decoratedProvider.getValuationDate())) {
        builder = builder.combinedWith(periodFn.apply(period, decoratedProvider));
      }
    }
    BiFunction<PaymentEvent, RatesProvider, PointSensitivityBuilder> eventFn = paymentEventPricer::presentValueSensitivity;
    for (PaymentEvent event : bond.getPaymentEvents()) {
      if (!event.getPaymentDate().isBefore(decoratedProvider.getValuationDate())) {
        builder = builder.combinedWith(eventFn.apply(event, decoratedProvider));
      }
    }
    return builder;
  }

  //-------------------------------------------------------------------------
  private double presentValuePeriodsInternal(Bond bond, RatesProvider provider) {
    double total = 0d;
    // TODO replacement of discouting curve, see BondSecurityDiscountingMethod
    for (PaymentPeriod period : bond.getPaymentPeriods()) {
      if (!period.getPaymentDate().isBefore(provider.getValuationDate())) {
        total += paymentPeriodPricer.presentValue(period, provider);
      }
    }
    return total;
  }

  private double presentValueEventsInternal(Bond bond, RatesProvider provider) {
    double total = 0d;
    // TODO replacement of discouting curve, see BondSecurityDiscountingMethod
    for (PaymentEvent event : bond.getPaymentEvents()) {
      if (!event.getPaymentDate().isBefore(provider.getValuationDate())) {
        total += paymentEventPricer.presentValue(event, provider);
      }
    }
    return total;
  }

  public double accruedInterest(Bond bond) { // TODO settlement date is stored somewhere else? or computed from valuation date in ratesProvider? how?
    int nbCoupon = bond.getPaymentPeriods().size();
    double result = 0;
    int couponIndex = 0;
    for (int loopcpn = 0; loopcpn < nbCoupon; loopcpn++) {
      if (bond.getPaymentPeriods().get(loopcpn).getEndDate().isAfter(bond.getSettlementDate())) { // TODO acc end date or payment date?
        couponIndex = loopcpn;
        break;
      }
    }
    RatePaymentPeriod periodIndex = (RatePaymentPeriod) bond.getPaymentPeriods().get(couponIndex);

    FixedRateObservation accPeriod = (FixedRateObservation) periodIndex.getAccrualPeriods().get(0).getRateObservation();
    LocalDate previousAccrualDate = periodIndex.getStartDate();
    LocalDate nextAccrualDate = periodIndex.getEndDate();

    DayCount dayCount = null; // define "IssuerIndex" ? holding these information?
    boolean isEOM = true;
    int exCouponDays = 0;

    double accruedInterest = AccruedInterestCalculator.getAccruedInterest(dayCount, couponIndex, nbCoupon, // TODO continue to use AccruedInterestCalculator? converter of DayCount
        previousAccrualDate, bond.getSettlementDate(), nextAccrualDate, accPeriod.getRate(), 1, isEOM) *
        periodIndex.getNotional();
    if (exCouponDays != 0 && nextAccrualDate.minusDays(exCouponDays).isBefore(bond.getSettlementDate())) {
      result = accruedInterest - periodIndex.getNotional();
    } else {
      result = accruedInterest;
    }
    return result;
  }
}
