import type { FormInstance } from '@arco-design/web-react';

export const USERNAME_RULE = /^[A-Za-z0-9._-]+$/;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 128;

/** Password rules a form can check before sending; the server also refuses common passwords. */
export const passwordRules = [
  { required: true, message: 'Enter a password' },
  { minLength: PASSWORD_MIN, message: `Use at least ${PASSWORD_MIN} characters` },
  { maxLength: PASSWORD_MAX, message: `Use at most ${PASSWORD_MAX} characters` },
];

/** The confirmation must repeat the field named `of`. */
export function confirmRule<T>(form: FormInstance<T>, of: keyof T & string) {
  return {
    validator: (value: string | undefined, callback: (error?: string) => void) => {
      callback(value === form.getFieldValue(of) ? undefined : 'The passwords do not match');
    },
  };
}
