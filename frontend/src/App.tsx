// 화면 주소(설계 3.10.0). 관리자 화면 주소는 관리자 API(/admin/**)와 겹치지 않게 /ops로 둠

import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router';
import { Layout } from './components/Layout';
import { NotFound } from './components/ui';
import { useSession, type LoginState } from './session/SessionProvider';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { AdminWaitPage } from './pages/AdminWaitPage';

const ADMIN = 'ADMIN';

/** 회원 화면: 로그인 전이면 로그인 화면으로(원래 주소 기억), 관리자면 관리자 첫 화면으로 */
function RequireUser() {
  const { claims } = useSession();
  const location = useLocation();
  if (!claims) return <Navigate to="/login" replace state={{ from: location.pathname + location.search } satisfies LoginState} />;
  if (claims.role === ADMIN) return <Navigate to="/ops" replace />;
  return <Outlet />;
}

function RequireAdmin() {
  const { claims } = useSession();
  if (!claims) return <Navigate to="/login" replace />;
  if (claims.role !== ADMIN) return <Navigate to="/" replace />;
  return <Outlet />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route element={<RequireAdmin />}>
        <Route path="/ops/*" element={<AdminWaitPage />} />
      </Route>
      <Route element={<RequireUser />}>
        <Route element={<Layout />}>
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
  );
}
