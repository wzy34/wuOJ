import type { Sample } from '@/api/generated/model';
import { CopyButton } from '@/components/CopyButton';
import styles from './Samples.module.css';

function SampleBlock({ title, text }: { title: string; text: string }) {
  return (
    <div className={styles.block}>
      <div className={styles.blockHeader}>
        <span>{title}</span>
        <CopyButton text={text} label={`Copy ${title.toLowerCase()}`} />
      </div>
      <pre className={styles.content}>{text}</pre>
    </div>
  );
}

/** Sample input/output pairs in monospace with exact-text copy buttons. */
export function Samples({ samples }: { samples: Sample[] }) {
  return (
    <section aria-label="Samples">
      {samples.map((sample, index) => (
        <div key={index} className={styles.sample}>
          <SampleBlock title={`Sample ${index + 1} input`} text={sample.input} />
          <SampleBlock title={`Sample ${index + 1} output`} text={sample.output} />
        </div>
      ))}
    </section>
  );
}
