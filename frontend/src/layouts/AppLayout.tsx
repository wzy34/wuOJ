import { Button, Dropdown, Layout, Menu, Tooltip } from '@arco-design/web-react';
import { IconDown, IconMoon, IconPoweroff, IconSun, IconUser } from '@arco-design/web-react/icon';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { useCurrentUser } from '@/features/auth/useCurrentUser';
import { useSignOut } from '@/features/auth/useSignOut';
import { APP_NAME } from '@/lib/constants';
import { useEffectiveTheme } from '@/lib/theme';
import { usePreferences } from '@/stores/preferences';
import styles from './AppLayout.module.css';

const SECTIONS = [
  { key: '/problems', label: 'Problems' },
  { key: '/submissions', label: 'Submissions' },
];

function ThemeToggle() {
  const theme = useEffectiveTheme();
  const setTheme = usePreferences((state) => state.setTheme);
  const next = theme === 'dark' ? 'light' : 'dark';
  const label = `Switch to ${next} theme`;
  return (
    <Tooltip content={label}>
      <Button
        shape="circle"
        type="secondary"
        aria-label={label}
        icon={theme === 'dark' ? <IconSun /> : <IconMoon />}
        onClick={() => setTheme(next)}
      />
    </Tooltip>
  );
}

function UserMenu() {
  const { user } = useCurrentUser();
  const { signOut, isPending } = useSignOut();
  const menu = (
    <Menu onClickMenuItem={(key) => key === 'sign-out' && signOut()}>
      <Menu.Item key="sign-out" disabled={isPending}>
        <IconPoweroff style={{ marginRight: 8 }} />
        Sign out
      </Menu.Item>
    </Menu>
  );
  return (
    <Dropdown droplist={menu} position="br" trigger="click">
      <Button
        type="text"
        aria-label="User menu"
        className={styles.userButton}
        // Arco colours text buttons with the primary colour; the account name reads better neutral.
        style={{ color: 'var(--color-text-1)' }}
      >
        <IconUser />
        <span>{user?.username}</span>
        <IconDown />
      </Button>
    </Dropdown>
  );
}

/** Top bar plus page area for every signed-in page. */
export function AppLayout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const current = SECTIONS.find(
    (section) => pathname === section.key || pathname.startsWith(`${section.key}/`),
  );

  return (
    <Layout className={styles.layout}>
      <Layout.Header className={styles.header}>
        <Link to="/problems" className={styles.brand}>
          {APP_NAME}
        </Link>
        <Menu
          mode="horizontal"
          className={styles.nav}
          selectedKeys={current ? [current.key] : []}
          onClickMenuItem={(key) => void navigate(key)}
        >
          {SECTIONS.map((section) => (
            <Menu.Item key={section.key}>{section.label}</Menu.Item>
          ))}
        </Menu>
        <div className={styles.actions}>
          <ThemeToggle />
          <UserMenu />
        </div>
      </Layout.Header>
      <Layout.Content className={styles.content}>
        <Outlet />
      </Layout.Content>
    </Layout>
  );
}
