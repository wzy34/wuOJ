import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Samples } from './Samples';

describe('Samples', () => {
  it('copies the exact sample text and confirms', async () => {
    const user = userEvent.setup();
    render(<Samples samples={[{ input: '3 3\nS.#\n', output: '4\n' }]} />);

    expect(screen.getByText('Sample 1 input')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Copy sample 1 input' }));

    expect(await navigator.clipboard.readText()).toBe('3 3\nS.#\n');
    expect(await screen.findByText('Copied')).toBeInTheDocument();
  });

  it('preserves whitespace in a monospaced block', () => {
    render(<Samples samples={[{ input: '1  2\n', output: '3\n' }]} />);
    const block = screen.getByText(
      (_, element) => element?.tagName === 'PRE' && element.textContent === '1  2\n',
    );
    expect(block.tagName).toBe('PRE');
  });
});
