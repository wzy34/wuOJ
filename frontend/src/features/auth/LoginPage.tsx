import { Alert, Button, Card, Form, Input, Typography } from '@arco-design/web-react';
import { IconLock, IconUser } from '@arco-design/web-react/icon';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { getGetCurrentUserQueryKey, useLogin } from '@/api/generated/auth/auth';
import type { LoginRequest } from '@/api/generated/model';
import { getApiProblem, getHttpStatus } from '@/api/http';
import { FullPageSpinner } from '@/components/PageState';
import { adoptGuestDrafts } from '@/features/problems/useDraft';
import { APP_NAME } from '@/lib/constants';
import { usePageTitle } from '@/lib/usePageTitle';
import styles from './LoginPage.module.css';
import { registerPath, safeRedirect } from './safeRedirect';
import { useCurrentUser, useRegistrationOpen } from './useCurrentUser';

export const INVALID_CREDENTIALS = 'Invalid username or password';

export function LoginPage() {
  usePageTitle('Sign in');
  const [form] = Form.useForm<LoginRequest>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, isPending: checkingSession } = useCurrentUser();
  const registrationOpen = useRegistrationOpen();
  const [error, setError] = useState<string>();
  const target = safeRedirect(searchParams.get('redirect'));

  const login = useLogin({
    mutation: {
      onSuccess: (signedIn) => {
        adoptGuestDrafts(signedIn.id);
        queryClient.setQueryData(getGetCurrentUserQueryKey(), signedIn);
        void navigate(target, { replace: true });
      },
      onError: (failure) => {
        const status = getHttpStatus(failure);
        if (status === 401) {
          setError(INVALID_CREDENTIALS);
          form.setFieldValue('password', '');
        } else if (status !== undefined && status < 500) {
          setError(getApiProblem(failure)?.detail ?? 'Sign in failed. Please try again.');
        }
        // Network and server errors are reported by the global notification.
      },
    },
  });

  if (user) return <Navigate to={target} replace />;
  if (checkingSession) return <FullPageSpinner />;

  return (
    <main className={styles.page}>
      <Card className={styles.card} bordered>
        <Typography.Title heading={3} className={styles.brand}>
          {APP_NAME}
        </Typography.Title>
        <Typography.Paragraph type="secondary" className={styles.subtitle}>
          Sign in to submit solutions and follow your progress.
        </Typography.Paragraph>
        {error && <Alert type="error" content={error} className={styles.alert} />}
        <Form
          form={form}
          layout="vertical"
          autoComplete="on"
          onSubmit={(values) => {
            setError(undefined);
            login.mutate({ data: values });
          }}
        >
          <Form.Item
            label="Username"
            field="username"
            rules={[{ required: true, message: 'Enter your username' }]}
          >
            <Input prefix={<IconUser />} autoComplete="username" autoFocus />
          </Form.Item>
          <Form.Item
            label="Password"
            field="password"
            rules={[{ required: true, message: 'Enter your password' }]}
          >
            <Input.Password prefix={<IconLock />} autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" long loading={login.isPending}>
            Sign in
          </Button>
        </Form>
        {registrationOpen && (
          <Typography.Paragraph className={styles.switch}>
            New here? <Link to={registerPath(target)}>Create an account</Link>
          </Typography.Paragraph>
        )}
        <Typography.Paragraph className={styles.switch}>
          <Link to="/problems">Browse problems without signing in</Link>
        </Typography.Paragraph>
      </Card>
    </main>
  );
}
