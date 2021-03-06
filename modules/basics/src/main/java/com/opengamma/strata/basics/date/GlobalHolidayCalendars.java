/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.basics.date;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static java.time.temporal.TemporalAdjusters.dayOfWeekInMonth;
import static java.time.temporal.TemporalAdjusters.firstInMonth;
import static java.time.temporal.TemporalAdjusters.lastInMonth;
import static java.util.stream.Collectors.toSet;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implementation of some common global holiday calendars.
 * <p>
 * The data provided here has been identified through direct research and is not
 * derived from a vendor of holiday calendar data.
 * This data may or may not be sufficient for your production needs.
 */
final class GlobalHolidayCalendars {

  /**
   * The holiday calendar for London, United Kingdom, with code 'GBLO'.
   * <p>
   * This constant provides the calendar for London bank holidays.
   * <p>
   * The default implementation is based on original research and covers 1950 to 2099.
   * Future dates are an extrapolations of the latest known rules.
   */
  public static final HolidayCalendar GBLO = generateLondon();
  /**
   * The holiday calendar for Paris, France, with code 'FRPA'.
   * <p>
   * This constant provides the calendar for Paris public holidays.
   * <p>
   * The default implementation is based on original research and covers 1950 to 2099.
   * Future and past dates are an extrapolations of the latest known rules.
   */
  public static final HolidayCalendar FRPA = generateParis();
  /**
   * The holiday calendar for Zurich, Switzerland, with code 'EUTA'.
   * <p>
   * This constant provides the calendar for Zurich public holidays.
   * <p>
   * The default implementation is based on original research and covers 1950 to 2099.
   * Future and past dates are an extrapolations of the latest known rules.
   */
  public static final HolidayCalendar CHZU = generateZurich();
  /**
   * The holiday calendar for the European Union TARGET system, with code 'EUTA'.
   * <p>
   * This constant provides the calendar for the TARGET interbank payment system holidays.
   * <p>
   * The default implementation is based on original research and covers 1997 to 2099.
   * Future dates are an extrapolations of the latest known rules.
   * <p>
   * Referenced by the 2006 ISDA definitions 1.8.
   */
  public static final HolidayCalendar EUTA = generateEuropeanTarget();
  /**
   * The holiday calendar for United States Government Securities, with code 'USGS'.
   * <p>
   * This constant provides the calendar for United States Government Securities as per SIFMA.
   * <p>
   * The default implementation is based on original research and covers 1950 to 2099.
   * Future and past dates are an extrapolations of the latest known rules.
   * <p>
   * Referenced by the 2006 ISDA definitions 1.11.
   */
  public static final HolidayCalendar USGS = generateUsGovtSecurities();
  /**
   * The holiday calendar for New York, United States, with code 'USNY'.
   * <p>
   * This constant provides the calendar for New York holidays.
   * <p>
   * The default implementation is based on original research and covers 1950 to 2099.
   * Future and past dates are an extrapolations of the latest known rules.
   */
  public static final HolidayCalendar USNY = generateUsNewYork();
  /**
   * The holiday calendar for the Federal Reserve Bank of New York, with code 'NYFD'.
   * <p>
   * This constant provides the calendar for the Federal Reserve Bank of New York holidays.
   * <p>
   * The default implementation is based on original research and covers 1950 to 2099.
   * Future and past dates are an extrapolations of the latest known rules.
   * <p>
   * Referenced by the 2006 ISDA definitions 1.9.
   */
  public static final HolidayCalendar NYFD = generateNewYorkFed();
  /**
   * The holiday calendar for the New York Stock Exchange, with code 'NYSE'.
   * <p>
   * This constant provides the calendar for the New York Stock Exchange.
   * <p>
   * The default implementation is based on original research and covers 1950 to 2099.
   * Future and past dates are an extrapolations of the latest known rules.
   * <p>
   * Referenced by the 2006 ISDA definitions 1.10.
   */
  public static final HolidayCalendar NYSE = generateNewYorkStockExchange();
  /**
   * The holiday calendar for Tokyo, Japan, with code 'JPTO'.
   * <p>
   * This constant provides the calendar for Tokyo bank holidays.
   * <p>
   * The default implementation is based on original research and covers 1950 to 2099.
   * Future and past dates are an extrapolations of the latest known rules.
   */
  public static final HolidayCalendar JPTO = generateTokyo();

