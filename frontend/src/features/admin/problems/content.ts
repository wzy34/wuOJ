import type { ProblemContent } from '@/api/generated/model';

export type ContentTab = 'details' | 'statement' | 'samples';

/** The tab each field the server can report on lives in. */
export const FIELD_TABS: Record<string, ContentTab> = {
  displayId: 'details',
  slug: 'details',
  title: 'details',
  difficulty: 'details',
  timeLimitMs: 'details',
  memoryLimitMb: 'details',
  visible: 'details',
  statement: 'statement',
  samples: 'samples',
};

export const CONTENT_FIELDS = Object.keys(FIELD_TABS);

export const NEW_PROBLEM: ProblemContent = {
  displayId: '',
  slug: '',
  title: '',
  difficulty: 'EASY',
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  statement: '',
  visible: false,
  samples: [],
};

/** The content as the API takes it, from the form's values (which may lack empty samples' text). */
export function toContent(values: Partial<ProblemContent>): ProblemContent {
  return {
    displayId: (values.displayId ?? '').trim(),
    slug: (values.slug ?? '').trim(),
    title: values.title ?? '',
    difficulty: values.difficulty ?? 'EASY',
    timeLimitMs: values.timeLimitMs ?? 0,
    memoryLimitMb: values.memoryLimitMb ?? 0,
    statement: values.statement ?? '',
    visible: values.visible ?? false,
    samples: (values.samples ?? []).map((s) => ({
      input: s?.input ?? '',
      output: s?.output ?? '',
    })),
  };
}

export function sameContent(a: ProblemContent, b: ProblemContent): boolean {
  return JSON.stringify(toContent(a)) === JSON.stringify(toContent(b));
}
