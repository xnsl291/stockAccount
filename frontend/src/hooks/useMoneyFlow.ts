// 돈·주식이 움직이는 작업의 "입력 → 확인 → 계좌 비밀번호 → 결과" 흐름(설계 3.5.4, 3.10.1)
// 입금·출금·송금·주문·주식 이체가 같이 씀. 중복 요청 방지 키 규칙(3.5.5)과 비밀번호 5회 오류 처리(3.12.2)를 여기 한곳에 둠

import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { getAccount, type Account } from '../api/endpoints';
import { ApiError } from '../lib/api';
import { IdempotencyKeyHolder } from '../lib/idempotency';
import { errorMessage, isRetryable, MESSAGES, type ErrorContext } from '../lib/messages';
import { keys } from './queries';

export type FlowStep = 'input' | 'confirm' | 'password' | 'result';

export interface SendError {
  message: string;
  retryable: boolean;
}

export interface MoneyFlow<R> {
  step: FlowStep;
  setStep(step: FlowStep): void;
  password: string;
  setPassword(v: string): void;
  submitting: boolean;
  pwError: string | null;
  /** 계좌가 거래정지됨. 보내기를 잠그고 잠금 해제 링크를 보임 */
  locked: boolean;
  sendError: SendError | null;
  result: { data: R; account: Account | null } | null;
  submit(): Promise<void>;
  reset(): void;
}

interface Options<R> {
  /** 결과에서 다시 읽을 계좌(보내는 계좌) */
  accountId: number | null;
  /** 입력값 전체(메모 포함)를 이은 글자. 하나라도 바뀌면 키를 버림 */
  inputSignature: string;
  errorContext: ErrorContext;
  send: (key: string, password: string) => Promise<R>;
}

export function useMoneyFlow<R>({ accountId, inputSignature, errorContext, send }: Options<R>): MoneyFlow<R> {
  const queryClient = useQueryClient();
  const holder = useRef(new IdempotencyKeyHolder());
  const busy = useRef(false);
  const [step, setStep] = useState<FlowStep>('input');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [pwError, setPwError] = useState<string | null>(null);
  const [locked, setLocked] = useState(false);
  const [sendError, setSendError] = useState<SendError | null>(null);
  const [result, setResult] = useState<MoneyFlow<R>['result']>(null);

  // 입력이 하나라도 바뀌면 새 작업 → 키를 버림. 화면을 떠날 때도 버림
  useEffect(() => holder.current.discard(), [inputSignature]);
  useEffect(() => () => holder.current.discard(), []);

  async function readAccount(): Promise<Account | null> {
    if (!accountId) return null;
    try {
      return await queryClient.fetchQuery({ queryKey: keys.account(accountId), queryFn: () => getAccount(accountId), staleTime: 0 });
    } catch {
      return null;
    }
  }

  async function submit() {
    if (busy.current || locked) return; // 빠른 두 번 누름: 응답 전에는 다시 보내지 않음
    busy.current = true;
    setSubmitting(true);
    setSendError(null);
    setPwError(null);
    try {
      let data: R;
      try {
        data = await holder.current.run((key) => send(key, password));
      } catch (error) {
        await handleFailure(error);
        return;
      }
      // 성공 응답이 문자열뿐이라 바뀐 잔액은 계좌를 다시 읽어 보여 줌. 화면이 잔액을 계산하지 않음
      const account = await readAccount();
      void queryClient.invalidateQueries();
      setPassword('');
      setResult({ data, account });
      setStep('result');
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  async function handleFailure(error: unknown) {
    const code = error instanceof ApiError ? error.code : '';
    if (code === 'UNMATCHED_PASSWORD' || code === 'PENDING_ACCOUNT') {
      setPassword('');
      // 5번째로 틀린 요청도 UNMATCHED_PASSWORD로 옴. 계좌를 다시 읽어 정지됐는지 확인함(사용자 결정)
      const account = await readAccount();
      if (code === 'PENDING_ACCOUNT' || account?.status === 'PENDING') {
        setLocked(true);
        setPwError(code === 'PENDING_ACCOUNT' ? errorMessage(error, errorContext) : MESSAGES.accountLocked);
      } else {
        setPwError(errorMessage(error, errorContext));
      }
      return;
    }
    if (code === 'EXCEED_BALANCE' || code === 'INSUFFICIENT_STOCK') void queryClient.invalidateQueries();
    setSendError({ message: errorMessage(error, errorContext), retryable: isRetryable(error) });
  }

  function reset() {
    holder.current.discard();
    setStep('input');
    setPassword('');
    setPwError(null);
    setSendError(null);
    setResult(null);
  }

  return { step, setStep, password, setPassword, submitting, pwError, locked, sendError, result, submit, reset };
}
