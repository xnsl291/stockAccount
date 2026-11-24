import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';
import { SessionProvider } from './session/SessionProvider';
import { trackAsOf } from './session/asOf';
import { applyDisplayPrefs } from './components/DisplayMenu';
import './styles/tokens.css';
import './styles/app.css';

const queryClient = new QueryClient({
  defaultOptions: {
    // 원장 값은 서버에만 있으므로 오래 들고 있지 않음. 실패는 바로 보여 주고 "다시 시도"로 다시 부름
    queries: { staleTime: 0, retry: false, refetchOnWindowFocus: false },
    mutations: { retry: false },
  },
});
trackAsOf(queryClient);
applyDisplayPrefs();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionProvider>
          <App />
        </SessionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
