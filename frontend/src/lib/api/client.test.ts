import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { User } from 'oidc-client-ts';

vi.mock('../auth/userManager', () => ({
  userManager: {
    getUser: vi.fn(),
    signinSilent: vi.fn(),
    signinRedirect: vi.fn(),
  },
}));

const { userManager } = await import('../auth/userManager');
const { apiClient } = await import('./client');

const okResponse = (
  config: InternalAxiosRequestConfig,
  data: unknown = { ok: true }
): AxiosResponse => ({
  data,
  status: 200,
  statusText: 'OK',
  headers: {},
  config,
});

const unauthorized = (config: InternalAxiosRequestConfig): AxiosError =>
  Object.assign(new Error('Unauthorized'), {
    config,
    response: { status: 401, statusText: 'Unauthorized', headers: {}, config, data: null },
  }) as AxiosError;

// The interceptors only read `access_token` and `expired`, so a partial user is
// enough; casting keeps the fixtures honest about being partial.
const partialUser = (accessToken: string | null, expired = false): User =>
  ({ access_token: accessToken, expired }) as User;

describe('api client interceptors', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('attaches the access token to every request', async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(partialUser('token-123'));
    let authorization: unknown;
    apiClient.defaults.adapter = async (config) => {
      authorization = config.headers.Authorization;
      return okResponse(config);
    };

    await apiClient.get('/catalog/movies');

    expect(authorization).toBe('Bearer token-123');
  });

  it('omits the header when there is no usable token', async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(partialUser('token-123', true));
    let authorization: unknown = 'unset';
    apiClient.defaults.adapter = async (config) => {
      authorization = config.headers.Authorization;
      return okResponse(config);
    };

    await apiClient.get('/catalog/movies');

    expect(authorization).toBeUndefined();
  });

  it('renews once and replays the request when the token is rejected', async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(partialUser('stale'));
    vi.mocked(userManager.signinSilent).mockResolvedValue(partialUser('fresh'));

    let attempts = 0;
    apiClient.defaults.adapter = async (config) => {
      attempts += 1;
      if (attempts === 1) {
        throw unauthorized(config);
      }
      return okResponse(config, { id: 'me' });
    };

    const response = await apiClient.get('/users/me');

    expect(attempts).toBe(2);
    expect(userManager.signinSilent).toHaveBeenCalledTimes(1);
    expect(response.data).toEqual({ id: 'me' });
  });

  it('sends the visitor to the login flow when there is no session at all', async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(null);
    apiClient.defaults.adapter = async (config) => {
      throw unauthorized(config);
    };

    await expect(apiClient.get('/users/me')).rejects.toThrow();
    expect(userManager.signinRedirect).toHaveBeenCalledTimes(1);
  });

  it('renews at most once and does not loop back to login if the fresh token is also rejected', async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(partialUser('stale'));
    vi.mocked(userManager.signinSilent).mockResolvedValue(partialUser('fresh'));
    let attempts = 0;
    apiClient.defaults.adapter = async (config) => {
      attempts += 1;
      throw unauthorized(config);
    };

    await expect(apiClient.get('/users/me')).rejects.toThrow();

    // One original attempt plus exactly one replay; signing in again would
    // produce the same rejected token and turn into a redirect loop.
    expect(attempts).toBe(2);
    expect(userManager.signinSilent).toHaveBeenCalledTimes(1);
    expect(userManager.signinRedirect).not.toHaveBeenCalled();
  });
});
