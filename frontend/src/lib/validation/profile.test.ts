import { describe, expect, it } from 'vitest';
import { profileFormSchema } from './profile';

describe('profileFormSchema', () => {
  it('accepts a valid profile and trims the name', () => {
    const parsed = profileFormSchema.parse({ name: '  Mate  ', kids: true, language: 'es' });

    expect(parsed).toEqual({ name: 'Mate', kids: true, language: 'es' });
  });

  it('rejects an empty or whitespace-only name', () => {
    expect(profileFormSchema.safeParse({ name: '', kids: false, language: 'es' }).success).toBe(
      false
    );
    expect(profileFormSchema.safeParse({ name: '   ', kids: false, language: 'es' }).success).toBe(
      false
    );
  });

  it('mirrors the API limit of 80 characters', () => {
    const tooLong = 'a'.repeat(81);

    expect(
      profileFormSchema.safeParse({ name: tooLong, kids: false, language: 'es' }).success
    ).toBe(false);
    expect(
      profileFormSchema.safeParse({ name: 'a'.repeat(80), kids: false, language: 'es' }).success
    ).toBe(true);
  });
});
