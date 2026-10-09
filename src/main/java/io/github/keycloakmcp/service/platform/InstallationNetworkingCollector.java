package io.github.keycloakmcp.service.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackend;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Route;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.BoundedKubernetesReader;
import io.github.keycloakmcp.domain.inventory.CollectionWarning;
import io.github.keycloakmcp.domain.inventory.CollectionWarning.WarningCode;
import io.github.keycloakmcp.domain.inventory.KeycloakWorkloadInfo;
import io.github.keycloakmcp.domain.inventory.NetworkingInfo;
import io.github.keycloakmcp.domain.inventory.NetworkingInfo.Exposure;
import io.github.keycloakmcp.domain.inventory.NetworkingInfo.ServiceRef;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability;

/** Pure read-side association of an already verified workload; no endpoint probing or name guessing. */
final class InstallationNetworkingCollector {
    NetworkingInfo collect(ClusterClient cluster, KeycloakWorkloadInfo root,
            ClusterApiCapabilities capabilities, List<CollectionWarning> warnings) {
        int before = warnings.size();
        var services = new LinkedHashMap<String, Service>();
        var exposures = new ArrayList<Exposure>();
        String namespace = root.namespace();
        try {
            var k8s = cluster.kubernetes();
            services.putAll(new InstallationServiceCollector().collect(cluster, root, warnings).services());
            if (warnings.stream().skip(before).anyMatch(w -> w.code() == WarningCode.BINDING_MISMATCH)) {
                return result(services, exposures, false);
            }
            // Queries remain necessary even when no Service matches, to distinguish supported empty scope from denial.
            for (Ingress ingress : BoundedKubernetesReader.list(k8s, BoundedKubernetesReader.INGRESSES, namespace)) {
                InstallationServiceCollector.checkpoint();
                if (ingress.getSpec() == null) {
                    warning(warnings, WarningCode.COLLECTION_FAILED, "Ingress configuration is incomplete");
                    continue;
                }
                ingressBackend(ingress, ingress.getSpec().getDefaultBackend(), null, null, services, exposures, warnings);
                if (ingress.getSpec().getRules() == null) continue;
                for (var rule : ingress.getSpec().getRules()) {
                    if (rule.getHttp() == null || rule.getHttp().getPaths() == null) continue;
                    for (var path : rule.getHttp().getPaths()) {
                        ingressBackend(ingress, path.getBackend(), rule.getHost(), path.getPath(), services, exposures, warnings);
                    }
                }
            }
            if (capabilities == null || capabilities.routeV1() == ApiAvailability.UNKNOWN) {
                warning(warnings, WarningCode.COLLECTION_FAILED, "Route API availability was not observed");
            } else if (capabilities.routeV1() == ApiAvailability.UNSUPPORTED_VERSION) {
                warning(warnings, WarningCode.NOT_SUPPORTED, "Advertised Route API versions are not supported by this collector");
            } else if (capabilities.routeV1() == ApiAvailability.SERVED) {
                for (Route route : BoundedKubernetesReader.list(k8s, BoundedKubernetesReader.ROUTES, namespace)) {
                    InstallationServiceCollector.checkpoint();
                    route(route, services, exposures, warnings);
                }
            }
        } catch (KubernetesClientException e) {
            warning(warnings, e.getCode() == 403 ? WarningCode.PERMISSION_DENIED : WarningCode.API_UNAVAILABLE,
                    "Networking API collection unavailable (HTTP " + e.getCode() + ")");
        } catch (RuntimeException e) {
            // Do not propagate server responses, raw resources, URLs or credentials into evidence/logs.
            warning(warnings, WarningCode.COLLECTION_FAILED, "Networking collection incomplete, changed or above its resource limit");
        }
        return result(services, exposures, warnings.stream().noneMatch(w -> "networking".equals(w.resource())));
    }

