// Fails when src/api/generated is out of date with api/openapi.yaml.
// Regenerates the client and compares every file with what was there before.
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = join(import.meta.dirname, '..', 'src', 'api', 'generated');

function snapshot(dir) {
  const files = new Map();
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      for (const [file, content] of snapshot(path)) files.set(file, content);
    } else {
      files.set(relative(root, path), readFileSync(path, 'utf8'));
    }
  }
  return files;
}

const before = snapshot(root);
execFileSync('pnpm', ['exec', 'orval', '--config', 'orval.config.ts'], { stdio: 'ignore' });
const after = snapshot(root);

const changed = [...new Set([...before.keys(), ...after.keys()])]
  .filter((file) => before.get(file) !== after.get(file))
  .sort();

if (changed.length > 0) {
  console.error('Generated API client was out of date and has been regenerated:');
  for (const file of changed) console.error(`  ${file}`);
  console.error('Review and commit the regenerated files.');
  process.exit(1);
}
console.log('Generated API client is up to date.');
