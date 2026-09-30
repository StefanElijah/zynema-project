import { z } from 'zod';

/**
 * The create-profile form, validated with the same limits the API enforces
 * (`ProfileRequest`: name `@NotBlank @Size(max = 80)`, language
 * `@Size(max = 10)`), so the client fails where the server would — but before
 * the round trip, and with a message in the user's language.
 */
export const profileFormSchema = z.object({
  name: z.string().trim().min(1, 'Ingresá un nombre.').max(80, 'Máximo 80 caracteres.'),
  kids: z.boolean(),
  language: z.string().trim().max(10, 'Máximo 10 caracteres.'),
});

export type ProfileFormValues = z.infer<typeof profileFormSchema>;
