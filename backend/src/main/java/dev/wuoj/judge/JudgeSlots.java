package dev.wuoj.judge;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.IntStream;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.config.Role;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** The concurrent judges of this worker; each slot is pinned to its own CPUs when configured. */
@Component
@ConditionalOnRole(Role.WORKER)
public class JudgeSlots {

    /** @param cpuset the CPUs this slot's programs run on, or null when unpinned */
    public record Slot(int index, @Nullable String cpuset) {}

    private final BlockingQueue<Slot> free;
    private final int size;

    public JudgeSlots(OjProperties properties) {
        List<String> configured = properties.judge().slots();
        this.size = configured.size();
        this.free = new ArrayBlockingQueue<>(size);
        IntStream.range(0, size).forEach(i -> free.add(
                new Slot(i, configured.get(i).isBlank() ? null : configured.get(i).strip())));
    }

    public int size() {
        return size;
    }

    public Slot take() throws InterruptedException {
        return free.take();
    }

    public void release(Slot slot) {
        free.add(slot);
    }
}
