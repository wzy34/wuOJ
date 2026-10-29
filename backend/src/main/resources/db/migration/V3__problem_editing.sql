-- Problem editing by admins; see the add-problem-editing design.

ALTER TABLE problems
    -- Increased by every change to the content (details, statement, samples, visibility); guards concurrent edits.
    ADD COLUMN revision         integer    NOT NULL DEFAULT 1,
    -- Whether the problem was last changed by an import or in the web app; re-imports don't overwrite web edits.
    ADD COLUMN last_edit_source varchar(8) NOT NULL DEFAULT 'IMPORT'
        CONSTRAINT problems_last_edit_source_check CHECK (last_edit_source IN ('IMPORT', 'WEB'));

-- The limits in force when the submission was made; it is judged with these, whatever the problem says later.
ALTER TABLE submissions
    ADD COLUMN time_limit_ms   integer,
    ADD COLUMN memory_limit_mb integer;
UPDATE submissions s
   SET time_limit_ms = p.time_limit_ms, memory_limit_mb = p.memory_limit_mb
  FROM problems p
 WHERE p.id = s.problem_id;
ALTER TABLE submissions
    ALTER COLUMN time_limit_ms SET NOT NULL,
    ALTER COLUMN memory_limit_mb SET NOT NULL;

-- Admin actions on problems as well as accounts. Each record targets exactly one of the two.
ALTER TABLE audit_log
    ALTER COLUMN target_user_id DROP NOT NULL,
    ADD COLUMN target_problem_id bigint REFERENCES problems (id),
    ADD CONSTRAINT audit_log_one_target CHECK ((target_user_id IS NULL) <> (target_problem_id IS NULL)),
    DROP CONSTRAINT audit_log_action_check,
    ADD CONSTRAINT audit_log_action_check CHECK (action IN ('ROLE_CHANGED', 'DISABLED', 'ENABLED', 'PASSWORD_RESET',
                                                            'PROBLEM_CREATED', 'PROBLEM_UPDATED', 'TESTS_UPDATED'));
