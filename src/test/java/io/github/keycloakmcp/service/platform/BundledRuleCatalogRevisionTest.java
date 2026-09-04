package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BundledRuleCatalogRevisionTest {
    @Test
    void packagedCatalogHasAStableDigest() {
        assertThat(BundledRuleCatalogRevision.current()).matches("[0-9a-f]{64}");
        assertThat(BundledRuleCatalogRevision.current()).isEqualTo(BundledRuleCatalogRevision.current());
    }

    @Test
    void resourceOrderDoesNotChangeDigestButContentDoes() {
        byte[] a = "rule-a".getBytes(StandardCharsets.UTF_8);
        byte[] b = "rule-b".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> first = new LinkedHashMap<>();
        first.put("a.yaml", a);
        first.put("b.yaml", b);
        Map<String, byte[]> second = new LinkedHashMap<>();
        second.put("b.yaml", b);
        second.put("a.yaml", a);
        assertThat(BundledRuleCatalogRevision.fingerprint(first)).isEqualTo(BundledRuleCatalogRevision.fingerprint(second));
        second.put("a.yaml", b);
        assertThat(BundledRuleCatalogRevision.fingerprint(first)).isNotEqualTo(BundledRuleCatalogRevision.fingerprint(second));
    }
}
