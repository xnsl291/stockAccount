// S-07 계좌 설정: 별칭·계좌 비밀번호 변경(C4)과 계좌 해지(C6)
// C6은 조건이 안 맞으면 오류가 아니라 false를 돌려줌(이유 없음). 잔액·보유 주식이 남은 경우만 오류(REMAINING_ASSET)로 옴

import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { closeAccount, updateAccount } from '../api/endpoints';
import { unlockPath } from '../components/Flow';
import { LoadError, Loading, TextField } from '../components/ui';
import { useAccount } from '../hooks/queries';
import { ApiError } from '../lib/api';
import { errorMessage, MESSAGES } from '../lib/messages';
import { NICKNAME_MAX, isBlank } from '../lib/validation';

export function AccountSettingsPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const query = useAccount(id);
  const [nickname, setNickname] = useState('');
  const [newPw, setNewPw] = useState('');
  const [newPw2, setNewPw2] = useState('');
  const [currentPw, setCurrentPw] = useState('');
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [locked, setLocked] = useState(false);
  const [closeOpen, setCloseOpen] = useState(false);
  const [closeError, setCloseError] = useState<string | null>(null);
  const [closing, setClosing] = useState(false);

  const original = query.data?.nickname ?? '';
  useEffect(() => setNickname(original), [original]);

  if (query.isPending) return <Loading />;
  if (query.isError) return <LoadError error={query.error} onRetry={() => void query.refetch()} />;
  const a = query.data;

  const nicknameChanged = nickname !== original;
  const pwChanged = newPw !== '';
  const newPwError = pwChanged && isBlank(newPw) ? '새 계좌 비밀번호는 비워 둘 수 없어요' : newPw2 && newPw !== newPw2 ? '새 비밀번호가 서로 달라요' : null;
  const canSave = (nicknameChanged || pwChanged) && !newPwError && (!pwChanged || newPw === newPw2) && currentPw !== '' && !saving;

  async function onSave() {
    setSaving(true);
    setError(null);
    setResult(null);
    try {
      // 바꾸지 않은 항목은 보내지 않음(빈 별칭을 보내면 별칭이 빈 값으로 바뀜)
      await updateAccount(id, {
        currentPassword: currentPw,
        ...(nicknameChanged ? { nickname } : {}),
        ...(pwChanged ? { newPassword: newPw } : {}),
      });
      setResult('저장했음');
      setNewPw('');
      setNewPw2('');
    } catch (e) {
      const code = e instanceof ApiError ? e.code : '';
      if (code === 'UNMATCHED_PASSWORD' || code === 'PENDING_ACCOUNT') {
        const fresh = await query.refetch();
        if (code === 'PENDING_ACCOUNT' || fresh.data?.status === 'PENDING') {
          setLocked(true);
          setError(code === 'PENDING_ACCOUNT' ? errorMessage(e, 'account') : MESSAGES.accountLocked);
        } else setError(errorMessage(e, 'account'));
      } else setError(errorMessage(e, 'account'));
    } finally {
      setCurrentPw('');
      setSaving(false);
      void queryClient.invalidateQueries();
    }
  }

  async function onClose() {
    setClosing(true);
    setCloseError(null);
    try {
      const ok = await closeAccount(id);
      if (ok) {
        await queryClient.invalidateQueries();
        navigate('/accounts', { replace: true });
        return;
      }
      setCloseError(MESSAGES.closeFalse);
    } catch (e) {
      setCloseError(errorMessage(e, 'close'));
    } finally {
      setClosing(false);
    }
  }

  return (
    <>
      <h1>계좌 설정 · {a.accountNumber}</h1>
      <section className="panel">
        <TextField label="별칭" testid="acctset-nickname-input" value={nickname} onChange={setNickname} maxLength={NICKNAME_MAX} />
        <TextField label="새 계좌 비밀번호(선택)" testid="acctset-new-pw-input" type="password" value={newPw} onChange={setNewPw} />
        <TextField
          label="새 계좌 비밀번호 확인"
          testid="acctset-new-pw-confirm-input"
          type="password"
          value={newPw2}
          onChange={setNewPw2}
          error={newPwError}
        />
        <TextField label="현재 계좌 비밀번호" testid="acctset-current-pw-input" type="password" value={currentPw} onChange={setCurrentPw} />
        <p className="hint" data-testid="acctset-pw-warning">
          {MESSAGES.pwWarning}
        </p>
        {result && (
          <p className="notice" data-testid="acctset-result" role="status">
            {result}
          </p>
        )}
        {error && (
          <p className="error" data-testid="acctset-error" role="alert">
            {error}
          </p>
        )}
        {(locked || a.status === 'PENDING') && (
          <Link to={unlockPath(id, `/accounts/${id}/settings`)} data-testid="acctset-unlock-link">
            잠금 해제로 이동
          </Link>
        )}
        <button type="button" className="primary" data-testid="acctset-submit" disabled={!canSave} onClick={() => void onSave()}>
          저장
        </button>
      </section>

      <section className="panel danger-zone">
        <button type="button" data-testid="acctset-close" onClick={() => setCloseOpen(true)}>
          계좌 해지
        </button>
        {closeOpen && (
          <div data-testid="acctset-close-panel">
            <h2>계좌 해지 확인</h2>
            <p>잔액과 보유 주식이 모두 0이어야 함. 마지막 남은 계좌와 거래정지된 계좌는 해지할 수 없음</p>
            {closeError && (
              <p className="error" data-testid="acctset-close-error" role="alert">
                {closeError}
              </p>
            )}
            <div className="actions">
              <button type="button" data-testid="acctset-close-back" onClick={() => setCloseOpen(false)}>
                돌아가기
              </button>
              <button type="button" className="primary" data-testid="acctset-close-submit" disabled={closing} onClick={() => void onClose()}>
                해지하기
              </button>
            </div>
          </div>
        )}
      </section>
    </>
  );
}
