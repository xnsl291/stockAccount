// 화면 주소(설계 3.10.0). 관리자 화면 주소는 관리자 API(/admin/**)와 겹치지 않게 /ops로 둠

import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router';
import { Layout } from './components/Layout';
import { NotFound } from './components/ui';
import { useSession, type LoginState } from './session/SessionProvider';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { HomePage } from './pages/HomePage';
import { AccountsPage } from './pages/AccountsPage';
import { OpenAccountPage } from './pages/OpenAccountPage';
import { AccountDetailPage } from './pages/AccountDetailPage';
import { AccountSettingsPage } from './pages/AccountSettingsPage';
import { UnlockPage } from './pages/UnlockPage';
import { DepositPage } from './pages/DepositPage';
import { WithdrawPage } from './pages/WithdrawPage';
import { TransferPage } from './pages/TransferPage';
import { StockSearchPage } from './pages/StockSearchPage';
import { StockDetailPage } from './pages/StockDetailPage';
import { OrderPage } from './pages/OrderPage';
import { OrdersPage, HoldingsPage } from './pages/StandaloneTabs';
import { StockTransferPage } from './pages/StockTransferPage';
import { MePage } from './pages/MePage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { OpsHome, OpsLayout, PlannedPage } from './pages/ops/OpsPages';
import { AdminPricePage, AdminStocksPage } from './pages/ops/AdminStockPages';

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
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route element={<RequireAdmin />}>
        <Route path="/ops" element={<OpsLayout />}>
          <Route index element={<OpsHome />} />
          <Route path="stocks" element={<AdminStocksPage />} />
          <Route path="price" element={<AdminPricePage />} />
          <Route path=":screen" element={<PlannedPage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
      <Route element={<RequireUser />}>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="/accounts" element={<AccountsPage />} />
          <Route path="/accounts/new" element={<OpenAccountPage />} />
          <Route path="/accounts/:id" element={<AccountDetailPage />} />
          <Route path="/accounts/:id/settings" element={<AccountSettingsPage />} />
          <Route path="/accounts/:id/unlock" element={<UnlockPage />} />
          <Route path="/deposit" element={<DepositPage />} />
          <Route path="/withdraw" element={<WithdrawPage />} />
          <Route path="/transfer" element={<TransferPage />} />
          <Route path="/stocks" element={<StockSearchPage />} />
          <Route path="/stocks/:id" element={<StockDetailPage />} />
          <Route path="/order" element={<OrderPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/holdings" element={<HoldingsPage />} />
          <Route path="/stock-transfer" element={<StockTransferPage />} />
          <Route path="/me" element={<MePage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
  );
}
