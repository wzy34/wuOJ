import {
  Button,
  Input,
  Message,
  Modal,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
} from '@arco-design/web-react';
import type { ColumnProps } from '@arco-design/web-react/es/Table';
import {
  IconArrowDown,
  IconArrowUp,
  IconDelete,
  IconEdit,
  IconEye,
  IconUpload,
} from '@arco-design/web-react/icon';
import { useEffect, useRef, useState, type DragEvent } from 'react';
import {
  downloadAdminProblemTestInput,
  downloadAdminProblemTestOutput,
} from '@/api/generated/admin/admin';
import { formatBytes } from '@/lib/format';
import styles from './ProblemEditor.module.css';
import {
  fileBytes,
  type DraftFile,
  type DraftTest,
  type Side,
  type useTestDraft,
} from './useTestDraft';

/** How much of a file the viewer shows; the rest is a download away. */
export const PREVIEW_BYTES = 64 * 1024;
/** Larger tests are replaced by uploading files, not edited in the page. */
export const INLINE_EDIT_BYTES = 1024 * 1024;

type Draft = ReturnType<typeof useTestDraft>;

const SIDE_LABEL: Record<Side, string> = { input: 'input', output: 'output' };

function downloadUrl(problemId: number, number: number, side: Side) {
  return `/api/admin/problems/${problemId}/tests/${number}/${side}`;
}

/** The text of a test file: the whole of it, or its first `limit` bytes. */
async function readFile(
  problemId: number | undefined,
  file: DraftFile,
  side: Side,
  limit?: number,
) {
  if (file.kind === 'file') return (limit ? file.file.slice(0, limit) : file.file).text();
  const download =
    side === 'input' ? downloadAdminProblemTestInput : downloadAdminProblemTestOutput;
  const headers = limit ? { Range: `bytes=0-${limit - 1}` } : undefined;
  const blob = await download(problemId!, file.number, { headers });
  return blob.text();
}

function FileCell({ file, side }: { file?: DraftFile; side: Side }) {
  if (!file) return <Tag color="red">Missing {SIDE_LABEL[side]}</Tag>;
  if (file.kind === 'keep') return <span>{formatBytes(file.bytes)}</span>;
  return (
    <span>
      {file.file.name} ({formatBytes(file.file.size)})
    </span>
  );
}

/** Whether the row differs from what is saved: new files, or a different position. */
function isUnsaved(test: DraftTest, index: number) {
  const kept = (file?: DraftFile) => file?.kind === 'keep' && file.number === index + 1;
  return !kept(test.input) || !kept(test.output);
}

function FileButton({
  label,
  multiple,
  onFiles,
  children,
  icon,
}: {
  label: string;
  multiple?: boolean;
  onFiles: (files: File[]) => void;
  children?: string;
  icon?: React.ReactNode;
}) {
  const input = useRef<HTMLInputElement>(null);
  return (
    <>
      <input
        ref={input}
        type="file"
        multiple={multiple}
        aria-label={label}
        className={styles.fileInput}
        onChange={(event) => {
          const files = [...(event.target.files ?? [])];
          event.target.value = '';
          if (files.length > 0) onFiles(files);
        }}
      />
      <Button
        size="small"
        icon={icon}
        aria-label={children ? undefined : label}
        onClick={() => input.current?.click()}
      >
        {children}
      </Button>
    </>
  );
}

