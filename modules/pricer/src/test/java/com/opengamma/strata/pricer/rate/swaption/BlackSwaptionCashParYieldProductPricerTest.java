/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate.swaption;

import static com.opengamma.strata.basics.LongShort.LONG;
import static com.opengamma.strata.basics.PayReceive.PAY;
import static com.opengamma.strata.basics.PayReceive.RECEIVE;
import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.strata.basics.date.DayCounts.THIRTY_U_360;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.basics.schedule.Frequency.P12M;
import static com.opengamma.strata.basics.schedule.Frequency.P6M;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendars;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.finance.rate.swap.FixedRateCalculation;
import com.opengamma.strata.finance.rate.swap.IborRateCalculation;
import com.opengamma.strata.finance.rate.swap.NotionalSchedule;
import com.opengamma.strata.finance.rate.swap.PaymentSchedule;
import com.opengamma.strata.finance.rate.swap.RateCalculationSwapLeg;
import com.opengamma.strata.finance.rate.swap.Swap;
import com.opengamma.strata.finance.rate.swap.SwapLeg;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConventions;
import com.opengamma.strata.finance.rate.swaption.CashSettlement;
import com.opengamma.strata.finance.rate.swaption.CashSettlementMethod;
import com.opengamma.strata.finance.rate.swaption.Swaption;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.surface.DefaultSurfaceMetadata;
import com.opengamma.strata.market.surface.InterpolatedNodalSurface;
import com.opengamma.strata.market.surface.NodalSurface;
import com.opengamma.strata.market.surface.SurfaceMetadata;
import com.opengamma.strata.market.surface.SurfaceName;
import com.opengamma.strata.market.value.ValueType;
import com.opengamma.strata.math.impl.interpolation.CombinedInterpolatorExtrapolatorFactory;
import com.opengamma.strata.math.impl.interpolation.GridInterpolator2D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1DFactory;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.swap.DiscountingSwapProductPricer;

/**
 * Test {@link BlackSwaptionCashParYieldProductPricer}.
 */