  //-------------------------------------------------------------------------
  /**
   * Restricted constructor.
   */
  private GlobalHolidayCalendars() {
  }

  //-------------------------------------------------------------------------
  // generate GBLO
  // common law (including before 1871) good friday and christmas day (unadjusted for weekends)
  // from 1871 easter monday, whit monday, first Mon in Aug and boxing day
  // from 1965 to 1970, first in Aug moved to Mon after last Sat in Aug
  // from 1971, whitsun moved to last Mon in May, last Mon in Aug
  // from 1974, added new year
  // from 1978, added first Mon in May
  // see Hansard for specific details
  // 1965, Whitsun, Last Mon Aug - http://hansard.millbanksystems.com/commons/1964/mar/04/staggered-holidays
  // 1966, Whitsun May - http://hansard.millbanksystems.com/commons/1964/mar/04/staggered-holidays
  // 1966, 29th Aug - http://hansard.millbanksystems.com/written_answers/1965/nov/25/august-bank-holiday
  // 1967, 29th May, 28th Aug - http://hansard.millbanksystems.com/written_answers/1965/jun/03/bank-holidays-1967-and-1968
  // 1968, 3rd Jun, 2nd Sep - http://hansard.millbanksystems.com/written_answers/1965/jun/03/bank-holidays-1967-and-1968
  // 1969, 26th May, 1st Sep - http://hansard.millbanksystems.com/written_answers/1967/mar/21/bank-holidays-1969-dates
  // 1970, 25th May, 31st Aug - http://hansard.millbanksystems.com/written_answers/1967/jul/28/bank-holidays
  static ImmutableHolidayCalendar generateLondon() {
    List<LocalDate> holidays = new ArrayList<>(2000);
    for (int year = 1950; year <= 2099; year++) {
      // new year
      if (year >= 1974) {
        holidays.add(bumpToMon(first(year, 1)));
      }
      // easter
      holidays.add(easter(year).minusDays(2));
      holidays.add(easter(year).plusDays(1));
      // early May
      if (year == 1995) {
        // ve day
        holidays.add(date(1995, 5, 8));
      } else if (year >= 1978) {
        holidays.add(first(year, 5).with(firstInMonth(MONDAY)));
      }
      // spring
      if (year == 2002) {
        // golden jubilee
        holidays.add(date(2002, 6, 3));
        holidays.add(date(2002, 6, 4));
      } else if (year == 2012) {
        // diamond jubilee
        holidays.add(date(2012, 6, 4));
        holidays.add(date(2012, 6, 5));
      } else if (year == 1967 || year == 1970) {
        holidays.add(first(year, 5).with(lastInMonth(MONDAY)));
      } else if (year < 1971) {
        // whitsun
        holidays.add(easter(year).plusDays(50));
      } else {
        holidays.add(first(year, 5).with(lastInMonth(MONDAY)));
      }
      // summer
      if (year < 1965) {
        holidays.add(first(year, 8).with(firstInMonth(MONDAY)));
      } else if (year < 1971) {
        holidays.add(first(year, 8).with(lastInMonth(SATURDAY)).plusDays(2));
      } else {
        holidays.add(first(year, 8).with(lastInMonth(MONDAY)));
      }
      // christmas
      holidays.add(christmas(year));
      holidays.add(boxingDay(year));
    }
    holidays.add(date(2011, 4, 29));  // royal wedding
    holidays.add(date(1999, 12, 31));  // millenium
    removeSatSun(holidays);
    return ImmutableHolidayCalendar.of("GBLO", holidays, SATURDAY, SUNDAY);
  }