function ViewDialog({
  problemId,
  test,
  number,
  onClose,
}: {
  problemId?: number;
  test: DraftTest;
  number: number;
  onClose: () => void;
}) {
  const [texts, setTexts] = useState<Partial<Record<Side, string>>>({});
  useEffect(() => {
    let live = true;
    for (const side of ['input', 'output'] as const) {
      const file = test[side];
      if (!file) continue;
      void readFile(problemId, file, side, PREVIEW_BYTES).then(
        (text) => live && setTexts((current) => ({ ...current, [side]: text })),
        () => live && setTexts((current) => ({ ...current, [side]: '(could not be loaded)' })),
      );
    }
    return () => {
      live = false;
    };
  }, [problemId, test]);

  return (
    <Modal title={`Test ${number}`} visible onCancel={onClose} footer={null} style={{ width: 720 }}>
      {(['input', 'output'] as const).map((side) => {
        const file = test[side];
        return (
          <section key={side} aria-label={`Test ${number} ${side}`} className={styles.preview}>
            <div className={styles.previewHeader}>
              <strong>{side === 'input' ? 'Input' : 'Expected output'}</strong>
              {file?.kind === 'keep' && problemId !== undefined && (
                <a href={downloadUrl(problemId, file.number, side)} download>
                  Download
                </a>
              )}
            </div>
            {!file ? (
              <Tag color="red">Missing</Tag>
            ) : texts[side] === undefined ? (
              <Spin size={16} />
            ) : (
              <>
                <pre className={styles.previewText}>{texts[side]}</pre>
                {fileBytes(file) > PREVIEW_BYTES && (
                  <p className={styles.hint}>
                    Showing the first 64 KB of {formatBytes(fileBytes(file))}.
                  </p>
                )}
              </>
            )}
          </section>
        );
      })}
    </Modal>
  );
}

function EditDialog({
  problemId,
  test,
  number,
  onApply,
  onClose,
}: {
  problemId?: number;
  test: DraftTest;
  number: number;
  onApply: (changes: Partial<Record<Side, File>>) => void;
  onClose: () => void;
}) {
  const [original, setOriginal] = useState<Partial<Record<Side, string>>>();
  const [texts, setTexts] = useState<Partial<Record<Side, string>>>({});
  useEffect(() => {
    let live = true;
    void Promise.all(
      (['input', 'output'] as const).map(async (side) => {
        const file = test[side];
        return [side, file ? await readFile(problemId, file, side) : ''] as const;
      }),
    ).then(
      (entries) => {
        if (!live) return;
        const loaded = Object.fromEntries(entries);
        setOriginal(loaded);
        setTexts(loaded);
      },
      () => {
        if (live) Message.error('The test could not be loaded.');
      },
    );
    return () => {
      live = false;
    };
  }, [problemId, test]);

  const apply = () => {
    const changes: Partial<Record<Side, File>> = {};
    for (const side of ['input', 'output'] as const) {
      const text = texts[side] ?? '';
      if (original && text !== original[side]) {
        changes[side] = new File([text], `${number}.${side === 'input' ? 'in' : 'out'}`);
      }
    }
    onApply(changes);
  };

  return (
    <Modal
      title={`Edit test ${number}`}
      visible
      onCancel={onClose}
      onOk={apply}
      okText="Apply"
      okButtonProps={{ disabled: !original }}
      style={{ width: 720 }}
    >
      {!original ? (
        <Spin />
      ) : (
        (['input', 'output'] as const).map((side) => (
          <label key={side} className={styles.editField}>
            <strong>{side === 'input' ? 'Input' : 'Expected output'}</strong>
            <Input.TextArea
              aria-label={`Test ${number} ${side}`}
              value={texts[side]}
              onChange={(value) => setTexts((current) => ({ ...current, [side]: value }))}
              autoSize={{ minRows: 4, maxRows: 14 }}
              className={styles.mono}
            />
          </label>
        ))
      )}
    </Modal>
  );
}

/**
 * The test list of a problem being created or edited. Every change is kept in `draft` until the
 * page saves it; files dropped on the list are paired by name (`3.in` with `3.out`).
 */
