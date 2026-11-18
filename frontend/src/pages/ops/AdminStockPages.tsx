// S-31 종목 관리와 S-32 시세 입력·체결 결과. 관리자 API(N-03 목록·한 건, N-09 종목별 주문, D2 시세 갱신)를 씀
// 시세를 바꾸면 서버가 그 시세에 맞는 미체결 주문을 바로 체결하고 결과(체결·남음·실패)를 돌려줌. 화면은 그 결과를 그대로 보임

import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getAdminStock,
  getAdminStocks,
  getStockOrders,
  updateStockPrice,
  type Order,
  type OrderStatus,
  type PriceUpdateResult,
} from '../../api/endpoints';
import { Amount, LoadError, Loading, NumberField, PageHead, SelectField, StatusBadge } from '../../components/ui';
import { formatDateTime } from '../../lib/format';
import { ORDER_STATUS, tradeSide } from '../../lib/labels';
import { errorMessage } from '../../lib/messages';

const PAGE_SIZE = 20;

/** 매수 빨강·매도 파랑. 색만으로 구분하지 않게 글자(매수/매도)를 같이 둠 */
function Side({ order, testid }: { order: Order; testid: string }) {
  const cls = order.type === 'BUY' ? 'trade-side buy-text' : order.type === 'SELL' ? 'trade-side sell-text' : 'trade-side';
  return (
    <span className={cls} data-testid={testid} data-state={order.type}>
      {tradeSide(order.type, order.quantity)}
    </span>
  );
}

/** 관리자 화면의 주문 한 줄. S-31 펼침과 S-32 미체결 목록이 같이 씀(`{p}-row`, 칸은 `{p}-row-{칸}`) */
function OrderLine({ order, p, willFill }: { order: Order; p: string; willFill?: boolean }) {
  return (
    <li className={`row wrap-sm${willFill ? ' will-fill' : ''}`} data-testid={`${p}-row`} data-id={order.id}>
      <span className="row-main">
        <span className="row-title">
          <Side order={order} testid={`${p}-row-side`} />
          <StatusBadge testid={`${p}-row-status`} state={order.status} label={ORDER_STATUS[order.status]} />
          {willFill && (
            <span className="badge badge-fill" data-testid={`${p}-row-will-fill`}>
              새 시세에서 체결
            </span>
          )}
        </span>
        <span className="row-sub">
          <span>
            주문 <span data-testid={`${p}-row-id`}>{order.id}</span>
          </span>
          <span>
            계좌 ID <span data-testid={`${p}-row-account`}>{order.accountId}</span>
          </span>
          <span className="nowrap">{formatDateTime(order.createdAt)}</span>
        </span>
      </span>
      <span className="row-side">
        <span className="row-amount">
          <Amount value={order.price} testid={`${p}-row-price`} /> × <Amount value={Math.abs(order.quantity)} unit="주" testid={`${p}-row-quantity`} />
        </span>
        {order.filledPrice !== null && (
          <span>
            체결가 <Amount value={order.filledPrice} testid={`${p}-row-filled-price`} />
          </span>
        )}
      </span>
    </li>
  );
}

/** S-31 줄 아래 펼치는 종목별 주문. 서버가 상태 하나씩만 받으므로 "전체"는 두지 않음 */
function StockOrders({ stockId }: { stockId: number }) {
  const [status, setStatus] = useState<OrderStatus>('PENDING');
  const query = useQuery({ queryKey: ['admin', 'orders', stockId, status], queryFn: () => getStockOrders(stockId, status) });
  return (
    <div className="panel" data-testid="opsstocks-orders-panel">
      <div className="filters">
        <SelectField
          label="주문 상태"
          testid="opsstocks-orders-status-select"
          value={status}
          onChange={(v) => setStatus(v as OrderStatus)}
          options={Object.entries(ORDER_STATUS).map(([value, label]) => ({ value, label }))}
        />
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <LoadError error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.length === 0 ? (
        <p className="empty" data-testid="opsstocks-orders-empty">
          {ORDER_STATUS[status]} 주문이 없어요
        </p>
      ) : (
        <ul className="rows">
          {query.data.map((o) => (
            <OrderLine key={o.id} order={o} p="opsstocks-order" />
          ))}
        </ul>
      )}
    </div>
  );
}

