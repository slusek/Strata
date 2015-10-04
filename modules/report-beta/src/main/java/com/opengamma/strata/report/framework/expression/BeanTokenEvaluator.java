/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.report.framework.expression;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.joda.beans.Bean;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Evaluates a token against a bean to produce another object.
 */
public class BeanTokenEvaluator extends TokenEvaluator<Bean> {

  @Override
  public Class<Bean> getTargetType() {
    return Bean.class;
  }

  @Override
  public Set<String> tokens(Bean bean) {
    if (bean.propertyNames().size() == 1) {
      String singlePropertyName = Iterables.getOnlyElement(bean.propertyNames());
      Object propertyValue = bean.property(singlePropertyName).get();
      Set<String> valueTokens = ValuePathEvaluator.tokens(propertyValue);

      return ImmutableSet.<String>builder()
          .add(singlePropertyName)
          .addAll(valueTokens)
          .build();
    } else {
      return bean.propertyNames();
    }
  }

  @Override
  public EvaluationResult evaluate(Bean bean, String firstToken, List<String> remainingTokens) {
    Optional<String> propertyName = bean.propertyNames().stream()
        .filter(p -> p.equalsIgnoreCase(firstToken))
        .findFirst();

    if (propertyName.isPresent()) {
      Object propertyValue = bean.property(propertyName.get()).get();

      return propertyValue != null ?
          EvaluationResult.success(propertyValue, remainingTokens) :
          EvaluationResult.failure("No value available for property '{}'", firstToken);
    }
    // The bean has a single property which doesn't match the token.
    // Return the property value without consuming any tokens.
    // This allows skipping over properties when the bean only has a single property.
    if (bean.propertyNames().size() == 1) {
      String singlePropertyName = Iterables.getOnlyElement(bean.propertyNames());
      Object propertyValue = bean.property(singlePropertyName).get();
      List<String> tokens = ImmutableList.<String>builder().add(firstToken).addAll(remainingTokens).build();

      return propertyValue != null ?
          EvaluationResult.success(propertyValue, tokens) :
          EvaluationResult.failure("No value available for property '{}'", firstToken);
    }
    return invalidTokenFailure(bean, firstToken);
  }

}
