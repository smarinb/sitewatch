package dev.sergiomarin.sitewatch.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WordPressVersionsTest {

    @Test
    void readsGeneratorMetaInEitherAttributeOrder() {
        assertThat(WordPressVersions.fromGeneratorMeta("<meta name=\"generator\" content=\"WordPress 6.7.1\" />"))
                .contains("6.7.1");
        assertThat(WordPressVersions.fromGeneratorMeta("<META content='WordPress 6.5' NAME='generator'>"))
                .contains("6.5");
    }

    @Test
    void ignoresOtherGeneratorsAndUnrelatedMeta() {
        assertThat(WordPressVersions.fromGeneratorMeta("<meta name=\"generator\" content=\"Hugo 0.140\">"))
                .isEmpty();
        assertThat(WordPressVersions.fromGeneratorMeta("<meta name=\"description\" content=\"WordPress 6.7\">"))
                .isEmpty();
        assertThat(WordPressVersions.fromGeneratorMeta(null)).isEmpty();
    }

    @Test
    void detectsWordPressWithoutVersion() {
        assertThat(WordPressVersions.looksLikeWordPress("<script src=\"/wp-includes/js/x.js\">", null)).isTrue();
        assertThat(WordPressVersions.looksLikeWordPress("",
                "<https://example.com/wp-json/>; rel=\"https://api.w.org/\"")).isTrue();
        assertThat(WordPressVersions.looksLikeWordPress("<html>static</html>", null)).isFalse();
    }

    @Test
    void readsFeedAndReadme() {
        assertThat(WordPressVersions.fromFeed("<generator>https://wordpress.org/?v=6.4.3</generator>"))
                .contains("6.4.3");
        assertThat(WordPressVersions.fromReadme("<br /> Version 4.9.8")).contains("4.9.8");
        assertThat(WordPressVersions.fromReadme("<h1>ReadMe</h1>")).isEmpty();
    }

    @Test
    void readmeIgnoresRequirementVersions() {
        // Current readme.html lists requirements; none of these is the WordPress version.
        assertThat(WordPressVersions.fromReadme("<li>PHP version 7.4 or greater.</li>")).isEmpty();
        assertThat(WordPressVersions.fromReadme("<li>MySQL version <strong>8.0</strong></li>")).isEmpty();
    }

    @Test
    void readsCoreAssetVersionsButNotPluginOnes() {
        assertThat(WordPressVersions.fromCoreAssets(
                "<link href='https://x.es/wp-admin/css/forms.min.css?ver=7.1.5' rel='stylesheet'>"))
                .contains("7.1.5");
        assertThat(WordPressVersions.fromCoreAssets(
                "<link href='/wp-content/plugins/elementor/assets/css/frontend.min.css?ver=4.4.1'>")).isEmpty();
    }
}
