// S-16·S-17 단독 화면(머리글 메뉴에서 들어옴). 계좌를 고르는 칸만 더하고 내용은 S-06의 탭과 같음(testid도 같음)

import { useSearchParams } from 'react-router';
import { LoadError, Loading, SelectField } from '../components/ui';
import { activeAccounts, useAccounts } from '../hooks/queries';
import { accountLabel } from '../lib/labels';
import { HoldingsTab } from './tabs/HoldingsTab';
import { OrdersTab } from './tabs/OrdersTab';

function WithAccount({ title, prefix, children }: { title: string; prefix: string; children: (accountId: number) => JSX.Element }) {
  const [params, setParams] = useSearchParams();
  const query = useAccounts();
  if (query.isPending) return <Loading />;
  if (query.isError) return <LoadError error={query.error} onRetry={() => void query.refetch()} />;
  const accounts = activeAccounts(query.data);
  const selected = Number(params.get('accountId')) || accounts[0]?.id || 0;

  return (
    <>
      <h1>{title}</h1>
      <SelectField
        label="계좌"
        testid={`${prefix}-account-select`}
        value={String(selected)}
        onChange={(v) => setParams({ accountId: v }, { replace: true })}
        options={accounts.map((a) => ({ value: String(a.id), label: accountLabel(a) }))}
      />
      {selected ? (
        children(selected)
      ) : (
        <p className="empty" data-testid={`${prefix}-empty`}>
          사용 중인 계좌가 없어요
        </p>
      )}
    </>
  );
}

export function OrdersPage() {
  return (
    <WithAccount title="주문 내역" prefix="orders">
      {(id) => <OrdersTab key={id} accountId={id} />}
    </WithAccount>
  );
}

export function HoldingsPage() {
  return (
    <WithAccount title="보유 주식" prefix="holdings">
      {(id) => <HoldingsTab key={id} accountId={id} />}
    </WithAccount>
  );
}
