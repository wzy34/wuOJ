import { useEffect, useRef } from 'react';

/**
 * Stand-in for @monaco-editor/react in jsdom, which cannot run Monaco. A textarea with the same
 * props; Ctrl/Cmd+Enter runs the command registered through `editor.addCommand`, like Monaco.
 */
const KeyMod = { CtrlCmd: 2048 };
const KeyCode = { Enter: 3 };

type StubProps = {
  value?: string;
  onChange?: (value: string | undefined) => void;
  onMount?: (editor: unknown, monaco: unknown) => void;
  options?: { readOnly?: boolean; ariaLabel?: string };
};

export default function Editor({ value, onChange, onMount, options }: StubProps) {
  const textarea = useRef<HTMLTextAreaElement>(null);
  const commands = useRef(new Map<number, () => void>());

  useEffect(() => {
    onMount?.(
      {
        addCommand: (keybinding: number, handler: () => void) =>
          commands.current.set(keybinding, handler),
        getValue: () => textarea.current?.value ?? '',
      },
      { KeyMod, KeyCode },
    );
    // Monaco calls onMount once.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <textarea
      ref={textarea}
      aria-label={options?.ariaLabel ?? 'Code editor'}
      readOnly={options?.readOnly}
      value={value}
      onChange={(event) => onChange?.(event.target.value)}
      onKeyDown={(event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
          event.preventDefault();
          commands.current.get(KeyMod.CtrlCmd | KeyCode.Enter)?.();
        }
      }}
    />
  );
}

export const loader = { config: () => {} };
