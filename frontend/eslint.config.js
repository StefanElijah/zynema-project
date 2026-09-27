import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';

/**
 * Flat ESLint config (ESLint 9+).
 *
 * Replaces the Fase-0 .eslintrc.json, which ESLint 9 no longer reads
 * (`pnpm lint` failed with "couldn't find eslint.config.js").
 */
export default [
  {
    ignores: ['dist/**', 'node_modules/**', 'playwright-report/**', 'test-results/**'],
  },
  js.configs.recommended,
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...react.configs.recommended.rules,
      ...react.configs['jsx-runtime'].rules,
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      'react/prop-types': 'off',
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
  {
    // Tests and test setup: Node + Vitest globals in scope.
    files: ['**/*.{test,spec}.{js,jsx}', 'src/test/**'],
    languageOptions: {
      globals: { ...globals.node },
    },
  },
  {
    // Playwright specs run in Node, not in the browser.
    files: ['tests/**/*.js', 'playwright.config.js', 'vite.config.js'],
    languageOptions: {
      globals: { ...globals.node },
    },
  },
];
