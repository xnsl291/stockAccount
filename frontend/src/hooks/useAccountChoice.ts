// 입금·출금·송금·주문·주식 이체의 "계좌 고르기". 주소의 accountId를 먼저 쓰고, 없으면 첫 계좌

import { useSearchParams } from 'react-router';
import type { Account } from '../api/endpoints';
import { accountLabel } from '../lib/labels';
import { activeAccounts, useAccounts } from './queries';

export function useAccountChoice() {
  const [params, setParams] = useSearchParams();
  const query = useAccounts();
  const accounts = activeAccounts(query.data);
  const fromUrl = Number(params.get('accountId'));
  const selectedId = accounts.some((a) => a.id === fromUrl) ? fromUrl : (accounts[0]?.id ?? null);

  return {
    query,
    accounts,
    selectedId,
    select(id: number) {
      const next = new URLSearchParams(params);
      next.set('accountId', String(id));
      setParams(next, { replace: true });
    },
    options: (disablePending = false) =>
      accounts.map((a: Account) => ({
        value: String(a.id),
        label: a.status === 'PENDING' ? `${accountLabel(a)} (거래정지)` : accountLabel(a),
        disabled: disablePending && a.status === 'PENDING',
      })),
  };
}
