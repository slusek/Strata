/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.report.framework.expression;

import static com.opengamma.strata.collect.Guavate.toImmutableList;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.joda.beans.Bean;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.result.FailureReason;
import com.opengamma.strata.collect.result.Result;
import com.opengamma.strata.engine.Column;
import com.opengamma.strata.engine.config.Measure;
import com.opengamma.strata.finance.Product;
import com.opengamma.strata.finance.ProductTrade;
import com.opengamma.strata.finance.SecurityTrade;
import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.finance.rate.fra.Fra;
import com.opengamma.strata.finance.rate.fra.FraTrade;
import com.opengamma.strata.report.ReportCalculationResults;

/**
 * Evaluates a path describing a value to be shown in a trade report.
 * <p>
 * For example, if the expression is '{@code Product.index.name}' and the results contain {@link FraTrade} instances
 * the following calls will be made for each trade in the results:
 * <ul>
 *   <li>{@code FraTrade.getProduct()} returning a {@link Fra}</li>
 *   <li>{@code Fra.getIndex()} returning an {@link IborIndex}</li>
 *   <li>{@code IborIndex.getName()} returning the index name</li>
 * </ul>
 * The result of evaluating the expression is the index name.
 */
public class ValuePathParser {

  /** The separator used in the value path. */
  private static final String PATH_SEPARATOR = "\\.";

  private static final ImmutableList<TokenParser<?>> PARSERS = ImmutableList.of(
      new CurrencyAmountTokenEvaluator(),
      new MapTokenEvaluator(),
      new CurveCurrencyParameterSensitivitiesTokenEvaluator(),
      new CurveCurrencyParameterSensitivityTokenEvaluator(),
      new TradeTokenEvaluator(),
      new BeanTokenEvaluator(),
      new IterableTokenEvaluator());

