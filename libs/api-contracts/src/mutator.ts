import Axios, { AxiosError, type AxiosRequestConfig } from 'axios';

/**
 * The one axios instance every generated call goes through.
 *
 * The generated code never creates its own client: it calls `customInstance`,
 * which delegates here. The app configures this instance once at bootstrap
 * (base URL, bearer interceptor, 401 renewal), so every hook — present and
 * future — inherits the same transport behaviour without codegen knowing
 * anything about authentication.
 */
export const AXIOS_INSTANCE = Axios.create();

export const customInstance = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig
): Promise<T> => {
  const source = Axios.CancelToken.source();
  const promise = AXIOS_INSTANCE({
    ...config,
    ...options,
    cancelToken: source.token,
  }).then(({ data }) => data);

  // React Query cancels a stale query through this method when `signal` is
  // enabled: the HTTP request is aborted instead of merely ignored.
  (promise as Promise<T> & { cancel?: () => void }).cancel = () =>
    source.cancel('Query was cancelled');

  return promise;
};

/** The error envelope every service writes (see the backend's exception handlers). */
export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
}

/**
 * What hooks report as `error`. Orval substitutes its own generic here, but
 * every failure in this platform is the same envelope, so the mutator pins it.
 */
export type ErrorType<_TError> = AxiosError<ApiErrorBody>;

export type BodyType<TBody> = TBody;
