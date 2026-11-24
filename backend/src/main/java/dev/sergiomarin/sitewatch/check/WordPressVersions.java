package dev.sergiomarin.sitewatch.check;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parsing of the places where WordPress reveals itself and its version. */
final class WordPressVersions {

    /** Matches the {@code wp_version} column. */
    static final int MAX_LENGTH = 20;

    private static final Pattern META_TAG = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERATOR_NAME = Pattern.compile("\\bname\\s*=\\s*[\"']generator[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WORDPRESS_CONTENT = Pattern.compile(
            "\\bcontent\\s*=\\s*[\"']\\s*WordPress\\s+(\\d+\\.\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEED_GENERATOR = Pattern.compile(
            "<generator>\\s*https?://wordpress\\.org/\\?v=(\\d+\\.\\d+(?:\\.\\d+)?)\\s*</generator>",
            Pattern.CASE_INSENSITIVE);
    /** Old readme.html: {@code <br /> Version 4.9.8}. Anchored so "PHP version 7.4" never matches. */
    private static final Pattern README_VERSION = Pattern.compile(
            "<br\\s*/?>\\s*Version\\s+(\\d+\\.\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    /** Core stylesheets are enqueued with the WordPress version, e.g. {@code /wp-admin/css/forms.min.css?ver=6.7.1}. */
    private static final Pattern CORE_ASSET_VERSION = Pattern.compile(
            "/wp-(?:admin|includes)/css/[\\w.-]+\\.css\\?ver=(\\d+\\.\\d+(?:\\.\\d+)?)");

    private WordPressVersions() {
    }

    /** Whether a page looks like it is served by WordPress, even with the version hidden. */
    static boolean looksLikeWordPress(String html, String linkHeader) {
        return (linkHeader != null && linkHeader.contains("api.w.org"))
                || (html != null && (html.contains("/wp-content/") || html.contains("/wp-includes/")));
    }

    /** {@code <meta name="generator" content="WordPress 6.7.1">}, in either attribute order. */
    static Optional<String> fromGeneratorMeta(String html) {
        if (html == null) {
            return Optional.empty();
        }
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            if (GENERATOR_NAME.matcher(tag).find()) {
                Matcher content = WORDPRESS_CONTENT.matcher(tag);
                if (content.find()) {
                    return Optional.of(content.group(1));
                }
            }
        }
        return Optional.empty();
    }

    /** {@code <generator>https://wordpress.org/?v=6.7.1</generator>} in the RSS feed. */
    static Optional<String> fromFeed(String xml) {
        return firstGroup(FEED_GENERATOR, xml);
    }

    /** "Version x.y" in {@code readme.html}; recent releases removed it, older ones still show it. */
    static Optional<String> fromReadme(String html) {
        return firstGroup(README_VERSION, html);
    }

    /**
     * Version of core stylesheets on {@code wp-login.php}. Hardening plugins usually strip the
     * generator tag and feed version but leave these alone.
     */
    static Optional<String> fromCoreAssets(String html) {
        return firstGroup(CORE_ASSET_VERSION, html);
    }

    private static Optional<String> firstGroup(Pattern pattern, String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1)).filter(v -> v.length() <= MAX_LENGTH)
                : Optional.empty();
    }
}
