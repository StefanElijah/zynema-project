// Public entry point for @zynema/api-contracts.
//
// Everything here is generated from the BFF's OpenAPI document (see
// orval.config.ts): the types, the request functions and the React Query
// hooks. The only hand-written piece is the axios mutator, which is exported
// so the app can configure the single instance all calls go through.

export * from './generated/zynema';
export { AXIOS_INSTANCE, customInstance } from './mutator';
export type { ApiErrorBody, BodyType, ErrorType } from './mutator';
