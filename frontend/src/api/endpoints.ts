// 백엔드 API 목록(설계 3.2.1의 A·M·C·T·K 번호). 응답 모양은 백엔드 DTO 그대로

import { createApiClient } from '../lib/api';
import { createTokenStore, type TokenPair } from '../lib/tokens';
import { emitSessionExpired } from '../session/events';

export const tokenStore = createTokenStore(window.localStorage);

export const api = createApiClient({
  fetch: (input, init) => window.fetch(input, init),
  tokens: tokenStore,
  locks: typeof navigator !== 'undefined' && 'locks' in navigator ? navigator.locks : null,
  onSessionExpired: emitSessionExpired,
});

export type AccountStatus = 'EXISTS' | 'PENDING' | 'DELETED';
export type TransactionType = 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER_OUT' | 'TRANSFER_IN' | 'STOCK_BUY' | 'STOCK_SELL' | 'FEE';
export type OrderStatus = 'PENDING' | 'FILLED' | 'CANCELLED';
export type TradeType = 'BUY' | 'SELL' | 'TRANSFER';
export type VerificationPurpose = 'SIGN_UP' | 'PHONE_CHANGE' | 'ACCOUNT_UNLOCK';

export interface Account {
  id: number;
  accountNumber: string;
  nickname: string | null;
  balance: number;
  heldAmount: number;
  availableBalance: number;
  status: AccountStatus;
  createdAt: string;
}

export interface Member {
  id: number;
  name: string;
  phoneNumber: string;
  role: string;
  createdAt: string;
}

export interface Transaction {
  accountId: number;
  type: TransactionType;
  name: string | null;
  amount: number;
  balanceAfter: number;
  memo: string | null;
  transactedAt: string;
}

export interface Page<T> {
  content: T[];
  number: number;
  size: number;
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
}

export interface Stock {
  id: number;
  code: string;
  name: string;
  currentPrice: number;
}

export interface Order {
  id: number;
  accountId: number;
  stockId: number;
  stockCode: string;
  stockName: string;
  type: TradeType;
  status: OrderStatus;
  price: number;
  quantity: number;
  filledPrice: number | null;
  createdAt: string;
  tradeAt: string | null;
}

export interface StockBalance {
  stockId: number;
  stockCode: string;
  stockName: string;
  quantity: number;
  availableQuantity: number;
  avgPrice: number;
  currentPrice: number;
  /** 현재가 × 수량. 서버가 계산한 표시용 값이며 원장 값이 아님 */
  evaluationAmount: number;
  profitNLoss: number;
}

/** 관리자 종목 한 줄(N-03). 종목과 그 종목에 걸린 미체결 주문 수 */
export interface AdminStock extends Stock {
  pendingOrderCount: number;
}

/** 시세 갱신(D2) 결과. 새 시세로 미체결 주문을 체결해 본 결과가 같이 옴(N-09) */
export interface PriceUpdateResult extends Stock {
  filledOrderIds: number[];
  failedOrderIds: number[];
  remainingCount: number;
  filledCount: number;
  failedCount: number;
}

export interface AccountSearch {
  accountId: number;
  accountNumber: string;
  ownerName: string;
  accountNickname: string | null;
  balance: number | null;
  status: AccountStatus | null;
}

const idem = (key: string) => ({ 'Idempotency-Key': key });

function query(params: Record<string, string | number | null | undefined>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== null && v !== undefined && v !== '') q.set(k, String(v));
  const s = q.toString();
  return s ? `?${s}` : '';
}

// 인증(A)
export const sendCode = (phoneNumber: string) =>
  api.request<string>('/api/auth/send-code', { method: 'POST', body: { phoneNumber }, auth: false });
export const verifyPhone = (phoneNumber: string, verificationCode: string, purpose: VerificationPurpose) =>
  api.request<boolean>('/api/auth/verify-phone', { method: 'POST', body: { phoneNumber, verificationCode, purpose }, auth: false });
export const signUp = (body: { name: string; phoneNumber: string; loginPassword: string; initialAccountPassword: string }) =>
  api.request<boolean>('/api/auth/sign-up', { method: 'POST', body, auth: false });
export const login = (phoneNumber: string, password: string) =>
  api.request<TokenPair>('/api/auth/login', { method: 'POST', body: { phoneNumber, password }, auth: false });
