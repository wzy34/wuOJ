package dev.wuoj.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class OjPropertiesTest {

    private static OjProperties.Judge judge(List<String> slots) {
        return new OjProperties.Judge("img", null, null, slots, Path.of("w"), false, Duration.ofMillis(1));
    }

    @Test
    void singleUnpinnedSlotIsAllowed() {
        assertThat(judge(List.of("")).slots()).containsExactly("");
        assertThat(judge(List.of()).slots()).containsExactly("");
    }

    @Test
    void pinnedDisjointSlotsAreAllowed() {
        assertThat(judge(List.of("4", "5-6", "7")).slots()).hasSize(3);
    }

    @Test
    void severalSlotsMustAllBePinned() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> judge(List.of("4", "")))
                .withMessageContaining("pinned");
    }

    @Test
    void slotsMustNotShareCpus() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> judge(List.of("2-3", "3")))
                .withMessageContaining("overlaps");
    }

    @Test
    void malformedCpusetIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> judge(List.of("a")));
        assertThatIllegalArgumentException().isThrownBy(() -> judge(List.of("3-1")));
    }

    @Test
    void limitDefaults() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .run(context -> {
                    OjProperties properties = context.getBean(OjProperties.class);
                    assertThat(properties.loginThrottle()).isEqualTo(
                            new OjProperties.LoginThrottle(5, 50, Duration.ofMinutes(5)));
                    assertThat(properties.registration()).isEqualTo(
                            new OjProperties.Registration(true, 5, Duration.ofHours(1)));
                    assertThat(properties.rateLimit()).isEqualTo(new OjProperties.RateLimit(3,
                            Duration.ofSeconds(10), 2, Duration.ofSeconds(5), 30, Duration.ofMinutes(1)));
                });
    }

    @Test
    void contextFailsToStartWithSharedSlots() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("oj.judge.slots=1,1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void apiRoleStartsNoWorkerBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(RoleBeans.class)
                .withPropertyValues("oj.roles=api")
                .run(context -> {
                    assertThat(context).hasBean("apiBean");
                    assertThat(context).doesNotHaveBean("workerBean");
                });
    }

    @Test
    void defaultRolesStartBoth() {
        new ApplicationContextRunner()
                .withUserConfiguration(RoleBeans.class)
                .run(context -> {
                    assertThat(context).hasBean("apiBean");
                    assertThat(context).hasBean("workerBean");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OjProperties.class)
    static class PropertiesConfig {}

    @Configuration(proxyBeanMethods = false)
    static class RoleBeans {
        @Bean
        @ConditionalOnRole(Role.API)
        String apiBean() {
            return "api";
        }

        @Bean
        @ConditionalOnRole(Role.WORKER)
        String workerBean() {
            return "worker";
        }
    }
}
