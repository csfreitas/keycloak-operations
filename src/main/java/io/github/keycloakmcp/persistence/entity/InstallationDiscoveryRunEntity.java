package io.github.keycloakmcp.persistence.entity;

import java.time.Instant;
import java.util.Map;
import io.github.keycloakmcp.target.KubernetesInstallationBinding;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "installation_discovery_runs")
public class InstallationDiscoveryRunEntity extends PanacheEntityBase {
    @Id public String id;
    @Column(name = "target_id", nullable = false) public String targetId;
    @Column(nullable = false) public String actor;
    @Column(name = "context_hash", nullable = false) public String contextHash;
    @Column(name = "binding_revision", nullable = false) public long bindingRevision;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    public boolean consumed;
    @JdbcTypeCode(SqlTypes.JSON) public Map<String, KubernetesInstallationBinding> candidates;
}
