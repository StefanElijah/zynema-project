import { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { AXIOS_INSTANCE } from '@zynema/api-contracts';
import { userManager } from '../auth/userManager';

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

/**
 * The one axios instance of the app.
 *
 * It is created inside the contracts package (the generated hooks call it
 * through the orval mutator) and configured here, where the app's concerns
 * live: base URL, timeout, the bearer token from the OIDC session, and the
 * single-renewal dance for a token that expired mid-flight. Hand-written
 * calls (`playback.ts`) and generated hooks share it, so they share transport
 * and authentication by construction.
 *
 * `apiClient` is the historical name; `AXIOS_INSTANCE` is the same object.
 */
export const apiClient = AXIOS_INSTANCE;

apiClient.defaults.baseURL = baseURL;
apiClient.defaults.timeout = 10000;
apiClient.defaults.headers.common['Content-Type'] = 'application/json';

/** A request the interceptor may replay once; the flag guards the retry. */
type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

apiClient.interceptors.request.use(async (config) => {
  const user = await userManager.getUser();
  if (user?.access_token && !user.expired) {
    config.headers.Authorization = `Bearer ${user.access_token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const status = error.response?.status;
    const original = error.config as RetriableConfig | undefined;

    if (status !== 401 || !original || original._retried) {
      return Promise.reject(error);
    }
    original._retried = true;

    const user = await userManager.getUser();
    if (!user) {
      // Nobody is signed in: send the user through the login flow.
      await userManager.signinRedirect({ state: { returnTo: window.location.pathname } });
      return Promise.reject(error);
    }

    try {
      // The token may simply have expired between the check and the request.
      const renewed = await userManager.signinSilent();
      original.headers.Authorization = `Bearer ${renewed?.access_token ?? ''}`;
      return apiClient.request(original);
    } catch (renewError) {
      await userManager.signinRedirect({ state: { returnTo: window.location.pathname } });
      return Promise.reject(renewError);
    }
  }
);