  //-------------------------------------------------------------------------
  // generate FRPA
  // data sources
  // http://www.legifrance.gouv.fr/affichCodeArticle.do?idArticle=LEGIARTI000006902611&cidTexte=LEGITEXT000006072050
  // http://jollyday.sourceforge.net/data/fr.html
  // Euronext holidays only New Year, Good Friday, Easter Monday, Labour Day, Christmas Day, Boxing Day
  // New Years Eve is holiday for cash markets and derivatives in 2015
  // https://www.euronext.com/en/holidays-and-hours
  // https://www.euronext.com/en/trading/nyse-euronext-trading-calendar/archives
  // evidence suggests that Monday is holiday when Tuesday is, and Friday is holiday when Thursday is
  static ImmutableHolidayCalendar generateParis() {
    List<LocalDate> holidays = new ArrayList<>(2000);
    for (int year = 1950; year <= 2099; year++) {
      holidays.add(date(year, 1, 1));  // new year
      holidays.add(easter(year).minusDays(2));  // good friday
      holidays.add(easter(year).plusDays(1));  // easter monday
      holidays.add(date(year, 5, 1));  // labour day
      holidays.add(date(year, 5, 8));  // victory in europe
      holidays.add(easter(year).plusDays(39));  // ascension day
      if (year <= 2004 || year >= 2008) {
        holidays.add(easter(year).plusDays(50));  // whit monday
      }
      holidays.add(date(year, 7, 14));  // bastille
      holidays.add(date(year, 8, 15));  // assumption of mary
      holidays.add(date(year, 11, 1));  // all saints
      holidays.add(date(year, 11, 11));  // armistice day
      holidays.add(date(year, 12, 25));  // christmas day
      holidays.add(date(year, 12, 26));  // saint stephen
    }
    holidays.add(date(1999, 12, 31));  // millenium
    applyBridging(holidays);
    removeSatSun(holidays);
    return ImmutableHolidayCalendar.of("FRPA", holidays, SATURDAY, SUNDAY);
  }

  //-------------------------------------------------------------------------
  // generate CHZU
  // data sources
  // http://jollyday.sourceforge.net/data/ch.html
  // https://github.com/lballabio/quantlib/blob/master/QuantLib/ql/time/calendars/switzerland.cpp
  // http://www.six-swiss-exchange.com/funds/trading/trading_and_settlement_calendar_en.html
  // http://www.six-swiss-exchange.com/swx_messages/online/swx7299e.pdf
  static ImmutableHolidayCalendar generateZurich() {
    List<LocalDate> holidays = new ArrayList<>(2000);
    for (int year = 1950; year <= 2099; year++) {
      holidays.add(date(year, 1, 1));  // new year
      holidays.add(date(year, 1, 2));  // saint berchtoldstag
      holidays.add(easter(year).minusDays(2));  // good friday
      holidays.add(easter(year).plusDays(1));  // easter monday
      holidays.add(date(year, 5, 1));  // labour day
      holidays.add(easter(year).plusDays(39));  // ascension day
      holidays.add(easter(year).plusDays(50));  // whit monday
      holidays.add(date(year, 8, 1));  // national day
      holidays.add(date(year, 12, 25));  // christmas day
      holidays.add(date(year, 12, 26));  // saint stephen
    }
    holidays.add(date(1999, 12, 31));  // millenium
    holidays.add(date(2000, 1, 3));  // millenium
    removeSatSun(holidays);
    return ImmutableHolidayCalendar.of("CHZU", holidays, SATURDAY, SUNDAY);
  }

