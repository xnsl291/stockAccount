// S-03 홈. 계좌 목록(C1)을 받아 합계를 화면에서 더하고, 계좌마다 미체결 주문(K7)과 최근 거래(T5)를 부름(N-06 전 임시)

import { Link } from 'react-router';
import { useQueries } from '@tanstack/react-query';
import { getOrders, getTransactions, type Order, type Transaction } from '../api/endpoints';
import { BalanceBar } from '../components/BalanceBar';
import { Amount, LoadError, Loading, StatusBadge } from '../components/ui';
import { activeAccounts, useAccounts } from '../hooks/queries';
import { formatShortDateTime } from '../lib/format';
import { ACCOUNT_STATUS, TX_TYPE, tradeSide } from '../lib/labels';
import { MESSAGES } from '../lib/messages';
import { buyHoldAmount } from '../lib/fee';
import { stockName } from '../lib/stockNames';

export function HomePage() {
  const accountsQuery = useAccounts();
  const accounts = activeAccounts(accountsQuery.data);

  const pending = useQueries({
    queries: accounts.map((a) => ({
      queryKey: ['orders', a.id, { status: 'PENDING' }],
      queryFn: () => getOrders(a.id, { status: 'PENDING' }),
    })),
    combine: (rs) => rs.flatMap((r) => r.data ?? []) as Order[],
  });
  const recent = useQueries({
    queries: accounts.map((a) => ({
      queryKey: ['tx', a.id, { type: '', from: '', to: '', page: 0, size: 5 }],
      queryFn: () => getTransactions(a.id, { page: 0, size: 5 }),
    })),
    combine: (rs) =>
      rs
        .flatMap((r) => r.data?.content ?? [])
        .sort((x, y) => y.transactedAt.localeCompare(x.transactedAt))
        .slice(0, 5) as Transaction[],
  });

  if (accountsQuery.isPending) return <Loading />;
  if (accountsQuery.isError) return <LoadError error={accountsQuery.error} onRetry={() => void accountsQuery.refetch()} />;

  const total = accounts.reduce(
    (s, a) => ({ balance: s.balance + a.balance, held: s.held + a.heldAmount, orderable: s.orderable + a.availableBalance }),
    { balance: 0, held: 0, orderable: 0 },
  );
  const numberOf = (id: number) => accounts.find((a) => a.id === id)?.accountNumber ?? '';

  return (
    <>
      <h1>홈</h1>
      <section className="statement">
        <h2>예수금 명세</h2>
        <dl className="summary">
          <div className="kv">
            <dt>총 예수금</dt>
            <dd>
              <Amount value={total.balance} testid="home-total-balance" />
            </dd>
          </div>
          <div className="kv">
            <dt>주문에 묶임</dt>
            <dd>
              <Amount value={total.held} testid="home-total-held" />
            </dd>
          </div>
          <div className="kv">
            <dt>주문 가능</dt>
            <dd>
              <Amount value={total.orderable} testid="home-total-orderable" />
            </dd>
          </div>
          <div className="kv">
            <dt>출금 가능</dt>
            <dd>
              <Amount value={total.orderable} testid="home-total-withdrawable" />
            </dd>
          </div>
        </dl>
        <p className="hint" data-testid="home-withdrawable-note">
          {MESSAGES.withdrawableNote}
        </p>
        <BalanceBar testid="home-bar" balance={total.balance} held={total.held} />
        <p className="hint">서버 원장에서 읽은 값. 화면은 잔액을 계산하지 않음(합계만 더함)</p>
      </section>

      <nav className="actions" aria-label="바로가기">
        <Link to="/deposit" data-testid="home-deposit-link">
          입금
        </Link>
        <Link to="/withdraw" data-testid="home-withdraw-link">
          출금
        </Link>
        <Link to="/stocks" data-testid="home-order-link">
          매수 주문
        </Link>
        <Link to="/accounts/new" data-testid="home-open-link">
          계좌 개설
        </Link>
      </nav>

      <section>
        <h2>계좌</h2>
        {accounts.length === 0 ? (
          <p className="empty" data-testid="home-empty">
            사용 중인 계좌가 없어요
          </p>
        ) : (
          <div className="cards">
            {accounts.map((a) => (
              <Link key={a.id} to={`/accounts/${a.id}`} className="card" data-testid="home-account-card" data-id={a.id}>
                <span data-testid="home-account-card-number">{a.accountNumber}</span>
                <span data-testid="home-account-card-nickname">{a.nickname || '별칭 없음'}</span>
                <StatusBadge testid="home-account-card-status" state={a.status} label={ACCOUNT_STATUS[a.status]} />
                <dl className="summary">
                  <div className="kv">
                    <dt>잔액</dt>
                    <dd>
                      <Amount value={a.balance} testid="home-account-card-balance" />
                    </dd>
                  </div>
                  <div className="kv">
                    <dt>묶임</dt>
                    <dd>
                      <Amount value={a.heldAmount} testid="home-account-card-held" />
                    </dd>
                  </div>
                  <div className="kv">
                    <dt>주문 가능</dt>
                    <dd>
                      <Amount value={a.availableBalance} testid="home-account-card-orderable" />
                    </dd>
                  </div>
                </dl>
              </Link>
            ))}
          </div>
        )}
      </section>

      <section>
        <h2>
          미체결 주문 <span data-testid="home-pending-count">{pending.length}</span>건
        </h2>
        {pending.length === 0 ? (
          <p className="empty" data-testid="home-pending-empty">
            미체결 주문이 없어요
          </p>
        ) : (
          <ul className="list">
            {pending.map((o) => (
              <li key={o.id} data-testid="home-pending-row" data-id={o.id}>
                {stockName(o.stockId)} · {tradeSide(o.type, o.quantity)} · <Amount value={o.price} /> × <Amount value={o.quantity} unit="주" /> · 묶인 것{' '}
                {o.type === 'BUY' ? <Amount value={buyHoldAmount(o.price, o.quantity)} /> : <Amount value={o.quantity} unit="주" />}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2>최근 거래</h2>
        {recent.length === 0 ? (
          <p className="empty" data-testid="home-recent-empty">
            아직 거래가 없어요. 입금부터 해 보세요
          </p>
        ) : (
          <ul className="list">
            {recent.map((t, i) => (
              <li key={i} data-testid="home-recent-row">
                {formatShortDateTime(t.transactedAt)} · {TX_TYPE[t.type]} · {numberOf(t.accountId)} · <Amount value={t.amount} signed tone />
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