export function TestsEditor({ problemId, draft }: { problemId?: number; draft: Draft }) {
  const [viewing, setViewing] = useState<string>();
  const [editing, setEditing] = useState<string>();
  const [dragging, setDragging] = useState(false);

  const addFiles = (files: File[]) => {
    const ignored = draft.add(files);
    if (ignored.length > 0) {
      Message.warning(
        `Not added: ${ignored.map((f) => f.name).join(', ')}. Name test files like 1.in and 1.out.`,
      );
    }
  };
  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    setDragging(false);
    addFiles([...event.dataTransfer.files]);
  };

  const indexOf = (key?: string) => draft.tests.findIndex((t) => t.key === key);
  const columns: ColumnProps<DraftTest>[] = [
    { title: '#', width: 60, render: (_: unknown, __, index) => index + 1 },
    { title: 'Input', render: (_: unknown, test) => <FileCell file={test.input} side="input" /> },
    {
      title: 'Output',
      render: (_: unknown, test) => <FileCell file={test.output} side="output" />,
    },
    {
      title: 'Status',
      width: 110,
      render: (_: unknown, test, index) =>
        isUnsaved(test, index) ? <Tag color="orange">Unsaved</Tag> : null,
    },
    {
      title: 'Actions',
      width: 330,
      render: (_: unknown, test, index) => {
        const n = index + 1;
        const editable =
          !!test.input &&
          !!test.output &&
          fileBytes(test.input) <= INLINE_EDIT_BYTES &&
          fileBytes(test.output) <= INLINE_EDIT_BYTES;
        return (
          <Space size={4} wrap>
            <Tooltip content="View">
              <Button
                size="small"
                icon={<IconEye />}
                aria-label={`View test ${n}`}
                onClick={() => setViewing(test.key)}
              />
            </Tooltip>
            <Tooltip content={editable ? 'Edit' : 'Files over 1 MB are replaced by uploading'}>
              <Button
                size="small"
                icon={<IconEdit />}
                aria-label={`Edit test ${n}`}
                disabled={!editable}
                onClick={() => setEditing(test.key)}
              />
            </Tooltip>
            <FileButton
              label={`Replace input of test ${n}`}
              onFiles={([file]) => draft.setFile(test.key, 'input', file)}
            >
              Input…
            </FileButton>
            <FileButton
              label={`Replace output of test ${n}`}
              onFiles={([file]) => draft.setFile(test.key, 'output', file)}
            >
              Output…
            </FileButton>
            <Button
              size="small"
              icon={<IconArrowUp />}
              aria-label={`Move test ${n} up`}
              disabled={index === 0}
              onClick={() => draft.move(test.key, -1)}
            />
            <Button
              size="small"
              icon={<IconArrowDown />}
              aria-label={`Move test ${n} down`}
              disabled={index === draft.tests.length - 1}
              onClick={() => draft.move(test.key, 1)}
            />
            <Button
              size="small"
              status="danger"
              icon={<IconDelete />}
              aria-label={`Delete test ${n}`}
              onClick={() => draft.remove(test.key)}
            />
          </Space>
        );
      },
    },
  ];

  const viewIndex = indexOf(viewing);
  const editIndex = indexOf(editing);
  return (
    <div
      className={dragging ? `${styles.dropZone} ${styles.dropping}` : styles.dropZone}
      aria-label="Test list"
      role="region"
      onDragOver={(event) => {
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
    >
      <div className={styles.toolbar}>
        <FileButton label="Add test files" multiple icon={<IconUpload />} onFiles={addFiles}>
          Add test files
        </FileButton>
        <span className={styles.hint}>
          Or drop files here. Files named 1.in and 1.out become test 1, and so on.
        </span>
      </div>
      <Table
        rowKey="key"
        columns={columns}
        data={draft.tests}
        pagination={false}
        noDataElement={<p className={styles.hint}>No tests yet. Add at least one.</p>}
        scroll={{ x: 800 }}
      />
      {/* Mounted per test while open, so each starts with nothing loaded. */}
      {viewIndex >= 0 && (
        <ViewDialog
          key={viewing}
          problemId={problemId}
          test={draft.tests[viewIndex]}
          number={viewIndex + 1}
          onClose={() => setViewing(undefined)}
        />
      )}
      {editIndex >= 0 && (
        <EditDialog
          key={editing}
          problemId={problemId}
          test={draft.tests[editIndex]}
          number={editIndex + 1}
          onClose={() => setEditing(undefined)}
          onApply={(changes) => {
            const key = editing!;
            for (const side of ['input', 'output'] as const) {
              const file = changes[side];
              if (file) draft.setFile(key, side, file);
            }
            setEditing(undefined);
          }}
        />
      )}
    </div>
  );
}
