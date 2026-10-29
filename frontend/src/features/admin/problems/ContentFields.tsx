import { Button, Form, Input, InputNumber, Select, Space, Switch } from '@arco-design/web-react';
import { IconArrowDown, IconArrowUp, IconDelete, IconPlus } from '@arco-design/web-react/icon';
import { LazyCodeEditor } from '@/components/LazyCodeEditor';
import { MarkdownView } from '@/components/MarkdownView';
import { DIFFICULTIES, DIFFICULTY_DISPLAY } from '@/features/problems/difficulty';
import styles from './ProblemEditor.module.css';

function Details({ showVisible }: { showVisible: boolean }) {
  return (
    <div className={styles.details}>
      <Form.Item
        label="Display ID"
        field="displayId"
        rules={[
          { required: true, message: 'Enter a display ID' },
          { match: /^[A-Za-z0-9_-]{1,32}$/, message: "1-32 letters, digits, '-' or '_'" },
        ]}
      >
        <Input aria-label="Display ID" />
      </Form.Item>
      <Form.Item
        label="Slug"
        field="slug"
        extra="The problem's address: /problems/your-slug"
        rules={[
          { required: true, message: 'Enter a slug' },
          {
            match: /^[a-z0-9]+(-[a-z0-9]+)*$/,
            message: 'Lowercase letters and digits separated by single hyphens',
          },
          { maxLength: 64, message: 'At most 64 characters' },
        ]}
      >
        <Input aria-label="Slug" />
      </Form.Item>
      <Form.Item
        label="Title"
        field="title"
        rules={[
          { required: true, message: 'Enter a title' },
          { maxLength: 200, message: 'At most 200 characters' },
        ]}
      >
        <Input aria-label="Title" />
      </Form.Item>
      <Form.Item label="Difficulty" field="difficulty" rules={[{ required: true }]}>
        <Select
          aria-label="Difficulty"
          options={DIFFICULTIES.map((d) => ({ value: d, label: DIFFICULTY_DISPLAY[d].label }))}
        />
      </Form.Item>
      <Form.Item
        label="Time limit (ms)"
        field="timeLimitMs"
        rules={[
          { required: true, message: 'Enter a time limit' },
          { type: 'number', min: 100, max: 10_000, message: 'Between 100 and 10,000 ms' },
        ]}
      >
        <InputNumber aria-label="Time limit (ms)" step={100} />
      </Form.Item>
      <Form.Item
        label="Memory limit (MB)"
        field="memoryLimitMb"
        rules={[
          { required: true, message: 'Enter a memory limit' },
          { type: 'number', min: 16, max: 1024, message: 'Between 16 and 1,024 MB' },
        ]}
      >
        <InputNumber aria-label="Memory limit (MB)" step={16} />
      </Form.Item>
      {showVisible && (
        <Form.Item
          label="Visible"
          field="visible"
          triggerPropName="checked"
          extra="Hidden problems can only be opened by admins."
        >
          <Switch aria-label="Visible" />
        </Form.Item>
      )}
    </div>
  );
}

/** Arco's Form.Item passes `value` and `onChange`. */
function StatementField({
  value = '',
  onChange,
}: {
  value?: string;
  onChange?: (value: string) => void;
}) {
  return (
    <div className={styles.statement}>
      <div className={styles.statementEditor}>
        <LazyCodeEditor
          language="markdown"
          ariaLabel="Statement editor"
          value={value}
          onChange={onChange}
          height={520}
        />
      </div>
      <div className={styles.statementPreview} role="region" aria-label="Statement preview">
        <MarkdownView>{value}</MarkdownView>
      </div>
    </div>
  );
}

function Samples() {
  return (
    <Form.List field="samples">
      {(fields, { add, remove, move }) => (
        <div>
          {fields.map((item, index) => {
            const n = index + 1;
            return (
              <div key={item.key} className={styles.sample} role="group" aria-label={`Sample ${n}`}>
                <div className={styles.sampleHeader}>
                  <strong>Sample {n}</strong>
                  <Space size={4}>
                    <Button
                      size="small"
                      icon={<IconArrowUp />}
                      aria-label={`Move sample ${n} up`}
                      disabled={index === 0}
                      onClick={() => move(index, index - 1)}
                    />
                    <Button
                      size="small"
                      icon={<IconArrowDown />}
                      aria-label={`Move sample ${n} down`}
                      disabled={index === fields.length - 1}
                      onClick={() => move(index, index + 1)}
                    />
                    <Button
                      size="small"
                      status="danger"
                      icon={<IconDelete />}
                      aria-label={`Remove sample ${n}`}
                      onClick={() => remove(index)}
                    />
                  </Space>
                </div>
                <div className={styles.sampleFields}>
                  <Form.Item label="Input" field={`${item.field}.input`}>
                    <Input.TextArea
                      aria-label={`Sample ${n} input`}
                      autoSize={{ minRows: 2, maxRows: 10 }}
                      className={styles.mono}
                    />
                  </Form.Item>
                  <Form.Item label="Output" field={`${item.field}.output`}>
                    <Input.TextArea
                      aria-label={`Sample ${n} output`}
                      autoSize={{ minRows: 2, maxRows: 10 }}
                      className={styles.mono}
                    />
                  </Form.Item>
                </div>
              </div>
            );
          })}
          <Button icon={<IconPlus />} onClick={() => add({ input: '', output: '' })}>
            Add sample
          </Button>
        </div>
      )}
    </Form.List>
  );
}

/**
 * The fields of the details, statement and samples tabs. All three stay mounted so the form always
 * holds every value; only the active one is shown.
 */
export function ContentFields({ tab, showVisible }: { tab: string; showVisible: boolean }) {
  return (
    <>
      <div hidden={tab !== 'details'}>
        <Details showVisible={showVisible} />
      </div>
      <div hidden={tab !== 'statement'}>
        <Form.Item field="statement" rules={[{ required: true, message: 'Write a statement' }]}>
          <StatementField />
        </Form.Item>
      </div>
      <div hidden={tab !== 'samples'}>
        <Samples />
      </div>
    </>
  );
}
