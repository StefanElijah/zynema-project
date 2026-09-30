/**
 * Conventional commits are enforced on every PR range (see the commitlint
 * workflow). One exception: the bootstrap commit from before the convention
 * was adopted (Fase 0) predates it, and rewriting published history to
 * rename it would be worse than ignoring it by message.
 */
export default {
  extends: ['@commitlint/config-conventional'],
  ignores: [(message) => message.startsWith('Inicializar el espacio de trabajo')],
};
