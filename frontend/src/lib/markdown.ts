import { defaultUrlTransform } from 'react-markdown';

const HAS_PROTOCOL = /^[a-z][a-z\d+.-]*:/i;
const SAFE_PROTOCOL = /^(https?|mailto):/i;

/** Allows only http, https and mailto links (react-markdown's default also permits irc/xmpp). */
export function statementUrlTransform(url: string): string {
  const safe = defaultUrlTransform(url);
  return HAS_PROTOCOL.test(safe) && !SAFE_PROTOCOL.test(safe) ? '' : safe;
}
