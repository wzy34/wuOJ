import { Tabs, Typography } from '@arco-design/web-react';
import { useLocation, useNavigate } from 'react-router';
import styles from './Admin.module.css';

const TABS = [
  { key: '/admin/users', title: 'Accounts' },
  { key: '/admin/problems', title: 'Problems' },
  { key: '/admin/audit', title: 'Audit log' },
];

/** The admin pages' heading, with tabs to move between them. */
export function AdminTabs() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  // The problem editor (/admin/problems/1) belongs to the Problems tab.
  const active = TABS.find((tab) => pathname === tab.key || pathname.startsWith(`${tab.key}/`));
  return (
    <div className={styles.heading}>
      <Typography.Title heading={2} className={styles.title}>
        Admin
      </Typography.Title>
      <Tabs
        activeTab={active?.key ?? pathname}
        onChange={(key) => void navigate(key)}
        className={styles.tabs}
      >
        {TABS.map((tab) => (
          <Tabs.TabPane key={tab.key} title={tab.title} />
        ))}
      </Tabs>
    </div>
  );
}