export function AdminStocksPage() {
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<number | null>(null);
  const query = useQuery({ queryKey: ['admin', 'stocks', page], queryFn: () => getAdminStocks(page, PAGE_SIZE) });

  return (
    <div className="stack">
      <PageHead title="종목 관리" sub="S-31 · 종목마다 현재가와 미체결 주문 수를 봐요. 최근 등록한 종목이 위에 와요" />
      <section className="panel">
        {query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <LoadError error={query.error} onRetry={() => void query.refetch()} />
        ) : query.data.content.length === 0 ? (
          <p className="empty" data-testid="opsstocks-empty">
            등록된 종목이 없어요
          </p>
        ) : (
          <>
            <p className="hint">
              전체 <span data-testid="opsstocks-total">{query.data.totalElements}</span>종목
            </p>
            <ul className="rows">
              {query.data.content.map((s) => (
                <li key={s.id} className="row-group">
                  <div className="row wrap-sm" data-testid="opsstocks-row" data-id={s.id}>
                    <span className="row-icon" aria-hidden="true">
                      {s.name.slice(0, 1)}
                    </span>
                    <span className="row-main">
                      <span className="row-title">
                        <span data-testid="opsstocks-row-name">{s.name}</span>
                        <span className="hint" data-testid="opsstocks-row-code">
                          {s.code}
                        </span>
                      </span>
                      <span className="row-sub">
                        <span>
                          미체결 <Amount value={s.pendingOrderCount} unit="" testid="opsstocks-row-pending" />건
                        </span>
                      </span>
                    </span>
                    <span className="row-side">
                      <span className="row-amount">
                        <Amount value={s.currentPrice} testid="opsstocks-row-price" />
                      </span>
                      <span className="row-actions">
                        <button
                          type="button"
                          className="small"
                          data-testid="opsstocks-row-orders-toggle"
                          aria-expanded={open === s.id}
                          aria-label={`${s.name} 주문 보기`}
                          onClick={() => setOpen(open === s.id ? null : s.id)}
                        >
                          주문 보기
                        </button>
                        <Link to={`/ops/price?stockId=${s.id}`} className="button small primary" data-testid="opsstocks-row-price-link" aria-label={`${s.name} 시세 입력`}>
                          시세 입력
                        </Link>
                      </span>
                    </span>
                  </div>
                  {open === s.id && (
                    <div className="row-extra">
                      <StockOrders stockId={s.id} />
                    </div>
                  )}
                </li>
              ))}
            </ul>
            <div className="actions pager">
              <button type="button" className="small" data-testid="opsstocks-prev" disabled={query.data.first} onClick={() => setPage((p) => p - 1)}>
                이전 쪽
              </button>
              <span data-testid="opsstocks-page" className="hint">
                {query.data.number + 1} / {Math.max(query.data.totalPages, 1)}쪽
              </span>
              <button type="button" className="small" data-testid="opsstocks-next" disabled={query.data.last} onClick={() => setPage((p) => p + 1)}>
                다음 쪽
              </button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function isFillable(o: Order, price: number): boolean {
  return o.type === 'BUY' ? price <= o.price : o.type === 'SELL' ? price >= o.price : false;
}

function PriceResult({ result }: { result: PriceUpdateResult }) {
  return (
    <section className="panel" data-testid="opsprice-result-panel" aria-live="polite">
      <h2>체결 결과</h2>
      <p>
        새 시세 <Amount value={result.currentPrice} testid="opsprice-result-price" />로 저장했어요
      </p>
      <div className="stat-grid">
        <div className="stat stat-filled">
          <span>체결</span>
          <b>
            <span data-testid="opsprice-result-filled">{result.filledCount}</span>건
          </b>
        </div>
        <div className="stat">
          <span>그대로 남음</span>
          <b>
            <span data-testid="opsprice-result-remaining">{result.remainingCount}</span>건
          </b>
        </div>
        <div className={`stat${result.failedCount > 0 ? ' stat-failed' : ''}`}>
          <span>실패</span>
          <b>
            <span data-testid="opsprice-result-failed">{result.failedCount}</span>건
          </b>
        </div>
      </div>
      <p>
        체결된 주문 <span data-testid="opsprice-result-filled-ids">{result.filledOrderIds.length ? result.filledOrderIds.join(', ') : '없음'}</span>
      </p>
      {result.failedCount > 0 && (
        <p className="error">
          실패한 주문 <span data-testid="opsprice-result-failed-ids">{result.failedOrderIds.join(', ')}</span>. 미체결로 남아 있고 원인은 서버 로그에 있어요
        </p>
      )}
      <p className="hint">그대로 남은 주문은 시세 조건이 맞지 않거나 계좌가 거래정지인 주문이에요</p>
    </section>
  );
}

function PriceForm({ stockId }: { stockId: number }) {
  const queryClient = useQueryClient();
  const [input, setInput] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<PriceUpdateResult | null>(null);
  const stock = useQuery({ queryKey: ['admin', 'stock', stockId], queryFn: () => getAdminStock(stockId) });
  const orders = useQuery({ queryKey: ['admin', 'orders', stockId, 'PENDING'], queryFn: () => getStockOrders(stockId, 'PENDING') });

  if (stock.isPending) return <Loading />;
  if (stock.isError) return <LoadError error={stock.error} onRetry={() => void stock.refetch()} />;

  const s = stock.data;
  const newPrice = Number(input);
  const valid = input !== '' && newPrice >= 1;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!valid || saving) return;
    setSaving(true);
    setError(null);
    try {
      setResult(await updateStockPrice(stockId, newPrice));
      setInput('');
    } catch (err) {
      setError(errorMessage(err, 'query'));
    } finally {
      setSaving(false);
      // 성공이든 실패든 시세와 미체결 목록을 다시 읽음(실패해도 일부가 반영됐을 수 있음)
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
    }
  }

  return (
    <>
      <PageHead title="시세 입력" sub="S-32 · 새 시세를 넣으면 그 시세에 맞는 미체결 주문이 바로 체결돼요" />
      <section className="panel hero">
        <div className="stock-line">
          <span className="row-title">
            <span data-testid="opsprice-stock-name">{s.name}</span>
            <span className="hint" data-testid="opsprice-stock-code">
              {s.code}
            </span>
          </span>
          <Link to="/ops/stocks" data-testid="opsprice-stocks-link">
            종목 목록
          </Link>
        </div>
        <h2 className="hero-label">지금 시세</h2>
        <p className="hero-amount">
          <Amount value={s.currentPrice} testid="opsprice-current-price" />
        </p>
        <p className="hint">
          미체결 주문 <span data-testid="opsprice-pending-count">{s.pendingOrderCount}</span>건. 관리자가 넣은 값이며 실시간 시세가 아님
        </p>
        <form onSubmit={(e) => void onSubmit(e)} className="stack-sm">
          <NumberField
            label="새 시세"
            testid="opsprice-price-input"
            value={input}
            onChange={(v) => {
              setInput(v);
              setError(null);
            }}
            suffix="원"
            error={input !== '' && !valid ? '1원 이상으로 넣어 주세요' : null}
            errorTestid="opsprice-price-error"
            hint="매수는 시세가 주문가 이하, 매도는 주문가 이상이면 체결돼요"
          />
          {error && (
            <p className="error" data-testid="opsprice-submit-error" role="alert">
              {error}
            </p>
          )}
          <div className="actions">
            <button type="submit" className="primary" data-testid="opsprice-submit" disabled={!valid || saving}>
              {saving ? '저장하는 중' : '시세 저장하고 체결 확인'}
            </button>
          </div>
        </form>
      </section>

      {result && <PriceResult result={result} />}

      <section className="panel">
        <h2>미체결 주문</h2>
        {valid && <p className="hint">새 시세 {newPrice.toLocaleString('ko-KR')}원에서 체결될 주문을 표시했어요</p>}
        {orders.isPending ? (
          <Loading />
        ) : orders.isError ? (
          <LoadError error={orders.error} onRetry={() => void orders.refetch()} />
        ) : orders.data.length === 0 ? (
          <p className="empty" data-testid="opsprice-orders-empty">
            미체결 주문이 없어요
          </p>
        ) : (
          <ul className="rows">
            {orders.data.map((o) => (
              <OrderLine key={o.id} order={o} p="opsprice-order" willFill={valid && isFillable(o, newPrice)} />
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

export function AdminPricePage() {
  const stockId = Number(useSearchParams()[0].get('stockId'));
  if (!Number.isInteger(stockId) || stockId <= 0)
    return (
      <div className="stack">
        <PageHead title="시세 입력" sub="S-32 · 새 시세를 넣으면 그 시세에 맞는 미체결 주문이 바로 체결돼요" />
        <section className="panel empty" data-testid="opsprice-pick-empty">
          <p>시세를 넣을 종목을 먼저 골라 주세요</p>
          <Link to="/ops/stocks" className="button soft" data-testid="opsprice-stocks-link">
            종목 관리로
          </Link>
        </section>
      </div>
    );
  // 종목이 바뀌면 입력·결과를 새로 시작
  return (
    <div className="stack">
      <PriceForm key={stockId} stockId={stockId} />
    </div>
  );
}
