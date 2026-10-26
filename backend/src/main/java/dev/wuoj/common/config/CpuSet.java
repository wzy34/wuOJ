package dev.wuoj.common.config;

import java.util.BitSet;

/** Parses Docker cpuset strings such as {@code "0-2,4"}. */
public final class CpuSet {

    private CpuSet() {}

    public static BitSet parse(String cpuset) {
        BitSet cpus = new BitSet();
        for (String part : cpuset.split(",")) {
            String range = part.strip();
            try {
                int dash = range.indexOf('-');
                int from = Integer.parseInt(dash < 0 ? range : range.substring(0, dash));
                int to = dash < 0 ? from : Integer.parseInt(range.substring(dash + 1));
                if (from < 0 || to < from) throw new NumberFormatException();
                cpus.set(from, to + 1);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid cpuset '" + cpuset + "'", e);
            }
        }
        return cpus;
    }
}
