// 겉모양과 무관한 작은 화면 조각. 새 디자인을 입힐 때는 클래스 이름과 styles/ 아래 CSS만 바꾸면 됨
// 금액·수량 요소의 글자는 숫자와 부호만 두고 단위("원"·"주")는 testid 요소 밖에 둠(설계 3.10.0)

import { useId, type CSSProperties, type ReactNode } from 'react';
import { Link } from 'react-router';
import { formatAmount, formatAmountInput, formatSigned, digitsOnly } from '../lib/format';
import { errorMessage, isNotFound, type ErrorContext } from '../lib/messages';
import { charCount } from '../lib/validation';

export function Amount({
  value,
  testid,
  signed = false,
  unit = '원',
  tone = false,
}: {
  value: number;
  testid?: string;
  signed?: boolean;
  unit?: '원' | '주' | '';
  /** 늘어남·줄어듦을 색으로도 보임(부호와 함께) */
  tone?: boolean;
}) {
  const cls = tone ? (value > 0 ? 'num plus' : value < 0 ? 'num minus' : 'num') : 'num';
  // 큰 금액 자리에서 자릿수에 맞춰 글자 크기를 줄일 때 씀(.hero-amount)
  const digits = { '--digits': String(Math.trunc(Math.abs(value))).length } as CSSProperties;
  return (
    <span className="amount" style={digits}>
      <span className={cls} data-testid={testid}>
        {signed ? formatSigned(value) : formatAmount(value)}
      </span>
      {unit && <span className="unit">{unit}</span>}
    </span>
  );
}

interface FieldProps {
  label: string;
  testid: string;
  value: string;
  onChange: (value: string) => void;
  error?: string | null;
  errorTestid?: string;
  hint?: ReactNode;
  type?: 'text' | 'password' | 'date';
  inputMode?: 'text' | 'numeric' | 'tel';
  autoComplete?: string;
  maxLength?: number;
  /** 글자 수 표시 상한. 넘게 입력은 되고, 넘으면 숫자와 테두리가 빨개짐(진행 막기는 화면이 따로 함) */
  countMax?: number;
  countTestid?: string;
  disabled?: boolean;
  placeholder?: string;
  suffix?: string;
}

export function TextField(props: FieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errId = `${id}-err`;
  const count = props.countMax !== undefined ? charCount(props.value) : null;
  const overCount = count !== null && count > props.countMax!;
  const describedBy = [props.hint ? hintId : null, props.error ? errId : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className="field">
      <label htmlFor={id}>{props.label}</label>
      <div className="field-input">
        <input
          id={id}
          data-testid={props.testid}
          type={props.type ?? 'text'}
          inputMode={props.inputMode}
          autoComplete={props.autoComplete ?? (props.type === 'password' ? 'off' : undefined)}
          maxLength={props.maxLength}
          value={props.value}
          disabled={props.disabled}
          placeholder={props.placeholder}
          aria-invalid={props.error || overCount ? true : undefined}
          aria-describedby={describedBy}
          className={props.suffix ? 'num' : undefined}
          onChange={(e) => props.onChange(e.target.value)}
        />
        {props.suffix && <span className="unit">{props.suffix}</span>}
      </div>
      {count !== null && (
        <p className={overCount ? 'char-count over' : 'char-count'} data-testid={props.countTestid}>
          {count}/{props.countMax}
        </p>
      )}
      {props.hint && (
        <p className="hint" id={hintId}>
          {props.hint}
        </p>
      )}
      {props.error && (
        <p className="error" id={errId} data-testid={props.errorTestid} role="alert">
          {props.error}
        </p>
      )}
    </div>
  );
}

/** 금액·수량 입력: 숫자만 받고 쉼표를 붙여 보임. value/onChange는 숫자 글자(쉼표 없음) */
export function NumberField(props: Omit<FieldProps, 'type' | 'inputMode'> & { suffix: string }) {
  return (
    <TextField
      {...props}
      inputMode="numeric"
      value={formatAmountInput(props.value)}
      onChange={(v) => props.onChange(digitsOnly(v).replace(/^0+(?=\d)/, ''))}
    />
  );
}

export function SelectField({
  label,
  testid,
  value,
  onChange,
  options,
  disabled,
  placeholder,
}: {
  label: string;
  testid: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string; disabled?: boolean }[];
  disabled?: boolean;
  placeholder?: string;
}) {
  const id = useId();
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <select id={id} data-testid={testid} value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value} disabled={o.disabled}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

export function Loading() {
  return (
    <div className="state" data-testid="state-loading" aria-busy="true">
      불러오는 중
    </div>
  );
}

export function NotFound({ message = '찾는 화면이 없어요' }: { message?: string }) {
  return (
    <div className="state" data-testid="state-not-found">
      <p>{message}</p>
      <Link to="/" data-testid="state-home-link">
        홈으로
      </Link>
    </div>
  );
}

/** 조회 실패 공통 처리. 없는 계좌·종목이면 "찾을 수 없음", 그 밖은 다시 시도 */
export function LoadError({ error, onRetry, context = 'query' }: { error: unknown; onRetry: () => void; context?: ErrorContext }) {
  if (isNotFound(error)) return <NotFound message={errorMessage(error, context)} />;
  return (
    <div className="state" data-testid="state-error" role="alert">
      <p>{errorMessage(error, context)}</p>
      <button type="button" data-testid="state-retry" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  );
}

export function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="kv">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

export function StatusBadge({ testid, state, label }: { testid: string; state: string; label: string }) {
  return (
    <span className={`badge badge-${state.toLowerCase()}`} data-testid={testid} data-state={state}>
      {label}
    </span>
  );
}

/** 화면 제목 줄. 제목 아래 한 줄 설명과 오른쪽 배지(2차·준비 중 등)를 둘 수 있음 */
export function PageHead({ title, sub, badge }: { title: ReactNode; sub?: ReactNode; badge?: ReactNode }) {
  return (
    <header className="page-head">
      <div className="page-head-row">
        <h1>{title}</h1>
        {badge}
      </div>
      {sub && <p className="hint">{sub}</p>}
    </header>
  );
}
