// S-16 주문 내역·미체결(S-06의 탭이자 단독 화면). 취소한 주문은 지우지 않고 줄을 그어 둠
// 주문 취소(K4)는 비밀번호와 중복 요청 방지 키를 받지 않음

import { Fragment, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { cancelOrder, type Order, type OrderStatus } from '../../api/endpoints';
import { Amount, LoadError, Loading, SelectField, StatusBadge } from '../../components/ui';
import { useAccount, useOrders } from '../../hooks/queries';
import { buyHoldAmount } from '../../lib/fee';
import { formatDateTime } from '../../lib/format';
import { ORDER_STATUS, tradeSide } from '../../lib/labels';
import { errorMessage } from '../../lib/messages';

function heldOf(o: Order): { amount: number | null; quantity: number | null } {
  if (o.status !== 'PENDING') return { amount: null, quantity: null };
  return o.type === 'BUY' ? { amount: buyHoldAmount(o.price, o.quantity), quantity: null } : { amount: null, quantity: o.quantity };
}

function Timeline({ order }: { order: Order }) {
  return (
    <ol className="timeline" data-testid="orders-timeline">
      <li data-testid="orders-timeline-step" data-step="accepted">
        접수 {formatDateTime(order.createdAt)}
      </li>
      {order.status === 'FILLED' && (
        <li data-testid="orders-timeline-step" data-step="filled">
          체결 {formatDateTime(order.tradeAt)} · 체결가 <Amount value={order.filledPrice ?? 0} />
        </li>
      )}
      {order.status === 'CANCELLED' && (
        <li data-testid="orders-timeline-step" data-step="cancelled">
          취소
        </li>
      )}
      <li className="hint">단계별 금액(묶인 금액·수수료)은 서버 응답에 없어 비워 둠</li>
    </ol>
  );
}

export function OrdersTab({ accountId }: { accountId: number }) {
  const queryClient = useQueryClient();
  const today = new Date();
  const thisYear = today.getFullYear();
  const thisMonth = today.getMonth() + 1;
  const [status, setStatus] = useState<OrderStatus | ''>('');
  const [year, setYear] = useState(thisYear);
  const [month, setMonth] = useState(thisMonth);
  const [openTimeline, setOpenTimeline] = useState<number | null>(null);
  const [cancelTarget, setCancelTarget] = useState<number | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const account = useAccount(accountId);
  const query = useOrders(accountId, { status, year, month });

  const orders = query.data ?? [];
  const pending = orders.filter((o) => o.status === 'PENDING');
  const pendingAmount = pending.reduce((s, o) => s + (heldOf(o).amount ?? 0), 0);
  const pendingQty = pending.reduce((s, o) => s + (heldOf(o).quantity ?? 0), 0);

  async function onCancel(o: Order) {
    setCancelling(true);
    setCancelError(null);
    try {
      await cancelOrder(accountId, o.id);
      setCancelTarget(null);
    } catch (e) {
      setCancelError(errorMessage(e, 'cancel'));
    } finally {
      setCancelling(false);
      // 성공이든 이미 체결·취소됐든 목록과 계좌를 다시 읽음
      void queryClient.invalidateQueries();
    }
  }

  const years = Array.from({ length: 6 }, (_, i) => thisYear - i);

  return (
    <section className="tab-body">
      <div className="filters">
        <SelectField
          label="주문 상태"
          testid="orders-status-select"
          value={status}
          onChange={(v) => setStatus(v as OrderStatus | '')}
          placeholder="전체"
          options={Object.entries(ORDER_STATUS).map(([value, label]) => ({ value, label }))}
        />
        <SelectField
          label="연도"
          testid="orders-year-select"
          value={String(year)}
          onChange={(v) => {
            setYear(Number(v));
            if (Number(v) === thisYear && month > thisMonth) setMonth(thisMonth);
          }}
          options={years.map((y) => ({ value: String(y), label: `${y}년` }))}
        />
        <SelectField
          label="월"
          testid="orders-month-select"
          value={String(month)}
          onChange={(v) => setMonth(Number(v))}
          options={Array.from({ length: 12 }, (_, i) => ({
            value: String(i + 1),
            label: `${i + 1}월`,
            disabled: year === thisYear && i + 1 > thisMonth, // 서버가 미래 달을 거절함
          }))}
        />
      </div>

      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <LoadError error={query.error} onRetry={() => void query.refetch()} />
      ) : orders.length === 0 ? (
        <p className="empty" data-testid="orders-empty">
          {year}년 {month}월에 낸 주문이 없어요
        </p>
      ) : (
        <>
          <p className="banner banner-info" data-testid="orders-summary">
            <span>
              미체결 주문 {pending.length}건이 <Amount value={pendingAmount} />과 <Amount value={pendingQty} unit="주" />를 잡고 있음
            </span>
          </p>
          <ul className="rows">
            {orders.map((o) => {
              const held = heldOf(o);
              const cancellable = o.status === 'PENDING' && o.type !== 'TRANSFER';
              return (
                <Fragment key={o.id}>
                  <li data-testid="orders-row" data-id={o.id} className={`row wrap-sm${o.status === 'CANCELLED' ? ' cancelled' : ''}`}>
                    <span className="row-main">
                      <span className="row-title">
                        <span data-testid="orders-row-stock">{o.stockName}</span>
                        <StatusBadge testid="orders-row-status" state={o.status} label={ORDER_STATUS[o.status]} />
                      </span>
                      <span className="row-sub">
                        <span data-testid="orders-row-side">{tradeSide(o.type, o.quantity)}</span>
                        <span className="nowrap" data-testid="orders-row-time">{formatDateTime(o.createdAt)}</span>
                        <span>
                          주문 <span data-testid="orders-row-id">{o.id}</span>
                        </span>
                      </span>
                      <span className="row-sub">
                        <span>
                          체결가{' '}
                          {o.filledPrice === null ? <span data-testid="orders-row-filled-price">없음</span> : <Amount value={o.filledPrice} testid="orders-row-filled-price" />}
                        </span>
                        {held.amount !== null && (
                          <span>
                            묶인 것 <Amount value={held.amount} testid="orders-row-held" />
                          </span>
                        )}
                        {held.quantity !== null && (
                          <span>
                            묶인 것 <Amount value={held.quantity} unit="주" testid="orders-row-held" />
                          </span>
                        )}
                      </span>
                    </span>
                    <span className="row-side">
                      <span className="row-amount">
                        <Amount value={o.price} testid="orders-row-price" /> × <Amount value={Math.abs(o.quantity)} unit="주" testid="orders-row-quantity" />
                      </span>
                      <span className="row-actions">
                        {cancellable && (
                          <button
                            type="button"
                            className="small danger"
                            data-testid="orders-row-cancel"
                            aria-label={`주문 ${o.id} 취소`}
                            onClick={() => {
                              setCancelTarget(o.id);
                              setCancelError(null);
                            }}
                          >
                            취소
                          </button>
                        )}
                        <button
                          type="button"
                          className="small"
                          data-testid="orders-row-timeline-toggle"
                          aria-label={`주문 ${o.id} 과정 보기`}
                          aria-expanded={openTimeline === o.id}
                          onClick={() => setOpenTimeline(openTimeline === o.id ? null : o.id)}
                        >
                          과정
                        </button>
                      </span>
                    </span>
                  </li>
                  {openTimeline === o.id && (
                    <li className="row-extra">
                      <Timeline order={o} />
                    </li>
                  )}
                  {cancelTarget === o.id && (
                    <li className="row-extra">
                      <div className="panel" data-testid="orders-cancel-panel">
                        <h3>주문 취소 확인</h3>
                        <p>
                          <span data-testid="orders-cancel-stock">{o.stockName}</span> · {tradeSide(o.type, o.quantity)} · <Amount value={o.price} /> ×{' '}
                          <Amount value={o.quantity} unit="주" />
                        </p>
                        <dl className="summary">
                          <div className="kv">
                            <dt>풀릴 {o.type === 'BUY' ? '금액' : '수량'}</dt>
                            <dd>
                              {o.type === 'BUY' ? (
                                <Amount value={held.amount ?? 0} testid="orders-cancel-release" />
                              ) : (
                                <Amount value={o.quantity} unit="주" testid="orders-cancel-release" />
                              )}
                            </dd>
                          </div>
                          {o.type === 'BUY' && account.data && (
                            <div className="kv">
                              <dt>취소 뒤 주문 가능(예상)</dt>
                              <dd>
                                <Amount value={account.data.availableBalance + (held.amount ?? 0)} testid="orders-cancel-after-orderable" />
                              </dd>
                            </div>
                          )}
                        </dl>
                        {cancelError && (
                          <p className="error" data-testid="orders-cancel-error" role="alert">
                            {cancelError}
                          </p>
                        )}
                        <div className="actions end">
                          <button type="button" data-testid="orders-cancel-back" onClick={() => setCancelTarget(null)}>
                            돌아가기
                          </button>
                          <button type="button" className="primary" data-testid="orders-cancel-submit" disabled={cancelling} onClick={() => void onCancel(o)}>
                            주문 취소
                          </button>
                        </div>
                      </div>
                    </li>
                  )}
                </Fragment>
              );
            })}
          </ul>
        </>
      )}
    </section>
  );
}
