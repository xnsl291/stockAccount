// S-10 출금. 출금 가능 금액보다 크면 화면이 미리 막음(사용자 결정). 1차의 출금 가능은 availableBalance(= 주문 가능)

import { useState } from 'react';
import { withdraw } from '../api/endpoints';
import { ConfirmPanel, LockedBanner, PasswordPanel, ResultPanel, unlockPath } from '../components/Flow';
import { Amount, LoadError, Loading, NumberField, PageHead, Row, SelectField, TextField } from '../components/ui';
import { useAccount } from '../hooks/queries';
import { useAccountChoice } from '../hooks/useAccountChoice';
import { useMoneyFlow } from '../hooks/useMoneyFlow';
import { formatAmount } from '../lib/format';
import { accountLabel } from '../lib/labels';
import { MESSAGES } from '../lib/messages';
import { MEMO_MAX } from '../lib/validation';

export function WithdrawPage() {
  const choice = useAccountChoice();
  const account = useAccount(choice.selectedId);
  const [amount, setAmount] = useState('');
  const [memo, setMemo] = useState('');
  const amountNum = Number(amount);
  const flow = useMoneyFlow({
    accountId: choice.selectedId,
    inputSignature: [choice.selectedId, amount, memo].join('|'),
    errorContext: 'account',
    send: (key, password) =>
      withdraw(key, { accountId: choice.selectedId!, amount: amountNum, memo: memo.trim() || null, accountPassword: password }),
  });

  if (choice.query.isPending) return <Loading />;
  if (choice.query.isError) return <LoadError error={choice.query.error} onRetry={() => void choice.query.refetch()} />;

  const a = account.data;
  const here = `/withdraw?accountId=${choice.selectedId}`;
  if (flow.step === 'result' && flow.result) {
    return (
      <div className="narrow">
        <PageHead title="출금" sub="출금 가능 금액 안에서만 뺄 수 있어요" />
        <ResultPanel p="withdraw" message="출금했음" account={flow.result.account} doneTo={`/accounts/${choice.selectedId}`} />
      </div>
    );
  }

  const withdrawable = a?.availableBalance ?? 0;
  const locked = a?.status === 'PENDING';
  const amountError =
    amount === ''
      ? withdrawable === 0 && a
        ? '지금 출금할 수 있는 돈이 없어요. 미체결 매수 주문에 묶인 돈은 취소하면 풀려요'
        : null
      : amountNum < 1
        ? '1원 이상 입력해 주세요'
        : amountNum > withdrawable
          ? `출금 가능 금액(${formatAmount(withdrawable)}원)보다 커요`
          : null;
  const canNext = !!a && !locked && amount !== '' && !amountError && memo.length <= MEMO_MAX;
  const editing = flow.step === 'input';

  return (
    <div className="narrow">
      <PageHead title="출금" sub="출금 가능 금액 안에서만 뺄 수 있어요" />
      <section className="panel">
        <SelectField
          label="출금할 계좌"
          testid="withdraw-account-select"
          value={choice.selectedId ? String(choice.selectedId) : ''}
          onChange={(v) => choice.select(Number(v))}
          options={choice.options()}
          disabled={!editing}
        />
        {a && (
          <p>
            출금 가능 금액 <Amount value={withdrawable} testid="withdraw-withdrawable" />
            <span className="hint" data-testid="withdraw-withdrawable-note">
              {MESSAGES.withdrawableNote}
            </span>
          </p>
        )}
        {locked && editing && <LockedBanner testid="withdraw-locked-banner" unlockTo={unlockPath(a.id, here)} linkTestid="withdraw-unlock-link" />}
        <NumberField
          label="출금 금액"
          testid="withdraw-amount-input"
          value={amount}
          onChange={setAmount}
          suffix="원"
          error={amountError}
          errorTestid="withdraw-amount-error"
          disabled={!editing}
        />
        <TextField label="메모(선택)" testid="withdraw-memo-input" value={memo} onChange={setMemo} maxLength={MEMO_MAX} disabled={!editing} />
        {editing && (
          <button type="button" className="primary block" data-testid="withdraw-next" disabled={!canNext} onClick={() => flow.setStep('confirm')}>
            출금 확인으로
          </button>
        )}
      </section>

      {!editing && a && (
        <ConfirmPanel p="withdraw" flow={flow}>
          <Row label="계좌">
            <span data-testid="withdraw-confirm-account">{accountLabel(a)}</span>
          </Row>
          <Row label="금액">
            <Amount value={amountNum} testid="withdraw-confirm-amount" />
          </Row>
          <Row label="메모">
            <span data-testid="withdraw-confirm-memo">{memo.trim() || '없음'}</span>
          </Row>
          <Row label="출금 뒤 출금 가능(예상)">
            <Amount value={withdrawable - amountNum} testid="withdraw-confirm-after" />
          </Row>
        </ConfirmPanel>
      )}
      {flow.step === 'password' && <PasswordPanel p="withdraw" flow={flow} submitLabel="출금하기" unlockTo={unlockPath(choice.selectedId!, here)} />}
    </div>
  );
}
