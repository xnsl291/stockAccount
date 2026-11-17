// S-17 보유 주식(S-06의 탭이자 단독 화면). 현재가는 종목마다 K2로 받고 평가금액은 화면이 계산(표시용, 원장 값 아님)

import { Link } from 'react-router';
import { Amount, LoadError, Loading } from '../../components/ui';
import { useHoldings, useStockPrices } from '../../hooks/queries';
import { stockLabel, stockName } from '../../lib/stockNames';

export function HoldingsTab({ accountId }: { accountId: number }) {
  const query = useHoldings(accountId);
  const prices = useStockPrices((query.data ?? []).map((h) => h.stockId));

  if (query.isPending) return <Loading />;
  if (query.isError) return <LoadError error={query.error} onRetry={() => void query.refetch()} />;
  const rows = query.data;
  if (rows.length === 0)
    return (
      <p className="empty" data-testid="holdings-empty">
        가진 주식이 없어요 <Link to="/stocks">종목 검색</Link>
      </p>
    );

  const totalValue = rows.reduce((s, h) => s + (prices.get(h.stockId) ?? 0) * h.quantity, 0);
  const totalPnl = rows.reduce((s, h) => s + h.profitNLoss, 0);

  return (
    <section>
      <dl className="summary">
        <div className="kv">
          <dt>평가금액 합계(화면 계산)</dt>
          <dd>
            <Amount value={totalValue} testid="holdings-total-value" />
          </dd>
        </div>
        <div className="kv">
          <dt>평가손익 합계</dt>
          <dd>
            <Amount value={totalPnl} signed tone testid="holdings-total-pnl" />
          </dd>
        </div>
      </dl>
      <table className="ledger">
        <thead>
          <tr>
            <th>종목</th>
            <th className="num">보유</th>
            <th className="num">팔 수 있음</th>
            <th className="num">평균단가</th>
            <th className="num">현재가</th>
            <th className="num">평가금액</th>
            <th className="num">평가손익</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows.map((h) => {
            const price = prices.get(h.stockId);
            const cost = h.avgPrice * h.quantity;
            const rate = cost > 0 ? ((h.profitNLoss / cost) * 100).toFixed(2) : '0.00';
            const name = stockName(h.stockId);
            const canMove = h.availableQuantity > 0;
            return (
              <tr key={h.stockId} data-testid="holdings-row" data-id={h.stockId}>
                <td>
                  <span data-testid="holdings-row-name">{name}</span> <span data-testid="holdings-row-code">{stockLabel(h.stockId)?.code ?? ''}</span>
                </td>
                <td className="num">
                  <Amount value={h.quantity} unit="주" testid="holdings-row-quantity" />
                </td>
                <td className="num">
                  <Amount value={h.availableQuantity} unit="주" testid="holdings-row-available" />
                  {h.availableQuantity !== h.quantity && (
                    <span className="hint" data-testid="holdings-row-held-note">
                      {h.quantity - h.availableQuantity}주 매도 주문에 묶임
                    </span>
                  )}
                </td>
                <td className="num">
                  <Amount value={h.avgPrice} testid="holdings-row-avg-price" />
                </td>
                <td className="num">{price === undefined ? '…' : <Amount value={price} testid="holdings-row-price" />}</td>
                <td className="num">{price === undefined ? '…' : <Amount value={price * h.quantity} testid="holdings-row-value" />}</td>
                <td className="num">
                  <Amount value={h.profitNLoss} signed tone testid="holdings-row-pnl" />{' '}
                  <span data-testid="holdings-row-pnl-rate">{rate}%</span>
                </td>
                <td>
                  {canMove ? (
                    <>
                      <Link to={`/order?stockId=${h.stockId}&side=sell&accountId=${accountId}`} data-testid="holdings-row-sell" aria-label={`${name} 매도`}>
                        매도
                      </Link>{' '}
                      <Link to={`/stock-transfer?accountId=${accountId}&stockId=${h.stockId}`} data-testid="holdings-row-transfer" aria-label={`${name} 이체`}>
                        이체
                      </Link>
                    </>
                  ) : (
                    <>
                      <span className="link-disabled" data-testid="holdings-row-sell" aria-disabled="true">
                        매도
                      </span>{' '}
                      <span className="link-disabled" data-testid="holdings-row-transfer" aria-disabled="true">
                        이체
                      </span>
                    </>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p className="hint" data-testid="holdings-calc-note">
        평가손익 = (현재가 − 평균단가) × 보유 수량. 실현손익은 보여 주지 않음
      </p>
    </section>
  );
}
