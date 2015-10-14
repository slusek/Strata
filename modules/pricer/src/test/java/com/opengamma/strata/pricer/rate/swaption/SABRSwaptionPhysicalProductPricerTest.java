package com.opengamma.strata.pricer.rate.swaption;

import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Doubles;
import com.opengamma.strata.basics.BuySell;
import com.opengamma.strata.basics.LongShort;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.finance.rate.swap.Swap;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConventions;
import com.opengamma.strata.finance.rate.swaption.PhysicalSettlement;
import com.opengamma.strata.finance.rate.swaption.Swaption;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.SurfaceCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.SurfaceCurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.SwaptionSABRSensitivity;
import com.opengamma.strata.market.surface.DefaultSurfaceMetadata;
import com.opengamma.strata.market.surface.InterpolatedNodalSurface;
import com.opengamma.strata.market.surface.SurfaceMetadata;
import com.opengamma.strata.market.surface.SurfaceName;
import com.opengamma.strata.market.surface.SurfaceParameterMetadata;
import com.opengamma.strata.market.surface.SwaptionVolatilitySurfaceExpiryTenorNodeMetadata;
import com.opengamma.strata.market.value.ValueType;
import com.opengamma.strata.math.impl.interpolation.CombinedInterpolatorExtrapolatorFactory;
import com.opengamma.strata.math.impl.interpolation.GridInterpolator2D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1DFactory;
import com.opengamma.strata.pricer.impl.option.SABRInterestRateParameters;
import com.opengamma.strata.pricer.impl.volatility.smile.function.SABRHaganVolatilityFunctionProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.swap.DiscountingSwapProductPricer;

@Test
public class SABRSwaptionPhysicalProductPricerTest {
  private static final LocalDate VALUATION = LocalDate.of(2014, 1, 22);
  private static final FixedIborSwapConvention SWAP_CONVENTION = FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M;

  private static final CurveInterpolator INTERPOLATOR = Interpolator1DFactory.LINEAR_INSTANCE;

  private static final double[] TIME_DSC = new double[] {0.0027397260273972603, 0.005479452054794521,
    0.0958904109589041, 0.1726027397260274, 0.26301369863013696, 0.5123287671232877, 0.7643835616438356,
    1.0164383561643835, 2.0135040047907777, 3.010958904109589, 4.010958904109589, 5.016438356164383, 6.016236245227937,
    7.013698630136986, 8.01095890410959, 9.01095890410959, 10.010771764353619 };
  private static final double[] RATE_DSC = new double[] {0.0017743012430444162, 0.0016475657039787027,
    8.00944979276571E-4, 7.991342366517293E-4, 7.769429292812209E-4, 8.011052753850106E-4, 8.544769819435054E-4,
    0.0010101196182894087, 0.0025295133435066005, 0.005928027386129847, 0.009984669002766438, 0.013910233828705014,
    0.017362472692574276, 0.02026566836808523, 0.02272069332675379, 0.024782351990410997, 0.026505391310201288 };
  private static final CurveName NAME_DSC = CurveName.of("USD-DSCON");
  private static final CurveMetadata META_DSC = Curves.zeroRates(NAME_DSC, ACT_ACT_ISDA);
  private static final InterpolatedNodalCurve CURVE_DSC =
      InterpolatedNodalCurve.of(META_DSC, TIME_DSC, RATE_DSC, INTERPOLATOR);

  private static final double[] TIME_FWD = new double[] {0.25205479452054796, 0.5013698630136987, 0.7534246575342466,
    1.010958904109589, 2.0107717643536196, 3.0054794520547947, 4.005479452054795, 5.005479452054795, 7.010958904109589,
    10.005307283479302, 12.01095890410959, 15.005479452054795, 20.005479452054793, 25.008219178082193,
    30.01077176435362 };
  private static final double[] RATE_FWD = new double[] {0.002377379439054076, 0.002418692953929592,
    0.002500627386941208, 0.002647539893522339, 0.0044829589913700256, 0.008123927669512542, 0.012380488135102518,
    0.01644838699856555, 0.023026212753825423, 0.02933978147314773, 0.03208786808445587, 0.03475307015968317,
    0.03689179443401795, 0.03776622232525561, 0.03810645431268746 };
  private static final CurveName NAME_FWD = CurveName.of("USD-LIBOR3M");
  private static final CurveMetadata META_FWD = Curves.zeroRates(NAME_FWD, ACT_ACT_ISDA);
  private static final InterpolatedNodalCurve CURVE_FWD =
      InterpolatedNodalCurve.of(META_FWD, TIME_FWD, RATE_FWD, INTERPOLATOR);
  private static final ImmutableRatesProvider RATE_PROVIDER = ImmutableRatesProvider.builder()
      .discountCurves(ImmutableMap.of(USD, CURVE_DSC))
      .indexCurves(ImmutableMap.of(USD_LIBOR_3M, CURVE_FWD))
      .fxMatrix(FxMatrix.empty())
      .valuationDate(VALUATION)
      .build();

