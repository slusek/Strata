/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.basics.index;

import static com.opengamma.strata.basics.date.BusinessDayConventions.FOLLOWING;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.BusinessDayConventions.PRECEDING;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.PeriodAdditionConvention;
import com.opengamma.strata.basics.date.PeriodAdditionConventions;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.date.TenorAdjustment;
import com.opengamma.strata.collect.io.CsvFile;
import com.opengamma.strata.collect.io.ResourceConfig;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.named.NamedLookup;

/**
 * Loads standard Ibor Index implementations from CSV.
 * <p>
 * See {@link IborIndices} for the description of each.
 */
final class IborIndexCsvLookup
    implements NamedLookup<IborIndex> {

  // http://www.opengamma.com/sites/default/files/interest-rate-instruments-and-market-conventions.pdf
  // LIBOR - http://www.bbalibor.com/technical-aspects/fixing-value-and-maturity
  // different rules for overnight
  // EURIBOR - http://www.bbalibor.com/technical-aspects/fixing-value-and-maturity
  // EURIBOR - http://www.emmi-benchmarks.eu/assets/files/Euribor_code_conduct.pdf
  // TIBOR - http://www.jbatibor.or.jp/english/public/

  /**
   * The logger.
   */
  private static final Logger log = Logger.getLogger(IborIndexCsvLookup.class.getName());
  /**
   * The singleton instance of the lookup.
   */
  public static final IborIndexCsvLookup INSTANCE = new IborIndexCsvLookup();

  // CSV column headers
  private static final String NAME_FIELD = "Name";
  private static final String CURRENCY_FIELD = "Currency";
  private static final String DAY_COUNT_FIELD = "Day Count";
  private static final String FIXING_CALENDAR_FIELD = "Fixing Calendar";
  private static final String OFFSET_DAYS_FIELD = "Offset Days";
  private static final String OFFSET_CALENDAR_FIELD = "Offset Calendar";
  private static final String EFFECTIVE_DATE_CALENDAR_FIELD = "Effective Date Calendar";
  private static final String TENOR_FIELD = "Tenor";
  private static final String TENOR_CONVENTION_FIELD = "Tenor Convention";

  /**
   * The cache by name.
   */
  private static final ImmutableMap<String, IborIndex> BY_NAME = loadFromCsv();

  /**
   * Restricted constructor.
   */
  private IborIndexCsvLookup() {
  }

  //-------------------------------------------------------------------------
  @Override
  public Map<String, IborIndex> lookupAll() {
    return BY_NAME;
  }

  private static ImmutableMap<String, IborIndex> loadFromCsv() {
    List<ResourceLocator> resources = ResourceConfig.orderedResources("IborIndexData.csv");
    Map<String, IborIndex> map = new HashMap<>();
    for (ResourceLocator resource : resources) {
      try {
        CsvFile csv = CsvFile.of(resource.getCharSource(), true);
        for (int i = 0; i < csv.rowCount(); i++) {
          IborIndex parsed = parseIborIndex(csv, i);
          map.put(parsed.getName(), parsed);
        }
      } catch (RuntimeException ex) {
        log.log(Level.SEVERE, "Error processing resource as Ibor Index CSV file: " + resource, ex);
        return ImmutableMap.of();
      }
    }
    return ImmutableMap.copyOf(map);
  }

  private static IborIndex parseIborIndex(CsvFile csv, int row) {
    String name = csv.field(row, NAME_FIELD);
    Currency currency = Currency.parse(csv.field(row, CURRENCY_FIELD));
    DayCount dayCount = DayCount.of(csv.field(row, DAY_COUNT_FIELD));
    HolidayCalendar fixingCal = HolidayCalendar.of(csv.field(row, FIXING_CALENDAR_FIELD));
    int offsetDays = Integer.parseInt(csv.field(row, OFFSET_DAYS_FIELD));
    HolidayCalendar offsetCal = HolidayCalendar.of(csv.field(row, OFFSET_CALENDAR_FIELD));
    HolidayCalendar effectiveCal = HolidayCalendar.of(csv.field(row, EFFECTIVE_DATE_CALENDAR_FIELD));
    Tenor tenor = Tenor.parse(csv.field(row, TENOR_FIELD));
    PeriodAdditionConvention tenorConvention = PeriodAdditionConvention.of(csv.field(row, TENOR_CONVENTION_FIELD));
    // interpret CSV
    DaysAdjustment fixingOffset = DaysAdjustment.ofBusinessDays(
        -offsetDays, offsetCal, BusinessDayAdjustment.of(PRECEDING, fixingCal)).normalize();
    DaysAdjustment effectiveOffset = DaysAdjustment.ofBusinessDays(
        offsetDays, offsetCal, BusinessDayAdjustment.of(FOLLOWING, effectiveCal)).normalize();
    BusinessDayAdjustment adj = BusinessDayAdjustment.of(
        isEndOfMonth(tenorConvention) ? MODIFIED_FOLLOWING : FOLLOWING,
        effectiveCal);
    TenorAdjustment tenorAdjustment = TenorAdjustment.of(tenor, tenorConvention, adj);
    // build result
    return ImmutableIborIndex.builder()
        .name(name)
        .currency(currency)
        .dayCount(dayCount)
        .fixingCalendar(fixingCal)
        .fixingDateOffset(fixingOffset)
        .effectiveDateOffset(effectiveOffset)
        .maturityDateOffset(tenorAdjustment)
        .build();
  }

  private static boolean isEndOfMonth(PeriodAdditionConvention tenorConvention) {
    return tenorConvention.equals(PeriodAdditionConventions.LAST_BUSINESS_DAY) ||
        tenorConvention.equals(PeriodAdditionConventions.LAST_DAY);
  }

}