  //-------------------------------------------------------------------------
  /**
   * Gets the measure encoded in a value path, if present.
   *
   * @param valuePath  the value path
   * @return the measure, if present
   */
  public static Optional<Measure> measure(String valuePath) {
    try {
      List<String> tokens = tokenize(valuePath);
      ValueRootType rootType = ValueRootType.parseToken(tokens.get(0));

      if (rootType != ValueRootType.MEASURES || tokens.size() < 2) {
        return Optional.empty();
      }
      Measure measure = Measure.of(tokens.get(1));
      return Optional.of(measure);
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  /**
   * Evaluates a value path against a set of results, returning the resolved result for each trade.
   *
   * @param valuePath  the value path
   * @param results  the calculation results
   * @return the list of resolved results for each trade
   */
  public static List<Result<?>> parse(String valuePath, ReportCalculationResults results) {
    List<String> tokens = tokenize(valuePath);

    if (tokens.size() < 2) {
      // TODO return failure - need the root token plus at least one more to select the value
    }
    return IntStream.range(0, results.getCalculationResults().getRowCount())
        .mapToObj(rowIndex -> parse(tokens, RootParser.INSTANCE, new ResultsRow(results, rowIndex)))
        .collect(toImmutableList());
  }

  // Tokens always has at least one token
  private static Result<?> parse(List<String> tokens, TokenParser<Object> parser, Object target) {
    ParseResult parseResult = parser.parse(target, tokens.get(0), ParserUtils.tail(tokens));

    if (parseResult.isComplete()) {
      return parseResult.getResult();
    }
    Object value = parseResult.getResult().getValue();
    Optional<TokenParser<Object>> nextParser = getParser(value.getClass());

    return nextParser.isPresent() ?
        parse(parseResult.getRemainingTokens(), nextParser.get(), value) :
        noParserResult(value);
  }

  private static Result<?> noParserResult(Object value) {
    // TODO impl
  }

  /**
   * Gets the supported tokens on the given object.
   *
   * @param object  the object for which to return the valid tokens
   * @return the tokens
   */
  public static Set<String> tokens(Object object) {
    // This must mirror the main evaluate method implementation
    Object evalObject = object;
    Set<String> tokens = new HashSet<>();
    Optional<TokenParser<Object>> parser = getParser(evalObject.getClass());

    if (evalObject instanceof Bean && !isTypeSpecificParser(parser)) {
      Bean bean = (Bean) evalObject;

      if (bean.propertyNames().size() == 1) {
        String onlyProperty = Iterables.getOnlyElement(bean.propertyNames());
        tokens.add(onlyProperty);
        evalObject = bean.property(onlyProperty).get();
        parser = getParser(evalObject.getClass());
      }
    }
    if (parser.isPresent()) {
      tokens.addAll(parser.get().tokens(evalObject));
    }
    return tokens;
  }

  //-------------------------------------------------------------------------
  // splits a value path into tokens for processing
  private static List<String> tokenize(String valuePath) {
    String[] tokens = valuePath.split(PATH_SEPARATOR);
    return ImmutableList.copyOf(tokens);
  }

  /**
   * Evaluates an expression to extract a value from an object.
   * <p>
   * For example, if the root value is a {@link Fra} and the expression is '{@code index.name}', the tokens will be
   * {@code ['index', 'name']} and this method will call:
   * <ul>
   *   <li>{@code Fra.getIndex()}, returning an {@code IborIndex}</li>
   *   <li>{@code IborIndex.getName()} returning the index name</li>
   * </ul>
   * The return value of this method will be the index name.
   *
   * @param rootObject  the object against which the expression is evaluated
   * @param tokens  the individual tokens making up the expression
   * @return the result of evaluating the expression against the object
   */

  @SuppressWarnings("unchecked")
  private static Optional<TokenParser<Object>> getParser(Class<?> targetClass) {
    return PARSERS.stream()
        .filter(e -> e.getTargetType().isAssignableFrom(targetClass))
        .map(e -> (TokenParser<Object>) e)
        .findFirst();
  }

  private static boolean isTypeSpecificParser(Optional<TokenParser<Object>> parser) {
    return parser.isPresent() && !Bean.class.equals(parser.get().getTargetType());
  }

  //-------------------------------------------------------------------------
  // restricted constrctor
  private ValuePathParser() {
  }

}

//--------------------------------------------------------------------------------------------------

interface TokenParser<T> {

  Class<?> getTargetType();

  Set<String> tokens(T target);

  // tokens always has at least one element
  ParseResult parse(T target, String firstToken, List<String> remainingTokens);

  default ParseResult invalidTokenFailure(Object object, String token) {
    // TODO Include list of valid tokens
    return ParseResult.failure("");
  }
}

//--------------------------------------------------------------------------------------------------

class ParseResult {

  private final Result<?> result;

  private final List<String> remainingTokens;

  ParseResult(Result<?> result, List<String> remainingTokens) {
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

  static ParseResult success(Object value, List<String> remainingTokens) {
    return new ParseResult(Result.success(value), remainingTokens);
  }

  static ParseResult failure(String message, Object... messageValues) {
    String msg = Messages.format(message, messageValues);
    return new ParseResult(Result.failure(FailureReason.INVALID_INPUT, msg), ImmutableList.of());
  }

  static ParseResult of(Result<?> result, List<String> remainingTokens) {
    return new ParseResult(result, remainingTokens);
  }
}

//--------------------------------------------------------------------------------------------------

class RootParser implements TokenParser<ResultsRow> {

  /** The single shared instance of this class. */
  static final RootParser INSTANCE = new RootParser();

  private static final ImmutableSet<String> TOKENS = ImmutableSet.of(
      ValueRootType.MEASURES.token(),
      ValueRootType.TRADE.token(),
      ValueRootType.PRODUCT.token());

  @Override
  public Class<?> getTargetType() {
    // This isn't used because the root parser has special treatment
    return ResultsRow.class;
  }

  @Override
  public Set<String> tokens(ResultsRow target) {
    return TOKENS;
  }

  @Override
  public ParseResult parse(ResultsRow target, String firstToken, List<String> remainingTokens) {
    ValueRootType rootType = ValueRootType.parseToken(firstToken);

    switch (rootType) {
      case MEASURES:
        return remainingTokens.isEmpty() ?
            ParseResult.failure("At least two tokens are required to select a measure value") :
            ParseResult.success(target.getResult(remainingTokens.get(1)), ParserUtils.tail(remainingTokens));
      case PRODUCT:
        return ParseResult.of(target.getProduct(), remainingTokens);
      case TRADE:
        return ParseResult.success(target.getTrade(), remainingTokens);
      default:
        throw new IllegalArgumentException("Unknown root token '" + rootType.token() + "'");
    }
  }
}

//--------------------------------------------------------------------------------------------------

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
          Result.failure(FailureReason.INVALID_INPUT, "Measure not found in results: {}", measureName) :
          results.getCalculationResults().get(rowIndex, columnIndex);
    } catch (IllegalArgumentException ex) {
      return Result.failure(FailureReason.INVALID_INPUT, "Invalid measure name: {}", measureName);
    }
  }
}

class ParserUtils {

  private ParserUtils() {
  }

  static List<String> tail(List<String> list) {
    return drop(list, 1);
  }

  static List<String> drop(List<String> list, int nItems) {
    return list.subList(nItems, list.size());
  }
}
