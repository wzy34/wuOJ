import { Alert, Modal, Typography } from '@arco-design/web-react';
import { CopyButton } from '@/components/CopyButton';
import styles from './Admin.module.css';

/**
 * Shows a temporary password once. Closing the dialog drops it from the page: the parent clears
 * the value, and the dialog's content is unmounted.
 */
export function ResetPasswordDialog({
  username,
  password,
  onClose,
}: {
  username: string;
  password: string | undefined;
  onClose: () => void;
}) {
  return (
    <Modal
      title={`Temporary password for ${username}`}
      visible={password !== undefined}
      onOk={onClose}
      onCancel={onClose}
      okText="Done"
      hideCancel
      unmountOnExit
    >
      {password !== undefined && (
        <>
          <Typography.Paragraph>
            Give this password to {username}. They must choose a new password when they next sign
            in.
          </Typography.Paragraph>
          <div className={styles.password}>
            <span aria-label="Temporary password">{password}</span>
            <CopyButton text={password} label="Copy temporary password" />
          </div>
          <Alert type="warning" content="This password will not be shown again." />
        </>
      )}
    </Modal>
  );
}
