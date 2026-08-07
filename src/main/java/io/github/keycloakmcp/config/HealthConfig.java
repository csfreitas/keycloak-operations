package io.github.keycloakmcp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "health")
public interface HealthConfig {

    Pods pods();

    Management management();

    interface Pods {
        @WithName("restart-warning-threshold")
        @WithDefault("3")
        int restartWarningThreshold();
    }

    interface Management {
        @WithName("connect-timeout-ms")
        @WithDefault("3000")
        int connectTimeoutMs();

        @WithName("read-timeout-ms")
        @WithDefault("5000")
        int readTimeoutMs();
    }
}
