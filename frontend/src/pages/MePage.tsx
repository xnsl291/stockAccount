// S-20 내 정보. M2는 이름·전화번호·새 비밀번호·현재 비밀번호 넷 다 필수라, 바꾸지 않는 값은 지금 값을 채워 보냄
// 새 비밀번호 칸에 현재 비밀번호를 그대로 넣으면 서버는 "비밀번호를 안 바꿈"으로 봄
// 비밀번호를 바꾸면 서버가 리프레시 토큰을 지우므로 바로 토큰을 지우고 다시 로그인하게 함(사용자 결정)

import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { leave, logout, updateMember, verifyPhone } from '../api/endpoints';
import { LoadError, Loading, TextField } from '../components/ui';
import { useMember } from '../hooks/queries';
import { useSmsCode } from '../hooks/useSmsCode';
import { formatDate, formatPhone } from '../lib/format';
import { errorMessage } from '../lib/messages';
import { CODE_PATTERN, isBlank, isName, isPhone } from '../lib/validation';
import { useMemberId, useSession } from '../session/SessionProvider';

type Action = 'name' | 'phone' | 'pw';

export function MePage() {
  const memberId = useMemberId();
  const member = useMember(memberId);
  const queryClient = useQueryClient();
  const { signOut } = useSession();
  const sms = useSmsCode();
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [phoneVerified, setPhoneVerified] = useState(false);
  const [currentPw, setCurrentPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [newPw2, setNewPw2] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [leaveOpen, setLeaveOpen] = useState(false);
  const [agree, setAgree] = useState(false);
  const [leaveError, setLeaveError] = useState<string | null>(null);

  if (member.isPending) return <Loading />;
  if (member.isError) return <LoadError error={member.error} onRetry={() => void member.refetch()} />;
  const m = member.data;

  async function save(action: Action) {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      await updateMember(memberId, {
        name: action === 'name' ? name : m.name,
        phoneNumber: action === 'phone' ? phone : m.phoneNumber,
        password: action === 'pw' ? newPw : currentPw,
        currentPassword: currentPw,
      });
      if (action === 'pw') {
        signOut('password');
        return;
      }
      setResult(action === 'name' ? '이름을 바꿨어요' : '전화번호를 바꿨어요');
      setName('');
      setPhone('');
      setCode('');
      setPhoneVerified(false);
      sms.reset();
      void queryClient.invalidateQueries();
    } catch (e) {
      setError(errorMessage(e, 'me'));
    } finally {
      setCurrentPw('');
      setBusy(false);
    }
  }

  async function onVerifyPhone() {
    sms.setError(null);
    try {
      await verifyPhone(phone, code, 'PHONE_CHANGE');
      setPhoneVerified(true);
    } catch (e) {
      sms.setError(errorMessage(e, 'sms'));
    }
  }

  async function onLogout() {
    try {
      await logout();
    } catch {
      // 서버 로그아웃이 실패해도 이 브라우저의 토큰은 지움
    }
    signOut('logout');
  }

  async function onLeave() {
    setLeaveError(null);
    try {
      await leave();
      signOut('withdrawn');
    } catch (e) {
      setLeaveError(errorMessage(e, 'leave'));
    }
  }

  const hasPw = currentPw !== '';
  const nameError = name && !isName(name) ? '이름은 한글·영문·숫자 2~10자로 입력해 주세요' : null;
  const newPwError = newPw2 && newPw !== newPw2 ? '새 비밀번호가 서로 달라요' : null;

  return (
    <>
      <h1>내 정보</h1>
      <dl className="summary panel">
        <div className="kv">
          <dt>이름</dt>
          <dd data-testid="me-name">{m.name}</dd>
        </div>
        <div className="kv">
          <dt>전화번호</dt>
          <dd data-testid="me-phone">{formatPhone(m.phoneNumber)}</dd>
        </div>
        <div className="kv">
          <dt>가입일</dt>
          <dd data-testid="me-created-at">{formatDate(m.createdAt)}</dd>
        </div>
      </dl>

      <section className="panel">
        <TextField
          label="현재 로그인 비밀번호"
          testid="me-current-pw-input"
          type="password"
          autoComplete="current-password"
          value={currentPw}
          onChange={setCurrentPw}
          hint="이름·전화번호·비밀번호를 바꿀 때마다 필요해요"
        />
        {result && (
          <p className="notice" data-testid="me-result" role="status">
            {result}
          </p>
        )}
        {error && (
          <p className="error" data-testid="me-error" role="alert">
            {error}
          </p>
        )}
      </section>

      <section className="panel">
        <h2>이름 변경</h2>
        <TextField label="새 이름" testid="me-name-input" value={name} onChange={setName} error={nameError} maxLength={10} />
        <button type="button" data-testid="me-name-submit" disabled={!isName(name) || !hasPw || busy} onClick={() => void save('name')}>
          이름 바꾸기
        </button>
      </section>

      <section className="panel">
        <h2>전화번호 변경</h2>
        <TextField
          label="새 전화번호"
          testid="me-phone-input"
          inputMode="tel"
          value={phone}
          onChange={(v) => {
            setPhone(v);
            setPhoneVerified(false);
            sms.reset();
          }}
        />
        {!sms.sent ? (
          <button type="button" data-testid="me-phone-send-code" disabled={!isPhone(phone) || sms.sending} onClick={() => void sms.send(phone)}>
            인증번호 받기
          </button>
        ) : (
          !phoneVerified && (
            <>
              <p data-testid="me-phone-code-timer">남은 시간 {sms.timerText}</p>
              <button type="button" data-testid="me-phone-resend" disabled={sms.sending} onClick={() => void sms.send(phone)}>
                인증번호 다시 받기
              </button>
              <TextField
                label="인증번호"
                testid="me-phone-code-input"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={5}
                value={code}
                onChange={(v) => setCode(v.replace(/\D/g, ''))}
              />
              <button type="button" data-testid="me-phone-verify" disabled={!CODE_PATTERN.test(code) || sms.expired} onClick={() => void onVerifyPhone()}>
                확인
              </button>
            </>
          )
        )}
        {sms.error && (
          <p className="error" data-testid="me-phone-code-error" role="alert">
            {sms.error}
          </p>
        )}
        <button type="button" data-testid="me-phone-submit" disabled={!phoneVerified || !hasPw || busy} onClick={() => void save('phone')}>
          전화번호 바꾸기
        </button>
      </section>

      <section className="panel">
        <h2>비밀번호 변경</h2>
        <TextField label="새 로그인 비밀번호" testid="me-new-pw-input" type="password" autoComplete="new-password" value={newPw} onChange={setNewPw} />
        <TextField
          label="새 비밀번호 확인"
          testid="me-new-pw-confirm-input"
          type="password"
          autoComplete="new-password"
          value={newPw2}
          onChange={setNewPw2}
          error={newPwError}
        />
        <button
          type="button"
          data-testid="me-pw-submit"
          disabled={isBlank(newPw) || newPw !== newPw2 || !hasPw || busy}
          onClick={() => void save('pw')}
        >
          비밀번호 바꾸기
        </button>
      </section>

      <section className="panel">
        <button type="button" data-testid="me-logout" onClick={() => void onLogout()}>
          로그아웃
        </button>
      </section>

      <section className="panel danger-zone">
        <button type="button" data-testid="me-withdraw" onClick={() => setLeaveOpen(true)}>
          회원 탈퇴
        </button>
        {leaveOpen && (
          <div data-testid="me-withdraw-panel">
            <h2>회원 탈퇴 확인</h2>
            <p>모든 계좌가 해지됨. 잔액이나 보유 주식이 남은 계좌가 있으면 탈퇴할 수 없음. 같은 전화번호로 다시 가입할 수 없음</p>
            <label>
              <input type="checkbox" data-testid="me-withdraw-agree" checked={agree} onChange={(e) => setAgree(e.target.checked)} /> 위 내용을 확인했음
            </label>
            {leaveError && (
              <p className="error" data-testid="me-withdraw-error" role="alert">
                {leaveError}
              </p>
            )}
            <button type="button" data-testid="me-withdraw-submit" disabled={!agree} onClick={() => void onLeave()}>
              탈퇴하기
            </button>
          </div>
        )}
      </section>
    </>
  );
}
