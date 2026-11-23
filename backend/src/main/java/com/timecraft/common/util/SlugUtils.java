package com.timecraft.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s_]+");
    private static final Pattern DASHES = Pattern.compile("-{2,}");

    private SlugUtils() {
    }

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String noWhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = NON_LATIN.matcher(normalized).replaceAll("");
        slug = DASHES.matcher(slug).replaceAll("-").toLowerCase(Locale.ROOT);
        return slug.replaceAll("^-|-$", "");
    }
}
