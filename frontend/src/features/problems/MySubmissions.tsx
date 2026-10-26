import { List, Typography } from '@arco-design/web-react';
import { Link } from 'react-router';
import { useListSubmissions } from '@/api/generated/submissions/submissions';
import { PageError, PageLoading } from '@/components/PageState';
import { VerdictTag } from '@/components/VerdictTag';
import { formatDateTime, formatFromNow, formatTime } from '@/lib/format';

/** The student's recent submissions for one problem, newest first. */
export function MySubmissions({ slug }: { slug: string }) {
  const submissions = useListSubmissions({ scope: 'mine', problem: slug, size: 20 });

  if (submissions.isPending) return <PageLoading rows={3} />;
  if (submissions.isError) {
    return <PageError onRetry={() => void submissions.refetch()} />;
  }
  if (submissions.data.items.length === 0) {
    return (
      <Typography.Text type="secondary">You have not submitted this problem yet.</Typography.Text>
    );
  }
  return (
    <section aria-label="My submissions">
      <List
        size="small"
        dataSource={submissions.data.items}
        render={(submission) => (
          <List.Item key={submission.id}>
            <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
              <Link to={`/submissions/${submission.id}`}>#{submission.id}</Link>
              <VerdictTag status={submission.status} verdict={submission.verdict} />
              <span>{formatTime(submission.timeMs)}</span>
              <Typography.Text type="secondary" title={formatDateTime(submission.createdAt)}>
                {formatFromNow(submission.createdAt)}
              </Typography.Text>
            </div>
          </List.Item>
        )}
      />
    </section>
  );
}
