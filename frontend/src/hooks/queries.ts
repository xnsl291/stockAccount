// 서버 조회. 서버 값은 오래 들고 있지 않고(staleTime 0), 돈이 움직인 뒤에는 모두 다시 읽음(설계 3.5.2)

import { useQueries, useQuery } from '@tanstack/react-query';
import {
  getAccount,
  getAccounts,
  getHoldings,
  getMember,
  getOrders,
  getStockPrice,
  getTransactions,
  type Account,
  type OrderStatus,
  type TransactionType,
} from '../api/endpoints';

export const keys = {
  accounts: ['accounts'] as const,
  account: (id: number) => ['account', id] as const,
  member: (id: number) => ['member', id] as const,
  price: (stockId: number) => ['price', stockId] as const,
};

/** 해지 계좌(DELETED)는 C1이 같이 돌려주므로 화면에서 거름 */
export function activeAccounts(list: Account[] | undefined): Account[] {
  return (list ?? []).filter((a) => a.status !== 'DELETED');
}

export function useAccounts() {
  return useQuery({ queryKey: keys.accounts, queryFn: getAccounts });
}

export function useAccount(id: number | null) {
  return useQuery({ queryKey: keys.account(id ?? 0), queryFn: () => getAccount(id!), enabled: id !== null && id > 0 });
}

export function useMember(memberId: number) {
  return useQuery({ queryKey: keys.member(memberId), queryFn: () => getMember(memberId) });
}

export function useTransactions(
  accountId: number,
  params: { type: TransactionType | ''; from: string; to: string; page: number; size: number },
) {
  return useQuery({
    queryKey: ['tx', accountId, params],
    queryFn: () => getTransactions(accountId, params),
    enabled: accountId > 0,
  });
}

export function useOrders(accountId: number, params: { status: OrderStatus | ''; year?: number; month?: number }) {
  return useQuery({
    queryKey: ['orders', accountId, params],
    queryFn: () => getOrders(accountId, params),
    enabled: accountId > 0,
  });
}

export function useHoldings(accountId: number | null) {
  return useQuery({
    queryKey: ['holdings', accountId],
    queryFn: () => getHoldings(accountId!),
    enabled: accountId !== null && accountId > 0,
  });
}

export function useStockPrice(stockId: number | null) {
  return useQuery({ queryKey: keys.price(stockId ?? 0), queryFn: () => getStockPrice(stockId!), enabled: !!stockId });
}

/** 종목마다 현재가(K2)를 따로 부름. 보유 주식 응답에 현재가가 없어서임(N-05 전 임시) */
export function useStockPrices(stockIds: number[]) {
  return useQueries({
    queries: stockIds.map((id) => ({ queryKey: keys.price(id), queryFn: () => getStockPrice(id) })),
    combine: (results) => {
      const prices = new Map<number, number>();
      results.forEach((r, i) => {
        if (r.data !== undefined) prices.set(stockIds[i], r.data);
      });
      return prices;
    },
  });
}

/** 계좌마다 보유 주식(K6). 종목 상세에서 내 계좌별 보유를 모을 때 씀 */
export function useHoldingsOfAccounts(accountIds: number[]) {
  return useQueries({
    queries: accountIds.map((id) => ({ queryKey: ['holdings', id], queryFn: () => getHoldings(id) })),
  });
}
