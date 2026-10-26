import { Space, Spin, Typography } from '@arco-design/web-react';
import { Link } from 'react-router';
import { VerdictTag } from '@/components/VerdictTag';
import { formatMemory, formatTime } from '@/lib/format';
import { runningProgress } from '@/features/submissions/progress';
import { useSubmissionLive } from '@/features/submissions/useSubmissionLive';
import styles from './ResultPanel.module.css';

/** The latest submission from this page, updating live until it has a verdict. */
export function ResultPanel({ submissionId }: { submissionId?: number }) {
  const { data: submission } = useSubmissionLive(submissionId);

  if (submissionId === undefined) {
    return (
      <div className={styles.panel}>
        <Typography.Text type="secondary">
          Submit your code to see the verdict here. Tip: press Ctrl+Enter (⌘+Enter on macOS) in the
          editor.
        </Typography.Text>
      </div>
    );
  }
  if (!submission) {
    return (
      <div className={styles.panel}>
        <Spin size={16} /> <span className={styles.muted}>Loading submission…</span>
      </div>
    );
  }

  const finished = submission.status === 'FINISHED';
  return (
    <div className={styles.panel} role="region" aria-label="Submission result" aria-live="polite">
      <Space size="medium" wrap>
        <Link to={`/submissions/${submission.id}`}>Submission #{submission.id}</Link>
        <VerdictTag
          status={submission.status}
          verdict={submission.verdict}
          progress={runningProgress(submission)}
        />
        {finished && submission.verdict !== 'CE' && (
          <span className={styles.muted}>
            {formatTime(submission.timeMs)} · {formatMemory(submission.memoryKb)}
          </span>
        )}
        {finished && <Link to={`/submissions/${submission.id}`}>View details</Link>}
      </Space>
      {finished && submission.verdict === 'CE' && submission.compileOutput && (
        <pre className={styles.compileOutput} aria-label="Compiler output">
          {submission.compileOutput}
        </pre>
      )}
    </div>
  );
}
