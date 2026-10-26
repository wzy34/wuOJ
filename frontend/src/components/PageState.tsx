import { Button, Empty, Result, Skeleton, Spin } from '@arco-design/web-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

/** Placeholder shown in place of page content while it loads. */
export function PageLoading({ rows = 6 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Loading" style={{ padding: '24px 0' }}>
      <Skeleton animation text={{ rows }} />
    </div>
  );
}

/** Centered spinner for whole-page waits, e.g. while the session is being checked. */
export function FullPageSpinner() {
  return (
    <div
      role="status"
      aria-label="Loading"
      style={{ display: 'grid', placeItems: 'center', minHeight: '60vh' }}
    >
      <Spin size={32} />
    </div>
  );
}

export function PageEmpty({ description, action }: { description: string; action?: ReactNode }) {
  return (
    <div style={{ padding: '40px 0' }}>
      <Empty description={description} />
      {action && <div style={{ textAlign: 'center', marginTop: 12 }}>{action}</div>}
    </div>
  );
}

export function PageError({
  title = 'Something went wrong',
  description = 'The content could not be loaded.',
  onRetry,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
}) {
  return (
    <Result
      status="error"
      title={title}
      subTitle={description}
      extra={
        onRetry && (
          <Button type="primary" onClick={onRetry}>
            Retry
          </Button>
        )
      }
    />
  );
}

export function NotFoundState({
  title = 'Page not found',
  linkTo = '/problems',
  linkText = 'Back to problems',
}: {
  title?: string;
  linkTo?: string;
  linkText?: string;
}) {
  return (
    <Result
      status="404"
      title={title}
      extra={
        <Link to={linkTo}>
          <Button type="primary">{linkText}</Button>
        </Link>
      }
    />
  );
}
