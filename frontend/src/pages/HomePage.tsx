// S-03 홈. 계좌 목록(C1)을 받아 합계를 화면에서 더하고, 계좌마다 미체결 주문(K7)과 최근 거래(T5)를 부름(N-06 전 임시)

import { Link } from 'react-router';
import { useQueries } from '@tanstack/react-query';
import { getOrders, getTransactions, type Order, type Transaction } from '../api/endpoints';
import { BalanceBar } from '../components/BalanceBar';
import { IconWallet } from '../components/icons';
import { Amount, LoadError, Loading, PageHead, StatusBadge } from '../components/ui';
import { activeAccounts, useAccounts, useMember } from '../hooks/queries';
import { useMemberId } from '../session/SessionProvider';
import { formatShortDateTime } from '../lib/format';
import { ACCOUNT_STATUS, TX_TYPE, tradeSide } from '../lib/labels';
import { MESSAGES } from '../lib/messages';
import { buyHoldAmount } from '../lib/fee';
import { stockName } from '../lib/stockNames';

export function HomePage() {
  const accountsQuery = useAccounts();
  const name = useMember(useMemberId()).data?.name;
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
      <PageHead title={name ? `${name}님의 자산` : '홈'} sub="서버 원장에서 읽은 값이에요. 화면은 계좌별 값을 더하기만 해요" />

      <section className="panel hero" aria-labelledby="home-total-title">
        <div className="hero-split">
          <div>
            <h2 className="hero-label" id="home-total-title">
              총 예수금
            </h2>
            <p className="hero-amount sensitive">
              <Amount value={total.balance} testid="home-total-balance" />
            </p>
            <nav className="hero-actions" aria-label="바로가기">
              <Link to="/deposit" className="button primary" data-testid="home-deposit-link">
                입금
              </Link>
              <Link to="/withdraw" className="button soft" data-testid="home-withdraw-link">
                출금
              </Link>
              <Link to="/stocks" className="button soft" data-testid="home-order-link">
                매수 주문
              </Link>
              <Link to="/accounts/new" className="button" data-testid="home-open-link">
                계좌 개설
              </Link>
            </nav>
          </div>
          <div>
            <dl className="summary">
              <div className="kv">
                <dt>
                  <span className="swatch swatch-held" />
                  주문에 묶임
                </dt>
                <dd className="sensitive">
                  <Amount value={total.held} testid="home-total-held" />
                </dd>
              </div>
              <div className="kv">
                <dt>주문 가능</dt>
                <dd className="sensitive">
                  <Amount value={total.orderable} testid="home-total-orderable" />
                </dd>
              </div>
              <div className="kv">
                <dt>
                  <span className="swatch swatch-avail" />
                  출금 가능
                </dt>
                <dd className="sensitive">
                  <Amount value={total.orderable} testid="home-total-withdrawable" />
                </dd>
              </div>
            </dl>
            <BalanceBar testid="home-bar" balance={total.balance} held={total.held} />
            <p className="hint" data-testid="home-withdrawable-note">
              {MESSAGES.withdrawableNote}
            </p>
          </div>
        </div>
      </section>

      <section className="panel" aria-labelledby="home-accounts-title">
        <div className="section-head">
          <h2 id="home-accounts-title">내 계좌 {accounts.length}개</h2>
          <Link to="/accounts" className="button ghost small">
            전체 보기
          </Link>
        </div>
        {accounts.length === 0 ? (
          <div className="empty" data-testid="home-empty">
            <p>사용 중인 계좌가 없어요</p>
            <Link to="/accounts/new" className="button primary">
              계좌 개설하기
            </Link>
          </div>
        ) : (
          <ul className="rows">
            {accounts.map((a) => (
              <li key={a.id}>
                <Link to={`/accounts/${a.id}`} className="row account-card" data-testid="home-account-card" data-id={a.id}>
                  <span className="row-icon" aria-hidden="true">
                    <IconWallet />
                  </span>
                  <span className="row-main">
                    <span className="row-title">
                      <span data-testid="home-account-card-nickname">{a.nickname || '별칭 없음'}</span>
                      <StatusBadge testid="home-account-card-status" state={a.status} label={ACCOUNT_STATUS[a.status]} />
                    </span>
                    <span className="row-sub">
                      <span data-testid="home-account-card-number">{a.accountNumber}</span>
                    </span>
                  </span>
                  <span className="row-side sensitive">
                    <Amount value={a.balance} testid="home-account-card-balance" />
                  </span>
                  <span className="row-meta">
                    <span>
                      묶임 <Amount value={a.heldAmount} testid="home-account-card-held" />
                    </span>
                    <span>
                      주문 가능 <Amount value={a.availableBalance} testid="home-account-card-orderable" />
                    </span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="grid-2">
        <section className="panel" aria-labelledby="home-pending-title">
          <div className="section-head">
            <h2 id="home-pending-title">
              미체결 주문 <span data-testid="home-pending-count">{pending.length}</span>건
            </h2>
            <Link to="/orders" className="button ghost small">
              주문 내역
            </Link>
          </div>
          {pending.length === 0 ? (
            <p className="empty" data-testid="home-pending-empty">
              미체결 주문이 없어요
            </p>
          ) : (
            <ul className="rows">
              {pending.map((o) => (
                <li key={o.id} className="row" data-testid="home-pending-row" data-id={o.id}>
                  <span className="row-main">
                    <span className="row-title">{stockName(o.stockId)}</span>
                    <span className="row-sub">
                      <span>{tradeSide(o.type, o.quantity)}</span>
                      <span>
                        <Amount value={o.price} /> × <Amount value={o.quantity} unit="주" />
                      </span>
                    </span>
                  </span>
                  <span className="row-side">
                    <span className="hint">묶인 것</span>
                    <span className="row-amount">
                      {o.type === 'BUY' ? <Amount value={buyHoldAmount(o.price, o.quantity)} /> : <Amount value={o.quantity} unit="주" />}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="panel" aria-labelledby="home-recent-title">
          <h2 id="home-recent-title">최근 거래</h2>
          {recent.length === 0 ? (
            <div className="empty" data-testid="home-recent-empty">
              <p>아직 거래가 없어요. 입금부터 해 보세요</p>
              <Link to="/deposit" className="button soft">
                입금하기
              </Link>
            </div>
          ) : (
            <ul className="rows">
              {recent.map((t, i) => (
                <li key={i} className="row" data-testid="home-recent-row">
                  <span className="row-main">
                    <span className="row-title">{TX_TYPE[t.type]}</span>
                    <span className="row-sub">
                      <span className="nowrap">{formatShortDateTime(t.transactedAt)}</span>
                      <span>계좌 끝 {numberOf(t.accountId).slice(-4)}</span>
                    </span>
                  </span>
                  <span className="row-side row-amount">
                    <Amount value={t.amount} signed tone />
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
