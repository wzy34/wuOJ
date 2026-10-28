import { Card, Grid, Space, Statistic, Tag, Typography } from '@arco-design/web-react';
import { Link, useParams } from 'react-router';
import { getHttpStatus } from '@/api/http';
import { useGetUserProfile } from '@/api/generated/users/users';
import { NotFoundState, PageError, PageLoading } from '@/components/PageState';
import { formatDate } from '@/lib/format';
import { usePageTitle } from '@/lib/usePageTitle';
import styles from './ProfilePage.module.css';

export function ProfilePage() {
  const { username = '' } = useParams();
  const profile = useGetUserProfile(username);
  const notFound = getHttpStatus(profile.error) === 404;
  usePageTitle(profile.data?.username ?? (notFound ? 'User not found' : undefined));

  if (profile.isPending) return <PageLoading />;
  if (notFound) return <NotFoundState title="User not found" />;
  if (profile.isError) return <PageError onRetry={() => void profile.refetch()} />;

  const data = profile.data;
  const stats = [
    { title: 'Solved', value: data.solvedCount },
    { title: 'Attempted', value: data.attemptedCount },
    { title: 'Submissions', value: data.submissionCount },
    { title: 'Accepted', value: data.acceptedCount },
  ];
  return (
    <section className={styles.page}>
      <div className={styles.header}>
        <Space align="center">
          <Typography.Title heading={2} className={styles.title}>
            {data.username}
          </Typography.Title>
          {data.role === 'ADMIN' && <Tag color="arcoblue">Admin</Tag>}
        </Space>
        <Typography.Text type="secondary">Joined {formatDate(data.joinedAt)}</Typography.Text>
      </div>
      <Card>
        <Grid.Row gutter={16}>
          {stats.map((stat) => (
            <Grid.Col key={stat.title} xs={12} md={6}>
              <Statistic title={stat.title} value={stat.value} groupSeparator />
            </Grid.Col>
          ))}
        </Grid.Row>
      </Card>
      <Card
        title="Solved problems"
        extra={
          <Link to={`/submissions?scope=all&user=${encodeURIComponent(data.username)}`}>
            View submissions
          </Link>
        }
      >
        {data.solved.length === 0 ? (
          <Typography.Text type="secondary">No problems solved yet</Typography.Text>
        ) : (
          <ul className={styles.solved} aria-label="Solved problems">
            {data.solved.map((problem) => (
              <li key={problem.id}>
                <Link to={`/problems/${problem.slug}`}>
                  {problem.displayId}. {problem.title}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </section>
  );
}
