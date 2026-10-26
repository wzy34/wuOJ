package dev.wuoj.sandbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.config.Role;
import dev.wuoj.judge.JudgeEngine;
import dev.wuoj.judge.JudgeRepository;
import dev.wuoj.testdata.TestDataStore;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/** The real judge: Docker containers. Used by workers unless the simulated judge is enabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(Role.WORKER)
@ConditionalOnProperty(name = "oj.judge.simulated", havingValue = "false", matchIfMissing = true)
public class SandboxConfig {

    private static final Logger log = LoggerFactory.getLogger(SandboxConfig.class);

    /**
     * The Docker endpoint: {@code oj.judge.docker-host}, else {@code DOCKER_HOST}, else Docker Desktop's per-user
     * socket if it exists, else the standard socket. The API version is not pinned, so the daemon's current version
     * is used.
     */
    public static String dockerHost(@Nullable String configured) {
        if (configured != null && !configured.isBlank()) return configured;
        String environment = System.getenv("DOCKER_HOST");
        if (environment != null && !environment.isBlank()) return environment;
        Path desktop = Path.of(System.getProperty("user.home"), ".docker", "run", "docker.sock");
        if (Files.exists(desktop)) return "unix://" + desktop;
        return "unix:///var/run/docker.sock";
    }

    @Bean(destroyMethod = "close")
    DockerClient dockerClient(OjProperties properties) {
        return createClient(dockerHost(properties.judge().dockerHost()));
    }

    public static DockerClient createClient(String dockerHost) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        ApacheDockerHttpClient http = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(64)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofMinutes(2))
                .build();
        return DockerClientImpl.getInstance(config, http);
    }

    @Bean
    DockerSandbox dockerSandbox(DockerClient dockerClient, OjProperties properties) {
        return new DockerSandbox(dockerClient, properties.judge().image());
    }

    @Bean
    JudgeEngine dockerJudgeEngine(DockerSandbox sandbox, TestDataStore testdata, OjProperties properties) {
        return new DockerJudgeEngine(sandbox, testdata, properties.judge().workDir(), properties.judge().runtime());
    }

    @Bean
    SandboxCleaner sandboxCleaner(DockerSandbox sandbox, JudgeRepository judge, OjProperties properties) {
        return new SandboxCleaner(sandbox, judge, properties.judge().workDir());
    }

    /** UP when the Docker daemon answers and the judge image is present. */
    @Bean
    HealthIndicator dockerHealthIndicator(DockerSandbox sandbox) {
        return () -> {
            try {
                String version = sandbox.serverVersion();
                Health.Builder health = sandbox.imagePresent() ? Health.up() : Health.down();
                return health.withDetail("version", version).withDetail("image", sandbox.image()).build();
            } catch (RuntimeException e) {
                return Health.down(e).build();
            }
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    void checkImage(ApplicationReadyEvent event) {
        DockerSandbox sandbox = event.getApplicationContext().getBean(DockerSandbox.class);
        try {
            if (!sandbox.imagePresent()) {
                log.error("Judge image {} is missing: build it with ./gradlew buildJudgeImage", sandbox.image());
            }
        } catch (RuntimeException e) {
            log.error("Cannot reach Docker at {}: {}", dockerHost(null), e.getMessage());
        }
    }
}
