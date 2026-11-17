// 돈이 움직이는 작업의 단계 영역(설계 3.10.1). {p}는 화면약어(deposit·withdraw·transfer·order·stocktransfer)
// 확인·비밀번호 단계는 별도 창이 아니라 같은 화면에 펼침

import { useEffect, useRef, type ReactNode } from 'react';
import { Link } from 'react-router';
import type { Account } from '../api/endpoints';
import type { MoneyFlow } from '../hooks/useMoneyFlow';
import { MESSAGES } from '../lib/messages';
import { Amount, TextField } from './ui';

/** 단계가 바뀌면 새 단계 제목으로 초점을 옮김 */
function PanelTitle({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLHeadingElement>(null);
  useEffect(() => ref.current?.focus(), []);
  return (
    <h2 ref={ref} tabIndex={-1}>
      {children}
    </h2>
  );
}

export function ConfirmPanel({
  p,
  flow,
  children,
  nextLabel = '비밀번호 입력',
  footer,
}: {
  p: string;
  flow: MoneyFlow<unknown>;
  children: ReactNode;
  nextLabel?: string;
  /** 입금은 비밀번호 단계가 없어 확인 영역 안에 보내기 버튼을 둠 */
  footer?: ReactNode;
}) {
  return (
    <section className="panel" data-testid={`${p}-confirm-panel`}>
      <PanelTitle>내용 확인</PanelTitle>
      <dl className="summary">{children}</dl>
      <div className="actions">
        <button
          type="button"
          data-testid={`${p}-confirm-back`}
          disabled={flow.submitting || flow.step === 'result'}
          onClick={() => {
            flow.setPassword('');
            flow.setStep('input');
          }}
        >
          고치기
        </button>
        {footer ??
          (flow.step === 'confirm' && (
            <button type="button" className="primary" data-testid={`${p}-confirm-next`} onClick={() => flow.setStep('password')}>
              {nextLabel}
            </button>
          ))}
      </div>
    </section>
  );
}

export function PasswordPanel({
  p,
  flow,
  submitLabel,
  unlockTo,
}: {
  p: string;
  flow: MoneyFlow<unknown>;
  submitLabel: string;
  /** 잠금 해제 화면 주소(돌아올 주소 포함) */
  unlockTo: string;
}) {
  return (
    <section className="panel" data-testid={`${p}-pw-panel`}>
      <PanelTitle>계좌 비밀번호</PanelTitle>
      <TextField
        label="계좌 비밀번호"
        testid={`${p}-pw-input`}
        type="password"
        autoComplete="off"
        value={flow.password}
        onChange={flow.setPassword}
        disabled={flow.locked}
      />
      <p className="hint" data-testid={`${p}-pw-warning`}>
        {MESSAGES.pwWarning}
      </p>
      {flow.pwError && (
        <p className="error" data-testid={`${p}-pw-error`} role="alert">
          {flow.pwError}
        </p>
      )}
      {flow.locked && (
        <Link to={unlockTo} data-testid={`${p}-unlock-link`}>
          잠금 해제로 이동
        </Link>
      )}
      <div className="actions">
        <SubmitButton p={p} flow={flow} label={submitLabel} disabled={flow.password === '' || flow.locked} />
      </div>
      <SendErrorBox p={p} flow={flow} />
    </section>
  );
}

export function SubmitButton({ p, flow, label, disabled }: { p: string; flow: MoneyFlow<unknown>; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      className="primary"
      data-testid={`${p}-submit`}
      disabled={disabled || flow.submitting}
      aria-busy={flow.submitting || undefined}
      onClick={() => void flow.submit()}
    >
      {flow.submitting ? '처리 중' : label}
    </button>
  );
}

export function SendErrorBox({ p, flow }: { p: string; flow: MoneyFlow<unknown> }) {
  if (!flow.sendError) return null;
  return (
    <div className="send-error" role="alert">
      <p className="error" data-testid={`${p}-send-error`}>
        {flow.sendError.message}
      </p>
      {flow.sendError.retryable && (
        <button type="button" data-testid={`${p}-retry`} disabled={flow.submitting} onClick={() => void flow.submit()}>
          다시 시도
        </button>
      )}
    </div>
  );
}

export function ResultPanel({
  p,
  message,
  account,
  doneTo,
  children,
}: {
  p: string;
  message: ReactNode;
  account: Account | null;
  doneTo: string;
  children?: ReactNode;
}) {
  return (
    <section className="panel" data-testid={`${p}-result-panel`}>
      <PanelTitle>처리 결과</PanelTitle>
      <p data-testid={`${p}-result-message`}>{message}</p>
      <dl className="summary">
        {children}
        {account ? (
          <>
            <div className="kv">
              <dt>잔액</dt>
              <dd>
                <Amount value={account.balance} testid={`${p}-result-balance`} />
              </dd>
            </div>
            <div className="kv">
              <dt>주문 가능 금액</dt>
              <dd>
                <Amount value={account.availableBalance} testid={`${p}-result-orderable`} />
              </dd>
            </div>
          </>
        ) : (
          <p className="hint">처리는 끝났지만 바뀐 잔액을 읽지 못했어요. 계좌 상세에서 확인해 주세요</p>
        )}
      </dl>
      <div className="actions">
        <Link to={doneTo} className="button primary" data-testid={`${p}-result-done`}>
          확인
        </Link>
      </div>
    </section>
  );
}

/** 거래정지 계좌를 골랐을 때 입력 단계 위에 보임 */
export function LockedBanner({ testid, unlockTo, linkTestid }: { testid: string; unlockTo: string; linkTestid: string }) {
  return (
    <div className="banner banner-danger" data-testid={testid} role="alert">
      <p>{MESSAGES.lockedBanner}</p>
      <Link to={unlockTo} data-testid={linkTestid}>
        잠금 해제
      </Link>
    </div>
  );
}

export function unlockPath(accountId: number, returnTo: string): string {
  return `/accounts/${accountId}/unlock?return=${encodeURIComponent(returnTo)}`;
}
