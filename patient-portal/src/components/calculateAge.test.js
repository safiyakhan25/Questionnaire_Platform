import { vi } from 'vitest';
import { calculateAge } from './calculateAge';

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-06-15'));
});

afterEach(() => {
  vi.useRealTimers();
});

it('returns null when given no date', () => {
  expect(calculateAge(null)).toBeNull();
  expect(calculateAge('')).toBeNull();
});

it('returns null for an unparseable date', () => {
  expect(calculateAge('not-a-date')).toBeNull();
});

it('computes whole years elapsed when the birthday already happened this year', () => {
  expect(calculateAge('1996-01-10')).toBe(30);
});

it('does not count this year yet when the birthday has not happened', () => {
  expect(calculateAge('1996-12-25')).toBe(29);
});

it('counts the birthday itself as having happened', () => {
  expect(calculateAge('1996-06-15')).toBe(30);
});
