import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, type AuthProviderProps } from 'react-oidc-context';
import App from './App';
import { userManager } from './lib/auth/userManager';
import { queryClient } from './lib/query/client';
// Side-effect import: configures the axios instance the generated hooks use
// (base URL, bearer token, 401 renewal) before anything can call it.
import './lib/api';
import './styles/variables.css';
import './index.css';

/**
 * After the OIDC redirect the authorization code is still in the URL. Strip it
 * so a reload does not replay the exchange. The callback route performs the
 * router-aware navigation after the provider publishes the authenticated user.
 */
const onSigninCallback: AuthProviderProps['onSigninCallback'] = () => {
  window.history.replaceState(window.history.state, document.title, window.location.pathname);
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
