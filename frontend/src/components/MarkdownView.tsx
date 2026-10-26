import 'katex/dist/katex.min.css';
import Markdown, { type Components } from 'react-markdown';
import rehypeKatex from 'rehype-katex';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import { statementUrlTransform } from '@/lib/markdown';
import styles from './MarkdownView.module.css';

const components: Components = {
  a: ({ node: _node, ...props }) => <a {...props} target="_blank" rel="noopener noreferrer" />,
};

/**
 * Renders problem statements: GFM (tables, lists) and `$…$` / `$$…$$` math. Raw HTML is never
 * turned into elements because rehype-raw is not used, so no sanitizer is needed.
 */
export function MarkdownView({ children }: { children: string }) {
  return (
    <div className={styles.markdown}>
      <Markdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        urlTransform={statementUrlTransform}
        components={components}
      >
        {children}
      </Markdown>
    </div>
  );
}