  //-------------------------------------------------------------------------
  // generate EUTA
  // 1997 - 1998 (testing phase), Jan 1, christmas day
  // https://www.ecb.europa.eu/pub/pdf/other/tagien.pdf
  // in 1999, Jan 1, christmas day, Dec 26, Dec 31
  // http://www.ecb.europa.eu/press/pr/date/1999/html/pr990715_1.en.html
  // http://www.ecb.europa.eu/press/pr/date/1999/html/pr990331.en.html
  // in 2000, Jan 1, good friday, easter monday, May 1, christmas day, Dec 26
  // http://www.ecb.europa.eu/press/pr/date/1999/html/pr990715_1.en.html
  // in 2001, Jan 1, good friday, easter monday, May 1, christmas day, Dec 26, Dec 31
  // http://www.ecb.europa.eu/press/pr/date/2000/html/pr000525_2.en.html
  // from 2002, Jan 1, good friday, easter monday, May 1, christmas day, Dec 26
  // http://www.ecb.europa.eu/press/pr/date/2000/html/pr001214_4.en.html
  static ImmutableHolidayCalendar generateEuropeanTarget() {
    List<LocalDate> holidays = new ArrayList<>(2000);
    for (int year = 1997; year <= 2099; year++) {
      if (year >= 2000) {
        holidays.add(date(year, 1, 1));
        holidays.add(easter(year).minusDays(2));
        holidays.add(easter(year).plusDays(1));
        holidays.add(date(year, 5, 1));
        holidays.add(date(year, 12, 25));
        holidays.add(date(year, 12, 26));
      } else {  // 1997 to 1999
        holidays.add(date(year, 1, 1));
        holidays.add(date(year, 12, 25));
      }
      if (year == 1999 || year == 2001) {
        holidays.add(date(year, 12, 31));
      }
    }
    removeSatSun(holidays);
    return ImmutableHolidayCalendar.of("EUTA", holidays, SATURDAY, SUNDAY);
  }

  //-------------------------------------------------------------------------
  // common US holidays
  private static void usCommon(
      List<LocalDate> holidays, int year, boolean bumpBack, boolean columbusVeteran, int mlkStartYear) {
    // new year, adjusted if Sunday
    holidays.add(bumpSunToMon(date(year, 1, 1)));
    // martin luther king
    if (year >= mlkStartYear) {
      holidays.add(date(year, 1, 1).with(dayOfWeekInMonth(3, MONDAY)));
    }
    // washington
    if (year < 1971) {
      holidays.add(bumpSunToMon(date(year, 2, 22)));
    } else {
      holidays.add(date(year, 2, 1).with(dayOfWeekInMonth(3, MONDAY)));
    }
    // memorial
    if (year < 1971) {
      holidays.add(bumpSunToMon(date(year, 5, 30)));
    } else {
      holidays.add(date(year, 5, 1).with(lastInMonth(MONDAY)));
    }
    // labor day
    holidays.add(date(year, 9, 1).with(firstInMonth(MONDAY)));
    // columbus day
    if (columbusVeteran) {
      if (year < 1971) {
        holidays.add(bumpSunToMon(date(year, 10, 12)));
      } else {
        holidays.add(date(year, 10, 1).with(dayOfWeekInMonth(2, MONDAY)));
      }
    }
    // veterans day
    if (columbusVeteran) {
      if (year >= 1971 && year < 1978) {
        holidays.add(date(year, 10, 1).with(dayOfWeekInMonth(4, MONDAY)));
      } else {
        holidays.add(bumpSunToMon(date(year, 11, 11)));
      }
    }
    // thanksgiving
    holidays.add(date(year, 11, 1).with(dayOfWeekInMonth(4, THURSDAY)));
    // independence day & christmas day
    if (bumpBack) {
      holidays.add(bumpToFriOrMon(date(year, 7, 4)));
      holidays.add(bumpToFriOrMon(date(year, 12, 25)));
    } else {
      holidays.add(bumpSunToMon(date(year, 7, 4)));
      holidays.add(bumpSunToMon(date(year, 12, 25)));
    }
  }

