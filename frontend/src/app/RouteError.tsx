import { Button, Result } from '@arco-design/web-react';
import { useRouteError } from 'react-router';

/** Shown instead of a page that crashed while rendering; the top bar above it stays usable. */
export function RouteError() {
  const error = useRouteError();
  if (import.meta.env.DEV) console.error(error);
  return (
    <Result
      status="error"
      title="This page crashed"
      subTitle="An unexpected error stopped this page from rendering."
      extra={
        <Button type="primary" onClick={() => window.location.reload()}>
          Reload
        </Button>
      }
    />
  );
}
