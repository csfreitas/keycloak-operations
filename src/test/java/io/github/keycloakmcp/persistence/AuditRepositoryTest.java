package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.persistence.entity.AuditEventEntity;
import io.github.keycloakmcp.persistence.repository.AuditRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class AuditRepositoryTest {

    @Inject
    AuditRepository auditRepository;

    @Test
    @Transactional
    void persistsAndQueriesByTarget() {
        String targetId = "lab-keycloak-a";
        AuditEventEntity entity = new AuditEventEntity();
        entity.id = UUID.randomUUID().toString();
        entity.traceId = UUID.randomUUID().toString();
        entity.source = "MCP";
        entity.tool = "test.tool";
        entity.targetId = targetId;
        entity.operation = "test.tool";
        entity.status = "SUCCESS";
        entity.durationMs = 12L;
        entity.params = Map.of("realm", "master");
        entity.createdAt = Instant.now();
        auditRepository.persist(entity);

        assertThat(auditRepository.listByTarget(targetId, 0, 20).items())
                .anyMatch(e -> e.id.equals(entity.id));
        assertThat(auditRepository.list(Optional.of(targetId), Optional.of("MCP"), 0, 20).total())
                .isGreaterThanOrEqualTo(1);
        assertThat(auditRepository.findByTraceId(entity.traceId)).isPresent();
    }

    @Test
    @Transactional
    void authorizedTargetsConstrainBothPageAndTotalAndExcludeGlobalRecords() {
        String ownTarget = "audit-own-" + UUID.randomUUID();
        String otherTarget = "audit-other-" + UUID.randomUUID();
        for (String target : new String[] { ownTarget, otherTarget, null }) {
            AuditEventEntity event = new AuditEventEntity();
            event.id = UUID.randomUUID().toString();
            event.traceId = UUID.randomUUID().toString();
            event.source = "MCP";
            event.targetId = target;
            event.operation = "scope.test";
            event.status = "SUCCESS";
            event.createdAt = Instant.now();
            auditRepository.persist(event);
        }
        var scoped = auditRepository.listForTargets(Set.of(ownTarget), Optional.empty(), 0, 1);
        assertThat(scoped.total()).isEqualTo(1);
        assertThat(scoped.items()).hasSize(1).allMatch(event -> ownTarget.equals(event.targetId));
        assertThat(auditRepository.listForTargets(Set.of(), Optional.empty(), 0, 20).total()).isZero();
    }
}
