import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { userManager } from '../auth/userManager';

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

export const apiClient = axios.create({
  baseURL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
});

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
