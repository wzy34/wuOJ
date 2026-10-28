import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

/** Local date and time, e.g. "2026-10-24 15:04:05". */
export function formatDateTime(iso: string): string {
  return dayjs(iso).format('YYYY-MM-DD HH:mm:ss');
}

/** Local date, e.g. "2026-10-24". */
export function formatDate(iso: string): string {
  return dayjs(iso).format('YYYY-MM-DD');
}

/** e.g. "3 minutes ago". */
export function formatFromNow(iso: string): string {
  return dayjs(iso).fromNow();
}

export function formatTime(ms: number | undefined): string {
  return ms === undefined ? '—' : `${ms} ms`;
}

export function formatMemory(kb: number | undefined): string {
  if (kb === undefined) return '—';
  return kb < 1024 ? `${kb} KB` : `${(kb / 1024).toFixed(1)} MB`;
}

export function formatTimeLimit(ms: number): string {
  return ms % 1000 === 0 ? `${ms / 1000} s` : `${ms} ms`;
}
