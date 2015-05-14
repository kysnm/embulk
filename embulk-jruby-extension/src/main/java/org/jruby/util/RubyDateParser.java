package org.jruby.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jruby.RubyString;
import org.jruby.runtime.ThreadContext;

import static org.jruby.util.RubyDateFormatter.Format;
import static org.jruby.util.RubyDateFormatter.Token;

public class RubyDateParser
{
    private static String[] dayNames = new String[] {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
            "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
    };

    private static String[] monNames = new String[] {
            "January", "February", "March", "April", "May", "June", "July", "August", "September",
            "October", "November", "December", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private static String[] meridNames = new String[] {
            "am", "pm", "a.m.", "p.m."
    };

    private static final Pattern ZONE_PARSE_REGEX = Pattern.compile("\\A(" +
                    "(?:gmt|utc?)?[-+]\\d+(?:[,.:]\\d+(?::\\d+)?)?" +
                    "|(?-i:[[\\p{Alpha}].\\s]+)(?:standard|daylight)\\s+time\\b" +
                    "|(?-i:[[\\p{Alpha}]]+)(?:\\s+dst)?\\b" +
                    ")"
    );

    private int matchAtPatterns(String text, int pos, String[] patterns)
    {
        int patIndex = -1;
        for (int i = 0; i < patterns.length; i++) {
            String pattern = patterns[i];
            int len = pattern.length();
            try {
                if (pattern.equalsIgnoreCase(text.substring(pos, pos + len))) { // IndexOutOfBounds
                    patIndex = i;
                    break;
                }
            } catch (IndexOutOfBoundsException e) {
                // ignorable error
            }
        }
        return patIndex;
    }

    private static boolean isValidRange(int v, int lower, int upper)
    {
        return lower <= v && v <= upper;
    }

    static boolean isDigit(char c)
    {
        return '0' <= c && c <= '9';
    }

    static boolean isSign(char c)
    {
        return c == '+' || c == '-';
    }

    static int toInt(char c)
    {
        return c - '0';
    }

    private static Set<Format> numPatterns; // CDdeFGgHIjkLlMmNQRrSsTUuVvWwXxYy
    static {
        numPatterns = new HashSet<>();
        numPatterns.add(Format.FORMAT_CENTURY); // 'C'
        // D
        numPatterns.add(Format.FORMAT_DAY); // 'd'
        numPatterns.add(Format.FORMAT_DAY_S); // 'e'
        // F
        numPatterns.add(Format.FORMAT_WEEKYEAR); // 'G'
        numPatterns.add(Format.FORMAT_WEEKYEAR_SHORT); // 'g'
        numPatterns.add(Format.FORMAT_HOUR); // 'H'
        numPatterns.add(Format.FORMAT_HOUR_M); // 'I'
        numPatterns.add(Format.FORMAT_DAY_YEAR); // 'j'
        numPatterns.add(Format.FORMAT_HOUR_BLANK); // 'k'
        numPatterns.add(Format.FORMAT_MILLISEC); // 'L'
        numPatterns.add(Format.FORMAT_HOUR_S); // 'l'
        numPatterns.add(Format.FORMAT_MINUTES); // 'M'
        numPatterns.add(Format.FORMAT_MONTH); // 'm'
        numPatterns.add(Format.FORMAT_NANOSEC); // 'N'
        // Q, R, r
        numPatterns.add(Format.FORMAT_SECONDS); // 'S'
        numPatterns.add(Format.FORMAT_EPOCH); // 's'
        // T
        numPatterns.add(Format.FORMAT_WEEK_YEAR_S); // 'U'
        numPatterns.add(Format.FORMAT_DAY_WEEK2); // 'u'
        numPatterns.add(Format.FORMAT_WEEK_WEEKYEAR); // 'V'
        // v
        numPatterns.add(Format.FORMAT_WEEK_YEAR_M); // 'W'
        numPatterns.add(Format.FORMAT_DAY_WEEK); // 'w'
        // X, x
        numPatterns.add(Format.FORMAT_YEAR_LONG); // 'Y'
        numPatterns.add(Format.FORMAT_YEAR_SHORT); // 'y'
    }

    private static boolean matchAtNumPatterns(Token token) // NUM_PATTERN_P
    {
        // TODO
        Format f = token.getFormat();
        if (f == Format.FORMAT_STRING && isDigit(((String)token.getData()).charAt(0))) {
            return true;

        } else if (numPatterns.contains(f)) {
            return true;

        }
        return false;
    }

    private static RuntimeException newInvalidDataException() // Token token, int v
    {
        return new RuntimeException("Invalid Data"); // TODO InvalidDataException
    }

    private final ThreadContext context;
    // Use RubyDateFormatter temporarily because it has useful lexer, token and format types
    private final RubyDateFormatter dateFormat;

    private List<Token> compiledPattern;
    private int pos;
    private String text;

    public RubyDateParser(ThreadContext context)
    {
        this.context = context;
        this.dateFormat = new RubyDateFormatter(context);
    }

    public List<Token> compilePattern(String format)
    {
        return compilePattern(context.runtime.newString(format), false);
    }

    public List<Token> compilePattern(RubyString format, boolean dateLibrary)
    {
        return dateFormat.compilePattern(format, dateLibrary);
    }

    // TODO RubyTime parse(RubyString format, RubyString text);
    // TODO RubyTime parse(List<Token> compiledPattern, RubyString text);

    public Temporal date_strptime(List<Token> compiledPattern, String text)
    {
        ParsedValues values = date_strptime_internal(compiledPattern, text);
        return Temporal.newTemporal(values);
    }

    ParsedValues date_strptime_internal(List<Token> compiledPattern, String text)
    {
        pos = 0;
        this.text = text;

        ParsedValues values = new ParsedValues();

        for (Token token : compiledPattern) {
            switch (token.getFormat()) {
                case FORMAT_ENCODING:
                    continue; // skip
                case FORMAT_OUTPUT:
                    continue; // skip
                case FORMAT_STRING:
                    pos += token.getData().toString().length();
                    break;
                case FORMAT_WEEK_LONG: // %A - The full weekday name (``Sunday'')
                case FORMAT_WEEK_SHORT: // %a - The abbreviated name (``Sun'')
                {
                    int dayIndex = matchAtPatterns(text, pos, dayNames);
                    if (dayIndex >= 0) {
                        values.wday = dayIndex % 7;
                        pos += dayNames[dayIndex].length();
                    } else {
                        values.fail();
                    }
                    break;
                }
                case FORMAT_MONTH_LONG: // %B - The full month name (``January'')
                case FORMAT_MONTH_SHORT: // %b, %h - The abbreviated month name (``Jan'')
                {
                    int monIndex = matchAtPatterns(text, pos, monNames);
                    if (monIndex >= 0) {
                        values.mon = monIndex % 12 + 1;
                        pos += monNames[monIndex].length();
                    } else {
                        values.fail();
                    }
                    break;
                }
                case FORMAT_CENTURY: // %C - year / 100 (round down.  20 in 2009)
                {
                    int c;
                    if (matchAtNumPatterns(token)) {
                        c = readDigits(2);
                    } else {
                        c = readDigitsMax();
                    }
                    values._cent = c;
                    break;
                }
                case FORMAT_DAY: // %d, %Od - Day of the month, zero-padded (01..31)
                case FORMAT_DAY_S: // %e, %Oe - Day of the month, blank-padded ( 1..31)
                {
                    int d;
                    if (text.charAt(pos) == ' ') { // brank or not
                        pos += 1; // brank
                        d = readDigits(1);
                    } else {
                        d = readDigits(2);
                    }

                    if (!isValidRange(d, 1, 31)) {
                        values.fail();
                    }
                    values.mday = d;
                    break;
                }
                case FORMAT_WEEKYEAR: // %G - The week-based year
                {
                    int y;
                    if (matchAtNumPatterns(token)) {
                        y = readDigits(4);
                    } else {
                        y = readDigitsMax();
                    }
                    values.cwyear = y;
                    break;
                }
                case FORMAT_WEEKYEAR_SHORT: // %g - The last 2 digits of the week-based year (00..99)
                {
                    int v = readDigits(2);
                    if (!isValidRange(v, 0, 99)) {
                        values.fail();
                    }
                    values.cwyear = v;
                    if (values._cent < 0) {
                        values._cent = v >= 69 ? 19 : 20;
                    }
                    break;
                }
                case FORMAT_HOUR: // %H, %OH - Hour of the day, 24-hour clock, zero-padded (00..23)
                case FORMAT_HOUR_BLANK: // %k - Hour of the day, 24-hour clock, blank-padded ( 0..23)
                {
                    int h;
                    if (text.charAt(pos) == ' ') { // brank or not
                        pos += 1; // brank
                        h = readDigits(1);
                    } else {
                        h = readDigits(2);
                    }

                    if (!isValidRange(h, 0, 23)) {
                        values.fail();
                    }
                    values.hour = h;
                    break;
                }
                case FORMAT_HOUR_M: // %I, %OI - Hour of the day, 12-hour clock, zero-padded (01..12)
                case FORMAT_HOUR_S: // %l - Hour of the day, 12-hour clock, blank-padded ( 1..12)
                {
                    int h;
                    if (text.charAt(pos) == ' ') { // brank or not
                        pos += 1; // brank
                        h = readDigits(1);
                    } else {
                        h = readDigits(2);
                    }

                    if (!isValidRange(h, 1, 12)) {
                        values.fail();
                    }
                    values.hour = h;
                    break;
                }
                case FORMAT_DAY_YEAR: // %j - Day of the year (001..366)
                {
                    int d = readDigits(3);
                    if (!isValidRange(d, 1, 366)) {
                        values.fail();
                    }
                    values.yday = d;
                    break;
                }
                case FORMAT_MILLISEC: // %L - Millisecond of the second (000..999)
                case FORMAT_NANOSEC: // %N - Fractional seconds digits, default is 9 digits (nanosecond)
                {
                    int v;
                    boolean negative = false;

                    if (isSign(text.charAt(pos))) {
                        negative = text.charAt(pos) == '-';
                        pos++;
                    }

                    if (matchAtNumPatterns(token)) {
                        v = token.getFormat() == Format.FORMAT_MILLISEC ?
                                readDigits(3) : readDigits(9);
                    } else {
                        v = readDigitsMax();
                    }

                    values.sec_fraction = !negative ? v : -v;
                    values.sec_fraction_rational = token.getFormat() == Format.FORMAT_MILLISEC ?
                            1000 : 1000000000;
                    break;
                }
                case FORMAT_MINUTES: // %M, %OM - Minute of the hour (00..59)
                {
                    int min = readDigits(2);
                    if (!isValidRange(min, 0, 59)) {
                        values.fail();
                    }
                    values.min = min;
                    break;
                }
                case FORMAT_MONTH: // %m, %Om - Month of the year, zero-padded (01..12)
                {
                    int mon = readDigits(2);
                    if (!isValidRange(mon, 1, 12)) {
                        values.fail();
                    }
                    values.mon = mon;
                    break;
                }
                case FORMAT_MERIDIAN: // %P - Meridian indicator, lowercase (``am'' or ``pm'')
                case FORMAT_MERIDIAN_LOWER_CASE: // %p - Meridian indicator, uppercase (``AM'' or ``PM'')
                {
                    int meridIndex = matchAtPatterns(text, pos, meridNames);
                    if (meridIndex >= 0) {
                        values._merid = meridIndex % 2 == 0 ? 0 : 12;
                    } else {
                        values.fail();
                    }
                    break;
                }
                case FORMAT_MICROSEC_EPOCH: // %Q - Number of microseconds since 1970-01-01 00:00:00 UTC.
                {
                    int sec;
                    boolean negative = false;

                    if (text.charAt(pos) == '-') {
                        negative = true;
                        pos++;
                    }

                    sec = readDigitsMax();
                    values.seconds = !negative ? sec : -sec;
                    values.seconds_rational = 1000;
                    break;
                }
                case FORMAT_SECONDS: // %S - Second of the minute (00..59)
                {
                    int sec = readDigits(2);
                    if (!isValidRange(sec, 0, 59)) {
                        values.fail();
                    }
                    values.sec = sec;
                    break;
                }
                case FORMAT_EPOCH: // %s - Number of seconds since 1970-01-01 00:00:00 UTC.
                {
                    int sec;
                    boolean negative = false;

                    if (text.charAt(pos) == '-') {
                        negative = true;
                        pos++;
                    }
                    sec = readDigitsMax();
                    values.seconds = !negative ? sec : -sec;
                    break;
                }
                case FORMAT_WEEK_YEAR_S: // %U, %OU - Week number of the year.  The week starts with Sunday.  (00..53)
                case FORMAT_WEEK_YEAR_M: // %W, %OW - Week number of the year.  The week starts with Monday.  (00..53)
                {
                    int w = readDigits(2);
                    if (!isValidRange(w, 0, 53)) {
                        values.fail();
                    }

                    if (token.getFormat() == Format.FORMAT_WEEK_YEAR_S) {
                        values.wnum0 = w;
                    } else {
                        values.wnum1 = w;
                    }
                    break;
                }
                case FORMAT_DAY_WEEK2: // %u, %Ou - Day of the week (Monday is 1, 1..7)
                {
                    int d = readDigits(1);
                    if (!isValidRange(d, 1, 7)) {
                        values.fail();
                    }
                    values.cwday = d;
                    break;
                }
                case FORMAT_WEEK_WEEKYEAR: // %V, %OV - Week number of the week-based year (01..53)
                {
                    int w = readDigits(2);
                    if (!isValidRange(w, 1, 53)) {
                        values.fail();
                    }
                    values.cweek = w;
                    break;
                }
                case FORMAT_DAY_WEEK: // %w - Day of the week (Sunday is 0, 0..6)
                {
                    int d = readDigits(1);
                    if (!isValidRange(d, 0, 6)) {
                        values.fail();
                    }
                    values.wday = d;
                    break;
                }
                case FORMAT_YEAR_LONG:
                    // %Y, %EY - Year with century (can be negative, 4 digits at least)
                    //           -0001, 0000, 1995, 2009, 14292, etc.
                {
                    int y;
                    boolean negative = false;

                    if (isSign(text.charAt(pos))) {
                        negative = text.charAt(pos) == '-';
                        pos++;
                    }

                    if (matchAtNumPatterns(token)) {
                        y = readDigits(4);
                    } else {
                        y = readDigitsMax();
                    }

                    values.year = !negative ? y : -y;
                    break;
                }
                case FORMAT_YEAR_SHORT: // %y, %Ey, %Oy - year % 100 (00..99)
                {
                    int y = readDigits(2);
                    if (!isValidRange(y, 0, 99)) {
                        values.fail();
                    }
                    values.year = y;
                    if (values._cent < 0) {
                        values._cent = y >= 69 ? 19 : 20;
                    }
                    break;
                }
                case FORMAT_ZONE_ID: // %Z - Time zone abbreviation name
                case FORMAT_COLON_ZONE_OFF:
                    // %z - Time zone as hour and minute offset from UTC (e.g. +0900)
                    //      %:z - hour and minute offset from UTC with a colon (e.g. +09:00)
                    //      %::z - hour, minute and second offset from UTC (e.g. +09:00:00)
                    //      %:::z - hour, minute and second offset from UTC
                    //          (e.g. +09, +09:30, +09:30:30)
                {
                    Matcher m = ZONE_PARSE_REGEX.matcher(text.substring(pos));
                    if (m.find()) {
                        // zone
                        String zone = text.substring(pos, pos + m.end());
                        values.zone = zone;
                        pos += zone.length();

                        // TODO not calcurate offset here
                        //// offset
                        //hash.put("offset", ParsedValues.toDiff(zone));
                    } else {
                        values.fail();
                    }
                    break;
                }
                case FORMAT_SPECIAL:
                {
                    throw new Error("FORMAT_SPECIAL is a special token only for the lexer.");
                }
            }
        }

        if (values._cent >= 0) {
            if (values.cwyear >= 0) {
                values.cwyear += values._cent * 100;
            }
            if (values.year >= 0) {
                values.year += values._cent * 100;
            }
        }

        if (values._merid >= 0) {
            if (values.hour >= 0) {
                values.hour %= 12;
                values.hour += values._merid;
            }
        }

        if (text.length() > pos) {
            values.leftover = text.substring(pos, text.length());
        }

        return values;
    }

    private int readDigits(int len)
    {
        int v = 0;
        try {
            for (int i = 0; i < len; i++) {
                char c = text.charAt(pos); // IndexOutOfBounds
                pos += 1;
                if (!isDigit(c)) {
                    if (i > 0) {
                        break;
                    } else {
                        throw newInvalidDataException();
                    }
                } else {
                    v += v * 10 + toInt(c);
                }
            }
        } catch (IndexOutOfBoundsException e) {
            // ignorable error
        }
        return v;
    }

    private int readDigitsMax()
    {
        return readDigits(Integer.MAX_VALUE);
    }
}