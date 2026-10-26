-- Accounts are created by the user importer; there is no self-registration.
CREATE TABLE users (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      varchar(64)  NOT NULL,
    password_hash varchar(255) NOT NULL,
    role          varchar(16)  NOT NULL CHECK (role IN ('STUDENT', 'ADMIN')),
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));

CREATE TABLE problems (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    display_id       varchar(32)  NOT NULL UNIQUE,
    slug             varchar(64)  NOT NULL UNIQUE,
    title            varchar(200) NOT NULL,
    difficulty       varchar(8)   NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    time_limit_ms    integer      NOT NULL CHECK (time_limit_ms BETWEEN 100 AND 10000),
    memory_limit_mb  integer      NOT NULL CHECK (memory_limit_mb BETWEEN 16 AND 1024),
    statement_md     text         NOT NULL,
    visible          boolean      NOT NULL DEFAULT false,
    -- The test-data version new submissions are judged against (a row of problem_testdata).
    testdata_version varchar(64)  NOT NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE problem_samples (
    problem_id bigint  NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    ordinal    integer NOT NULL CHECK (ordinal >= 1),
    input      text    NOT NULL,
    output     text    NOT NULL,
    PRIMARY KEY (problem_id, ordinal)
);

-- Immutable test-data versions; the files live in the test-data store under <problem id>/<version>.
CREATE TABLE problem_testdata (
    problem_id  bigint      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    version     varchar(64) NOT NULL,
    test_count  integer     NOT NULL CHECK (test_count >= 1),
    total_bytes bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (problem_id, version)
);

CREATE TABLE submissions (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint      NOT NULL REFERENCES users (id),
    problem_id       bigint      NOT NULL REFERENCES problems (id),
    language         varchar(16) NOT NULL CHECK (language IN ('CPP17')),
    source_code      text        NOT NULL,
    status           varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COMPILING', 'RUNNING', 'FINISHED')),
    verdict          varchar(8) CHECK (verdict IN ('AC', 'WA', 'TLE', 'MLE', 'RE', 'CE', 'OLE', 'SE')),
    time_ms          integer,
    memory_kb        integer,
    current_test     integer,
    total_tests      integer,
    compile_output   text,
    testdata_version varchar(64) NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    finished_at      timestamptz,
    -- Queue bookkeeping; see design.md decision 6.
    enqueued_at      timestamptz,
    worker_id        varchar(100),
    lease_until      timestamptz,
    attempts         integer     NOT NULL DEFAULT 0,
    -- Incremented with every change clients can observe; orders the live status events.
    event_seq        bigint      NOT NULL DEFAULT 1,
    FOREIGN KEY (problem_id, testdata_version) REFERENCES problem_testdata (problem_id, version),
    CHECK ((status = 'FINISHED') = (verdict IS NOT NULL))
);
CREATE INDEX submissions_user_idx ON submissions (user_id, id DESC);
CREATE INDEX submissions_problem_idx ON submissions (problem_id, id DESC);
CREATE INDEX submissions_verdict_idx ON submissions (verdict, id DESC);
CREATE INDEX submissions_unfinished_user_idx ON submissions (user_id) WHERE status <> 'FINISHED';
CREATE INDEX submissions_unfinished_lease_idx ON submissions (status, lease_until) WHERE status <> 'FINISHED';

CREATE TABLE submission_test_results (
    submission_id bigint     NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    test_number   integer    NOT NULL CHECK (test_number >= 1),
    verdict       varchar(8) NOT NULL CHECK (verdict IN ('AC', 'WA', 'TLE', 'MLE', 'RE', 'OLE', 'SE')),
    time_ms       integer    NOT NULL,
    memory_kb     integer    NOT NULL,
    PRIMARY KEY (submission_id, test_number)
);

-- A row means "attempted"; solved = true means at least one accepted submission.
CREATE TABLE user_problem_status (
    user_id         bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    problem_id      bigint      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    solved          boolean     NOT NULL DEFAULT false,
    first_solved_at timestamptz,
    PRIMARY KEY (user_id, problem_id)
);
