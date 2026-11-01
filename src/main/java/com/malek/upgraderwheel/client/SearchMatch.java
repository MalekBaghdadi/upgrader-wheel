package com.malek.upgraderwheel.client;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Forgiving search for the result list: words in any order, small typos allowed, filler words ignored.
 * A word scores its length when the name has it, so the long, meaningful words of a query count most.
 */
final class SearchMatch {

    private static final Set<String> FILLER = new HashSet<>(Arrays.asList("of", "the", "a", "an", "and"));

    private SearchMatch() {
    }

    /** Lowercased words of a query without filler ("ring of strength" -> ring, strength); "@mod" words stay. */
    static String[] tokens(String query) {
        String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        String[] kept = Arrays.stream(words).filter(w -> !w.isEmpty() && !FILLER.contains(w)).toArray(String[]::new);
        return kept.length > 0 || query.trim().isEmpty() ? kept : words;
    }

    /** Best possible score: the combined length of the words that are not @mod filters. */
    static int total(String[] tokens) {
        int total = 0;
        for (String token : tokens) {
            if (!token.startsWith("@")) {
                total += token.length();
            }
        }
        return total;
    }

    /** Combined length of the words the name has, or -1 when an @mod filter fails. */
    static int score(String name, String modId, String[] tokens) {
        int score = 0;
        for (String token : tokens) {
            if (token.startsWith("@")) {
                if (!modId.startsWith(token.substring(1))) {
                    return -1;
                }
            } else if (hasWord(name, token)) {
                score += token.length();
            }
        }
        return score;
    }

    /** The name contains the word, or has a word one typo away (two for long words); short words must match exactly. */
    private static boolean hasWord(String name, String token) {
        if (name.contains(token)) {
            return true;
        }
        int allowed = token.length() >= 9 ? 2 : token.length() >= 5 ? 1 : 0;
        if (allowed == 0) {
            return false;
        }
        for (String word : name.split("[^\\p{L}\\p{N}]+")) {
            if (Math.abs(word.length() - token.length()) <= allowed && distance(word, token) <= allowed) {
                return true;
            }
        }
        return false;
    }

    /** Edit distance where swapping two neighbours costs 1, so "strenght" is one off "strength". */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
