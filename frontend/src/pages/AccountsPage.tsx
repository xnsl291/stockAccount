// S-04 계좌 목록. 해지 계좌는 C1이 같이 돌려주므로 화면에서 거름
// 줄 하나가 계좌 상세로 가는 링크라 키보드(Tab·Enter)와 화면 낭독기에서 그대로 동작함

import { Link } from 'react-router';
import { IconChevron, IconWallet } from '../components/icons';
import { Amount, LoadError, Loading, PageHead, StatusBadge } from '../components/ui';
import { activeAccounts, useAccounts } from '../hooks/queries';
import { ACCOUNT_STATUS } from '../lib/labels';

export function AccountsPage() {
  const query = useAccounts();
  if (query.isPending) return <Loading />;
  if (query.isError) return <LoadError error={query.error} onRetry={() => void query.refetch()} />;
  const accounts = activeAccounts(query.data);

  return (
    <>
      <PageHead title="계좌" sub="계좌를 누르면 잔액 구성과 거래내역을 볼 수 있어요" />
      <section className="panel">
        <div className="section-head">
          <h2>사용 중인 계좌 {accounts.length}개</h2>
          <Link to="/accounts/new" className="button soft small" data-testid="accounts-open-link">
            계좌 개설
          </Link>
        </div>
        {accounts.length === 0 ? (
          <p className="empty" data-testid="accounts-empty">
            사용 중인 계좌가 없어요
          </p>
        ) : (
          <ul className="rows">
            {accounts.map((a) => (
              <li key={a.id}>
                <Link to={`/accounts/${a.id}`} className="row" data-testid="accounts-row" data-id={a.id} aria-label={`계좌 ${a.accountNumber}`}>
                  <span className="row-icon" aria-hidden="true">
                    <IconWallet />
                  </span>
                  <span className="row-main">
                    <span className="row-title">
                      <span data-testid="accounts-row-nickname">{a.nickname || '별칭 없음'}</span>
                      <StatusBadge testid="accounts-row-status" state={a.status} label={ACCOUNT_STATUS[a.status]} />
                    </span>
                    <span className="row-sub">
                      <span data-testid="accounts-row-number">{a.accountNumber}</span>
                    </span>
                  </span>
                  <span className="row-side">
                    <span className="row-amount sensitive">
                      <Amount value={a.balance} testid="accounts-row-balance" />
                    </span>
                    <span className="hint">
                      주문 가능 <Amount value={a.availableBalance} testid="accounts-row-orderable" />
                    </span>
                  </span>
                  <IconChevron className="row-chevron" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
