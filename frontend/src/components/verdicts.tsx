import {
  IconBug,
  IconCheckCircle,
  IconClockCircle,
  IconCloseCircle,
  IconCode,
  IconExclamationCircle,
  IconFile,
  IconStorage,
} from '@arco-design/web-react/icon';
import type { ReactNode } from 'react';
import type { Verdict } from '@/api/generated/model';

type Display = { label: string; short: string; color: string; icon: ReactNode };

export const VERDICT_DISPLAY: Record<Verdict, Display> = {
  AC: { label: 'Accepted', short: 'AC', color: 'green', icon: <IconCheckCircle /> },
  WA: { label: 'Wrong Answer', short: 'WA', color: 'red', icon: <IconCloseCircle /> },
  TLE: {
    label: 'Time Limit Exceeded',
    short: 'TLE',
    color: 'orangered',
    icon: <IconClockCircle />,
  },
  MLE: { label: 'Memory Limit Exceeded', short: 'MLE', color: 'orangered', icon: <IconStorage /> },
  RE: { label: 'Runtime Error', short: 'RE', color: 'magenta', icon: <IconBug /> },
  CE: { label: 'Compilation Error', short: 'CE', color: 'gold', icon: <IconCode /> },
  OLE: { label: 'Output Limit Exceeded', short: 'OLE', color: 'orangered', icon: <IconFile /> },
  SE: { label: 'System Error', short: 'SE', color: 'gray', icon: <IconExclamationCircle /> },
};

export const IN_PROGRESS: Record<'PENDING' | 'COMPILING' | 'RUNNING', string> = {
  PENDING: 'Pending',
  COMPILING: 'Compiling',
  RUNNING: 'Running',
};

export const VERDICT_OPTIONS = (Object.keys(VERDICT_DISPLAY) as Verdict[]).map((verdict) => ({
  value: verdict,
  label: VERDICT_DISPLAY[verdict].label,
}));
