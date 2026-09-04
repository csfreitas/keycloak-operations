package io.github.keycloakmcp.service.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.yaml.snakeyaml.Yaml;

/** Fingerprints packaged rule resources only, not runtime overrides or retained observations. */
final class BundledRuleCatalogRevision {
    private BundledRuleCatalogRevision() { }

    static String current() {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Map<String, byte[]> resources = new TreeMap<>();
            byte[] index = read(loader, "rules/index.yaml");
            resources.put("rules/index.yaml", index);
            Object parsed = new Yaml().load(new String(index, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> root) || !(root.get("packs") instanceof List<?> packs)) {
                return null;
            }
            for (Object item : packs) {
                if (!(item instanceof Map<?, ?> pack) || !(pack.get("path") instanceof String path)) {
                    return null;
                }
                resources.put(path, read(loader, path));
            }
            return fingerprint(resources);
        } catch (IOException | RuntimeException e) {
            // Unknown provenance must be visible; do not fabricate a revision or expose an exception.
            return null;
        }
    }

    static String fingerprint(Map<String, byte[]> resources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            new TreeMap<>(resources).forEach((path, bytes) -> {
                digest.update(path.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) 0);
                digest.update(bytes);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] read(ClassLoader loader, String path) throws IOException {
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Bundled rule resource unavailable");
            return in.readAllBytes();
        }
    }
}
