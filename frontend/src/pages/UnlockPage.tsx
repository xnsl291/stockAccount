// S-08 계좌 잠금 해제. 등록된 전화번호(M1)로 문자 인증(A1·A2 ACCOUNT_UNLOCK) 뒤 해제(C5)
// C5만 실패하면 다시 시도 때 C5만 부름(인증 표시는 10분 유효)

import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { unlockAccount, verifyPhone } from '../api/endpoints';
import { LoadError, Loading, PageHead, StatusBadge, TextField } from '../components/ui';
import { useAccount, useMember } from '../hooks/queries';
import { useSmsCode } from '../hooks/useSmsCode';
import { maskPhone } from '../lib/format';
import { ACCOUNT_STATUS } from '../lib/labels';
import { errorMessage } from '../lib/messages';
import { CODE_PATTERN } from '../lib/validation';
import { useMemberId } from '../session/SessionProvider';

/** 돌아갈 주소는 이 앱 안의 경로만 받음(다른 사이트로 보내는 주소를 막음) */
function safeReturn(value: string | null, fallback: string): string {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : fallback;
}

export function UnlockPage() {
  const id = Number(useParams().id);
  const [params] = useSearchParams();
  const queryClient = useQueryClient();
  const memberId = useMemberId();
  const account = useAccount(id);
  const member = useMember(memberId);
  const sms = useSmsCode();
  const [code, setCode] = useState('');
  const [verified, setVerified] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const returnTo = safeReturn(params.get('return'), `/accounts/${id}`);

  if (account.isPending || member.isPending) return <Loading />;
  if (account.isError) return <LoadError error={account.error} onRetry={() => void account.refetch()} />;
  if (member.isError) return <LoadError error={member.error} onRetry={() => void member.refetch()} />;
  const a = account.data;
  const phone = member.data.phoneNumber;

  async function onSubmit() {
    setSubmitting(true);
    setError(null);
    try {
      if (!verified) {
        await verifyPhone(phone, code, 'ACCOUNT_UNLOCK');
        setVerified(true);
      }
      await unlockAccount(id);
      await queryClient.invalidateQueries();
      setDone(true);
    } catch (e) {
      setError(errorMessage(e, 'sms'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="narrow">
      <PageHead title="계좌 잠금 해제" sub="등록된 전화번호로 문자 인증을 하면 풀려요" />
      <p>
        <span data-testid="unlock-account-number">{a.accountNumber}</span>{' '}
        <StatusBadge testid="unlock-status" state={a.status} label={ACCOUNT_STATUS[a.status]} />
      </p>

      {a.status !== 'PENDING' && !done ? (
        <div className="panel" data-testid="unlock-not-locked">
          <p>이 계좌는 잠겨 있지 않아요</p>
          <Link to={`/accounts/${id}`}>계좌 상세</Link>
        </div>
      ) : done ? (
        <div className="panel">
          <p className="notice" data-testid="unlock-result" role="status">
            잠금이 풀렸음. 비밀번호 틀린 횟수도 0으로 돌아감
          </p>
          <Link to={returnTo} data-testid="unlock-return-link">
            하던 화면으로 돌아가기
          </Link>
        </div>
      ) : (
        <section className="panel">
          <p>
            등록된 전화번호 <span data-testid="unlock-phone">{maskPhone(phone)}</span>
          </p>
          {!sms.sent ? (
            <button type="button" className="primary" data-testid="unlock-send-code" disabled={sms.sending} onClick={() => void sms.send(phone)}>
              인증번호 받기
            </button>
          ) : (
            <>
              <p data-testid="unlock-code-timer" aria-live="polite">
                남은 시간 {sms.timerText}
              </p>
              <button type="button" data-testid="unlock-resend" disabled={sms.sending} onClick={() => void sms.send(phone)}>
                인증번호 다시 받기
              </button>
              {!verified && (
                <TextField
                  label="인증번호"
                  testid="unlock-code-input"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={5}
                  value={code}
                  onChange={(v) => setCode(v.replace(/\D/g, ''))}
                />
              )}
              <button
                type="button"
                className="primary"
                data-testid="unlock-submit"
                disabled={submitting || (!verified && (!CODE_PATTERN.test(code) || sms.expired))}
                onClick={() => void onSubmit()}
              >
                잠금 해제
              </button>
            </>
          )}
          {(error ?? sms.error) && (
            <p className="error" data-testid="unlock-code-error" role="alert">
              {error ?? sms.error}
            </p>
          )}
        </section>
      )}
    </div>
  );
}
