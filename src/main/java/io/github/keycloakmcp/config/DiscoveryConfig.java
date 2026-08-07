package io.github.keycloakmcp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "discovery")
public interface DiscoveryConfig {

    Kubernetes kubernetes();

    OpenShift openshift();

    interface Kubernetes {
        @WithDefault("false")
        boolean enabled();
    }

    interface OpenShift {
        @WithDefault("false")
        boolean enabled();
    }
}
