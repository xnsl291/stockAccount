// S-09 입금. 계좌 비밀번호는 받지 않음. 중복 요청 방지 키를 "보내기를 처음 누른 순간" 만드는 규칙과 맞추려고 확인 단계를 둠(사용자 결정)

import { useState } from 'react';
import { deposit } from '../api/endpoints';
import { ConfirmPanel, LockedBanner, ResultPanel, SendErrorBox, SubmitButton, unlockPath } from '../components/Flow';
import { Amount, LoadError, Loading, NumberField, Row, SelectField, TextField } from '../components/ui';
import { useAccount } from '../hooks/queries';
import { useAccountChoice } from '../hooks/useAccountChoice';
import { useMoneyFlow } from '../hooks/useMoneyFlow';
import { accountLabel } from '../lib/labels';
import { AMOUNT_MAX, MEMO_MAX } from '../lib/validation';

export function DepositPage() {
  const choice = useAccountChoice();
  const account = useAccount(choice.selectedId);
  const [amount, setAmount] = useState('');
  const [memo, setMemo] = useState('');
  const amountNum = Number(amount);
  const flow = useMoneyFlow({
    accountId: choice.selectedId,
    inputSignature: [choice.selectedId, amount, memo].join('|'),
    errorContext: 'deposit',
    send: (key) => deposit(key, { accountId: choice.selectedId!, amount: amountNum, memo: memo.trim() || null }),
  });

  if (choice.query.isPending) return <Loading />;
  if (choice.query.isError) return <LoadError error={choice.query.error} onRetry={() => void choice.query.refetch()} />;

  const a = account.data;
  const locked = a?.status === 'PENDING';
  const amountError =
    amount === '' ? null : amountNum < 1 ? '1원 이상 입력해 주세요' : amountNum > AMOUNT_MAX ? '처리할 수 있는 금액보다 커요' : null;
  const canNext = !!a && !locked && amount !== '' && !amountError && memo.length <= MEMO_MAX;
  const editing = flow.step === 'input';

  if (flow.step === 'result' && flow.result) {
    return (
      <>
        <h1>입금</h1>
        <ResultPanel p="deposit" message="입금했음" account={flow.result.account} doneTo={`/accounts/${choice.selectedId}`} />
      </>
    );
  }

  return (
    <>
      <h1>입금</h1>
      <section className="panel">
        <SelectField
          label="입금할 계좌"
          testid="deposit-account-select"
          value={choice.selectedId ? String(choice.selectedId) : ''}
          onChange={(v) => choice.select(Number(v))}
          options={choice.options(true)}
          placeholder={choice.selectedId ? undefined : '입금할 계좌를 골라 주세요'}
          disabled={!editing}
        />
        {a && (
          <p>
            지금 잔액 <Amount value={a.balance} testid="deposit-current-balance" />
          </p>
        )}
        {locked && editing && <LockedBanner testid="deposit-locked-banner" unlockTo={unlockPath(a.id, `/deposit?accountId=${a.id}`)} linkTestid="deposit-unlock-link" />}
        <NumberField
          label="입금 금액"
          testid="deposit-amount-input"
          value={amount}
          onChange={setAmount}
          suffix="원"
          error={amountError}
          errorTestid="deposit-amount-error"
          disabled={!editing}
        />
        <TextField label="메모(선택)" testid="deposit-memo-input" value={memo} onChange={setMemo} maxLength={MEMO_MAX} disabled={!editing} />
        {editing && (
          <button type="button" className="primary" data-testid="deposit-next" disabled={!canNext} onClick={() => flow.setStep('confirm')}>
            입금 확인으로
          </button>
        )}
      </section>

      {!editing && a && (
        <ConfirmPanel
          p="deposit"
          flow={flow}
          footer={
            <>
              <SubmitButton p="deposit" flow={flow} label="입금하기" />
            </>
          }
        >
          <Row label="계좌">
            <span data-testid="deposit-confirm-account">{accountLabel(a)}</span>
          </Row>
          <Row label="금액">
            <Amount value={amountNum} testid="deposit-confirm-amount" />
          </Row>
          <Row label="메모">
            <span data-testid="deposit-confirm-memo">{memo.trim() || '없음'}</span>
          </Row>
        </ConfirmPanel>
      )}
      {!editing && <SendErrorBox p="deposit" flow={flow} />}
    </>
  );
}
