import type { AuditEntry, UserRole } from '@/api/generated/model';

const ROLE_LABEL: Record<UserRole, string> = { USER: 'User', ADMIN: 'Admin' };

export function describeAction(entry: AuditEntry): string {
  switch (entry.action) {
    case 'ROLE_CHANGED':
      return entry.fromRole && entry.toRole
        ? `Changed role from ${ROLE_LABEL[entry.fromRole]} to ${ROLE_LABEL[entry.toRole]}`
        : 'Changed role';
    case 'DISABLED':
      return 'Disabled the account';
    case 'ENABLED':
      return 'Enabled the account';
    case 'PASSWORD_RESET':
      return 'Reset the password';
  }
}
