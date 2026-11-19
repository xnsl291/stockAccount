// S-11 송금. 받는 계좌번호 → 이름 확인(C7) → 금액·메모 → 확인 → 계좌 비밀번호
// 받는 계좌가 보내는 계좌와 같으면 화면이 미리 막음(서버도 SAME_ACCOUNT_TRANSFER)

import { useState } from 'react';
import { transfer } from '../api/endpoints';
import { ConfirmPanel, LockedBanner, PasswordPanel, ResultPanel, unlockPath } from '../components/Flow';
import { Amount, LoadError, Loading, NumberField, PageHead, Row, SelectField, TextField } from '../components/ui';
import { useAccount } from '../hooks/queries';
import { useAccountChoice } from '../hooks/useAccountChoice';
import { useMoneyFlow } from '../hooks/useMoneyFlow';
import { useRecipient } from '../hooks/useRecipient';
import { formatAmount } from '../lib/format';
import { MEMO_MAX, charCount } from '../lib/validation';

export function TransferPage() {
  const choice = useAccountChoice();
  const account = useAccount(choice.selectedId);
  const recipient = useRecipient(choice.selectedId, choice.accounts);
  const [amount, setAmount] = useState('');
  const [memo, setMemo] = useState('');
  const amountNum = Number(amount);
  const to = recipient.found;
  const flow = useMoneyFlow({
    accountId: choice.selectedId,
    inputSignature: [choice.selectedId, to?.accountId, amount, memo].join('|'),
    errorContext: 'transfer',
    send: (key, password) =>
      transfer(key, choice.selectedId!, to!.accountId, { amount: amountNum, memo: memo.trim() || null, accountPassword: password }),
  });

  if (choice.query.isPending) return <Loading />;
  if (choice.query.isError) return <LoadError error={choice.query.error} onRetry={() => void choice.query.refetch()} />;

  const a = account.data;
  const here = `/transfer?accountId=${choice.selectedId}`;
  if (flow.step === 'result' && flow.result && to) {
    return (
      <div className="narrow">
        <PageHead title="송금" sub="받는 사람 이름을 확인한 뒤 보내요" />
        <ResultPanel p="transfer" message={`${to.ownerName}님에게 보냈어요`} account={flow.result.account} doneTo={`/accounts/${choice.selectedId}`} />
      </div>
    );
  }

  const sendable = a?.availableBalance ?? 0;
  const locked = a?.status === 'PENDING';
  const amountError =
    amount === ''
      ? sendable === 0 && a
        ? '지금 출금할 수 있는 돈이 없어요. 미체결 매수 주문에 묶인 돈은 취소하면 풀려요'
        : null
      : amountNum < 1
        ? '1원 이상 입력해 주세요'
        : amountNum > sendable
          ? `보낼 수 있는 금액(${formatAmount(sendable)}원)보다 커요`
          : null;
  const canNext = !!a && !locked && !!to && amount !== '' && !amountError && charCount(memo) <= MEMO_MAX;
  const editing = flow.step === 'input';

  return (
    <div className="narrow">
      <PageHead title="송금" sub="받는 사람 이름을 확인한 뒤 보내요" />
      <section className="panel">
        <SelectField
          label="보내는 계좌"
          testid="transfer-from-select"
          value={choice.selectedId ? String(choice.selectedId) : ''}
          onChange={(v) => choice.select(Number(v))}
          options={choice.options()}
          disabled={!editing}
        />
        {a && (
          <p>
            보낼 수 있는 금액 <Amount value={sendable} testid="transfer-withdrawable" />
          </p>
        )}
        {locked && editing && <LockedBanner testid="transfer-locked-banner" unlockTo={unlockPath(a.id, here)} linkTestid="transfer-unlock-link" />}
        <TextField
          label="받는 계좌번호"
          testid="transfer-to-number-input"
          inputMode="numeric"
          value={recipient.number}
          onChange={recipient.setNumber}
          maxLength={14}
          disabled={!editing}
        />
        <button type="button" data-testid="transfer-to-check" disabled={!recipient.canCheck || !editing} onClick={() => void recipient.check()}>
          받는 사람 확인
        </button>
        {to && <p data-testid="transfer-to-name">{recipient.displayName}</p>}
        {recipient.error && (
          <p className="error" data-testid="transfer-to-error" role="alert">
            {recipient.error}
          </p>
        )}
        <NumberField
          label="보낼 금액"
          testid="transfer-amount-input"
          value={amount}
          onChange={setAmount}
          suffix="원"
          error={amountError}
          errorTestid="transfer-amount-error"
          disabled={!editing}
        />
        <TextField
          label="메모(선택)"
          testid="transfer-memo-input"
          value={memo}
          onChange={setMemo}
          countMax={MEMO_MAX} countTestid="transfer-memo-count"
          hint="받는 사람 거래내역에도 남아요"
          disabled={!editing}
        />
        {editing && (
          <button type="button" className="primary block" data-testid="transfer-next" disabled={!canNext} onClick={() => flow.setStep('confirm')}>
            송금 확인으로
          </button>
        )}
      </section>

      {!editing && to && (
        <ConfirmPanel p="transfer" flow={flow}>
          <Row label="받는 사람">
            <span data-testid="transfer-confirm-to-name">{to.ownerName}</span>
          </Row>
          <Row label="받는 계좌">
            <span data-testid="transfer-confirm-to-number">{to.accountNumber}</span>
          </Row>
          <Row label="금액">
            <Amount value={amountNum} testid="transfer-confirm-amount" />
          </Row>
          <Row label="메모">
            <span data-testid="transfer-confirm-memo">{memo.trim() || '없음'}</span>
          </Row>
        </ConfirmPanel>
      )}
      {flow.step === 'password' && <PasswordPanel p="transfer" flow={flow} submitLabel="송금하기" unlockTo={unlockPath(choice.selectedId!, here)} />}
    </div>
  );
}
