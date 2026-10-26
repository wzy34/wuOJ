import { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
// monaco-editor 0.57 maps 'monaco-editor/*' to esm/vs/*.js; the older esm/vs/... path no longer resolves.
import EditorWorker from 'monaco-editor/editor/editor.worker?worker';

/**
 * Bundles Monaco with the app instead of loading it from a CDN (monaco-react's default), so the
 * editor works on classroom networks that block CDNs. C++ has no language worker, so every
 * label gets the base editor worker and the TS/JSON/CSS/HTML workers are never bundled.
 */
self.MonacoEnvironment = {
  getWorker: () => new EditorWorker(),
};

loader.config({ monaco });
