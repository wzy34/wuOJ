import '@/lib/monaco';
import Editor, { type OnMount } from '@monaco-editor/react';
import { useEffect, useRef } from 'react';
import { useEffectiveTheme } from '@/lib/theme';
import { usePreferences } from '@/stores/preferences';

type Props = {
  value: string;
  onChange?: (value: string) => void;
  /**
   * Called on Ctrl+Enter (Cmd+Enter on macOS) with the editor's text, which can be ahead of
   * `value` when the shortcut follows the last keystroke before React re-renders.
   */
  onSubmitShortcut?: (source: string) => void;
  readOnly?: boolean;
  height?: string | number;
  /** C++ unless given; problem statements use `markdown`. */
  language?: 'cpp' | 'markdown';
  ariaLabel?: string;
};

/** Monaco, for C++ by default, following the app theme and the saved font size. */
export function CodeEditor({
  value,
  onChange,
  onSubmitShortcut,
  readOnly = false,
  height = '100%',
  language = 'cpp',
  ariaLabel,
}: Props) {
  const theme = useEffectiveTheme();
  const fontSize = usePreferences((state) => state.editorFontSize);
  const submitRef = useRef(onSubmitShortcut);
  useEffect(() => {
    submitRef.current = onSubmitShortcut;
  }, [onSubmitShortcut]);

  const handleMount: OnMount = (editor, monaco) => {
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () =>
      submitRef.current?.(editor.getValue()),
    );
  };

  return (
    <Editor
      height={height}
      language={language}
      theme={theme === 'dark' ? 'vs-dark' : 'light'}
      value={value}
      onChange={(next) => onChange?.(next ?? '')}
      onMount={handleMount}
      loading="Loading editor…"
      options={{
        readOnly,
        fontSize,
        tabSize: 4,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        automaticLayout: true,
        renderLineHighlight: readOnly ? 'none' : 'line',
        domReadOnly: readOnly,
        ariaLabel: ariaLabel ?? (readOnly ? 'Source code' : 'Code editor'),
        wordWrap: language === 'markdown' ? 'on' : 'off',
      }}
    />
  );
}