  // generate USGS
  // http://www.sifma.org/services/holiday-schedule/
  static ImmutableHolidayCalendar generateUsGovtSecurities() {
    List<LocalDate> holidays = new ArrayList<>(2000);
    for (int year = 1950; year <= 2099; year++) {
      usCommon(holidays, year, true, true, 1986);
      // good friday, in 1999/2007 only a partial holiday
      holidays.add(easter(year).minusDays(2));
      // hurricane sandy
      if (year == 2012) {
        holidays.add(date(year, 10, 30));
      }
    }
    removeSatSun(holidays);
    return ImmutableHolidayCalendar.of("USGS", holidays, SATURDAY, SUNDAY);
  }

  //-------------------------------------------------------------------------
  // generate USNY
  // http://www.cs.ny.gov/attendance_leave/2012_legal_holidays.cfm
  // http://www.cs.ny.gov/attendance_leave/2013_legal_holidays.cfm
  // etc
  // ignore election day and lincoln day
  static ImmutableHolidayCalendar generateUsNewYork() {
    List<LocalDate> holidays = new ArrayList<>(2000);
    for (int year = 1950; year <= 2099; year++) {
      usCommon(holidays, year, false, true, 1986);
    }
    removeSatSun(holidays);
    return ImmutableHolidayCalendar.of("USNY", holidays, SATURDAY, SUNDAY);
  }

  //-------------------------------------------------------------------------
  // generate NYFD
  // http://www.ny.frb.org/aboutthefed/holiday_schedule.html
  static ImmutableHolidayCalendar generateNewYorkFed() {
    List<LocalDate> holidays = new ArrayList<>(2000);
    for (int year = 1950; year <= 2099; year++) {
      usCommon(holidays, year, false, true, 1986);
    }
    removeSatSun(holidays);
    return ImmutableHolidayCalendar.of("NYFD", holidays, SATURDAY, SUNDAY);
  }

