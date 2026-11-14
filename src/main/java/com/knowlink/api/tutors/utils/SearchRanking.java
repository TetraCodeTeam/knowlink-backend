package com.knowlink.api.tutors.utils;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Shared ranking for the unified search (US-06 QA adjustments).
 *
 * <p>Text is normalized before comparing: lowercase, no diacritics (NFD plus
 * removal of the combining marks) and trimmed, so "algebra" matches
 * "Algebra" with an accent. Against that normalized text a name gets a rank:
 *
 * <ul>
 *   <li>0 - the name starts with the query</li>
 *   <li>1 - another word of the name starts with the query (e.g. the surname)</li>
 *   <li>2 - the query is contained in the name but not at the start of a word</li>
 *   <li>3 - the name does not contain the query</li>
 * </ul>
 *
 * <p>Results are ordered by rank ascending and, inside the same rank, with a
 * Spanish primary collator (accent and case insensitive). Callers should chain
 * a stable id tie-break (userId / subjectId) on top of {@link #comparator} so
 * the final order is deterministic.
 */
public final class SearchRanking {

    /** Combining marks left by the NFD decomposition (accents, tildes, cedillas). */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private SearchRanking() {
    }

    /**
     * Rank of {@code name} against {@code query}: 0 prefix, 1 prefix of another
     * word, 2 contained, 3 absent. A blank query ranks everything as 0.
     */
    public static int rank(String name, String query) {
        String normalizedName = normalize(name);
        String normalizedQuery = normalize(query);
        if (!normalizedName.contains(normalizedQuery)) {
            return 3;
        }
        if (normalizedName.startsWith(normalizedQuery)) {
            return 0;
        }
        return startsAtAnotherWord(normalizedName, normalizedQuery) ? 1 : 2;
    }

    /**
     * Comparator ordering elements by {@link #rank(String, String)} and, inside
     * the same rank, alphabetically with the Spanish primary collator.
     */
    public static <T> Comparator<T> comparator(String query, Function<T, String> nameExtractor) {
        String normalizedQuery = normalize(query);
        Collator collator = Collator.getInstance(new Locale("es", "AR"));
        collator.setStrength(Collator.PRIMARY);
        return (left, right) -> {
            String leftName = normalize(nameExtractor.apply(left));
            String rightName = normalize(nameExtractor.apply(right));
            int byRank = Integer.compare(rank(leftName, normalizedQuery), rank(rightName, normalizedQuery));
            if (byRank != 0) {
                return byRank;
            }
            return collator.compare(leftName, rightName);
        };
    }

    /** True when some word other than the first one starts with the query. */
    private static boolean startsAtAnotherWord(String normalizedName, String normalizedQuery) {
        for (int i = 1; i < normalizedName.length(); i++) {
            if (!Character.isLetterOrDigit(normalizedName.charAt(i - 1))
                    && normalizedName.startsWith(normalizedQuery, i)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").trim();
    }
}
