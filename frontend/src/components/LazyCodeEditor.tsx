import { Spin } from '@arco-design/web-react';
import { lazy, Suspense, type ComponentProps } from 'react';

const CodeEditor = lazy(async () => ({ default: (await import('./CodeEditor')).CodeEditor }));

/** The code editor, loaded on first use so Monaco never weighs down pages without an editor. */
export function LazyCodeEditor(props: ComponentProps<typeof CodeEditor>) {
  return (
    <Suspense
      fallback={
        <div role="status" aria-label="Loading editor" style={{ padding: 16 }}>
          <Spin size={16} />
        </div>
      }
    >
      <CodeEditor {...props} />
    </Suspense>
  );
}
