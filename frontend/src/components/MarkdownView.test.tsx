import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MarkdownView } from './MarkdownView';

describe('MarkdownView', () => {
  it('typesets inline and display math', () => {
    const { container } = render(
      <MarkdownView>
        {'Constraints: $1 \\le n \\le 10^5$\n\n$$\n\\sum_{i=1}^{n} a_i\n$$'}
      </MarkdownView>,
    );
    expect(container.querySelectorAll('.katex').length).toBe(2);
    expect(container.querySelector('.katex-display')).not.toBeNull();
    expect(container).not.toHaveTextContent('$1');
  });

  it('never turns raw HTML into elements', () => {
    const alert = vi.spyOn(window, 'alert').mockImplementation(() => {});
    const { container } = render(
      <MarkdownView>
        {'Hi <script>alert(1)</script> <img src=x onerror="alert(2)"> there'}
      </MarkdownView>,
    );
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
    expect(alert).not.toHaveBeenCalled();
  });

  it('renders GFM tables', () => {
    render(<MarkdownView>{'| n | answer |\n| --- | --- |\n| 10 | 4 |'}</MarkdownView>);
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '4' })).toBeInTheDocument();
  });

  it('opens safe links in a new tab and drops other protocols', () => {
    render(
      <MarkdownView>
        {
          '[docs](https://en.cppreference.com) [mail](mailto:t@example.com) [bad](javascript:alert(1)) [chat](irc://x)'
        }
      </MarkdownView>,
    );
    const docs = screen.getByRole('link', { name: 'docs' });
    expect(docs).toHaveAttribute('href', 'https://en.cppreference.com');
    expect(docs).toHaveAttribute('target', '_blank');
    expect(docs).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.getByRole('link', { name: 'mail' })).toHaveAttribute(
      'href',
      'mailto:t@example.com',
    );
    expect(screen.getByText('bad').getAttribute('href') ?? '').toBe('');
    expect(screen.getByText('chat').getAttribute('href') ?? '').toBe('');
  });
});
