// S-02 회원가입. ① 전화번호 → 인증번호 받기 ② 인증번호 5자리 ③ 이름·비밀번호. 끝나면 로그인 화면으로

import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { signUp, verifyPhone } from '../api/endpoints';
import { TextField } from '../components/ui';
import { useSmsCode } from '../hooks/useSmsCode';
import { ApiError } from '../lib/api';
import { errorMessage } from '../lib/messages';
import { CODE_PATTERN, isBlank, isName, isPhone } from '../lib/validation';
import type { LoginState } from '../session/SessionProvider';

type Step = 1 | 2 | 3;

// 서버 errors의 키 → 칸 오류 testid
const FIELD_ERROR_IDS: Record<string, string> = {
  name: 'signup-name-error',
  phoneNumber: 'signup-phone-error',
  loginPassword: 'signup-login-pw-error',
  initialAccountPassword: 'signup-account-pw-error',
};

export function SignupPage() {
  const navigate = useNavigate();
  const sms = useSmsCode();
  const [step, setStep] = useState<Step>(1);
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [verifying, setVerifying] = useState(false);
  const [name, setName] = useState('');
  const [loginPw, setLoginPw] = useState('');
  const [loginPw2, setLoginPw2] = useState('');
  const [accountPw, setAccountPw] = useState('');
  const [accountPw2, setAccountPw2] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSend() {
    if (await sms.send(phone)) {
      setCode('');
      setStep(2);
    }
  }

  async function onVerify() {
    setVerifying(true);
    sms.setError(null);
    try {
      await verifyPhone(phone, code, 'SIGN_UP');
      setStep(3);
    } catch (e) {
      sms.setError(errorMessage(e, 'sms'));
    } finally {
      setVerifying(false);
    }
  }

  const nameError = name && !isName(name) ? '이름은 한글·영문·숫자 2~10자로 입력해 주세요' : null;
  const loginPwError = loginPw2 && loginPw !== loginPw2 ? '비밀번호가 서로 달라요' : null;
  const accountPwError = accountPw2 && accountPw !== accountPw2 ? '계좌 비밀번호가 서로 달라요' : null;
  const canSubmit =
    isName(name) && !isBlank(loginPw) && loginPw === loginPw2 && !isBlank(accountPw) && accountPw === accountPw2 && !submitting;

  async function onSubmit() {
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      await signUp({ name, phoneNumber: phone, loginPassword: loginPw, initialAccountPassword: accountPw });
      navigate('/login', { replace: true, state: { notice: 'signup' } satisfies LoginState });
    } catch (e) {
      if (e instanceof ApiError && e.errors) setFieldErrors(e.errors);
      if (e instanceof ApiError && e.code === 'UNVERIFIED_PHONE_NUMBER') setError('인증이 만료됐어요. 전화번호 인증부터 다시 해 주세요');
      else setError(errorMessage(e, 'signup'));
    } finally {
      setSubmitting(false);
    }
  }

  const serverFieldError = (key: string) =>
    fieldErrors[key] ? (
      <p className="error" data-testid={FIELD_ERROR_IDS[key]}>
        입력값을 다시 확인해 주세요
      </p>
    ) : null;

  return (
    <main className="auth">
      <h1>회원가입</h1>
      <ol className="steps" data-testid="signup-steps">
        <li aria-current={step === 1 ? 'step' : undefined}>1 전화번호</li>
        <li aria-current={step === 2 ? 'step' : undefined}>2 인증번호</li>
        <li aria-current={step === 3 ? 'step' : undefined}>3 정보 입력</li>
      </ol>

      <section className="panel">
        <TextField
          label="전화번호"
          testid="signup-phone-input"
          inputMode="tel"
          value={phone}
          onChange={setPhone}
          disabled={step !== 1}
        />
        {serverFieldError('phoneNumber')}
        <p className="hint" data-testid="signup-send-notice">
          같은 번호로는 10분에 3번까지 받을 수 있음
        </p>
        {step === 1 ? (
          <button type="button" className="primary" data-testid="signup-send-code" disabled={!isPhone(phone) || sms.sending} onClick={() => void onSend()}>
            인증번호 받기
          </button>
        ) : (
          <button
            type="button"
            data-testid="signup-change-phone"
            onClick={() => {
              sms.reset();
              setStep(1);
            }}
          >
            전화번호 바꾸기
          </button>
        )}
        {step === 1 && sms.error && (
          <p className="error" data-testid="signup-code-error" role="alert">
            {sms.error}
          </p>
        )}
      </section>

      {step === 2 && (
        <section className="panel">
          <p data-testid="signup-code-timer" aria-live="polite">
            남은 시간 {sms.timerText}
          </p>
          <TextField
            label="인증번호"
            testid="signup-code-input"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={5}
            value={code}
            onChange={(v) => setCode(v.replace(/\D/g, ''))}
          />
          {sms.error && (
            <p className="error" data-testid="signup-code-error" role="alert">
              {sms.error}
            </p>
          )}
          <div className="actions">
            <button type="button" data-testid="signup-resend" disabled={sms.sending} onClick={() => void sms.send(phone)}>
              인증번호 다시 받기
            </button>
            <button
              type="button"
              className="primary"
              data-testid="signup-verify"
              disabled={!CODE_PATTERN.test(code) || verifying || sms.expired}
              onClick={() => void onVerify()}
            >
              확인
            </button>
          </div>
        </section>
      )}

      {step === 3 && (
        <section className="panel">
          <p className="notice" data-testid="signup-verified-notice">
            인증은 10분 동안 유효함. 그 안에 가입을 마쳐 주세요
          </p>
          <TextField label="이름" testid="signup-name-input" value={name} onChange={setName} error={nameError} errorTestid="signup-name-error" maxLength={10} />
          {!nameError && serverFieldError('name')}
          <TextField
            label="로그인 비밀번호"
            testid="signup-login-pw-input"
            type="password"
            autoComplete="new-password"
            value={loginPw}
            onChange={setLoginPw}
          />
          {serverFieldError('loginPassword')}
          <TextField
            label="로그인 비밀번호 확인"
            testid="signup-login-pw-confirm-input"
            type="password"
            autoComplete="new-password"
            value={loginPw2}
            onChange={setLoginPw2}
            error={loginPwError}
            errorTestid="signup-login-pw-error"
          />
          <TextField
            label="첫 계좌 비밀번호"
            testid="signup-account-pw-input"
            type="password"
            value={accountPw}
            onChange={setAccountPw}
            hint="출금·송금·주문 때 쓰는 비밀번호. 5번 연속 틀리면 계좌가 거래정지됨"
          />
          {serverFieldError('initialAccountPassword')}
          <TextField
            label="첫 계좌 비밀번호 확인"
            testid="signup-account-pw-confirm-input"
            type="password"
            value={accountPw2}
            onChange={setAccountPw2}
            error={accountPwError}
            errorTestid="signup-account-pw-error"
          />
          {error && (
            <p className="error" data-testid="signup-error" role="alert">
              {error}
            </p>
          )}
          <button type="button" className="primary" data-testid="signup-submit" disabled={!canSubmit} onClick={() => void onSubmit()}>
            가입하기
          </button>
        </section>
      )}

      <p>
        <Link to="/login" data-testid="signup-login-link">
          이미 회원이면 로그인
        </Link>
      </p>
    </main>
  );
}
