package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PromQlEscaperTest {

    @Test
    void escapesBackslashQuoteAndNewlines() {
        assertThat(PromQlEscaper.labelValue("a\\b\"c\nd\re\tf"))
                .isEqualTo("a\\\\b\\\"c\\nd\\re\\tf");
    }

    @Test
    void escapesInjectionPayloadBreakingLabel() {
        String payload = "\"} or vector(1)";
        String escaped = PromQlEscaper.labelValue(payload);
        assertThat(escaped).isEqualTo("\\\"} or vector(1)");
        String matcher = PromQlEscaper.labelEq("namespace", payload);
        assertThat(matcher).isEqualTo("namespace=\"\\\"} or vector(1)\"");
        // Injection must remain inside the quoted label value
        assertThat(matcher).startsWith("namespace=\"");
        assertThat(matcher).endsWith("\"");
        assertThat(matcher.indexOf(" or vector")).isGreaterThan(matcher.indexOf('\\'));
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(PromQlEscaper.labelValue(null)).isEmpty();
    }

    @Test
    void rejectsInvalidLabelNames() {
        assertThatThrownBy(() -> PromQlEscaper.labelEq("bad-label", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PromQlEscaper.labelEq("", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
