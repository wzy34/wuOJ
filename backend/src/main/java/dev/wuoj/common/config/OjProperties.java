package dev.wuoj.common.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.BitSet;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Everything configurable under {@code oj.*}. */
@ConfigurationProperties("oj")
public record OjProperties(
        @DefaultValue({"api", "worker"}) List<Role> roles,
        @DefaultValue TestData testdata,
        @DefaultValue Judge judge,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue LoginThrottle loginThrottle) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public record TestData(@DefaultValue("./var/testdata") Path root) {}

    /**
     * @param slots one entry per concurrent judge: a Docker cpuset such as {@code "4"} or {@code "4-5"}, or an
     *     empty string for an unpinned slot (only allowed when there is a single slot)
     * @param runtime the container runtime for judge containers (e.g. {@code runsc}); null for Docker's default
     * @param dockerHost the Docker endpoint; null to use {@code DOCKER_HOST} or the platform's default socket
     */
    public record Judge(
            @DefaultValue("wuoj/judge-cpp:dev") String image,
            @Nullable String runtime,
            @Nullable String dockerHost,
            @DefaultValue("") List<String> slots,
            @DefaultValue("./var/judge") Path workDir,
            @DefaultValue("false") boolean simulated,
            @DefaultValue("350ms") Duration simulatedStep) {

        public Judge {
            slots = slots.isEmpty() ? List.of("") : List.copyOf(slots);
            validateSlots(slots);
        }

        private static void validateSlots(List<String> slots) {
            if (slots.size() == 1) {
                if (!slots.getFirst().isBlank()) CpuSet.parse(slots.getFirst());
                return;
            }
            BitSet used = new BitSet();
            for (String slot : slots) {
                if (slot.isBlank()) {
                    throw new IllegalArgumentException(
                            "oj.judge.slots: with more than one judge slot, every slot must be pinned to CPUs");
                }
                BitSet cpus = CpuSet.parse(slot);
                if (used.intersects(cpus)) {
                    throw new IllegalArgumentException("oj.judge.slots: slots must not share CPUs, but '" + slot
                            + "' overlaps another slot");
                }
                used.or(cpus);
            }
        }
    }

    public record RateLimit(
            @DefaultValue("3") int submissions,
            @DefaultValue("10s") Duration window,
            @DefaultValue("2") int unfinished,
            @DefaultValue("5s") Duration unfinishedRetryAfter) {}

    public record LoginThrottle(@DefaultValue("5") int maxFailures, @DefaultValue("5m") Duration window) {}
}
