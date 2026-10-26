import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
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

  it("shows the student's name, a theme toggle and sets the tab title", async () => {
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
});
