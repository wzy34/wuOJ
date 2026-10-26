import { defineConfig } from 'orval';

export default defineConfig({
  wuoj: {
    input: {
      target: '../api/openapi.yaml',
      filters: {
        // The SSE endpoint is consumed with EventSource (src/features/submissions/useSubmissionLive.ts),
        // but its event payload schema is still generated.
        mode: 'exclude',
        tags: ['submission-events'],
        includeUnreferencedSchemas: true,
      },
    },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated/endpoints.ts',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      httpClient: 'axios',
      clean: true,
      formatter: 'prettier',
      mock: {
        generators: [{ type: 'msw' }],
      },
      override: {
        mutator: {
          path: 'src/api/http.ts',
          name: 'customInstance',
        },
        query: {
          signal: true,
        },
      },
    },
  },
});
