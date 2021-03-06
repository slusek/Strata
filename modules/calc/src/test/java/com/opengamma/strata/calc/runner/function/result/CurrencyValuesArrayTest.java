/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calc.runner.function.result;

import static com.opengamma.strata.collect.Guavate.toImmutableList;
import static com.opengamma.strata.collect.TestHelper.assertThrows;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static com.opengamma.strata.collect.TestHelper.date;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.basics.market.FxRateId;
import com.opengamma.strata.basics.market.MarketDataFeed;
import com.opengamma.strata.calc.marketdata.CalculationEnvironment;
import com.opengamma.strata.calc.marketdata.DefaultCalculationMarketData;
import com.opengamma.strata.calc.marketdata.MarketEnvironment;
import com.opengamma.strata.calc.marketdata.mapping.MarketDataMappings;
import com.opengamma.strata.collect.array.DoubleArray;

@Test
public class CurrencyValuesArrayTest {

  public void create() {
    DoubleArray values = DoubleArray.of(1, 2, 3);
    CurrencyValuesArray test = CurrencyValuesArray.of(Currency.GBP, values);
    assertThat(test.getCurrency()).isEqualTo(Currency.GBP);
    assertThat(test.getValues()).isEqualTo(values);
    assertThat(test.size()).isEqualTo(3);
    assertThat(test.get(0)).isEqualTo(CurrencyAmount.of(Currency.GBP, 1));
    assertThat(test.get(1)).isEqualTo(CurrencyAmount.of(Currency.GBP, 2));
    assertThat(test.get(2)).isEqualTo(CurrencyAmount.of(Currency.GBP, 3));
    assertThat(test.stream().collect(toList())).isEqualTo(ImmutableList.of(
        CurrencyAmount.of(Currency.GBP, 1), CurrencyAmount.of(Currency.GBP, 2), CurrencyAmount.of(Currency.GBP, 3)));
  }

  /**
   * Test that values are converted to the reporting currency using the rates in the market data.
   */
  public void convert() {
    DoubleArray values = DoubleArray.of(1, 2, 3);
    List<FxRate> rates = ImmutableList.of(1.61, 1.62, 1.63).stream()
        .map(rate -> FxRate.of(Currency.GBP, Currency.USD, rate))
        .collect(toImmutableList());
    CurrencyValuesArray list = CurrencyValuesArray.of(Currency.GBP, values);
    CalculationEnvironment marketData = MarketEnvironment.builder()
        .valuationDate(date(2011, 3, 8))
        .addValue(FxRateId.of(Currency.GBP, Currency.USD), rates)
        .build();
    MarketDataMappings mappings = MarketDataMappings.of(MarketDataFeed.NONE);
    DefaultCalculationMarketData calculationMarketData = new DefaultCalculationMarketData(marketData, mappings);

    CurrencyValuesArray convertedList = list.convertedTo(Currency.USD, calculationMarketData);
    DoubleArray expectedValues = DoubleArray.of(1 * 1.61, 2 * 1.62, 3 * 1.63);
    CurrencyValuesArray expectedList = CurrencyValuesArray.of(Currency.USD, expectedValues);
    assertThat(convertedList).isEqualTo(expectedList);
  }

  /**
   * Test that no conversion is done and no rates are used if the values are already in the reporting currency.
   */
  public void noConversionNecessary() {
    DoubleArray values = DoubleArray.of(1, 2, 3);
    CurrencyValuesArray list = CurrencyValuesArray.of(Currency.GBP, values);
    CalculationEnvironment marketData = MarketEnvironment.builder().valuationDate(date(2011, 3, 8)).build();
    MarketDataMappings mappings = MarketDataMappings.of(MarketDataFeed.NONE);
    DefaultCalculationMarketData calculationMarketData = new DefaultCalculationMarketData(marketData, mappings);

    CurrencyValuesArray convertedList = list.convertedTo(Currency.GBP, calculationMarketData);
    assertThat(convertedList).isEqualTo(list);
  }

  /**
   * Test the expected exception is thrown when there are no FX rates available to convert the values.
   */
  public void missingFxRates() {
    DoubleArray values = DoubleArray.of(1, 2, 3);
    CurrencyValuesArray list = CurrencyValuesArray.of(Currency.GBP, values);
    CalculationEnvironment marketData = MarketEnvironment.builder().valuationDate(date(2011, 3, 8)).build();
    MarketDataMappings mappings = MarketDataMappings.of(MarketDataFeed.NONE);
    DefaultCalculationMarketData calculationMarketData = new DefaultCalculationMarketData(marketData, mappings);

    assertThrows(
        () -> list.convertedTo(Currency.USD, calculationMarketData),
        IllegalArgumentException.class,
        "No market data available for .*");
  }

  /**
   * Test the expected exception is thrown if there are not the same number of rates as there are values.
   */
  public void wrongNumberOfFxRates() {
    DoubleArray values = DoubleArray.of(1, 2, 3);
    List<FxRate> rates = ImmutableList.of(1.61, 1.62).stream()
        .map(rate -> FxRate.of(Currency.GBP, Currency.USD, rate))
        .collect(toImmutableList());
    CurrencyValuesArray list = CurrencyValuesArray.of(Currency.GBP, values);
    CalculationEnvironment marketData = MarketEnvironment.builder()
        .valuationDate(date(2011, 3, 8))
        .addValue(FxRateId.of(Currency.GBP, Currency.USD), rates)
        .build();
    MarketDataMappings mappings = MarketDataMappings.of(MarketDataFeed.NONE);
    DefaultCalculationMarketData calculationMarketData = new DefaultCalculationMarketData(marketData, mappings);

    assertThrows(
        () -> list.convertedTo(Currency.USD, calculationMarketData),
        IllegalArgumentException.class,
        "Number of rates .* must be 1 or the same as the number of values .*");
  }

  public void coverage() {
    DoubleArray values = DoubleArray.of(1, 2, 3);
    CurrencyValuesArray test = CurrencyValuesArray.of(Currency.GBP, values);
    coverImmutableBean(test);
    DoubleArray values2 = DoubleArray.of(1, 2, 3);
    CurrencyValuesArray test2 = CurrencyValuesArray.of(Currency.GBP, values2);
    coverBeanEquals(test, test2);
  }

}
