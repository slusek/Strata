/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * <p>
 * Please see distribution for license.
 */
package com.opengamma.strata.report.framework.expression;

import static com.opengamma.strata.collect.Guavate.toImmutableList;

import java.util.List;
import java.util.Set;

import com.opengamma.strata.collect.result.FailureReason;
import com.opengamma.strata.collect.result.Result;
import com.opengamma.strata.collect.type.TypedString;
import com.opengamma.strata.engine.Column;
import com.opengamma.strata.engine.config.Measure;
import com.opengamma.strata.finance.Product;
import com.opengamma.strata.finance.ProductTrade;
import com.opengamma.strata.finance.SecurityTrade;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.function.StandardComponents;
import com.opengamma.strata.report.ReportCalculationResults;

/**
 *
 */
class ResultsRow {

  private final ReportCalculationResults results;
  private final int rowIndex;

  public ResultsRow(ReportCalculationResults results, int rowIndex) {
    this.results = results;
    this.rowIndex = rowIndex;
  }

  Trade getTrade() {
    return results.getTrades().get(rowIndex);
  }

  Result<Product> getProduct() {
    Trade trade = getTrade();

    if (trade instanceof ProductTrade) {
      return Result.success(((ProductTrade<?>) trade).getProduct());
    }
    if (trade instanceof SecurityTrade) {
      return Result.success(((SecurityTrade<?>) trade).getProduct());
    }
    return Result.failure(FailureReason.INVALID_INPUT, "Trade does not contain a product");
  }

  Result<?> getResult(String measureName) {
    try {
      Column column = Column.of(Measure.of(measureName));
      int columnIndex = results.getColumns().indexOf(column);
      return columnIndex == -1 ?
          Result.failure(
              FailureReason.INVALID_INPUT,
              "Measure not found in results: '{}'. Valid measure names: {}",
              measureName,
              measureNames(results.getTrades().get(rowIndex))) :
          results.getCalculationResults().get(rowIndex, columnIndex);
    } catch (IllegalArgumentException ex) {
      return Result.failure(
          FailureReason.INVALID_INPUT,
          "Invalid measure name: '{}'. Valid measure names: {}",
          measureName,
          measureNames(results.getTrades().get(rowIndex)));
    }
  }

  private static List<String> measureNames(Trade trade) {
    Set<Measure> validMeasures = StandardComponents.pricingRules().configuredMeasures(trade);
    return validMeasures.stream()
        .map(TypedString::toString)
        .sorted()
        .collect(toImmutableList());
  }
}
