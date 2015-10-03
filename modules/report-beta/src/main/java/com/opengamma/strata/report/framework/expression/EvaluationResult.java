/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * <p>
 * Please see distribution for license.
 */
package com.opengamma.strata.report.framework.expression;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.result.FailureReason;
import com.opengamma.strata.collect.result.Result;

/**
 *
 */
class EvaluationResult {

  private final Result<?> result;

  private final List<String> remainingTokens;

  EvaluationResult(Result<?> result, List<String> remainingTokens) {
    this.result = result;
    this.remainingTokens = remainingTokens;
  }

  Result<?> getResult() {
    return result;
  }

  List<String> getRemainingTokens() {
    return remainingTokens;
  }

  boolean isComplete() {
    return getResult().isFailure() || getRemainingTokens().isEmpty();
  }

  static EvaluationResult success(Object value, List<String> remainingTokens) {
    return new EvaluationResult(Result.success(value), remainingTokens);
  }

  static EvaluationResult failure(String message, Object... messageValues) {
    String msg = Messages.format(message, messageValues);
    return new EvaluationResult(Result.failure(FailureReason.INVALID_INPUT, msg), ImmutableList.of());
  }

  static EvaluationResult of(Result<?> result, List<String> remainingTokens) {
    return new EvaluationResult(result, remainingTokens);
  }
}
