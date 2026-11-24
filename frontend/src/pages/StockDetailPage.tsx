// S-14 종목 상세. 이름·코드·현재가는 종목 한 건 조회(N-04), 보유는 계좌마다 K6

import { useNavigate, useParams } from 'react-router';
import { Amount, LoadError, Loading, NotFound } from '../components/ui';
import { activeAccounts, useAccounts, useHoldingsOfAccounts, useStockInfo } from '../hooks/queries';
import { accountLabel } from '../lib/labels';

export function StockDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const valid = Number.isInteger(id) && id > 0;
  const stock = useStockInfo(valid ? id : null);
  const accounts = activeAccounts(useAccounts().data);
  const holdingQueries = useHoldingsOfAccounts(accounts.map((a) => a.id));

  if (!valid) return <NotFound message="이 종목을 찾을 수 없어요" />;
  if (stock.isPending) return <Loading />;
  if (stock.isError) return <LoadError error={stock.error} onRetry={() => void stock.refetch()} />;

  const holdings = accounts.flatMap((a, i) => {
    const h = holdingQueries[i]?.data?.find((x) => x.stockId === id);
    return h ? [{ account: a, h }] : [];
  });

  return (
    <>
      <header className="page-head">
        <div className="page-head-row">
          <h1 data-testid="stock-name">{stock.data.name}</h1>
          <span className="hint" data-testid="stock-code">
            {stock.data.code}
          </span>
        </div>
      </header>

      <section className="panel hero">
        <h2 className="hero-label">현재가</h2>
        <p className="hero-amount">
          <Amount value={stock.data.currentPrice} testid="stock-price" />
        </p>
        <p className="hint" data-testid="stock-price-note">
          관리자가 넣은 값이며 실시간 시세가 아님
        </p>
        <div className="hero-actions">
          <button type="button" className="buy" data-testid="stock-buy" onClick={() => navigate(`/order?stockId=${id}&side=buy`)}>
            매수
          </button>
          <button
            type="button"
            className="sell"
            data-testid="stock-sell"
            disabled={holdings.length === 0}
            onClick={() => navigate(`/order?stockId=${id}&side=sell&accountId=${holdings[0]?.account.id ?? ''}`)}
          >
            매도
          </button>
        </div>
      </section>

      <section className="panel">
        <h2>내 보유</h2>
        {holdings.length === 0 ? (
          <p className="empty" data-testid="stock-holding-empty">
            이 종목을 가진 계좌가 없음
          </p>
        ) : (
          <ul className="rows">
            {holdings.map(({ account, h }) => (
              <li key={account.id} className="row" data-testid="stock-holding-row" data-id={account.id}>
                <span className="row-main">
                  <span className="row-title" data-testid="stock-holding-row-account">
                    {accountLabel(account)}
                  </span>
                  <span className="row-sub">
                    <span>
                      팔 수 있음 <Amount value={h.availableQuantity} unit="주" testid="stock-holding-row-available" />
                    </span>
                    <span>
                      평균 <Amount value={h.avgPrice} testid="stock-holding-row-avg-price" />
                    </span>
                  </span>
                </span>
                <span className="row-side row-amount">
                  <Amount value={h.quantity} unit="주" testid="stock-holding-row-quantity" />
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
