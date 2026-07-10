import axios from 'axios';

const baseURL = import.meta.env.VITE_BFF_URL || '/api';

export const apiClient = axios.create({
  baseURL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('zynema.access_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('zynema.access_token');
      window.location.href = '/auth/login';
    }
    return Promise.reject(err);
  }
);