export const logout = () => api.request<boolean>('/api/auth/logout', { method: 'POST' });
export const leave = () => api.request<boolean>('/api/auth/sign-out', { method: 'DELETE' });

// 회원(M)
export const getMember = (memberId: number) => api.request<Member>(`/api/member/${memberId}`);
export const updateMember = (
  memberId: number,
  body: { name: string; phoneNumber: string; password: string; currentPassword: string },
) => api.request<Member>(`/api/member/${memberId}`, { method: 'PATCH', body });

// 계좌(C)
export const getAccounts = () => api.request<Account[]>('/api/accounts');
export const getAccount = (id: number) => api.request<Account>(`/api/accounts/${id}`);
export const openAccount = (body: { nickname: string | null; password: string }) =>
  api.request<Account>('/api/accounts/open', { method: 'POST', body });
export const updateAccount = (id: number, body: { nickname?: string; currentPassword: string; newPassword?: string }) =>
  api.request<Account>(`/api/accounts/${id}`, { method: 'PATCH', body });
export const unlockAccount = (id: number) => api.request<Account>(`/api/accounts/${id}/unlock`, { method: 'POST' });
export const closeAccount = (id: number) => api.request<boolean>(`/api/accounts/${id}`, { method: 'DELETE' });
export const searchAccount = (accountNumber: string) =>
  api.request<AccountSearch>(`/api/accounts/search/${encodeURIComponent(accountNumber)}`);

// 거래(T)
export const deposit = (key: string, body: { accountId: number; amount: number; memo: string | null }) =>
  api.request<string>('/api/transactions/deposit', { method: 'POST', body, headers: idem(key) });
export const withdraw = (
  key: string,
  body: { accountId: number; amount: number; memo: string | null; accountPassword: string },
) => api.request<string>('/api/transactions/withdrawal', { method: 'POST', body, headers: idem(key) });
export const transfer = (
  key: string,
  fromId: number,
  toId: number,
  body: { amount: number; memo: string | null; accountPassword: string },
) => api.request<string>(`/api/transactions/${fromId}/transfer/${toId}`, { method: 'POST', body, headers: idem(key) });
export const getTransactions = (
  accountId: number,
  params: { type?: TransactionType | ''; from?: string; to?: string; page: number; size: number },
) => api.request<Page<Transaction>>(`/api/transactions/${accountId}/history${query(params)}`);

// 주식(K)
export const searchStocks = (keyword: string) => api.request<Stock[]>(`/api/stocks${query({ keyword })}`);
export const getStockInfo = (stockId: number) => api.request<Stock>(`/api/stocks/${stockId}/info`);
export const placeOrder = (
  key: string,
  side: 'buy' | 'sell',
  body: { accountId: number; stockId: number; price: number; quantity: number; accountPassword: string },
) => api.request<Order>(`/api/stocks/${side}`, { method: 'POST', body, headers: idem(key) });
export const cancelOrder = (accountId: number, orderId: number) =>
  api.request<Order>(`/api/stocks/${accountId}/orders/${orderId}`, { method: 'DELETE' });
export const transferStock = (
  key: string,
  body: { senderAccountId: number; receiverAccountId: number; stockId: number; quantity: number; accountPassword: string },
) => api.request<string>('/api/stocks/transfer', { method: 'POST', body, headers: idem(key) });
export const getHoldings = (accountId: number) => api.request<StockBalance[]>(`/api/stocks/${accountId}/balance`);
export const getOrders = (accountId: number, params: { status?: OrderStatus | ''; year?: number; month?: number }) =>
  api.request<Order[]>(`/api/stocks/${accountId}/history${query(params)}`);

// 관리자(D·N)
export const getAdminStocks = (page: number, size: number) => api.request<Page<AdminStock>>(`/admin/stocks${query({ page, size })}`);
export const getAdminStock = (stockId: number) => api.request<AdminStock>(`/admin/stocks/${stockId}`);
export const getStockOrders = (stockId: number, status: OrderStatus) =>
  api.request<Order[]>(`/admin/stocks/${stockId}/orders${query({ status })}`);
export const updateStockPrice = (stockId: number, price: number) =>
  api.request<PriceUpdateResult>(`/admin/stocks/${stockId}/price`, { method: 'PUT', body: { price } });
