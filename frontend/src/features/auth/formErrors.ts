import type { FormInstance } from '@arco-design/web-react';
import { getApiProblem } from '@/api/http';

/**
 * Shows the server's field errors next to the form fields they name; returns whether any did.
 * Values are kept, so a person only has to fix the field in question.
 */
export function showFieldErrors<T>(
  form: FormInstance<T>,
  failure: unknown,
  fields: string[],
): boolean {
  const errors = getApiProblem(failure)?.errors?.filter((e) => fields.includes(e.field)) ?? [];
  if (errors.length === 0) return false;
  form.setFields(
    Object.fromEntries(
      errors.map((e) => [
        e.field,
        { value: form.getFieldValue(e.field as never), error: { message: e.message } },
      ]),
    ) as never,
  );
  return true;
}
