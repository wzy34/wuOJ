import { Button } from '@arco-design/web-react';
import { IconCheck, IconCopy } from '@arco-design/web-react/icon';
import { useEffect, useState } from 'react';

/** Copies `text` exactly and confirms with "Copied" for a moment. */
export function CopyButton({ text, label }: { text: string; label: string }) {
  const [copied, setCopied] = useState(false);
  useEffect(() => {
    if (!copied) return;
    const timer = setTimeout(() => setCopied(false), 1500);
    return () => clearTimeout(timer);
  }, [copied]);

  return (
    <Button
      size="mini"
      type="text"
      aria-label={label}
      icon={copied ? <IconCheck /> : <IconCopy />}
      onClick={() => {
        void navigator.clipboard.writeText(text).then(() => setCopied(true));
      }}
    >
      {copied ? 'Copied' : 'Copy'}
    </Button>
  );
}