  //-------------------------------------------------------------------------
  // generate NYSE
  // https://www.nyse.com/markets/hours-calendars
  // http://www1.nyse.com/pdfs/closings.pdf
  static ImmutableHolidayCalendar generateNewYorkStockExchange() {
    List<LocalDate> holidays = new ArrayList<>(2000);
    for (int year = 1950; year <= 2099; year++) {
      usCommon(holidays, year, true, false, 1998);
      // good friday
      holidays.add(easter(year).minusDays(2));
    }
    // Lincoln day 1896-1953
    // Columbus day 1909-1953
    // Veterans day 1934-1953
    for (int i = 1950; i <= 1953; i++) {
      holidays.add(date(i, 2, 12));
      holidays.add(date(i, 10, 12));
      holidays.add(date(i, 11, 11));
    }
    // election day, Tue after first Monday of November
    for (int i = 1950; i <= 1968; i++) {
      holidays.add(date(i, 11, 1).with(TemporalAdjusters.nextOrSame(MONDAY)).plusDays(1));
    }
    holidays.add(date(1972, 11, 7));
    holidays.add(date(1976, 11, 2));
    holidays.add(date(1980, 11, 4));
    // special days
    holidays.add(date(1955, 12, 24));  // Christmas Eve
    holidays.add(date(1956, 12, 24));  // Christmas Eve
    holidays.add(date(1958, 12, 26));  // Day after Christmas
    holidays.add(date(1961, 5, 29));  // Decoration day
    holidays.add(date(1963, 11, 25));  // Death of John F Kennedy
    holidays.add(date(1965, 12, 24));  // Christmas Eve
    holidays.add(date(1968, 2, 12));  // Lincoln birthday
    holidays.add(date(1968, 4, 9));  // Death of Martin Luther King
    holidays.add(date(1968, 6, 12));  // Paperwork crisis
    holidays.add(date(1968, 6, 19));  // Paperwork crisis
    holidays.add(date(1968, 6, 26));  // Paperwork crisis
    holidays.add(date(1968, 7, 3));  // Paperwork crisis
    holidays.add(date(1968, 7, 5));  // Day after independence
    holidays.add(date(1968, 7, 10));  // Paperwork crisis
    holidays.add(date(1968, 7, 17));  // Paperwork crisis
    holidays.add(date(1968, 7, 24));  // Paperwork crisis
    holidays.add(date(1968, 7, 31));  // Paperwork crisis
    holidays.add(date(1968, 8, 7));  // Paperwork crisis
    holidays.add(date(1968, 8, 13));  // Paperwork crisis
    holidays.add(date(1968, 8, 21));  // Paperwork crisis
    holidays.add(date(1968, 8, 28));  // Paperwork crisis
    holidays.add(date(1968, 9, 4));  // Paperwork crisis
    holidays.add(date(1968, 9, 11));  // Paperwork crisis
    holidays.add(date(1968, 9, 18));  // Paperwork crisis
    holidays.add(date(1968, 9, 25));  // Paperwork crisis
    holidays.add(date(1968, 10, 2));  // Paperwork crisis
    holidays.add(date(1968, 10, 9));  // Paperwork crisis
    holidays.add(date(1968, 10, 16));  // Paperwork crisis
    holidays.add(date(1968, 10, 23));  // Paperwork crisis
    holidays.add(date(1968, 10, 30));  // Paperwork crisis
    holidays.add(date(1968, 11, 6));  // Paperwork crisis
    holidays.add(date(1968, 11, 13));  // Paperwork crisis
    holidays.add(date(1968, 11, 20));  // Paperwork crisis
    holidays.add(date(1968, 11, 27));  // Paperwork crisis
    holidays.add(date(1968, 12, 4));  // Paperwork crisis
    holidays.add(date(1968, 12, 11));  // Paperwork crisis
    holidays.add(date(1968, 12, 18));  // Paperwork crisis
    holidays.add(date(1968, 12, 25));  // Paperwork crisis
    holidays.add(date(1968, 12, 31));  // Paperwork crisis
    holidays.add(date(1969, 2, 10));  // Snow
    holidays.add(date(1969, 3, 31));  // Death of Dwight Eisenhower
    holidays.add(date(1969, 7, 21));  // Lunar exploration
    holidays.add(date(1972, 12, 28));  // Death of Harry Truman
    holidays.add(date(1973, 1, 25));  // Death of Lyndon Johnson
    holidays.add(date(1977, 7, 14));  // Blackout
    holidays.add(date(1985, 9, 27));  // Hurricane Gloria
    holidays.add(date(1994, 4, 27));  // Death of Richard Nixon
    holidays.add(date(2001, 9, 11));  // 9/11 attack
    holidays.add(date(2001, 9, 12));  // 9/11 attack
    holidays.add(date(2001, 9, 13));  // 9/11 attack
    holidays.add(date(2001, 9, 14));  // 9/11 attack
    holidays.add(date(2004, 6, 11));  // Death of Ronald Reagan
    holidays.add(date(2007, 1, 2));  // Death of Gerald Ford
    holidays.add(date(2012, 10, 30));  // Hurricane Sandy
    removeSatSun(holidays);
    return ImmutableHolidayCalendar.of("NYSE", holidays, SATURDAY, SUNDAY);
  }