  private static final Interpolator1D LINEAR_FLAT = CombinedInterpolatorExtrapolatorFactory.getInterpolator(
      Interpolator1DFactory.LINEAR,
      Interpolator1DFactory.FLAT_EXTRAPOLATOR,
      Interpolator1DFactory.FLAT_EXTRAPOLATOR);
  private static final GridInterpolator2D INTERPOLATOR_2D = new GridInterpolator2D(LINEAR_FLAT, LINEAR_FLAT);
  private static final SurfaceMetadata META_ALPHA = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.of("Tenor"))
      .zValueType(ValueType.of("SABRParameter"))
      .surfaceName(SurfaceName.of("ALPHA"))
      .build();
  private static final InterpolatedNodalSurface SURFACE_ALPHA = InterpolatedNodalSurface.of(
      META_ALPHA,
      new double[] {0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10 },
      new double[] {1, 1, 1, 1, 1, 1, 5, 5, 5, 5, 5, 5, 10, 10, 10, 10, 10, 10 },
      new double[] {0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.06, 0.06, 0.06, 0.06,
        0.06, 0.06 },
      INTERPOLATOR_2D);
  private static final SurfaceMetadata META_BETA = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.of("Tenor"))
      .zValueType(ValueType.of("SABRParameter"))
      .surfaceName(SurfaceName.of("BETA"))
      .build();
  private static final InterpolatedNodalSurface SURFACE_BETA = InterpolatedNodalSurface.of(
      META_BETA,
      new double[] {0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10 },
      new double[] {1, 1, 1, 1, 1, 1, 5, 5, 5, 5, 5, 5, 10, 10, 10, 10, 10, 10 },
      new double[] {0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5 },
      INTERPOLATOR_2D);
  private static final SurfaceMetadata META_RHO = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.of("Tenor"))
      .zValueType(ValueType.of("SABRParameter"))
      .surfaceName(SurfaceName.of("RHO"))
      .build();
  private static final InterpolatedNodalSurface SURFACE_RHO = InterpolatedNodalSurface.of(
      META_RHO,
      new double[] {0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10 },
      new double[] {1, 1, 1, 1, 1, 1, 5, 5, 5, 5, 5, 5, 10, 10, 10, 10, 10, 10 },
      new double[] {-0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25,
        0.00, 0.00, 0.00, 0.00 },
      INTERPOLATOR_2D);
  private static final SurfaceMetadata META_NU = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.of("Tenor"))
      .zValueType(ValueType.of("SABRParameter"))
      .surfaceName(SurfaceName.of("NU"))
      .build();
  private static final InterpolatedNodalSurface SURFACE_NU = InterpolatedNodalSurface.of(
      META_NU,
      new double[] {0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10 },
      new double[] {1, 1, 1, 1, 1, 1, 5, 5, 5, 5, 5, 5, 10, 10, 10, 10, 10, 10 },
      new double[] {0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.30, 0.30,
        0.30, 0.30 },
      INTERPOLATOR_2D);
  private static final SABRInterestRateParameters SABR_PARAM = SABRInterestRateParameters.of(SURFACE_ALPHA,
      SURFACE_BETA, SURFACE_RHO, SURFACE_NU, SABRHaganVolatilityFunctionProvider.DEFAULT);
  private static final SABRVolatilitySwaptionProvider VOL_PROVIDER = SABRVolatilitySwaptionProvider.of(SABR_PARAM,
      SWAP_CONVENTION, ACT_ACT_ISDA, VALUATION);

  private static final double NOTIONAL = 100000000; //100m
  private static final double RATE = 0.0350;
  private static final Tenor TENOR = Tenor.TENOR_7Y;
  private static final ZonedDateTime EXPIRY_DATE = LocalDate.of(2016, 1, 22).atStartOfDay(ZoneOffset.UTC); // 2Y
  private static final Swap SWAP =
      SWAP_CONVENTION.toTrade(EXPIRY_DATE.toLocalDate(), TENOR, BuySell.BUY, NOTIONAL, RATE).getProduct();
  private static final Swaption SWAPTION = Swaption.builder()
      .expiryDate(AdjustableDate.of(EXPIRY_DATE.toLocalDate()))
      .expiryTime(EXPIRY_DATE.toLocalTime())
      .expiryZone(EXPIRY_DATE.getZone())
      .longShort(LongShort.LONG)
      .swaptionSettlement(PhysicalSettlement.DEFAULT)
      .underlying(SWAP)
      .build();

  private static final SABRSwaptionPhysicalProductPricer SWAPTION_PRICER = SABRSwaptionPhysicalProductPricer.DEFAULT;
  private static final DiscountingSwapProductPricer SWAP_PRICER = DiscountingSwapProductPricer.DEFAULT;

  private static final double REGRESSION_TOL = 1.0e-5; // due to tenor computation difference

  public void regressionPv() {
    CurrencyAmount pvComputed = SWAPTION_PRICER.presentValue(SWAPTION, RATE_PROVIDER, VOL_PROVIDER);
    assertEquals(pvComputed.getAmount(), 3156216.489577751, REGRESSION_TOL * NOTIONAL);
  }

  public void regressionPvCurveSensi() {
    PointSensitivityBuilder point = SWAPTION_PRICER.presentValueSensitivity(SWAPTION, RATE_PROVIDER, VOL_PROVIDER);
    CurveCurrencyParameterSensitivities sensiComputed = RATE_PROVIDER.curveParameterSensitivity(point.build());
    final double[] deltaDsc = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 109037.92080563342, 637123.4570377409,
      -931862.187003511, -2556192.7520530378, -4233440.216336116, -5686205.439275854, -6160338.898970505,
      -3709275.494841247, 0.0 };
    final double[] deltaFwd = {0.0, 0.0, 0.0, 0.0, -1.0223186788452002E8, 2506923.9169937484, 4980364.73045286,
      1.254633556119663E7, 1.528160539036628E8, 2.5824191204559547E8, 0.0, 0.0, 0.0, 0.0, 0.0 };
    CurveCurrencyParameterSensitivities sensiExpected = CurveCurrencyParameterSensitivities.of(
        CurveCurrencyParameterSensitivity.of(META_DSC, USD, deltaDsc),
        CurveCurrencyParameterSensitivity.of(META_FWD, USD, deltaFwd));
    assertTrue(sensiComputed.equalWithTolerance(sensiExpected, NOTIONAL * REGRESSION_TOL));
  }

  @Test(enabled = false)
  public void regressionPvSurfaceSensi() {
    SwaptionSABRSensitivity point =
        SWAPTION_PRICER.presentValueSABRParameterSensitivity(SWAPTION, RATE_PROVIDER, VOL_PROVIDER);
    SurfaceCurrencyParameterSensitivities sensiComputed = VOL_PROVIDER.surfaceCurrencyParameterSensitivity(point);
    // TODO equalWithTol

    double[][] alphaExp = new double[][] { {1.0, 5.0, 6204.475194599176 }, {2.0, 10.0, 2.6312850632053435E7 },
      {1.0, 10.0, 4136.961894403856 },
      {2.0, 5.0, 3.946312129841228E7 } };
    double[][] betaExp = new double[][] { {1.0, 5.0, -1135.9264046809967 }, {2.0, 10.0, -4817403.709083163 },
      {1.0, 10.0, -757.402375482628 },
      {2.0, 5.0, -7224978.7593665235 } };
    double[][] rhoExp = new double[][] { {1.0, 5.0, 25.108219123928023 }, {2.0, 10.0, 106482.62725264493 },
      {1.0, 10.0, 16.74142332657722 },
      {2.0, 5.0, 159699.0342933747 } };
    double[][] nuExp = new double[][] { {1.0, 5.0, 37.75195237231597 }, {2.0, 10.0, 160104.0301854763 },
      {1.0, 10.0, 25.17189343259352 },
      {2.0, 5.0, 240118.59649586905 } };
    double[][][] exps = new double[][][] {alphaExp, betaExp, rhoExp, nuExp };
    SurfaceMetadata[] metadata = new SurfaceMetadata[] {META_ALPHA, META_BETA, META_RHO, META_NU };
    SurfaceCurrencyParameterSensitivities sensiExpected = SurfaceCurrencyParameterSensitivities.empty();
    for (int i = 0; i < exps.length; ++i) {
      int size = exps[i].length;
      List<SurfaceParameterMetadata> paramMetadata = new ArrayList<SurfaceParameterMetadata>(size);
      List<Double> sensi = new ArrayList<Double>(size);
      for (int j = 0; j < size; ++j) {
        paramMetadata.add(SwaptionVolatilitySurfaceExpiryTenorNodeMetadata.of(exps[i][j][0], exps[i][j][1]));
        sensi.add(exps[i][j][2]);
      }
      SurfaceMetadata surfaceMetadata = metadata[i].withParameterMetadata(paramMetadata);
      sensiExpected = sensiExpected.combinedWith(
          SurfaceCurrencyParameterSensitivity.of(surfaceMetadata, USD, Doubles.toArray(sensi)));
    }
    assertTrue(sensiComputed.equalWithTolerance(sensiExpected, NOTIONAL * REGRESSION_TOL));

  }
}
