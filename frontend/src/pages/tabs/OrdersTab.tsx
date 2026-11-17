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
import { stockName } from '../../lib/stockNames';

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
    <section>
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
          <p data-testid="orders-summary">
            미체결 주문 {pending.length}건이 <Amount value={pendingAmount} />과 <Amount value={pendingQty} unit="주" />를 잡고 있음
          </p>
          <table className="ledger">
            <thead>
              <tr>
                <th>번호</th>
                <th>시각</th>
                <th>종목</th>
                <th>구분</th>
                <th className="num">주문가</th>
                <th className="num">수량</th>
                <th className="num">체결가</th>
                <th className="num">묶인 것</th>
                <th>상태</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => {
                const held = heldOf(o);
                const cancellable = o.status === 'PENDING' && o.type !== 'TRANSFER';
                return (
                  <Fragment key={o.id}>
                    <tr data-testid="orders-row" data-id={o.id} className={o.status === 'CANCELLED' ? 'cancelled' : undefined}>
                      <td data-testid="orders-row-id">{o.id}</td>
                      <td data-testid="orders-row-time">{formatDateTime(o.createdAt)}</td>
                      <td data-testid="orders-row-stock">{stockName(o.stockId)}</td>
                      <td data-testid="orders-row-side">{tradeSide(o.type, o.quantity)}</td>
                      <td className="num">
                        <Amount value={o.price} testid="orders-row-price" />
                      </td>
                      <td className="num">
                        <Amount value={Math.abs(o.quantity)} unit="주" testid="orders-row-quantity" />
                      </td>
                      <td className="num">
                        {o.filledPrice === null ? <span data-testid="orders-row-filled-price">없음</span> : <Amount value={o.filledPrice} testid="orders-row-filled-price" />}
                      </td>
                      <td className="num">
                        {held.amount !== null && <Amount value={held.amount} testid="orders-row-held" />}
                        {held.quantity !== null && <Amount value={held.quantity} unit="주" testid="orders-row-held" />}
                      </td>
                      <td>
                        <StatusBadge testid="orders-row-status" state={o.status} label={ORDER_STATUS[o.status]} />
                      </td>
                      <td>
                        {cancellable && (
                          <button
                            type="button"
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
                          data-testid="orders-row-timeline-toggle"
                          aria-label={`주문 ${o.id} 과정 보기`}
                          aria-expanded={openTimeline === o.id}
                          onClick={() => setOpenTimeline(openTimeline === o.id ? null : o.id)}
                        >
                          과정
                        </button>
                      </td>
                    </tr>
                    {openTimeline === o.id && (
                      <tr>
                        <td colSpan={10}>
                          <Timeline order={o} />
                        </td>
                      </tr>
                    )}
                    {cancelTarget === o.id && (
                      <tr>
                        <td colSpan={10}>
                          <div className="panel" data-testid="orders-cancel-panel">
                            <h3>주문 취소 확인</h3>
                            <p>
                              <span data-testid="orders-cancel-stock">{stockName(o.stockId)}</span> · {tradeSide(o.type, o.quantity)} ·{' '}
                              <Amount value={o.price} /> × <Amount value={o.quantity} unit="주" />
                            </p>
                            <p>
                              풀릴 {o.type === 'BUY' ? '금액' : '수량'}{' '}
                              {o.type === 'BUY' ? (
                                <Amount value={held.amount ?? 0} testid="orders-cancel-release" />
                              ) : (
                                <Amount value={o.quantity} unit="주" testid="orders-cancel-release" />
                              )}
                            </p>
                            {o.type === 'BUY' && account.data && (
                              <p>
                                취소 뒤 주문 가능(예상){' '}
                                <Amount value={account.data.availableBalance + (held.amount ?? 0)} testid="orders-cancel-after-orderable" />
                              </p>
                            )}
                            {cancelError && (
                              <p className="error" data-testid="orders-cancel-error" role="alert">
                                {cancelError}
                              </p>
                            )}
                            <div className="actions">
                              <button type="button" data-testid="orders-cancel-back" onClick={() => setCancelTarget(null)}>
                                돌아가기
                              </button>
                              <button type="button" className="primary" data-testid="orders-cancel-submit" disabled={cancelling} onClick={() => void onCancel(o)}>
                                주문 취소
                              </button>
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
