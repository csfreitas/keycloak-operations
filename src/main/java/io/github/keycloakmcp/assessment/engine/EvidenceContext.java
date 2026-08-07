package io.github.keycloakmcp.assessment.engine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class EvidenceContext {

    private final List<Evidence> evidence;
    private final String targetId;

    public EvidenceContext(List<Evidence> evidence) {
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        this.targetId = this.evidence.stream()
                .map(Evidence::targetId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse("-");
    }

    public EvidenceContext(String targetId, List<Evidence> evidence) {
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        this.targetId = targetId == null || targetId.isBlank() ? "-" : targetId;
    }

    public String targetId() {
        return targetId;
    }

    public List<Evidence> evidence() {
        return evidence;
    }

    public Optional<Evidence> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return evidence.stream().filter(e -> key.equals(e.key())).findFirst();
    }

    public Optional<Object> get(String key) {
        return find(key).map(Evidence::value);
    }

    public OptionalIntResult getInt(String key) {
        return get(key).map(value -> {
            if (value instanceof Number number) {
                return OptionalIntResult.of(number.intValue());
            }
            if (value instanceof String text) {
                try {
                    return OptionalIntResult.of(Integer.parseInt(text.trim()));
                } catch (NumberFormatException ignored) {
                    return OptionalIntResult.empty();
                }
            }
            return OptionalIntResult.empty();
        }).orElseGet(OptionalIntResult::empty);
    }

    public Optional<String> findString(String key) {
        return get(key).map(String::valueOf);
    }

    public boolean hasKey(String key) {
        return find(key).isPresent();
    }

    public List<Evidence> bySource(String source) {
        return evidence.stream().filter(e -> source.equals(e.source())).toList();
    }

    public List<Evidence> byCategory(String category) {
        return evidence.stream().filter(e -> category.equals(e.category())).toList();
    }

    public Collection<Evidence> all() {
        return evidence;
    }

    public record OptionalIntResult(boolean present, int value) {
        public static OptionalIntResult of(int value) {
            return new OptionalIntResult(true, value);
        }

        public static OptionalIntResult empty() {
            return new OptionalIntResult(false, 0);
        }

        public OptionalIntResult filter(java.util.function.IntPredicate predicate) {
            if (!present || !predicate.test(value)) {
                return empty();
            }
            return this;
        }
    }
}
