// S-15 매수·매도 주문(지정가). 주문가·수량을 넣으면 "주문 금액 + 예상 수수료 = 묶일 금액"이 바로 바뀜(설계 3.2.5의 4번)
// 수수료는 서버와 같은 식으로 화면이 계산하고 "예상"으로 표시. 실제 값은 주문 뒤 계좌를 다시 읽어 보여 줌

import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { placeOrder, type Order } from '../api/endpoints';
import { ConfirmPanel, LockedBanner, PasswordPanel, unlockPath } from '../components/Flow';
import { Amount, LoadError, Loading, NotFound, NumberField, PageHead, Row, SelectField, StatusBadge } from '../components/ui';
import { useAccount, useHoldings, useStockInfo } from '../hooks/queries';
import { useAccountChoice } from '../hooks/useAccountChoice';
import { useMoneyFlow } from '../hooks/useMoneyFlow';
import { buyHoldAmount, maxBuyQuantity, tradeFee } from '../lib/fee';
import { formatAmount } from '../lib/format';
import { ORDER_STATUS } from '../lib/labels';

const MAX_QTY = 1_000_000; // TradeStockDto @Max

export function OrderPage() {
  const [params, setParams] = useSearchParams();
  const side = params.get('side') === 'sell' ? 'sell' : 'buy';
  const stockId = Number(params.get('stockId'));
  const validStock = Number.isInteger(stockId) && stockId > 0;
  const choice = useAccountChoice();
  const account = useAccount(choice.selectedId);
  const holdings = useHoldings(choice.selectedId);
  const stock = useStockInfo(validStock ? stockId : null);
  const [orderPrice, setOrderPrice] = useState('');
  const [quantity, setQuantity] = useState('');
  const priceNum = Number(orderPrice);
  const qtyNum = Number(quantity);

  const flow = useMoneyFlow<Order>({
    accountId: choice.selectedId,
    inputSignature: [side, choice.selectedId, stockId, orderPrice, quantity].join('|'),
    errorContext: 'order',
    send: (key, password) =>
      placeOrder(key, side, { accountId: choice.selectedId!, stockId, price: priceNum, quantity: qtyNum, accountPassword: password }),
  });

  if (!validStock) return <NotFound message="이 종목을 찾을 수 없어요" />;
  if (choice.query.isPending || stock.isPending) return <Loading />;
  if (choice.query.isError) return <LoadError error={choice.query.error} onRetry={() => void choice.query.refetch()} />;
  if (stock.isError) return <LoadError error={stock.error} onRetry={() => void stock.refetch()} />;

  const a = account.data;
  const here = `/order?${params.toString()}`;
  const sideLabel = side === 'buy' ? '매수' : '매도';
  const { name, code } = stock.data;

  if (flow.step === 'result' && flow.result) {
    const o = flow.result.data;
    const filled = o.status === 'FILLED';
    return (
      <div className="narrow">
        <PageHead title={`${sideLabel} 주문`} />
        <section className="panel" data-testid="order-result-panel">
          <h2>처리 결과</h2>
          <p>
            <StatusBadge testid="order-result-status" state={o.status} label={ORDER_STATUS[o.status]} />
          </p>
          <p data-testid="order-result-message">
            {filled ? `체결됐어요. 체결가 ${formatAmount(o.filledPrice ?? 0)}원` : '주문을 넣었어요. 시세가 주문가에 닿으면 체결돼요'}
          </p>
          <dl className="summary">
            <Row label="주문 번호">
              <span data-testid="order-result-order-id">{o.id}</span>
            </Row>
            {filled && (
              <Row label="체결가">
                <Amount value={o.filledPrice ?? 0} testid="order-result-filled-price" />
              </Row>
            )}
            {flow.result.account && (
              <>
                <Row label="묶인 금액">
                  <Amount value={flow.result.account.heldAmount} testid="order-result-held" />
                </Row>
                <Row label="잔액">
                  <Amount value={flow.result.account.balance} testid="order-result-balance" />
                </Row>
                <Row label="주문 가능 금액">
                  <Amount value={flow.result.account.availableBalance} testid="order-result-orderable" />
                </Row>
              </>
            )}
          </dl>
          <div className="actions">
            <Link to={`/orders?accountId=${choice.selectedId}`} data-testid="order-result-orders-link">
              주문 내역 보기
            </Link>
            <Link to={`/accounts/${choice.selectedId}`} className="button primary" data-testid="order-result-done">
              확인
            </Link>
          </div>
        </section>
      </div>
    );
  }

  const orderable = a?.availableBalance ?? 0;
  const sellable = holdings.data?.find((h) => h.stockId === stockId)?.availableQuantity ?? 0;
  const amount = priceNum * qtyNum;
  const fee = tradeFee(amount);
  const hold = buyHoldAmount(priceNum, qtyNum);
  const maxQty = side === 'buy' ? maxBuyQuantity(priceNum, orderable) : sellable;
  const locked = a?.status === 'PENDING';
  const priceError = orderPrice !== '' && priceNum < 1 ? '1원 이상 입력해 주세요' : null;
  const quantityError =
    quantity === ''
      ? null
      : qtyNum < 1
        ? '1주 이상 입력해 주세요'
        : qtyNum > MAX_QTY
          ? '한 번에 1,000,000주까지 주문할 수 있어요'
          : side === 'buy' && priceNum > 0 && hold > orderable
            ? `묶일 금액이 주문 가능 금액(${formatAmount(orderable)}원)보다 커요`
            : side === 'sell' && qtyNum > sellable
              ? `팔 수 있는 수량(${formatAmount(sellable)}주)보다 많아요`
              : null;
  const canNext = !!a && !locked && orderPrice !== '' && quantity !== '' && !priceError && !quantityError && (side === 'buy' || sellable > 0);
  const editing = flow.step === 'input';

  function setSide(next: 'buy' | 'sell') {
    const p = new URLSearchParams(params);
    p.set('side', next);
    setParams(p, { replace: true });
  }

  return (
    <div className="narrow">
      <PageHead title="주문" sub="화면의 계산 값은 예상이에요. 실제 값은 주문 뒤 계좌를 다시 읽어 보여 줘요" />
      <div role="tablist" className="tabs">
        <button type="button" role="tab" className="buy" aria-selected={side === 'buy'} data-testid="order-side-buy" disabled={!editing} onClick={() => setSide('buy')}>
          매수
        </button>
        <button type="button" role="tab" className="sell" aria-selected={side === 'sell'} data-testid="order-side-sell" disabled={!editing} onClick={() => setSide('sell')}>
          매도
        </button>
      </div>
      <section className="panel">
        <p>
          <span data-testid="order-stock-name">{name}</span> <span data-testid="order-stock-code">{code}</span> · 현재가{' '}
          <Amount value={stock.data.currentPrice} testid="order-stock-price" />
        </p>
        <SelectField
          label="주문 계좌"
          testid="order-account-select"
          value={choice.selectedId ? String(choice.selectedId) : ''}
          onChange={(v) => choice.select(Number(v))}
          options={choice.options()}
          disabled={!editing}
        />
        {locked && editing && a && <LockedBanner testid="order-locked-banner" unlockTo={unlockPath(a.id, here)} linkTestid="order-unlock-link" />}
        {a &&
          (side === 'buy' ? (
            <p>
              주문 가능 금액 <Amount value={orderable} testid="order-orderable" />
            </p>
          ) : (
            <p>
              팔 수 있는 수량 <Amount value={sellable} unit="주" testid="order-sellable" />
              {sellable === 0 && <span className="hint"> 이 계좌에 팔 수 있는 주식이 없어요</span>}
            </p>
          ))}
        <NumberField
          label="주문가"
          testid="order-price-input"
          value={orderPrice}
          onChange={setOrderPrice}
          suffix="원"
          error={priceError}
          errorTestid="order-price-error"
          hint={side === 'buy' ? '매수는 현재가가 주문가 이하면 바로 체결' : '매도는 현재가가 주문가 이상이면 바로 체결'}
          disabled={!editing}
        />
        <NumberField
          label="수량"
          testid="order-quantity-input"
          value={quantity}
          onChange={setQuantity}
          suffix="주"
          error={quantityError}
          errorTestid="order-quantity-error"
          disabled={!editing}
        />
        <p className="hint">
          최대 <span data-testid="order-max-quantity">{formatAmount(maxQty)}</span>주
        </p>

        <dl className="summary calc">
          <Row label="주문 금액">
            <Amount value={amount} testid="order-calc-amount" />
          </Row>
          <Row label="예상 수수료(0.015%, 원 미만 버림)">
            <Amount value={fee} testid="order-calc-fee" />
          </Row>
          {side === 'buy' ? (
            <>
              <Row label="묶일 금액(예상)">
                <Amount value={hold} testid="order-calc-hold" />
              </Row>
              <Row label="주문 뒤 주문 가능(예상)">
                <Amount value={orderable - hold} testid="order-calc-after-orderable" />
              </Row>
            </>
          ) : (
            <p className="hint" data-testid="order-sell-note">
              체결가는 그때의 시세로 정해짐. 수량 {formatAmount(qtyNum)}주가 체결 전까지 묶임
            </p>
          )}
        </dl>
        {editing && (
          <button type="button" className="primary block" data-testid="order-next" disabled={!canNext} onClick={() => flow.setStep('confirm')}>
            {sideLabel} 주문 확인으로
          </button>
        )}
      </section>

      {!editing && (
        <ConfirmPanel p="order" flow={flow}>
          <Row label="구분">
            <span data-testid="order-confirm-side">{sideLabel}</span>
          </Row>
          <Row label="종목">
            <span data-testid="order-confirm-stock">{name}</span>
          </Row>
          <Row label="주문가">
            <Amount value={priceNum} testid="order-confirm-price" />
          </Row>
          <Row label="수량">
            <Amount value={qtyNum} unit="주" testid="order-confirm-quantity" />
          </Row>
          {side === 'buy' && (
            <Row label="묶일 금액(예상)">
              <Amount value={hold} testid="order-confirm-hold" />
            </Row>
          )}
        </ConfirmPanel>
      )}
      {flow.step === 'password' && (
        <PasswordPanel p="order" flow={flow} submitLabel={`${sideLabel} 주문 내기`} unlockTo={unlockPath(choice.selectedId!, here)} />
      )}
    </div>
  );
}
