package com.opengamma.strata.pricer.datasets;

import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;

import java.time.LocalDate;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.math.interpolation.Interpolator1DFactory;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.value.BondGroup;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.LegalEntityGroup;
import com.opengamma.strata.market.value.SimpleDiscountFactors;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;

/**
 * LegalEntityDiscountingProvider data sets for testing.
 */
public class LegalEntityDiscountingProviderDataSets {
  //      =====     issuer curve + repo curve      =====        
  private static final CurveInterpolator INTERPOLATOR = Interpolator1DFactory.LINEAR_INSTANCE;
  private static final LocalDate VALUATION = LocalDate.of(2011, 6, 20);
  private static final StandardId BOND_SECURITY_ID = StandardId.of("OG-Ticker", "GOVT1-BONDS");
  private static final StandardId ISSUER_ID = StandardId.of("OG-Ticker", "GOVT1");
  private static final CurveName NAME_REPO = CurveName.of("TestRepoCurve");
  private static final CurveName NAME_ISSUER = CurveName.of("TestIssuerCurve");
  /** time data for repo rate curve */
  public static final double[] REPO_TIME = new double[] {0.0, 0.5, 1.0, 2.0, 5.0, 10.0 };
  /** zero rate data for repo rate curve */
  public static final double[] REPO_RATE = new double[] {0.0120, 0.0120, 0.0120, 0.0140, 0.0140, 0.0140 };
  /** discount factor data for repo rate curve */
  public static final double[] REPO_FACTOR = new double[] {1.0, 0.9940, 0.9881, 0.9724, 0.9324, 0.8694 };
  /** meta data of repo zero rate curve*/
  public static final CurveMetadata META_ZERO_REPO = Curves.zeroRates(NAME_REPO, ACT_ACT_ISDA);
  /** meta data of repo discount factor curve */
  public static final CurveMetadata META_SIMPLE_REPO = Curves.discountFactors(NAME_REPO, ACT_ACT_ISDA);
  /** time data for issuer curve */
  public static final double[] ISSUER_TIME = new double[] {0.0, 0.5, 1.0, 2.0, 5.0, 10.0 };
  /** zero rate data for issuer curve */
  public static final double[] ISSUER_RATE = new double[] {0.0100, 0.0100, 0.0100, 0.0120, 0.0120, 0.0120 };
  /** discount factor data for issuer curve */
  public static final double[] ISSUER_FACTOR = new double[] {1.0, 0.9950, 0.9900, 0.9763, 0.9418, 0.8869 };
  /** meta data of issuer zero rate curve*/
  public static final CurveMetadata META_ZERO_ISSUER = Curves.zeroRates(NAME_ISSUER, ACT_ACT_ISDA);
  /** meta data of issuer discount factor curve */
  public static final CurveMetadata META_SIMPLE_ISSUER = Curves.discountFactors(NAME_ISSUER, ACT_ACT_ISDA);
  private static final BondGroup GROUP_REPO = BondGroup.of("GOVT1 BONDS");
  private static final ImmutableList<StandardId> LIST_REPO = ImmutableList.<StandardId>of(ISSUER_ID, BOND_SECURITY_ID);
  private static final LegalEntityGroup GROUP_ISSUER = LegalEntityGroup.of("GOVT1");
  // zero rate curves
  private static final InterpolatedNodalCurve CURVE_ZERO_REPO =
      InterpolatedNodalCurve.of(META_ZERO_REPO, REPO_TIME, REPO_RATE, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_ZERO_REPO = 
      ZeroRateDiscountFactors.of(USD, VALUATION, CURVE_ZERO_REPO);
  private static final InterpolatedNodalCurve CURVE_ZERO_ISSUER =
      InterpolatedNodalCurve.of(META_ZERO_ISSUER, ISSUER_TIME, ISSUER_RATE, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_ZERO_ISSUER =
      ZeroRateDiscountFactors.of(USD, VALUATION, CURVE_ZERO_ISSUER);
  /** provider with zero rate curves */
  public static final LegalEntityDiscountingProvider ISSUER_REPO_ZERO = LegalEntityDiscountingProvider.builder()
      .issuerCurves(ImmutableMap.<Pair<LegalEntityGroup, Currency>, DiscountFactors>of(
          Pair.<LegalEntityGroup, Currency>of(GROUP_ISSUER, USD), DSC_FACTORS_ZERO_ISSUER))
      .legalEntityMap(ImmutableMap.<StandardId, LegalEntityGroup>of(ISSUER_ID, GROUP_ISSUER))
      .repoCurves(ImmutableMap.<Pair<BondGroup, Currency>, DiscountFactors>of(
          Pair.<BondGroup, Currency>of(GROUP_REPO, USD), DSC_FACTORS_ZERO_REPO))
      .BondMap(ImmutableMap.<List<StandardId>, BondGroup>of(LIST_REPO, GROUP_REPO))
      .build();
  // discount factor curves
  private static final InterpolatedNodalCurve CURVE_SIMPLE_REPO =
      InterpolatedNodalCurve.of(META_SIMPLE_REPO, REPO_TIME, REPO_FACTOR, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_SIMPLE_REPO =
      SimpleDiscountFactors.of(USD, VALUATION, CURVE_SIMPLE_REPO);
  private static final InterpolatedNodalCurve CURVE_SIMPLE_ISSUER =
      InterpolatedNodalCurve.of(META_SIMPLE_ISSUER, ISSUER_TIME, ISSUER_FACTOR, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_SIMPLE_ISSUER =
      SimpleDiscountFactors.of(USD, VALUATION, CURVE_SIMPLE_ISSUER);
  /** provider with discount factor curve */
  public static final LegalEntityDiscountingProvider ISSUER_REPO_SIMPLE = LegalEntityDiscountingProvider.builder()
      .issuerCurves(ImmutableMap.<Pair<LegalEntityGroup, Currency>, DiscountFactors>of(
          Pair.<LegalEntityGroup, Currency>of(GROUP_ISSUER, USD), DSC_FACTORS_SIMPLE_ISSUER))
      .legalEntityMap(ImmutableMap.<StandardId, LegalEntityGroup>of(ISSUER_ID, GROUP_ISSUER))
      .repoCurves(ImmutableMap.<Pair<BondGroup, Currency>, DiscountFactors>of(
          Pair.<BondGroup, Currency>of(GROUP_REPO, USD), DSC_FACTORS_SIMPLE_REPO))
      .BondMap(ImmutableMap.<List<StandardId>, BondGroup>of(LIST_REPO, GROUP_REPO))
      .build();
}
