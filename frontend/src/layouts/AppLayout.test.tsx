import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { mockConfig } from '@/mocks/db';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

describe('AppLayout', () => {
  it('highlights the current section', async () => {
    await signIn();
    renderRoute('/submissions/42');
    const submissions = await screen.findByRole('menuitem', { name: 'Submissions' });
    expect(submissions).toHaveClass('arco-menu-selected');
    expect(screen.getByRole('menuitem', { name: 'Problems' })).not.toHaveClass(
      'arco-menu-selected',
    );
  });

  it("shows the user's name, a theme toggle and sets the tab title", async () => {
    await signIn();
    renderRoute('/problems');
    expect(await screen.findByRole('button', { name: 'User menu' })).toHaveTextContent('student');
    expect(screen.getByRole('button', { name: 'Switch to dark theme' })).toBeInTheDocument();
    await waitFor(() => expect(document.title).toBe('Problems · wuOJ'));
  });

  it('switches and remembers the theme from the toggle', async () => {
    await signIn();
    renderRoute('/problems');
    await userEvent.click(await screen.findByRole('button', { name: 'Switch to dark theme' }));
    expect(document.body).toHaveAttribute('arco-theme', 'dark');
    expect(screen.getByRole('button', { name: 'Switch to light theme' })).toBeInTheDocument();
    expect(localStorage.getItem('wuoj-preferences')).toContain('"theme":"dark"');
  });

  it('offers visitors to sign in or register, coming back to the page', async () => {
    const { router } = renderRoute('/problems?page=2');
    await userEvent.click(await screen.findByRole('button', { name: 'Register' }));
    expect(router.state.location.pathname).toBe('/register');
    expect(router.state.location.search).toBe('?redirect=%2Fproblems%3Fpage%3D2');

    await router.navigate('/problems');
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.queryByRole('button', { name: 'User menu' })).not.toBeInTheDocument();
  });

  it('hides Register while registration is closed', async () => {
    mockConfig.registrationEnabled = false;
    renderRoute('/problems');
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Register' })).not.toBeInTheDocument();
  });

  it('gives users a menu with their profile, settings and sign out', async () => {
    await signIn('alice');
    const { router } = renderRoute('/problems');
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    await user.click(await screen.findByRole('button', { name: 'User menu' }));
    expect(await screen.findByText('Settings')).toBeInTheDocument();
    expect(screen.getByText('Sign out')).toBeInTheDocument();
    await user.click(screen.getByText('Profile'));
    expect(router.state.location.pathname).toBe('/users/alice');
    expect(screen.queryByRole('menuitem', { name: 'Admin' })).not.toBeInTheDocument();
  });

  it('shows admins an Admin link', async () => {
    await signIn('admin');
    const { router } = renderRoute('/problems');
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Admin' }));
    expect(router.state.location.pathname).toBe('/admin/users');
    expect(await screen.findByRole('menuitem', { name: 'Admin' })).toHaveClass(
      'arco-menu-selected',
    );
  });
});
