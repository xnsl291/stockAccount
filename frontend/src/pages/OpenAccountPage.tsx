// S-05 계좌 개설. 이 API는 중복 요청 방지 키를 받지 않아 빠른 두 번 누름은 버튼 잠금으로만 막음

import { useRef, useState } from 'react';
import { Link } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { openAccount, type Account } from '../api/endpoints';
import { TextField } from '../components/ui';
import { ApiError } from '../lib/api';
import { errorMessage } from '../lib/messages';
import { NICKNAME_MAX, isBlank } from '../lib/validation';

export function OpenAccountPage() {
  const queryClient = useQueryClient();
  const busy = useRef(false);
  const [nickname, setNickname] = useState('');
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pwServerError, setPwServerError] = useState<string | null>(null);
  const [created, setCreated] = useState<Account | null>(null);

  const pw2Error = pw2 && pw !== pw2 ? '계좌 비밀번호가 서로 달라요' : null;
  const canSubmit = !isBlank(pw) && pw === pw2 && nickname.length <= NICKNAME_MAX && !submitting;

  async function onSubmit() {
    if (busy.current) return;
    busy.current = true;
    setSubmitting(true);
    setError(null);
    setPwServerError(null);
    try {
      setCreated(await openAccount({ nickname: nickname.trim() || null, password: pw }));
      void queryClient.invalidateQueries();
    } catch (e) {
      if (e instanceof ApiError && e.errors?.password) setPwServerError('계좌 비밀번호를 입력해 주세요');
      else setError(errorMessage(e, 'query'));
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  if (created) {
    return (
      <section className="panel" data-testid="open-result-panel">
        <h1>새 계좌가 만들어졌음</h1>
        <p>
          계좌번호 <span data-testid="open-result-number">{created.accountNumber}</span>
        </p>
        <Link to={`/accounts/${created.id}`} data-testid="open-result-detail-link">
          계좌 보기
        </Link>
      </section>
    );
  }

  return (
    <>
      <h1>계좌 개설</h1>
      <section className="panel">
        <TextField
          label="별칭(선택)"
          testid="open-nickname-input"
          value={nickname}
          onChange={setNickname}
          maxLength={NICKNAME_MAX}
          hint={`${NICKNAME_MAX}자까지`}
        />
        <TextField
          label="계좌 비밀번호"
          testid="open-pw-input"
          type="password"
          value={pw}
          onChange={setPw}
          error={pwServerError}
          errorTestid="open-pw-error"
        />
        <TextField
          label="계좌 비밀번호 확인"
          testid="open-pw-confirm-input"
          type="password"
          value={pw2}
          onChange={setPw2}
          error={pw2Error}
          errorTestid="open-pw-confirm-error"
        />
        {error && (
          <p className="error" data-testid="open-error" role="alert">
            {error}
          </p>
        )}
        <button type="button" className="primary" data-testid="open-submit" disabled={!canSubmit} onClick={() => void onSubmit()}>
          계좌 개설하기
        </button>
      </section>
    </>
  );
}
