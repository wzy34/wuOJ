import { useCallback, useMemo, useState } from 'react';
import type { AdminTestSet, TestFileRef, TestManifest } from '@/api/generated/model';

/** One side of a test: a file of the saved version, or a file chosen in the browser. */
export type DraftFile =
  { kind: 'keep'; number: number; bytes: number } | { kind: 'file'; file: File };

export type DraftTest = { key: string; input?: DraftFile; output?: DraftFile };

export type Side = 'input' | 'output';

const TEST_FILE = /^([1-9][0-9]*)\.(in|out)$/;

let nextKey = 0;
const newKey = () => `t${++nextKey}`;

function fromTestSet(set: AdminTestSet | undefined): DraftTest[] {
  return (set?.tests ?? []).map((test) => ({
    key: newKey(),
    input: { kind: 'keep', number: test.number, bytes: test.inputBytes },
    output: { kind: 'keep', number: test.number, bytes: test.outputBytes },
  }));
}

export function fileBytes(file: DraftFile): number {
  return file.kind === 'keep' ? file.bytes : file.file.size;
}

/**
 * Groups files named `<n>.in` and `<n>.out` into tests, in numeric order; a number with only one of
 * the two becomes a test missing the other. Files named otherwise are returned as `ignored`.
 */
export function pairFiles(files: File[]): { tests: DraftTest[]; ignored: File[] } {
  const groups = new Map<number, DraftTest>();
  const ignored: File[] = [];
  for (const file of files) {
    const match = TEST_FILE.exec(file.name);
    if (!match) {
      ignored.push(file);
      continue;
    }
    const number = Number(match[1]);
    const test = groups.get(number) ?? { key: newKey() };
    test[match[2] === 'in' ? 'input' : 'output'] = { kind: 'file', file };
    groups.set(number, test);
  }
  const tests = [...groups.entries()].sort(([a], [b]) => a - b).map(([, test]) => test);
  return { tests, ignored };
}

/**
 * The manifest for saving `tests` (or creating a problem with them): kept files refer to the base
 * version, chosen files become `files` parts in order. Every test must have both sides.
 */
export function buildManifest(
  tests: DraftTest[],
  baseVersion?: string,
): { manifest: TestManifest; files: File[] } {
  const files: File[] = [];
  const ref = (file: DraftFile | undefined): TestFileRef => {
    if (!file) throw new Error('A test is missing a file');
    if (file.kind === 'keep') return { keep: file.number };
    files.push(file.file);
    return { upload: files.length - 1 };
  };
  const manifest: TestManifest = {
    tests: tests.map((test) => ({ input: ref(test.input), output: ref(test.output) })),
  };
  if (baseVersion !== undefined) manifest.baseVersion = baseVersion;
  return { manifest, files };
}

function unchanged(tests: DraftTest[], base: AdminTestSet | undefined): boolean {
  const saved = base?.tests ?? [];
  return (
    tests.length === saved.length &&
    tests.every(
      (test, i) =>
        test.input?.kind === 'keep' &&
        test.input.number === i + 1 &&
        test.output?.kind === 'keep' &&
        test.output.number === i + 1,
    )
  );
}

/**
 * The test list being edited: changes stay in the browser until they are saved together as one new
 * test-data version. `base` is the saved version, absent while creating a problem.
 */
export function useTestDraft(base: AdminTestSet | undefined) {
  const [shown, setShown] = useState(base);
  const [tests, setTests] = useState<DraftTest[]>(() => fromTestSet(base));
  // A newly loaded or saved version replaces the draft.
  if (base?.version !== shown?.version) {
    setShown(base);
    setTests(fromTestSet(base));
  }

  const add = useCallback((files: File[]) => {
    const { tests: added, ignored } = pairFiles(files);
    setTests((current) => [...current, ...added]);
    return ignored;
  }, []);

  const setFile = useCallback((key: string, side: Side, file: File) => {
    setTests((current) =>
      current.map((test) =>
        test.key === key ? { ...test, [side]: { kind: 'file', file } } : test,
      ),
    );
  }, []);

  const remove = useCallback((key: string) => {
    setTests((current) => current.filter((test) => test.key !== key));
  }, []);

  const move = useCallback((key: string, offset: -1 | 1) => {
    setTests((current) => {
      const from = current.findIndex((test) => test.key === key);
      const to = from + offset;
      if (from < 0 || to < 0 || to >= current.length) return current;
      const next = [...current];
      [next[from], next[to]] = [next[to], next[from]];
      return next;
    });
  }, []);

  const discard = useCallback(() => setTests(fromTestSet(base)), [base]);

  const dirty = useMemo(() => !unchanged(tests, base), [tests, base]);
  const complete = tests.length > 0 && tests.every((test) => test.input && test.output);

  return { tests, add, setFile, remove, move, discard, dirty, complete };
}
