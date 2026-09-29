import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, type AuthProviderProps } from 'react-oidc-context';
import { type User } from 'oidc-client-ts';
import App from './App';
import { userManager } from './lib/auth/userManager';
import './styles/variables.css';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      gcTime: 10 * 60 * 1000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

/**
 * After the OIDC redirect the authorization code is still in the URL. Strip it
 * so a reload does not replay the exchange, and honour the `returnTo` state the
 * axios interceptor stored when it had to send the user to login.
 */
const onSigninCallback: AuthProviderProps['onSigninCallback'] = (user?: User) => {
  const returnTo = (user?.state as { returnTo?: string } | undefined)?.returnTo;
  window.history.replaceState({}, document.title, returnTo || window.location.pathname);
};

const container = document.getElementById('root');
if (!container) {
  throw new Error('Missing #root element in index.html');
}

createRoot(container).render(
  <StrictMode>
    <AuthProvider userManager={userManager} onSigninCallback={onSigninCallback}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </AuthProvider>
  </StrictMode>
);
