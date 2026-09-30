/**
 * The API layer, in one import.
 *
 * Importing anything from here also ensures the axios instance behind the
 * generated hooks has been configured (base URL, bearer token, renewal),
 * because `./client` runs its setup on import. Pages and hooks should import
 * from this module rather than from the contracts package directly, so the
 * configuration can never be accidentally skipped.
 */
import './client';

export * from '@zynema/api-contracts';
