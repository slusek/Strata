/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.market.curve.definition;

import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.index.IborIndices.EUR_LIBOR_3M;
import static com.opengamma.strata.basics.index.IborIndices.GBP_LIBOR_6M;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.Set;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.BuySell;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.market.ObservableKey;
import com.opengamma.strata.basics.market.ObservableValues;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.finance.TradeInfo;
import com.opengamma.strata.finance.rate.deposit.IborFixingDeposit;
import com.opengamma.strata.finance.rate.deposit.IborFixingDepositTemplate;
import com.opengamma.strata.finance.rate.deposit.IborFixingDepositTrade;
import com.opengamma.strata.market.curve.CurveParameterMetadata;
import com.opengamma.strata.market.curve.TenorCurveNodeMetadata;
import com.opengamma.strata.market.key.QuoteKey;
import com.opengamma.strata.market.value.ValueType;

/**
 * Test {@link IborFixingDepositCurveNode}.
 */
@Test
public class IborFixingDepositCurveNodeTest {

  private static final QuoteKey QUOTE_KEY = QuoteKey.of(StandardId.of("OG-Ticker", "Deposit1"));
  private static final double SPREAD = 0.0015;
  private static final IborFixingDepositTemplate TEMPLATE = IborFixingDepositTemplate.of(EUR_LIBOR_3M);

  public void test_builder() {
    IborFixingDepositCurveNode test = IborFixingDepositCurveNode.builder()
        .rateKey(QUOTE_KEY)
        .template(TEMPLATE)
        .spread(SPREAD)
        .build();
    assertEquals(test.getRateKey(), QUOTE_KEY);
    assertEquals(test.getSpread(), SPREAD);
    assertEquals(test.getTemplate(), TEMPLATE);
  }

  public void test_of_spread() {
    IborFixingDepositCurveNode test = IborFixingDepositCurveNode.of(TEMPLATE, QUOTE_KEY, SPREAD);
    assertEquals(test.getRateKey(), QUOTE_KEY);
    assertEquals(test.getSpread(), SPREAD);
    assertEquals(test.getTemplate(), TEMPLATE);
  }

  public void test_of_noSpread() {
    IborFixingDepositCurveNode test = IborFixingDepositCurveNode.of(TEMPLATE, QUOTE_KEY);
    assertEquals(test.getRateKey(), QUOTE_KEY);
    assertEquals(test.getSpread(), 0.0);
    assertEquals(test.getTemplate(), TEMPLATE);
  }

  public void test_requirements() {
    IborFixingDepositCurveNode test = IborFixingDepositCurveNode.of(TEMPLATE, QUOTE_KEY, SPREAD);
    Set<ObservableKey<Double>> set = test.requirements();
    Iterator<ObservableKey<Double>> itr = set.iterator();
    assertEquals(itr.next(), QUOTE_KEY);
    assertFalse(itr.hasNext());
  }

  public void test_trade() {
    IborFixingDepositCurveNode node = IborFixingDepositCurveNode.of(TEMPLATE, QUOTE_KEY, SPREAD);
    LocalDate valuationDate = LocalDate.of(2015, 1, 22);
    double rate = 0.035;
    IborFixingDepositTrade trade = node.trade(valuationDate, ObservableValues.of(QUOTE_KEY, rate));
    LocalDate startDateExpected = TEMPLATE.getConvention().getSpotDateOffset().adjust(valuationDate);
    LocalDate endDateExpected = startDateExpected.plus(TEMPLATE.getDepositPeriod());
    IborFixingDeposit depositExpected = IborFixingDeposit.builder()
        .buySell(BuySell.BUY)
        .index(EUR_LIBOR_3M)
        .startDate(startDateExpected)
        .endDate(endDateExpected)
        .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, EUR_LIBOR_3M.getFixingCalendar()))
        .notional(1.0d)
        .fixedRate(rate + SPREAD)
        .build();
    TradeInfo tradeInfoExpected = TradeInfo.builder()
        .tradeDate(valuationDate)
        .build();
    assertEquals(trade.getProduct(), depositExpected);
    assertEquals(trade.getTradeInfo(), tradeInfoExpected);
  }

  public void test_trade_differentKey() {
    IborFixingDepositCurveNode node = IborFixingDepositCurveNode.of(TEMPLATE, QUOTE_KEY, SPREAD);
    LocalDate valuationDate = LocalDate.of(2015, 1, 22);
    double rate = 0.035;
    assertThrowsIllegalArg(() -> node.trade(
        valuationDate, ObservableValues.of(QuoteKey.of(StandardId.of("OG-Ticker", "Deposit2")), rate)));
  }

  public void test_initialGuess() {
    IborFixingDepositCurveNode node = IborFixingDepositCurveNode.of(TEMPLATE, QUOTE_KEY, SPREAD);
    LocalDate valuationDate = LocalDate.of(2015, 1, 22);
    double rate = 0.035;
    assertEquals(node.initialGuess(valuationDate, ObservableValues.of(QUOTE_KEY, rate), ValueType.ZERO_RATE), rate);
    assertEquals(node.initialGuess(valuationDate, ObservableValues.of(QUOTE_KEY, rate), ValueType.DISCOUNT_FACTOR), 0d);
  }

  public void test_metadata() {
    IborFixingDepositCurveNode node = IborFixingDepositCurveNode.of(TEMPLATE, QUOTE_KEY, SPREAD);
    LocalDate valuationDate = LocalDate.of(2015, 1, 22);
    CurveParameterMetadata metadata = node.metadata(valuationDate);
    assertEquals(((TenorCurveNodeMetadata) metadata).getDate(), LocalDate.of(2015, 4, 27));
    assertEquals(((TenorCurveNodeMetadata) metadata).getTenor(), Tenor.TENOR_3M);
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    IborFixingDepositCurveNode test = IborFixingDepositCurveNode.of(TEMPLATE, QUOTE_KEY, SPREAD);
    coverImmutableBean(test);
    IborFixingDepositCurveNode test2 = IborFixingDepositCurveNode.of(
        IborFixingDepositTemplate.of(GBP_LIBOR_6M), QuoteKey.of(StandardId.of("OG-Ticker", "Deposit2")));
    coverBeanEquals(test, test2);
  }

  public void test_serialization() {
    IborFixingDepositCurveNode test = IborFixingDepositCurveNode.of(TEMPLATE, QUOTE_KEY, SPREAD);
    assertSerialization(test);
  }

}
