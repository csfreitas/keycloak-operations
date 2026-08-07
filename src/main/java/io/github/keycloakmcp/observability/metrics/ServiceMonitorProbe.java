package io.github.keycloakmcp.observability.metrics;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Best-effort ServiceMonitor / scrape readiness for OpenShift Monitoring targets.
 * Never reads Secret contents. Permission failures yield UNKNOWN / PERMISSION_DENIED.
 */
@ApplicationScoped
public class ServiceMonitorProbe {

    private static final Logger LOG = Logger.getLogger(ServiceMonitorProbe.class);

    private static final ResourceDefinitionContext SERVICE_MONITOR = new ResourceDefinitionContext.Builder()
            .withGroup("monitoring.coreos.com")
            .withVersion("v1")
            .withKind("ServiceMonitor")
            .withPlural("servicemonitors")
            .withNamespaced(true)
            .build();

    public record Result(
            ScrapeReadiness readiness,
            Boolean serviceMonitorPresent,
            String interval,
            String scrapeTimeout,
            String detail) {
    }

    private final InfrastructureClientFactory infrastructureClientFactory;
    private final MetricsProviderFactory metricsProviderFactory;

    @Inject
    public ServiceMonitorProbe(
            InfrastructureClientFactory infrastructureClientFactory,
            MetricsProviderFactory metricsProviderFactory) {
        this.infrastructureClientFactory = infrastructureClientFactory;
        this.metricsProviderFactory = metricsProviderFactory;
    }

    public Result probe(Target target) {
        if (target == null || !target.hasInfrastructure()) {
            return new Result(ScrapeReadiness.UNKNOWN, null, null, null, "No infrastructure binding");
        }
        if (target.infrastructureTypeOrNone() != InfrastructureType.OPENSHIFT
                && target.infrastructureTypeOrNone() != InfrastructureType.KUBERNETES) {
            return new Result(ScrapeReadiness.UNKNOWN, null, null, null, "Not a cluster target");
        }

        Optional<ClusterClient> clientOpt;
        try {
            clientOpt = infrastructureClientFactory.resolve(target);
        } catch (RuntimeException e) {
            LOG.debugf(e, "Infrastructure client unavailable for ServiceMonitor probe target=%s", target.id().value());
            return new Result(ScrapeReadiness.UNKNOWN, null, null, null, "Infrastructure client unavailable");
        }
        if (clientOpt.isEmpty()) {
            return new Result(ScrapeReadiness.UNKNOWN, null, null, null, "No cluster client");
        }

        String namespace = clientOpt.get().namespace();
        if (namespace == null || namespace.isBlank()) {
            return new Result(ScrapeReadiness.UNKNOWN, null, null, null, "Namespace unknown");
        }

        Boolean present;
        String interval = null;
        String timeout = null;
        try {
            KubernetesClient k8s = clientOpt.get().kubernetes();
            GenericKubernetesResourceList list = k8s.genericKubernetesResources(SERVICE_MONITOR)
                    .inNamespace(namespace)
                    .list();
            present = list != null && list.getItems() != null && !list.getItems().isEmpty();
            if (Boolean.TRUE.equals(present)) {
                GenericKubernetesResource first = list.getItems().get(0);
                Object specObj = first.getAdditionalProperties() == null
                    ? null
                    : first.getAdditionalProperties().get("spec");
            if (specObj instanceof Map<?, ?> spec) {
                    Object ep = spec.get("endpoints");
                    if (ep instanceof Iterable<?> endpoints) {
                        for (Object item : endpoints) {
                            if (item instanceof Map<?, ?> epMap) {
                                Object i = epMap.get("interval");
                                Object t = epMap.get("scrapeTimeout");
                                if (i != null) {
                                    interval = String.valueOf(i);
                                }
                                if (t != null) {
                                    timeout = String.valueOf(t);
                                }
                                break;
                            }
                        }
                    }
            }
            }
        } catch (KubernetesClientException e) {
            int code = e.getCode();
            if (code == 403 || code == 401) {
                return new Result(
                        ScrapeReadiness.PERMISSION_DENIED, null, null, null, "ServiceMonitor list forbidden");
            }
            LOG.debugf(e, "ServiceMonitor list failed target=%s", target.id().value());
            return new Result(ScrapeReadiness.UNKNOWN, null, null, null, "ServiceMonitor list failed");
        } catch (RuntimeException e) {
            LOG.debugf(e, "ServiceMonitor probe failed target=%s", target.id().value());
            return new Result(ScrapeReadiness.UNKNOWN, null, null, null, "ServiceMonitor probe failed");
        }

        if (Boolean.FALSE.equals(present)) {
            return new Result(ScrapeReadiness.SERVICEMONITOR_MISSING, false, null, null, "No ServiceMonitor in namespace");
        }

        // Scrape health via target-scoped `up` only
        MetricsProvider provider = metricsProviderFactory.forTarget(target);
        if (!provider.supported(target)) {
            return new Result(
                    ScrapeReadiness.METRICS_DISABLED, present, interval, timeout, "Metrics provider not configured");
        }
        SemanticMetricResult up = provider.probeSeries(target, "up");
        if (up.availability() == MetricAvailability.AVAILABLE
                && up.value() != null
                && up.value() > 0) {
            return new Result(ScrapeReadiness.SCRAPE_HEALTHY, present, interval, timeout, "up>0");
        }
        if (up.availability() == MetricAvailability.AVAILABLE
                && up.value() != null
                && up.value() == 0) {
            return new Result(ScrapeReadiness.SCRAPE_TARGET_DOWN, present, interval, timeout, "up==0");
        }
        String detail = up.reason() == null ? "up unknown" : up.reason().toLowerCase(Locale.ROOT);
        return new Result(ScrapeReadiness.UNKNOWN, present, interval, timeout, detail);
    }
}
