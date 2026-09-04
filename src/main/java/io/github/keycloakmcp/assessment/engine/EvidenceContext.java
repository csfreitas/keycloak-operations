package io.github.keycloakmcp.assessment.engine;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class EvidenceContext {

    private final List<Evidence> evidence;
    private final String targetId;
    private final EvidenceSubject subject;

    public EvidenceContext(List<Evidence> evidence) {
        this(evidence == null ? "-" : evidence.stream()
                .map(Evidence::targetId)
                .filter(id -> id != null && !id.isBlank() && !"-".equals(id))
                .findFirst()
                .orElse("-"), evidence, null);
    }

    public EvidenceContext(String targetId, List<Evidence> evidence) {
        this(targetId, evidence, null);
    }

    private EvidenceContext(String targetId, List<Evidence> evidence, EvidenceSubject subject) {
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        this.targetId = targetId == null || targetId.isBlank() ? "-" : targetId;
        this.subject = subject;
        if (this.evidence.stream().map(Evidence::targetId)
                .anyMatch(id -> id != null && !id.isBlank() && !"-".equals(id) && !this.targetId.equals(id))) {
            throw new IllegalArgumentException("Evidence must belong to the assessed target");
        }
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
        if (subject != null) {
            Optional<Evidence> scoped = evidence.stream()
                    .filter(e -> key.equals(e.key()) && subject.equals(e.subject())).findFirst();
            if (scoped.isPresent()) {
                return scoped;
            }
            if (key.startsWith("realm.") || key.startsWith("client.")) {
                // Old target aggregates must not stand in for a missing resource property.
                return Optional.empty();
            }
        }
        // Never select an arbitrary realm/client when evaluating a target-level rule.
        return evidence.stream().filter(e -> key.equals(e.key())
                && (e.subject() == null || e.subject().type() == SubjectType.TARGET)).findFirst();
    }

    public Optional<EvidenceSubject> subject() {
        return Optional.ofNullable(subject);
    }

    public List<EvidenceContext> forSubjects(SubjectType type) {
        return evidence.stream().map(Evidence::subject).filter(s -> s != null && s.type() == type)
                .distinct().sorted(Comparator.comparing(EvidenceSubject::id))
                .map(this::forSubject)
                .toList();
    }

    public EvidenceContext forSubject(EvidenceSubject selected) {
        return new EvidenceContext(targetId, evidence.stream()
                .filter(e -> e.subject() == null || e.subject().type() == SubjectType.TARGET
                        || selected.equals(e.subject())).toList(), selected);
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
        return get(key).isPresent();
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
