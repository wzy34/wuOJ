import { Alert, Button, Card, Form, Input, Typography } from '@arco-design/web-react';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useChangePassword } from '@/api/generated/account/account';
import { getGetCurrentUserQueryKey } from '@/api/generated/auth/auth';
import type { CurrentUser } from '@/api/generated/model';
import { getApiProblem, getHttpStatus } from '@/api/http';
import { usePageTitle } from '@/lib/usePageTitle';
import { showFieldErrors } from './formErrors';
import { confirmRule, passwordRules } from './formRules';
import { safeRedirect } from './safeRedirect';
import styles from './SettingsPage.module.css';
import { useCurrentUser } from './useCurrentUser';

export const PASSWORD_CHANGED = 'Password changed. You were signed out on your other devices.';

type PasswordForm = { currentPassword: string; newPassword: string; confirm: string };

export function SettingsPage() {
  usePageTitle('Settings');
  const [form] = Form.useForm<PasswordForm>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useCurrentUser();
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string>();
  const forced = user?.mustChangePassword === true;

  const change = useChangePassword({
    mutation: {
      onSuccess: () => {
        form.resetFields();
        queryClient.setQueryData<CurrentUser>(
          getGetCurrentUserQueryKey(),
          (current) => current && { ...current, mustChangePassword: false },
        );
        if (forced) {
          void navigate(safeRedirect(searchParams.get('redirect')), { replace: true });
        } else {
          setDone(true);
        }
      },
      onError: (failure) => {
        const status = getHttpStatus(failure);
        if (status === 400 && showFieldErrors(form, failure, ['currentPassword', 'newPassword'])) {
          return;
        }
        if (status !== undefined && status < 500 && status !== 401) {
          setError(getApiProblem(failure)?.detail ?? 'The password could not be changed.');
        }
      },
    },
  });

  return (
    <section className={styles.page}>
      <Typography.Title heading={2} className={styles.title}>
        Settings
      </Typography.Title>
      {forced && (
        <Alert
          type="warning"
          title="Choose a new password"
          content="An admin reset your password. Choose a new one to continue using wuOJ."
          className={styles.alert}
        />
      )}
      <Card title="Change password">
        {done && (
          <Alert
            type="success"
            content={PASSWORD_CHANGED}
            closable
            onClose={() => setDone(false)}
            className={styles.alert}
          />
        )}
        {error && <Alert type="error" content={error} className={styles.alert} />}
        <Form
          form={form}
          layout="vertical"
          onSubmit={({ currentPassword, newPassword }) => {
            setDone(false);
            setError(undefined);
            change.mutate({ data: { currentPassword, newPassword } });
          }}
        >
          <Form.Item
            label={forced ? 'Temporary password' : 'Current password'}
            field="currentPassword"
            rules={[{ required: true, message: 'Enter your current password' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item label="New password" field="newPassword" rules={passwordRules}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="Confirm new password"
            field="confirm"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: 'Repeat the new password' },
              confirmRule(form, 'newPassword'),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={change.isPending}>
            Change password
          </Button>
        </Form>
        <Typography.Paragraph type="secondary" className={styles.note}>
          There is no password recovery. If you forget your password, an admin can reset it for you.
        </Typography.Paragraph>
      </Card>
    </section>
  );
}