    private static void ingressBackend(Ingress ingress, IngressBackend backend, String host, String path,
            Map<String, Service> services, List<Exposure> out, List<CollectionWarning> warnings) {
        if (backend == null) return;
        if (backend.getService() == null) {
            warning(warnings, WarningCode.NOT_SUPPORTED, "Ingress resource backends are not evaluated");
            return;
        }
        Service service = services.get(backend.getService().getName());
        if (service == null) return;
        var port = backend.getService().getPort();
        boolean validPort = port != null && service.getSpec().getPorts() != null
                && service.getSpec().getPorts().stream().anyMatch(p ->
                        (p.getProtocol() == null || "TCP".equals(p.getProtocol()))
                        && (port.getName() != null ? port.getName().equals(p.getName()) : port.getNumber() != null && port.getNumber().equals(p.getPort())));
        if (!validPort) {
            warning(warnings, WarningCode.RESOURCE_NOT_FOUND, "Ingress backend port does not resolve to an associated Service port");
            return;
        }
        Boolean tls = host == null ? null : ingress.getSpec().getTls() != null && ingress.getSpec().getTls().stream()
                .anyMatch(t -> t.getHosts() != null && t.getHosts().contains(host));
        out.add(exposure(ingress, service, host, path, tls));
    }

    private static void route(Route route, Map<String, Service> services, List<Exposure> out, List<CollectionWarning> warnings) {
        if (route.getSpec() == null) {
            warning(warnings, WarningCode.COLLECTION_FAILED, "Route configuration is incomplete");
            return;
        }
        var refs = new ArrayList<io.fabric8.openshift.api.model.RouteTargetReference>();
        if (route.getSpec().getTo() != null) refs.add(route.getSpec().getTo());
        if (route.getSpec().getAlternateBackends() != null) refs.addAll(route.getSpec().getAlternateBackends());
        if (refs.stream().noneMatch(r -> services.containsKey(r.getName()))) return;
        if (refs.stream().anyMatch(r -> !services.containsKey(r.getName()) || r.getKind() != null && !"Service".equals(r.getKind()))) {
            warning(warnings, WarningCode.AMBIGUOUS_RESOURCE, "Route includes backends not exclusively associated with this installation");
            return;
        }
        // Keep all configured alternatives, including weight zero: this describes intent, not current traffic.
        for (var ref : refs) {
            out.add(exposure(route, services.get(ref.getName()), route.getSpec().getHost(), route.getSpec().getPath(), route.getSpec().getTls() != null));
        }
    }

    private static Exposure exposure(HasMetadata resource, Service service, String host, String path, Boolean tls) {
        return new Exposure(resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getUid(),
                service.getMetadata().getName(), service.getMetadata().getUid(), host, path, tls);
    }

    private static NetworkingInfo result(Map<String, Service> services, List<Exposure> exposures, boolean complete) {
        exposures.sort(Comparator.comparing(Exposure::kind).thenComparing(Exposure::name)
                .thenComparing(e -> Objects.toString(e.host(), "")).thenComparing(e -> Objects.toString(e.path(), ""))
                .thenComparing(Exposure::serviceName));
        var hosts = exposures.stream().map(Exposure::host).filter(Objects::nonNull).distinct().toList();
        var refs = services.values().stream().map(s -> new ServiceRef(s.getMetadata().getName(), s.getMetadata().getUid()))
                .sorted(Comparator.comparing(ServiceRef::name)).toList();
        return new NetworkingInfo(!exposures.isEmpty(), hosts.size() == 1 && exposures.stream().allMatch(e -> e.host() != null) ? hosts.getFirst() : null,
                !exposures.isEmpty() && exposures.stream().allMatch(e -> Boolean.TRUE.equals(e.tlsConfigured())), refs, exposures, complete);
    }

    private static void warning(List<CollectionWarning> warnings, WarningCode code, String message) {
        var warning = new CollectionWarning(code, "networking", message);
        if (!warnings.contains(warning)) warnings.add(warning);
    }
}
