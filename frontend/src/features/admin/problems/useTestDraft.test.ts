import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { AdminTestSet } from '@/api/generated/model';
import { buildManifest, pairFiles, useTestDraft, type DraftFile } from './useTestDraft';

const file = (name: string, content = name) => new File([content], name);

const saved: AdminTestSet = {
  version: 'v1',
  tests: [
    { number: 1, inputBytes: 2, outputBytes: 3 },
    { number: 2, inputBytes: 4, outputBytes: 5 },
  ],
};

describe('pairFiles', () => {
  it('pairs n.in with n.out in numeric order and reports other names', () => {
    const { tests, ignored } = pairFiles([
      file('12.out'),
      file('11.in'),
      file('notes.txt'),
      file('12.in'),
      file('11.out'),
      file('13.in'),
    ]);
    const name = (f?: DraftFile) => (f?.kind === 'file' ? f.file.name : undefined);
    expect(tests.map((t) => [name(t.input), name(t.output)])).toEqual([
      ['11.in', '11.out'],
      ['12.in', '12.out'],
      ['13.in', undefined],
    ]);
    expect(ignored.map((f) => f.name)).toEqual(['notes.txt']);
  });
});

describe('buildManifest', () => {
  it('keeps saved files by number and uploads the rest in order', () => {
    const a = file('a.out');
    const b = file('b.in');
    const { manifest, files } = buildManifest(
      [
        {
          key: '1',
          input: { kind: 'keep', number: 2, bytes: 4 },
          output: { kind: 'file', file: a },
        },
        {
          key: '2',
          input: { kind: 'file', file: b },
          output: { kind: 'keep', number: 1, bytes: 3 },
        },
      ],
      'v1',
    );
    expect(manifest).toEqual({
      baseVersion: 'v1',
      tests: [
        { input: { keep: 2 }, output: { upload: 0 } },
        { input: { upload: 1 }, output: { keep: 1 } },
      ],
    });
    expect(files).toEqual([a, b]);
  });

  it('has no base version when creating a problem', () => {
    const { manifest } = buildManifest([
      {
        key: '1',
        input: { kind: 'file', file: file('1.in') },
        output: { kind: 'file', file: file('1.out') },
      },
    ]);
    expect(manifest.baseVersion).toBeUndefined();
  });
});

describe('useTestDraft', () => {
  it('starts from the saved tests and tracks unsaved changes', () => {
    const { result } = renderHook(() => useTestDraft(saved));
    expect(result.current.tests).toHaveLength(2);
    expect(result.current.dirty).toBe(false);
    expect(result.current.complete).toBe(true);

    act(() => result.current.move(result.current.tests[0].key, 1));
    expect(result.current.dirty).toBe(true);
    act(() => result.current.discard());
    expect(result.current.dirty).toBe(false);

    act(() => void result.current.add([file('3.in')]));
    expect(result.current.complete).toBe(false);
    act(() => result.current.setFile(result.current.tests[2].key, 'output', file('3.out')));
    expect(result.current.complete).toBe(true);
    act(() => result.current.remove(result.current.tests[2].key));
    expect(result.current.dirty).toBe(false);
  });

  it('starts over when a new version is saved', () => {
    const { result, rerender } = renderHook(({ base }) => useTestDraft(base), {
      initialProps: { base: saved },
    });
    act(() => result.current.remove(result.current.tests[0].key));
    rerender({ base: { version: 'v2', tests: [{ number: 1, inputBytes: 1, outputBytes: 1 }] } });
    expect(result.current.tests).toHaveLength(1);
    expect(result.current.dirty).toBe(false);
  });
});
