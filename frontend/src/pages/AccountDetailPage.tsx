// S-06 계좌 상세. 아래 탭 세 개(거래내역 S-12, 보유 주식 S-17, 주문 내역 S-16)

import { Link, useParams, useSearchParams } from 'react-router';
import { BalanceBar } from '../components/BalanceBar';
import { LockedBanner, unlockPath } from '../components/Flow';
import { Amount, LoadError, Loading, NotFound, StatusBadge } from '../components/ui';
import { useAccount } from '../hooks/queries';
import { formatDate } from '../lib/format';
import { ACCOUNT_STATUS } from '../lib/labels';
import { MESSAGES } from '../lib/messages';
import { HoldingsTab } from './tabs/HoldingsTab';
import { OrdersTab } from './tabs/OrdersTab';
import { TxTab } from './tabs/TxTab';

const TABS = [
  { key: 'tx', label: '거래내역', testid: 'account-tab-tx' },
  { key: 'holdings', label: '보유 주식', testid: 'account-tab-holdings' },
  { key: 'orders', label: '주문 내역', testid: 'account-tab-orders' },
] as const;

export function AccountDetailPage() {
  const id = Number(useParams().id);
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') ?? 'tx') as (typeof TABS)[number]['key'];
  const query = useAccount(Number.isInteger(id) && id > 0 ? id : null);

  if (!Number.isInteger(id) || id <= 0) return <NotFound message="이 계좌를 찾을 수 없어요" />;
  if (query.isPending) return <Loading />;
  if (query.isError) return <LoadError error={query.error} onRetry={() => void query.refetch()} />;
  const a = query.data;
  if (a.status === 'DELETED') return <NotFound message="이 계좌를 찾을 수 없어요" />;
  const locked = a.status === 'PENDING';
  const here = `/accounts/${id}`;

  return (
    <>
      <h1>
        <span data-testid="account-nickname">{a.nickname || '별칭 없음'}</span>{' '}
        <span data-testid="account-number">{a.accountNumber}</span>
      </h1>
      <p>
        <StatusBadge testid="account-status" state={a.status} label={ACCOUNT_STATUS[a.status]} /> 개설일{' '}
        <span data-testid="account-created-at">{formatDate(a.createdAt)}</span>
      </p>
      {locked && <LockedBanner testid="account-locked-banner" unlockTo={unlockPath(id, here)} linkTestid="account-unlock-link" />}

      <section className="statement">
        <dl className="summary">
          <div className="kv">
            <dt>잔액</dt>
            <dd>
              <Amount value={a.balance} testid="account-balance" />
            </dd>
          </div>
          <div className="kv">
            <dt>주문에 묶임</dt>
            <dd>
              <Amount value={a.heldAmount} testid="account-held" />
            </dd>
          </div>
          <div className="kv">
            <dt>주문 가능</dt>
            <dd>
              <Amount value={a.availableBalance} testid="account-orderable" />
            </dd>
          </div>
          <div className="kv">
            <dt>출금 가능</dt>
            <dd>
              <Amount value={a.availableBalance} testid="account-withdrawable" />
            </dd>
          </div>
        </dl>
        <p className="hint" data-testid="account-withdrawable-note">
          {MESSAGES.withdrawableNote}
        </p>
        <p className="proof" data-testid="account-proof">
          잔액 <Amount value={a.balance} /> − 주문에 묶임 <Amount value={a.heldAmount} /> = 주문 가능 <Amount value={a.availableBalance} />
        </p>
        <BalanceBar testid="account-bar" balance={a.balance} held={a.heldAmount} />
      </section>

      <nav className="actions">
        {locked ? (
          <>
            <span className="link-disabled" data-testid="account-deposit-link" aria-disabled="true">
              입금
            </span>
            <span className="link-disabled" data-testid="account-withdraw-link" aria-disabled="true">
              출금
            </span>
            <span className="link-disabled" data-testid="account-transfer-link" aria-disabled="true">
              송금
            </span>
          </>
        ) : (
          <>
            <Link to={`/deposit?accountId=${id}`} data-testid="account-deposit-link">
              입금
            </Link>
            <Link to={`/withdraw?accountId=${id}`} data-testid="account-withdraw-link">
              출금
            </Link>
            <Link to={`/transfer?accountId=${id}`} data-testid="account-transfer-link">
              송금
            </Link>
          </>
        )}
        <Link to={`/accounts/${id}/settings`} data-testid="account-settings-link">
          계좌 설정
        </Link>
      </nav>

      <div role="tablist" className="tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            data-testid={t.testid}
            onClick={() => setParams({ tab: t.key }, { replace: true })}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel">
        {tab === 'tx' && <TxTab accountId={id} />}
        {tab === 'holdings' && <HoldingsTab accountId={id} />}
        {tab === 'orders' && <OrdersTab accountId={id} />}
      </div>
    </>
  );
}
