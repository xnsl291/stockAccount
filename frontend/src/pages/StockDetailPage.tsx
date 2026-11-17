// S-14 종목 상세. 현재가는 K2(가격 숫자 하나), 이름·코드는 임시 저장소(없으면 "종목 #ID"), 보유는 계좌마다 K6

import { useNavigate, useParams } from 'react-router';
import { Amount, LoadError, Loading, NotFound } from '../components/ui';
import { activeAccounts, useAccounts, useHoldingsOfAccounts, useStockPrice } from '../hooks/queries';
import { accountLabel } from '../lib/labels';
import { stockLabel, stockName } from '../lib/stockNames';

export function StockDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const valid = Number.isInteger(id) && id > 0;
  const price = useStockPrice(valid ? id : null);
  const accounts = activeAccounts(useAccounts().data);
  const holdingQueries = useHoldingsOfAccounts(accounts.map((a) => a.id));

  if (!valid) return <NotFound message="이 종목을 찾을 수 없어요" />;
  if (price.isPending) return <Loading />;
  if (price.isError) return <LoadError error={price.error} onRetry={() => void price.refetch()} />;

  const holdings = accounts.flatMap((a, i) => {
    const h = holdingQueries[i]?.data?.find((x) => x.stockId === id);
    return h ? [{ account: a, h }] : [];
  });

  return (
    <>
      <h1>
        <span data-testid="stock-name">{stockName(id)}</span> <span data-testid="stock-code">{stockLabel(id)?.code ?? ''}</span>
      </h1>
      <p>
        현재가 <Amount value={price.data} testid="stock-price" />
      </p>
      <p className="hint" data-testid="stock-price-note">
        관리자가 넣은 값이며 실시간 시세가 아님
      </p>

      <h2>내 보유</h2>
      {holdings.length === 0 ? (
        <p className="empty" data-testid="stock-holding-empty">
          이 종목을 가진 계좌가 없음
        </p>
      ) : (
        <table className="ledger">
          <thead>
            <tr>
              <th>계좌</th>
              <th className="num">보유</th>
              <th className="num">팔 수 있음</th>
              <th className="num">평균단가</th>
            </tr>
          </thead>
          <tbody>
            {holdings.map(({ account, h }) => (
              <tr key={account.id} data-testid="stock-holding-row" data-id={account.id}>
                <td data-testid="stock-holding-row-account">{accountLabel(account)}</td>
                <td className="num">
                  <Amount value={h.quantity} unit="주" testid="stock-holding-row-quantity" />
                </td>
                <td className="num">
                  <Amount value={h.availableQuantity} unit="주" testid="stock-holding-row-available" />
                </td>
                <td className="num">
                  <Amount value={h.avgPrice} testid="stock-holding-row-avg-price" />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="actions">
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
    </>
  );
}
