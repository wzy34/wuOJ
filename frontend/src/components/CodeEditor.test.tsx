import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { CodeEditor } from './CodeEditor';

describe('CodeEditor', () => {
  it('runs the submit shortcut with the current text', async () => {
    const onSubmit = vi.fn();
    function Harness() {
      const [code, setCode] = useState('');
      return <CodeEditor value={code} onChange={setCode} onSubmitShortcut={onSubmit} />;
    }
    render(<Harness />);
    const user = userEvent.setup();

    await user.type(screen.getByRole('textbox', { name: 'Code editor' }), 'int main() {{}');
    await user.keyboard('{Control>}{Enter}{/Control}');

    expect(onSubmit).toHaveBeenCalledWith('int main() {}');
  });

  it('can be read-only', () => {
    render(<CodeEditor value="int main() {}" readOnly />);
    expect(screen.getByRole('textbox', { name: 'Source code' })).toHaveAttribute('readonly');
  });
});