  //-------------------------------------------------------------------------
  // generate JPTO
  // data sources
  // https://www.boj.or.jp/en/about/outline/holi.htm/
  // http://web.archive.org/web/20110513190217/http://www.boj.or.jp/en/about/outline/holi.htm/
  // http://web.archive.org/web/20130502031733/http://www.boj.or.jp/en/about/outline/holi.htm
  // http://www8.cao.go.jp/chosei/shukujitsu/gaiyou.html (law)
  // http://www.nao.ac.jp/faq/a0301.html (equinox)
  // http://eco.mtk.nao.ac.jp/koyomi/faq/holiday.html.en
  static ImmutableHolidayCalendar generateTokyo() {
    List<LocalDate> holidays = new ArrayList<>(2000);
    for (int year = 1950; year <= 2099; year++) {
      // new year
      holidays.add(date(year, 1, 1));
      holidays.add(date(year, 1, 2));
      holidays.add(date(year, 1, 3));
      // coming of age
      if (year >= 2000) {
        holidays.add(date(year, 1, 1).with(dayOfWeekInMonth(2, MONDAY)));
      } else {
        holidays.add(bumpSunToMon(date(year, 1, 15)));
      }
      // national foundation
      if (year >= 1967) {
        holidays.add(bumpSunToMon(date(year, 2, 11)));
      }
      // vernal equinox (from 1948), 20th or 21st (predictions/facts 2000 to 2030)
      if (year == 2000 || year == 2001 || year == 2004 || year == 2005 || year == 2008 || year == 2009 ||
          year == 2012 || year == 2013 || year == 2016 || year == 2017 ||
          year == 2020 || year == 2021 || year == 2024 || year == 2025 || year == 2026 || year == 2028 ||
          year == 2029 || year == 2030) {
        holidays.add(bumpSunToMon(date(year, 3, 20)));
      } else {
        holidays.add(bumpSunToMon(date(year, 3, 21)));
      }
      // showa (from 2007 onwards), greenery (from 1989 to 2006), emperor (before 1989)
      // http://news.bbc.co.uk/1/hi/world/asia-pacific/4543461.stm
      holidays.add(bumpSunToMon(date(year, 4, 29)));
      // constitution (from 1948)
      // greenery (from 2007 onwards), holiday between two other holidays before that (from 1985)
      // children (from 1948)
      if (year >= 1985) {
        holidays.add(bumpSunToMon(date(year, 5, 3)));
        holidays.add(bumpSunToMon(date(year, 5, 4)));
        holidays.add(bumpSunToMon(date(year, 5, 5)));
        if (year >= 2007 && (date(year, 5, 3).getDayOfWeek() == SUNDAY || date(year, 5, 4).getDayOfWeek() == SUNDAY)) {
          holidays.add(date(year, 5, 6));
        }
      } else {
        holidays.add(bumpSunToMon(date(year, 5, 3)));
        holidays.add(bumpSunToMon(date(year, 5, 5)));
      }
      // marine
      if (year >= 2003) {
        holidays.add(date(year, 7, 1).with(dayOfWeekInMonth(3, MONDAY)));
      } else if (year >= 1996) {
        holidays.add(bumpSunToMon(date(year, 7, 20)));
      }
      // mountain
      if (year >= 2016) {
        holidays.add(bumpSunToMon(date(year, 8, 11)));
      }
      // aged
      if (year >= 2003) {
        holidays.add(date(year, 9, 1).with(dayOfWeekInMonth(3, MONDAY)));
      } else if (year >= 1966) {
        holidays.add(bumpSunToMon(date(year, 9, 15)));
      }
      // autumn equinox (from 1948), 22nd or 23rd (predictions/facts 2000 to 2030)
      if (year == 2012 || year == 2016 || year == 2020 || year == 2024 || year == 2028) {
        holidays.add(bumpSunToMon(date(year, 9, 22)));
      } else {
        holidays.add(bumpSunToMon(date(year, 9, 23)));
      }
      citizensDay(holidays, date(year, 9, 20), date(year, 9, 22));
      citizensDay(holidays, date(year, 9, 21), date(year, 9, 23));
      // health-sports
      if (year >= 2000) {
        holidays.add(date(year, 10, 1).with(dayOfWeekInMonth(2, MONDAY)));
      } else if (year >= 1966) {
        holidays.add(bumpSunToMon(date(year, 10, 10)));
      }
      // culture (from 1948)
      holidays.add(bumpSunToMon(date(year, 11, 3)));
      // labor (from 1948)
      holidays.add(bumpSunToMon(date(year, 11, 23)));
      // emperor (current emporer)
      if (year >= 1990) {
        holidays.add(bumpSunToMon(date(year, 12, 23)));
      }
      // new years eve - bank of Japan, but not national holiday
      holidays.add(bumpSunToMon(date(year, 12, 31)));
    }
    holidays.add(date(1959, 4, 10));  // marriage akihito
    holidays.add(date(1989, 2, 24));  // funeral showa
    holidays.add(date(1990, 11, 12));  // enthrone akihito
    holidays.add(date(1993, 6, 9));  // marriage naruhito
    removeSatSun(holidays);
    return ImmutableHolidayCalendar.of("JPTO", holidays, SATURDAY, SUNDAY);
  }

