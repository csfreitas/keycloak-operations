package io.github.keycloakmcp.persistence.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "targets")
public class TargetEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 128, nullable = false)
    public String id;

    @Column(name = "display_name", nullable = false)
    public String displayName;

    @Column(name = "product_type", length = 32, nullable = false)
    public String productType;

    @Column(name = "environment", length = 32, nullable = false)
    public String environment;

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;

    @Column(name = "keycloak_url", length = 1024, nullable = false)
    public String keycloakUrl;

    @Column(name = "keycloak_auth_realm", nullable = false)
    public String keycloakAuthRealm;

    @Column(name = "keycloak_client_id", nullable = false)
    public String keycloakClientId;

    /** Credential reference only — never a secret value. */
    @Column(name = "keycloak_credential_ref", nullable = false)
    public String keycloakCredentialRef;

    @Column(name = "infra_type", length = 32)
    public String infraType;

    @Column(name = "infra_cluster_id")
    public String infraClusterId;

    @Column(name = "infra_namespace")
    public String infraNamespace;

    @Column(name = "infra_credential_ref")
    public String infraCredentialRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "observability", columnDefinition = "jsonb")
    public Map<String, Object> observability;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "target_tags", joinColumns = @JoinColumn(name = "target_id"))
    @MapKeyColumn(name = "tag_key")
    @Column(name = "tag_value")
    public Map<String, String> tags = new HashMap<>();
}
