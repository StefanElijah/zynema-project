import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../auth/userManager', () => ({
  userManager: {
    getUser: vi.fn(),
    signinSilent: vi.fn(),
    signinRedirect: vi.fn(),
  },
}));

const { userManager } = await import('../auth/userManager');
const { apiClient } = await import('./client');

const okResponse = (config, data = { ok: true }) => ({
  data,
  status: 200,
  statusText: 'OK',
  headers: {},
  config,
});

const unauthorized = (config) =>
  Object.assign(new Error('Unauthorized'), { config, response: { status: 401 } });

describe('api client interceptors', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('attaches the access token to every request', async () => {
    userManager.getUser.mockResolvedValue({ access_token: 'token-123', expired: false });
    let authorization;
    apiClient.defaults.adapter = async (config) => {
      authorization = config.headers.Authorization;
      return okResponse(config);
    };

    await apiClient.get('/catalog/movies');

    expect(authorization).toBe('Bearer token-123');
  });

  it('omits the header when there is no usable token', async () => {
    userManager.getUser.mockResolvedValue({ access_token: 'token-123', expired: true });
    let authorization = 'unset';
    apiClient.defaults.adapter = async (config) => {
      authorization = config.headers.Authorization;
      return okResponse(config);
    };

    await apiClient.get('/catalog/movies');

    expect(authorization).toBeUndefined();
  });

  it('renews once and replays the request when the token is rejected', async () => {
    userManager.getUser.mockResolvedValue({ access_token: 'stale', expired: false });
    userManager.signinSilent.mockResolvedValue({ access_token: 'fresh' });

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
    userManager.getUser.mockResolvedValue(null);
    apiClient.defaults.adapter = async (config) => {
      throw unauthorized(config);
    };

    await expect(apiClient.get('/users/me')).rejects.toThrow();
    expect(userManager.signinRedirect).toHaveBeenCalledTimes(1);
  });

  it('renews at most once and does not loop back to login if the fresh token is also rejected', async () => {
    userManager.getUser.mockResolvedValue({ access_token: 'stale', expired: false });
    userManager.signinSilent.mockResolvedValue({ access_token: 'fresh' });
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
