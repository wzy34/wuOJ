import { setupWorker } from 'msw/browser';
import { problems } from './db';
import { createHandlers } from './handlers';
import { resumeUnfinished } from './mockJudge';

export const worker = setupWorker(...createHandlers());

/** Starts mock mode: intercepts /api in the browser and resumes judging interrupted by a reload. */
export async function startMockWorker(): Promise<void> {
  await worker.start({ onUnhandledRequest: 'bypass' });
  resumeUnfinished(problems);
}
