// S-04 계좌 목록. 해지 계좌는 C1이 같이 돌려주므로 화면에서 거름

import { Link, useNavigate } from 'react-router';
import { Amount, LoadError, Loading, StatusBadge } from '../components/ui';
import { activeAccounts, useAccounts } from '../hooks/queries';
import { ACCOUNT_STATUS } from '../lib/labels';

export function AccountsPage() {
  const query = useAccounts();
  const navigate = useNavigate();
  if (query.isPending) return <Loading />;
  if (query.isError) return <LoadError error={query.error} onRetry={() => void query.refetch()} />;
  const accounts = activeAccounts(query.data);

  return (
    <>
      <h1>계좌</h1>
      <Link to="/accounts/new" data-testid="accounts-open-link">
        계좌 개설
      </Link>
      {accounts.length === 0 ? (
        <p className="empty" data-testid="accounts-empty">
          사용 중인 계좌가 없어요
        </p>
      ) : (
        <table className="ledger">
          <thead>
            <tr>
              <th>계좌번호</th>
              <th>별칭</th>
              <th>상태</th>
              <th className="num">잔액</th>
              <th className="num">주문 가능</th>
            </tr>
          </thead>
          <tbody>
            {accounts.map((a) => (
              <tr
                key={a.id}
                data-testid="accounts-row"
                data-id={a.id}
                aria-label={`계좌 ${a.accountNumber}`}
                tabIndex={0}
                className="clickable"
                onClick={() => navigate(`/accounts/${a.id}`)}
                onKeyDown={(e) => e.key === 'Enter' && navigate(`/accounts/${a.id}`)}
              >
                <td data-testid="accounts-row-number">{a.accountNumber}</td>
                <td data-testid="accounts-row-nickname">{a.nickname || '별칭 없음'}</td>
                <td>
                  <StatusBadge testid="accounts-row-status" state={a.status} label={ACCOUNT_STATUS[a.status]} />
                </td>
                <td className="num">
                  <Amount value={a.balance} testid="accounts-row-balance" />
                </td>
                <td className="num">
                  <Amount value={a.availableBalance} testid="accounts-row-orderable" />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
