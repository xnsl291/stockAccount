// S-18 주식 이체. 다른 회원에게 보내면 현재가 × 수량의 1%(원 미만 버림)가 보내는 계좌에서 빠짐. 내 계좌끼리는 무료
// 예상 수수료는 화면 계산("예상"). 받는 계좌가 내 계좌인지는 내 계좌 목록(C1)과 맞춰 봄

import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { transferStock } from '../api/endpoints';
import { ConfirmPanel, LockedBanner, PasswordPanel, ResultPanel, unlockPath } from '../components/Flow';
import { Amount, LoadError, Loading, NumberField, PageHead, Row, SelectField, TextField } from '../components/ui';
import { useAccount, useHoldings } from '../hooks/queries';
import { useAccountChoice } from '../hooks/useAccountChoice';
import { useMoneyFlow } from '../hooks/useMoneyFlow';
import { useRecipient } from '../hooks/useRecipient';
import { stockTransferFee } from '../lib/fee';
import { formatAmount } from '../lib/format';

export function StockTransferPage() {
  const [params, setParams] = useSearchParams();
  const choice = useAccountChoice();
  const account = useAccount(choice.selectedId);
  const holdings = useHoldings(choice.selectedId);
  const recipient = useRecipient(choice.selectedId, choice.accounts);
  const list = holdings.data ?? [];
  const stockId = list.some((h) => h.stockId === Number(params.get('stockId'))) ? Number(params.get('stockId')) : (list[0]?.stockId ?? null);
  const holding = list.find((h) => h.stockId === stockId);
  const [quantity, setQuantity] = useState('');
  const qtyNum = Number(quantity);
  const to = recipient.found;
  const flow = useMoneyFlow({
    accountId: choice.selectedId,
    inputSignature: [choice.selectedId, stockId, quantity, to?.accountId].join('|'),
    errorContext: 'stocktransfer',
    send: (key, password) =>
      transferStock(key, {
        senderAccountId: choice.selectedId!,
        receiverAccountId: to!.accountId,
        stockId: stockId!,
        quantity: qtyNum,
        accountPassword: password,
      }),
  });

  if (choice.query.isPending) return <Loading />;
  if (choice.query.isError) return <LoadError error={choice.query.error} onRetry={() => void choice.query.refetch()} />;

  const a = account.data;
  const here = `/stock-transfer?${params.toString()}`;
  const name = holding?.stockName ?? '';
  if (flow.step === 'result' && flow.result) {
    return (
      <div className="narrow">
        <PageHead title="주식 이체" sub="다른 회원에게 보내면 수수료 1%가 빠져요" />
        <ResultPanel p="stocktransfer" message={`${name} ${formatAmount(qtyNum)}주를 보냈어요`} account={flow.result.account} doneTo={`/accounts/${choice.selectedId}`}>
          <Link to={`/holdings?accountId=${choice.selectedId}`} data-testid="stocktransfer-result-holdings-link">
            보유 주식 보기
          </Link>
        </ResultPanel>
      </div>
    );
  }

  const available = list.find((h) => h.stockId === stockId)?.availableQuantity ?? 0;
  const fee = to && !recipient.isMine && holding ? stockTransferFee(holding.currentPrice, qtyNum) : 0;
  const locked = a?.status === 'PENDING';
  const quantityError = quantity === '' ? null : qtyNum < 1 ? '1주 이상 입력해 주세요' : qtyNum > available ? `보낼 수 있는 수량(${formatAmount(available)}주)보다 많아요` : null;
  const canNext = !!a && !locked && !!stockId && !!to && quantity !== '' && !quantityError;
  const editing = flow.step === 'input';

  function selectStock(id: string) {
    const p = new URLSearchParams(params);
    p.set('stockId', id);
    setParams(p, { replace: true });
  }

  return (
    <div className="narrow">
      <PageHead title="주식 이체" sub="다른 회원에게 보내면 수수료 1%가 빠져요" />
      <section className="panel">
        <SelectField
          label="보내는 계좌"
          testid="stocktransfer-from-select"
          value={choice.selectedId ? String(choice.selectedId) : ''}
          onChange={(v) => choice.select(Number(v))}
          options={choice.options()}
          disabled={!editing}
        />
        {locked && editing && a && <LockedBanner testid="stocktransfer-locked-banner" unlockTo={unlockPath(a.id, here)} linkTestid="stocktransfer-unlock-link" />}
        {holdings.isSuccess && list.length === 0 ? (
          <p className="empty" data-testid="stocktransfer-empty">
            가진 주식이 없어요 <Link to="/stocks">종목 검색</Link>
          </p>
        ) : (
          <>
            <SelectField
              label="보낼 종목"
              testid="stocktransfer-stock-select"
              value={stockId ? String(stockId) : ''}
              onChange={selectStock}
              options={list.map((h) => ({ value: String(h.stockId), label: h.stockName }))}
              disabled={!editing}
            />
            <p>
              보낼 수 있는 수량 <Amount value={available} unit="주" testid="stocktransfer-available" /> · 현재가{' '}
              {holding && <Amount value={holding.currentPrice} testid="stocktransfer-price" />}
            </p>
            <NumberField
              label="보낼 수량"
              testid="stocktransfer-quantity-input"
              value={quantity}
              onChange={setQuantity}
              suffix="주"
              error={quantityError}
              disabled={!editing}
            />
          </>
        )}
        <TextField
          label="받는 계좌번호"
          testid="stocktransfer-to-number-input"
          inputMode="numeric"
          value={recipient.number}
          onChange={recipient.setNumber}
          maxLength={14}
          disabled={!editing}
        />
        <button type="button" data-testid="stocktransfer-to-check" disabled={!recipient.canCheck || !editing} onClick={() => void recipient.check()}>
          받는 사람 확인
        </button>
        {to && <p data-testid="stocktransfer-to-name">{recipient.displayName}</p>}
        {recipient.error && (
          <p className="error" data-testid="stocktransfer-to-error" role="alert">
            {recipient.error}
          </p>
        )}
        <p className="hint" data-testid="stocktransfer-fee-notice">
          다른 회원에게 보내면 현재가 × 수량의 1%(원 미만 버림)가 보내는 계좌에서 빠짐. 내 계좌끼리는 무료
        </p>
        <p>
          예상 수수료 <Amount value={fee} testid="stocktransfer-fee" />
        </p>
        {editing && (
          <button type="button" className="primary block" data-testid="stocktransfer-next" disabled={!canNext} onClick={() => flow.setStep('confirm')}>
            이체 확인으로
          </button>
        )}
      </section>

      {!editing && to && (
        <ConfirmPanel p="stocktransfer" flow={flow}>
          <Row label="종목">
            <span data-testid="stocktransfer-confirm-stock">{name}</span>
          </Row>
          <Row label="수량">
            <Amount value={qtyNum} unit="주" testid="stocktransfer-confirm-quantity" />
          </Row>
          <Row label="받는 사람">
            <span data-testid="stocktransfer-confirm-to-name">{recipient.displayName}</span>
          </Row>
          <Row label="예상 수수료">
            <Amount value={fee} testid="stocktransfer-confirm-fee" />
          </Row>
        </ConfirmPanel>
      )}
      {flow.step === 'password' && (
        <PasswordPanel p="stocktransfer" flow={flow} submitLabel="이체하기" unlockTo={unlockPath(choice.selectedId!, here)} />
      )}
    </div>
  );
}
