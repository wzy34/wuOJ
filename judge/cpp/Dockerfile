# The C++17 judge image: GCC for compiling submissions, and oj-runner, which runs and measures them.
# Built by `./gradlew buildJudgeImage` in backend/ (tag wuoj/judge-cpp:dev).

# Pinned (multi-arch index digest) so judge results do not change with base image updates.
ARG BASE=debian:trixie-slim@sha256:62bd423aeee912639ca8ee681c2b762cb3e25137e0a92b20be455898a9f034ad

FROM ${BASE} AS runner
RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc libc6-dev make \
    && rm -rf /var/lib/apt/lists/*
COPY runner/oj-runner.c runner/Makefile /src/
RUN make -C /src

FROM ${BASE}
RUN apt-get update \
    && apt-get install -y --no-install-recommends g++ libc6-dev \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 1000 --user-group --no-create-home --shell /usr/sbin/nologin judge
COPY --from=runner /src/oj-runner /usr/local/bin/oj-runner
COPY --chmod=0755 runner/selftest.sh /usr/local/share/oj-runner/selftest.sh
WORKDIR /work
CMD ["/usr/local/bin/oj-runner", "idle"]
