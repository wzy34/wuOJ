import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

vi.mock('@/features/submissions/SubmissionListPage', () => ({
  SubmissionListPage: () => {
    throw new Error('boom');
  },
}));

describe('route error boundary', () => {
  it('replaces a crashed page with a reload screen and keeps the top bar', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    await signIn();
    renderRoute('/submissions');

    expect(await screen.findByText('This page crashed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'wuOJ' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Problems' })).toBeInTheDocument();
  });
});
