package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

class CollectionDiagnosticPolicyTest {
    @ParameterizedTest
    @MethodSource("categories")
    void eachSensitiveCategoryRejectsWithoutExposingItsName(String enabledCategory) {
        McpException failure = catchThrowableOfType(() ->
                CollectionDiagnosticPolicy.check(enabledCategory::equals), McpException.class);
        assertSafe(failure);
        assertThat(failure.getMessage()).doesNotContain(enabledCategory);
    }

    @Test
    void disabledDiagnosticsAllowCollectionAndCheckEveryCategory() {
        List<String> visited = new ArrayList<>();
        CollectionDiagnosticPolicy.check(category -> { visited.add(category); return false; });
        assertThat(visited).containsExactlyElementsOf(CollectionDiagnosticPolicy.diagnosticCategories())
                .doesNotHaveDuplicates();
    }

    @Test
    void unavailableDiagnosticStateFailsClosedWithoutRawCause() {
        McpException failure = catchThrowableOfType(() -> CollectionDiagnosticPolicy.check(category -> {
            throw new IllegalStateException("credential=diagnostic-canary");
        }), McpException.class);
        assertSafe(failure);
        assertThat(failure.getMessage()).doesNotContain("diagnostic-canary");
    }

    private static Stream<String> categories() {
        return CollectionDiagnosticPolicy.diagnosticCategories().stream();
    }

    private static void assertSafe(McpException failure) {
        assertThat(failure).isNotNull().hasNoCause()
                .hasMessage("Scoped Admin collection requires safe transport diagnostics");
        assertThat(failure.getCode()).isEqualTo(ErrorCode.EVIDENCE_COLLECTION_FAILED);
        assertThat(failure.getError().details()).isEmpty();
        assertThat(failure.getSuppressed()).isEmpty();
    }
}
