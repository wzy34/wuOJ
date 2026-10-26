import { Tag, Tooltip } from '@arco-design/web-react';
import { IconLoading } from '@arco-design/web-react/icon';
import type { SubmissionStatus, Verdict } from '@/api/generated/model';
import { IN_PROGRESS, VERDICT_DISPLAY } from './verdicts';

/**
 * The one way statuses and verdicts are shown across the app: label, colour and icon, so meaning
 * never depends on colour alone. `short` shows the abbreviation with the full name on hover.
 */
export function VerdictTag({
  status,
  verdict,
  short = false,
  progress,
}: {
  status: SubmissionStatus;
  verdict?: Verdict;
  short?: boolean;
  /** Shown after "Running", e.g. "test 3 of 10". */
  progress?: string;
}) {
  if (status !== 'FINISHED' || !verdict) {
    const label = status === 'FINISHED' ? 'Finished' : IN_PROGRESS[status];
    return (
      <Tag color="arcoblue" icon={<IconLoading />} bordered>
        {progress ? `${label} ${progress}` : label}
      </Tag>
    );
  }
  const display = VERDICT_DISPLAY[verdict];
  const tag = (
    <Tag color={display.color} icon={display.icon} bordered>
      {short ? display.short : display.label}
    </Tag>
  );
  return short ? <Tooltip content={display.label}>{tag}</Tooltip> : tag;
}
