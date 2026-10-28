package com.ironoak.eval;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text normalisation used to match facts, evidence and citations. Must stay
 * identical to normalize() in scripts/eval/validate_golden.py.
 */
final class TextNormalizer {

    /** 1,500 / 24,000: comma groups after 1 to 3 digits. */
    private static final Pattern COMMA_THOUSANDS = Pattern.compile(
            "(?<![\\d.,])([1-9]\\d{0,2})((?:,\\d{3})+)(?!\\d)");
    /** 24 000 / 12 800: space groups after 1 or 2 digits only, so "700 850 1000" stays apart. */
    private static final Pattern SPACE_THOUSANDS = Pattern.compile(
            "(?<![\\d.,])([1-9]\\d?)((?: \\d{3})+)(?!\\d)");
    private static final Pattern TIMES = Pattern.compile("(\\d)\\s*[x×]\\s*(\\d)");
    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");

    private TextNormalizer() {
    }

    static String normalize(String text) {

        if (text == null) {
            return "";
        }
        String value = text.replace("¼", "1/4").replace("½", "1/2");
        value = Normalizer.normalize(value, Normalizer.Form.NFKD);
        value = MARKS.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);

        value = joinThousands(joinThousands(value, COMMA_THOUSANDS), SPACE_THOUSANDS);
        value = TIMES.matcher(value).replaceAll("$1x$2");
        return SPACES.matcher(value).replaceAll(" ").strip();
    }

    private static String joinThousands(String value, Pattern pattern) {

        Matcher matcher = pattern.matcher(value);
        StringBuilder joined = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group(1) + matcher.group(2).replaceAll("[ ,]", "");
            matcher.appendReplacement(joined, digits);
        }
        matcher.appendTail(joined);
        return joined.toString();
    }

    /** Letters and digits only, for loose comparisons (titles, codes, emails). */
    static String compact(String text) {
        return NON_ALNUM.matcher(normalize(text)).replaceAll("");
    }

    /** True when any alternative of the fact occurs in the normalised text. */
    static boolean containsFact(String normalizedText, java.util.List<String> alternatives) {
        return alternatives.stream()
                .map(TextNormalizer::normalize)
                .anyMatch(normalizedText::contains);
    }
}