@Test
public class BlackSwaptionCashParYieldProductPricerTest {
  private static final LocalDate VALUATION = LocalDate.of(2012, 1, 10);
  // curve
  private static final CurveInterpolator INTERPOLATOR = Interpolator1DFactory.LINEAR_INSTANCE;
  private static final double[] DSC_TIME = new double[] {0.0, 0.5, 1.0, 2.0, 5.0, 10.0 };
  private static final double[] DSC_RATE = new double[] {0.0150, 0.0125, 0.0150, 0.0175, 0.0150, 0.0150 };
  private static final CurveName DSC_NAME = CurveName.of("EUR Dsc");
  private static final CurveMetadata META_DSC = Curves.zeroRates(DSC_NAME, ACT_ACT_ISDA);
  private static final InterpolatedNodalCurve DSC_CURVE = InterpolatedNodalCurve.of(META_DSC, DSC_TIME, DSC_RATE, INTERPOLATOR); 
  private static final double[] FWD6_TIME = new double[] {0.0, 0.5, 1.0, 2.0, 5.0, 10.0 };
  private static final double[] FWD6_RATE = new double[] {0.0150, 0.0125, 0.0150, 0.0175, 0.0150, 0.0150 };
  private static final CurveName FWD6_NAME = CurveName.of("EUR EURIBOR 6M");
  private static final CurveMetadata META_FWD6 = Curves.zeroRates(FWD6_NAME, ACT_ACT_ISDA);
  private static final InterpolatedNodalCurve FWD6_CURVE = InterpolatedNodalCurve.of(META_FWD6, FWD6_TIME, FWD6_RATE, INTERPOLATOR); 
  private static final ImmutableRatesProvider RATE_PROVIDER = ImmutableRatesProvider.builder()
      .discountCurves(ImmutableMap.of(EUR, DSC_CURVE))
      .indexCurves(ImmutableMap.of(EUR_EURIBOR_6M, FWD6_CURVE))
      .fxMatrix(FxMatrix.empty())
      .valuationDate(VALUATION)
      .build();
  // surface
  private static final Interpolator1D LINEAR_FLAT = CombinedInterpolatorExtrapolatorFactory.getInterpolator(
      Interpolator1DFactory.LINEAR,
      Interpolator1DFactory.FLAT_EXTRAPOLATOR,
      Interpolator1DFactory.FLAT_EXTRAPOLATOR);
  private static final GridInterpolator2D INTERPOLATOR_2D = new GridInterpolator2D(LINEAR_FLAT, LINEAR_FLAT);
  private static final double[] EXPIRY = new double[] {0.5, 1.0, 5.0, 0.5, 1.0, 5.0 };
  private static final double[] TENOR = new double[] {2, 2, 2, 10, 10, 10 };
  private static final double[] VOL = new double[] {0.35, 0.34, 0.25, 0.30, 0.25, 0.20 };
  private static final SurfaceMetadata METADATA = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.YEAR_FRACTION)
      .zValueType(ValueType.VOLATILITY)
      .surfaceName(SurfaceName.of("Black Vol"))
      .build();
  private static final NodalSurface SURFACE = InterpolatedNodalSurface.of(METADATA, EXPIRY, TENOR, VOL, INTERPOLATOR_2D);
  private static final FixedIborSwapConvention SWAP_CONVENTION = FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
  private static final BlackVolatilityExpiryTenorSwaptionProvider VOL_PROVIDER =
      BlackVolatilityExpiryTenorSwaptionProvider.of(SURFACE, SWAP_CONVENTION, ACT_ACT_ISDA, VALUATION);
  // underlying swap and swaption
  private static final HolidayCalendar CALENDAR = HolidayCalendars.SAT_SUN;
  private static final BusinessDayAdjustment BDA_MF = BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CALENDAR);
  private static final LocalDate MATURITY = BDA_MF.adjust(VALUATION.plusMonths(26));
  private static final LocalDate SETTLE = BDA_MF.adjust(CALENDAR.shift(MATURITY, 2));
  private static final double NOTIONAL = 123456789.0;
  private static final LocalDate END = SETTLE.plusYears(5);
  private static final double RATE = 0.02;
  private static final SwapLeg FIXED_LEG = RateCalculationSwapLeg.builder()
      .payReceive(RECEIVE)
      .accrualSchedule(PeriodicSchedule.builder()
          .startDate(SETTLE)
          .endDate(END)
          .frequency(P12M)
          .businessDayAdjustment(BDA_MF)
          .stubConvention(StubConvention.SHORT_FINAL)
          .build())
      .paymentSchedule(PaymentSchedule.builder()
          .paymentFrequency(P12M)
          .paymentDateOffset(DaysAdjustment.NONE)
          .build())
      .notionalSchedule(NotionalSchedule.of(EUR, NOTIONAL))
      .calculation(FixedRateCalculation.builder()
          .dayCount(THIRTY_U_360)
          .rate(ValueSchedule.of(RATE))
          .build())
      .build();
  private static final SwapLeg IBOR_LEG = RateCalculationSwapLeg.builder()
      .payReceive(PAY)
      .accrualSchedule(PeriodicSchedule.builder()
          .startDate(SETTLE)
          .endDate(END)
          .frequency(P6M)
          .businessDayAdjustment(BDA_MF)
          .stubConvention(StubConvention.SHORT_FINAL)
          .build())
      .paymentSchedule(PaymentSchedule.builder()
          .paymentFrequency(P6M)
          .paymentDateOffset(DaysAdjustment.NONE)
          .build())
      .notionalSchedule(NotionalSchedule.of(EUR, NOTIONAL))
      .calculation(IborRateCalculation.builder()
          .index(EUR_EURIBOR_6M)
          .fixingDateOffset(DaysAdjustment.ofBusinessDays(-2, CALENDAR, BDA_MF))
          .build())
      .build();
  private static final Swap SWAP = Swap.of(FIXED_LEG, IBOR_LEG);
  private static final Swaption SWAPTION = Swaption
      .builder()
      .expiryDate(AdjustableDate.of(MATURITY, BDA_MF))
      .expiryTime(LocalTime.NOON)
      .expiryZone(ZoneOffset.UTC)
      .swaptionSettlement(
          CashSettlement.builder()
              .cashSettlementMethod(CashSettlementMethod.PAR_YIELD)
              .settlementDate(SETTLE).build())
      .longShort(LONG)
      .underlying(SWAP)
      .build();
  // test parameters
  private static final double TOL = 1.0e-12;
  private static final double FD_EPS = 1.0e-7;
  // pricer
  private static final BlackSwaptionCashParYieldProductPricer PRICER = BlackSwaptionCashParYieldProductPricer.DEFAULT;
  private static final DiscountingSwapProductPricer SWAP_PRICER = DiscountingSwapProductPricer.DEFAULT;

  public void test_presentValue() {
    CurrencyAmount computed = PRICER.presentValue(SWAPTION, RATE_PROVIDER, VOL_PROVIDER);
    double forward = SWAP_PRICER.parRate(SWAP, RATE_PROVIDER);
    double annuityCash = SWAP_PRICER.getLegPricer().annuityCash(FIXED_LEG, forward);
    double expiry = VOL_PROVIDER.relativeTime(SWAPTION.getExpiryDateTime());
    double tenor = VOL_PROVIDER.tenor(SETTLE, END);
    double volatility = SURFACE.zValue(expiry, tenor);
    double settle = ACT_ACT_ISDA.relativeYearFraction(VALUATION, SETTLE);
    double df = Math.exp(-DSC_CURVE.yValue(settle) * settle);
    double expected = df * annuityCash * BlackFormulaRepository.price(forward, RATE, expiry, volatility, false);
    assertEquals(computed.getAmount(), expected, NOTIONAL * TOL);
  }

  //-------------------------------------------------------------------------
  public void pv() {
    CurrencyAmount pv = PRICER.presentValue(SWAPTION, RATE_PROVIDER, VOL_PROVIDER);
    assertEquals(pv.getAmount(), 3823688.253812721, NOTIONAL * TOL); // 2.x
  }

  @Test(enabled = false)
  public void pvCurveSensi() {
    PointSensitivityBuilder point =
        PRICER.presentValueSensitivityStickyStrike(SWAPTION, RATE_PROVIDER, VOL_PROVIDER);
    CurveCurrencyParameterSensitivities computed = RATE_PROVIDER.curveParameterSensitivity(point.build());
    computed.getSensitivity(DSC_NAME, EUR).getSensitivity();

    double[] dscSensi = new double[] {0.0, 0.0, 0.0, -7143525.908886078, -1749520.4110068753, -719115.4683096837 };
    //    double[] dscSensi = new double[] {0d, 0d, 0d, 681318.093005985, -1287702.9501952156, -719115.4683096837 };
    double[] fwdSensi = new double[] {0d, 0d, 0d, 1.7943318714062232E8, -3.4987983718159467E8, -2.6516758066404995E8 };
    CurveCurrencyParameterSensitivity dsc = CurveCurrencyParameterSensitivity.of(META_DSC, EUR, dscSensi);
    CurveCurrencyParameterSensitivity fwd = CurveCurrencyParameterSensitivity.of(META_FWD6, EUR, fwdSensi);
    CurveCurrencyParameterSensitivities expected = CurveCurrencyParameterSensitivities.of(ImmutableList.of(dsc, fwd));

    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * TOL * 1000d));
  }
}
