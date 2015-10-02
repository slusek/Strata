/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.report.framework.expression;

import java.util.List;

import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.currency.CurrencyAmount;

/**
 * Evaluates a token against a currency amount.
 */
public class CurrencyAmountTokenEvaluator implements TokenParser<CurrencyAmount> {

  private final String CURRENCY_FIELD = "currency";
  private final String AMOUNT_FIELD = "amount";

  @Override
  public Class<CurrencyAmount> getTargetType() {
    return CurrencyAmount.class;
  }

  @Override
  public ImmutableSet<String>tokens(CurrencyAmount amount) {
    return ImmutableSet.of(CURRENCY_FIELD, AMOUNT_FIELD);
  }

  @Override
  public ParseResult parse(CurrencyAmount amount, String firstToken, List<String> remainingTokens) {
    if (firstToken.equals(CURRENCY_FIELD)) {
      return ParseResult.success(amount.getCurrency(), remainingTokens);
    }
    if (firstToken.equals(AMOUNT_FIELD)) {
      // Can be rendered directly - retains the currency for formatting purposes
      return ParseResult.success(amount, remainingTokens);
    }
    return invalidTokenFailure(amount, firstToken);
  }

}
