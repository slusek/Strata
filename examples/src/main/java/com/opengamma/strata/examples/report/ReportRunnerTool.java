/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.examples.report;

import static com.opengamma.strata.collect.Guavate.toImmutableList;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Strings;
import com.opengamma.strata.basics.Trade;
import com.opengamma.strata.calc.CalculationEngine;
import com.opengamma.strata.calc.CalculationRules;
import com.opengamma.strata.calc.Column;
import com.opengamma.strata.calc.config.pricing.PricingRules;
import com.opengamma.strata.calc.marketdata.MarketEnvironment;
import com.opengamma.strata.calc.runner.Results;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.examples.engine.ExampleEngine;
import com.opengamma.strata.examples.marketdata.ExampleMarketData;
import com.opengamma.strata.examples.marketdata.ExampleMarketDataBuilder;
import com.opengamma.strata.function.StandardComponents;
import com.opengamma.strata.product.FinanceTrade;
import com.opengamma.strata.report.Report;
import com.opengamma.strata.report.ReportCalculationResults;
import com.opengamma.strata.report.ReportRequirements;
import com.opengamma.strata.report.ReportRunner;
import com.opengamma.strata.report.ReportTemplate;
import com.opengamma.strata.report.cashflow.CashFlowReportRunner;
import com.opengamma.strata.report.cashflow.CashFlowReportTemplate;
import com.opengamma.strata.report.framework.format.ReportOutputFormat;
import com.opengamma.strata.report.trade.TradeReportRunner;
import com.opengamma.strata.report.trade.TradeReportTemplate;

/**
 * Tool for running a report from the command line.
 */
public class ReportRunnerTool {

  @Parameter(
      names = {"-t", "--template"},
      description = "Report template input file",
      required = true,
      converter = ReportTemplateParameterConverter.class)
  private ReportTemplate template;

  @Parameter(
      names = {"-m", "--marketdata"},
      description = "Market data root directory",
      validateValueWith = MarketDataRootValidator.class)
  private File marketDataRoot;

  @Parameter(
      names = {"-p", "--portfolio"},
      description = "Portfolio input file",
      required = true,
      converter = PortfolioParameterConverter.class)
  private TradePortfolio portfolio;

  @Parameter(
      names = {"-d", "--date"},
      description = "Valuation date, YYYY-MM-DD",
      required = true,
      converter = LocalDateParameterConverter.class)
  private LocalDate valuationDate;

  @Parameter(
      names = {"-f", "--format"},
      description = "Report output format, ascii or csv",
      converter = ReportOutputFormatParameterConverter.class)
  private ReportOutputFormat format = ReportOutputFormat.ASCII_TABLE;
  
  @Parameter(
      names = {"-i", "--id"},
      description = "An ID by which to select a single trade")
  private String idSearch;

  @Parameter(
      names = {"-h", "--help"},
      description = "Displays this message",
      help = true)
  private boolean help;
  
  @Parameter(
      names = {"-v", "--version"},
      description = "Prints the version of this tool",
      help = true)
  private boolean version;

  /**
   * Runs the tool.
   * 
   * @param args  the command-line arguments
   */
  public static void main(String[] args) {
    ReportRunnerTool reportRunner = new ReportRunnerTool();
    JCommander commander = new JCommander(reportRunner);
    commander.setProgramName(ReportRunnerTool.class.getSimpleName());
    try {
      commander.parse(args);
    } catch (ParameterException e) {
      System.err.println("Error: " + e.getMessage());
      System.err.println();
      commander.usage();
      return;
    }
    if (reportRunner.help) {
      commander.usage();
    } else if (reportRunner.version) {
      String versionName = ReportRunnerTool.class.getPackage().getImplementationVersion();
      if (versionName == null) {
        versionName = "unknown";
      }
      System.out.println("Strata Report Runner Tool, version " + versionName);
    } else {
      try {
        reportRunner.run();
      } catch (Exception e) {
        System.err.println(Messages.format("Error: {}\n", e.getMessage()));
        commander.usage();
      }
    }
  }

  //-------------------------------------------------------------------------
  private void run() {
    ReportRunner<ReportTemplate> reportRunner = getReportRunner(template);
    ReportRequirements requirements = reportRunner.requirements(template);
    ReportCalculationResults calculationResults = runCalculationRequirements(requirements);

    Report report = reportRunner.runReport(calculationResults, template);

    switch (format) {
      case ASCII_TABLE:
        report.writeAsciiTable(System.out);
        break;
      case CSV:
        report.writeCsv(System.out);
        break;
    }
  }

  private ReportCalculationResults runCalculationRequirements(ReportRequirements requirements) {
    List<Column> columns = requirements.getTradeMeasureRequirements();

    PricingRules pricingRules = StandardComponents.pricingRules();

    ExampleMarketDataBuilder marketDataBuilder = marketDataRoot == null ?
        ExampleMarketData.builder() : ExampleMarketDataBuilder.ofPath(marketDataRoot.toPath());

    CalculationRules rules = CalculationRules.builder()
        .pricingRules(pricingRules)
        .marketDataRules(marketDataBuilder.rules())
        .build();

    MarketEnvironment snapshot = marketDataBuilder.buildSnapshot(valuationDate);

    CalculationEngine calculationEngine = ExampleEngine.create();
    
    List<Trade> trades;

    if (Strings.nullToEmpty(idSearch).trim().isEmpty()) {
      trades = portfolio.getTrades();
    } else {
      trades = portfolio.getTrades().stream()
          .filter(t -> t instanceof FinanceTrade)
          .map(t -> (FinanceTrade) t)
          .filter(t -> t.getTradeInfo().getId().isPresent())
          .filter(t -> t.getTradeInfo().getId().get().getValue().equals(idSearch))
          .collect(toImmutableList());
      if (trades.size() > 1) {
        throw new IllegalArgumentException(
            Messages.format("More than one trade found matching ID: '{}'", idSearch));
      }
    }
    if (trades.isEmpty()) {
      throw new IllegalArgumentException("No trades found. Please check the input portfolio or trade ID filter.");
    }
    
    Results results = calculationEngine.calculate(trades, columns, rules, snapshot);
    return ReportCalculationResults.builder()
        .valuationDate(valuationDate)
        .trades(trades)
        .columns(requirements.getTradeMeasureRequirements())
        .calculationResults(results)
        .build();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private ReportRunner<ReportTemplate> getReportRunner(ReportTemplate reportTemplate) {
    // double-casts to achieve result type, allowing report runner to be used without external knowledge of template type
    if (reportTemplate instanceof TradeReportTemplate) {
      return (ReportRunner) TradeReportRunner.INSTANCE;
    } else if (reportTemplate instanceof CashFlowReportTemplate) {
      return (ReportRunner) CashFlowReportRunner.INSTANCE;
    }
    throw new IllegalArgumentException(Messages.format("Unsupported report type: {}", reportTemplate.getClass().getSimpleName()));
  }

}
