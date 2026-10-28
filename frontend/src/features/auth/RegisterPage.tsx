import { Alert, Button, Card, Form, Input, Typography } from '@arco-design/web-react';
import { IconLock, IconUser } from '@arco-design/web-react/icon';
import { useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import {
  getGetCurrentUserQueryKey,
  useGetAuthOptions,
  useRegister,
} from '@/api/generated/auth/auth';
import { getApiProblem, getHttpStatus } from '@/api/http';
import { FullPageSpinner } from '@/components/PageState';
import { adoptGuestDrafts } from '@/features/problems/useDraft';
import { APP_NAME } from '@/lib/constants';
import { usePageTitle } from '@/lib/usePageTitle';
import { showFieldErrors } from './formErrors';
import { confirmRule, passwordRules, USERNAME_RULE } from './formRules';
import styles from './LoginPage.module.css';
import { loginPath, safeRedirect } from './safeRedirect';
import { useCurrentUser } from './useCurrentUser';

type RegisterForm = { username: string; password: string; confirm: string };

function Page({ children }: { children: ReactNode }) {
  return (
    <main className={styles.page}>
      <Card className={styles.card} bordered>
        <Typography.Title heading={3} className={styles.brand}>
          {APP_NAME}
        </Typography.Title>
        {children}
      </Card>
    </main>
  );
}

export function RegisterPage() {
  usePageTitle('Register');
  const [form] = Form.useForm<RegisterForm>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, isPending: checkingSession } = useCurrentUser();
  const options = useGetAuthOptions({ query: { staleTime: Infinity, retry: false } });
  const [error, setError] = useState<string>();
  const target = safeRedirect(searchParams.get('redirect'));

  const register = useRegister({
    mutation: {
      onSuccess: (registered) => {
        adoptGuestDrafts(registered.id);
        queryClient.setQueryData(getGetCurrentUserQueryKey(), registered);
        void navigate(target, { replace: true });
      },
      onError: (failure) => {
        const status = getHttpStatus(failure);
        if (status === 400 && showFieldErrors(form, failure, ['username', 'password'])) return;
        if (status !== undefined && status < 500) {
          setError(getApiProblem(failure)?.detail ?? 'Registration failed. Please try again.');
        }
        // Network and server errors are reported by the global notification.
      },
    },
  });

  if (user) return <Navigate to={target} replace />;
  if (checkingSession || options.isPending) return <FullPageSpinner />;

  if (!options.data?.registrationEnabled) {
    return (
      <Page>
        <Alert type="info" content="Registration is closed." className={styles.alert} />
        <Typography.Paragraph className={styles.switch}>
          <Link to={loginPath(target)}>Sign in</Link>
        </Typography.Paragraph>
      </Page>
    );
  }

  return (
    <Page>
      <Typography.Paragraph type="secondary" className={styles.subtitle}>
        Create an account to submit solutions and follow your progress.
      </Typography.Paragraph>
      {error && <Alert type="error" content={error} className={styles.alert} />}
      <Form
        form={form}
        layout="vertical"
        autoComplete="on"
        onSubmit={({ username, password }) => {
          setError(undefined);
          register.mutate({ data: { username, password } });
        }}
      >
        <Form.Item
          label="Username"
          field="username"
          extra="Letters, digits, '.', '_' and '-'. Shown on your profile and submissions."
          rules={[
            { required: true, message: 'Choose a username' },
            { maxLength: 64, message: 'Use at most 64 characters' },
            { match: USERNAME_RULE, message: "Use only letters, digits, '.', '_' and '-'" },
          ]}
        >
          <Input prefix={<IconUser />} autoComplete="username" autoFocus />
        </Form.Item>
        <Form.Item label="Password" field="password" rules={passwordRules}>
          <Input.Password prefix={<IconLock />} autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          label="Confirm password"
          field="confirm"
          dependencies={['password']}
          rules={[
            { required: true, message: 'Repeat the password' },
            confirmRule(form, 'password'),
          ]}
        >
          <Input.Password prefix={<IconLock />} autoComplete="new-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" long loading={register.isPending}>
          Create account
        </Button>
      </Form>
      <Typography.Paragraph className={styles.switch}>
        Already have an account? <Link to={loginPath(target)}>Sign in</Link>
      </Typography.Paragraph>
    </Page>
  );
}