  // extra day between two other holidays, appears to exclude weekends
  private static void citizensDay(List<LocalDate> holidays, LocalDate date1, LocalDate date2) {
    if (holidays.contains(date1) && holidays.contains(date2)) {
      if (date1.getDayOfWeek() == MONDAY || date1.getDayOfWeek() == TUESDAY || date1.getDayOfWeek() == WEDNESDAY) {
        holidays.add(date1.plusDays(1));
      }
    }
  }

  //-------------------------------------------------------------------------
  // date
  private static LocalDate date(int year, int month, int day) {
    return LocalDate.of(year, month, day);
  }

  // bump to following Monday
  private static LocalDate bumpToMon(LocalDate date) {
    if (date.getDayOfWeek() == SATURDAY) {
      return date.plusDays(2);
    } else if (date.getDayOfWeek() == SUNDAY) {
      return date.plusDays(1);
    }
    return date;
  }

  // bump Sunday to following Monday
  private static LocalDate bumpSunToMon(LocalDate date) {
    if (date.getDayOfWeek() == SUNDAY) {
      return date.plusDays(1);
    }
    return date;
  }

  // bump to Saturday to Friday and Sunday to Monday
  private static LocalDate bumpToFriOrMon(LocalDate date) {
    if (date.getDayOfWeek() == SATURDAY) {
      return date.minusDays(1);
    } else if (date.getDayOfWeek() == SUNDAY) {
      return date.plusDays(1);
    }
    return date;
  }

  // christmas
  private static LocalDate christmas(int year) {
    LocalDate base = LocalDate.of(year, 12, 25);
    if (base.getDayOfWeek() == SATURDAY || base.getDayOfWeek() == SUNDAY) {
      return LocalDate.of(year, 12, 27);
    }
    return base;
  }

  // boxing day
  private static LocalDate boxingDay(int year) {
    LocalDate base = LocalDate.of(year, 12, 26);
    if (base.getDayOfWeek() == SATURDAY || base.getDayOfWeek() == SUNDAY) {
      return LocalDate.of(year, 12, 28);
    }
    return base;
  }

  // first of a month
  private static LocalDate first(int year, int month) {
    return LocalDate.of(year, month, 1);
  }

  // remove any holidays covered by Sat/Sun
  private static void removeSatSun(List<LocalDate> holidays) {
    holidays.removeIf(date -> date.getDayOfWeek() == SATURDAY || date.getDayOfWeek() == SUNDAY);
  }

  // apply bridging (Mon/Fri are holidays if Tue/Thu are)
  private static void applyBridging(List<LocalDate> holidays) {
    Set<LocalDate> additional1 = holidays.stream()
        .filter(date -> date.getDayOfWeek() == TUESDAY &&
            !MonthDay.from(date).equals(MonthDay.of(1, 1)))
        .map(date -> date.minusDays(1))
        .collect(toSet());
    Set<LocalDate> additional2 = holidays.stream()
        .filter(date -> date.getDayOfWeek() == THURSDAY &&
            !MonthDay.from(date).equals(MonthDay.of(12, 26)))
        .map(date -> date.plusDays(1))
        .collect(toSet());
    holidays.addAll(additional1);
    holidays.addAll(additional2);
  }

  // calculate easter day by Delambre
  static LocalDate easter(int year) {
    int a = year % 19;
    int b = year / 100;
    int c = year % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int month = (h + l - 7 * m + 114) / 31;
    int day = ((h + l - 7 * m + 114) % 31) + 1;
    return LocalDate.of(year, month, day);
  }

}
