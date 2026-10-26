import type { Difficulty } from '@/api/generated/model';

export const DIFFICULTY_DISPLAY: Record<Difficulty, { label: string; color: string }> = {
  EASY: { label: 'Easy', color: 'green' },
  MEDIUM: { label: 'Medium', color: 'orange' },
  HARD: { label: 'Hard', color: 'red' },
};

export const DIFFICULTIES = Object.keys(DIFFICULTY_DISPLAY) as Difficulty[];

export function isDifficulty(value: string | null): value is Difficulty {
  return DIFFICULTIES.includes(value as Difficulty);
}
