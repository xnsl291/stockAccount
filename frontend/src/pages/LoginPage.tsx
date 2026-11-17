// S-01 로그인. 회원과 관리자가 같이 씀. 로그인 뒤 토큰의 역할로 갈 곳을 정함

import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { login } from '../api/endpoints';
import { TextField } from '../components/ui';
import { errorMessage } from '../lib/messages';
import { isPhone } from '../lib/validation';
import { clearLoginNotice, peekLoginNotice, useSession, type LoginNotice, type LoginState } from '../session/SessionProvider';

const NOTICES: Record<LoginNotice, string> = {
  signup: '가입이 끝났어요. 첫 계좌가 함께 만들어졌어요. 로그인해 주세요',
  session: '로그인이 끝났어요. 다시 로그인해 주세요',
  logout: '로그아웃했어요',
  withdrawn: '탈퇴가 끝났어요',
  password: '비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요',
};

export function LoginPage() {
  const location = useLocation();
  const state = (location.state ?? {}) as LoginState;
  const navigate = useNavigate();
  const { signIn } = useSession();
  const [notice] = useState<LoginNotice | null>(() => state.notice ?? peekLoginNotice());
  useEffect(() => clearLoginNotice(), []);
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!isPhone(phone)) {
      setError('전화번호 형식을 확인해 주세요');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const claims = signIn(await login(phone, password));
      if (claims?.role === 'ADMIN') navigate('/ops', { replace: true });
      else navigate(state.from && state.from !== '/login' ? state.from : '/', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'login'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth">
      <h1>로그인</h1>
      {notice && (
        <p className="notice" data-testid="login-notice" role="status">
          {NOTICES[notice]}
        </p>
      )}
      <form onSubmit={(e) => void onSubmit(e)}>
        <TextField label="전화번호" testid="login-phone-input" inputMode="tel" autoComplete="tel" value={phone} onChange={setPhone} />
        <TextField
          label="비밀번호"
          testid="login-password-input"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={setPassword}
        />
        {error && (
          <p className="error" data-testid="login-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="primary" data-testid="login-submit" disabled={!phone || !password || submitting}>
          로그인
        </button>
      </form>
      <p>
        <Link to="/signup" data-testid="login-signup-link">
          회원가입
        </Link>
      </p>
    </main>
  );
}
