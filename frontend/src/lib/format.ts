// 화면 표시 형식. 금액은 원 단위 정수, 천 단위 쉼표, 빼기는 U+2212(−)로 통일함(설계 3.10.0)

export const MINUS = '−';

const grouping = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });

/** 1000000 → "1,000,000", -30000 → "−30,000" */
export function formatAmount(value: number): string {
  const text = grouping.format(Math.abs(value));
  return value < 0 ? MINUS + text : text;
}

/** 늘어난 금액은 "+", 줄어든 금액은 "−"를 붙임. 0은 부호 없음 */
export function formatSigned(value: number): string {
  if (value > 0) return '+' + grouping.format(value);
  return formatAmount(value);
}

/** 숫자 아닌 글자를 모두 지움. 서버의 PhoneNumber.normalize와 같은 규칙 */
export function digitsOnly(text: string): string {
  return text.replace(/\D/g, '');
}

/** 입력 칸용: "1000000" → "1,000,000". 빈 값은 빈 글자 */
export function formatAmountInput(text: string): string {
  const digits = digitsOnly(text).replace(/^0+(?=\d)/, '');
  return digits === '' ? '' : grouping.format(Number(digits));
}

/** "01012345678" → "010-1234-5678" */
export function formatPhone(phone: string): string {
  const d = digitsOnly(phone);
  if (d.length === 11) return `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `${d.slice(0, 3)}-${d.slice(3, 6)}-${d.slice(6)}`;
  return phone;
}

/** 가운데를 가림: "01012345678" → "010-****-5678" */
export function maskPhone(phone: string): string {
  const d = digitsOnly(phone);
  if (d.length < 10) return phone;
  return `${d.slice(0, 3)}-****-${d.slice(-4)}`;
}

// 서버의 LocalDateTime은 시간대 없는 "2026-11-17T14:32:05.123" 형식. 서버 시각(한국 시각) 그대로 보여 주므로 Date로 바꾸지 않고 글자에서 자름
const DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2}))?)?/;

/** "2026-11-17T14:32:05" → "2026.11.17 14:32" */
export function formatDateTime(value: string | null | undefined): string {
  const m = value ? DATE_TIME.exec(value) : null;
  if (!m) return '';
  return m[4] ? `${m[1]}.${m[2]}.${m[3]} ${m[4]}:${m[5]}` : `${m[1]}.${m[2]}.${m[3]}`;
}

/** "2026-11-17T14:32:05" → "2026.11.17" */
export function formatDate(value: string | null | undefined): string {
  const m = value ? DATE_TIME.exec(value) : null;
  return m ? `${m[1]}.${m[2]}.${m[3]}` : '';
}

/** "2026-11-17T09:41:00" → "11.17 09:41" (거래내역 줄) */
export function formatShortDateTime(value: string | null | undefined): string {
  const m = value ? DATE_TIME.exec(value) : null;
  if (!m) return '';
  return m[4] ? `${m[2]}.${m[3]} ${m[4]}:${m[5]}` : `${m[2]}.${m[3]}`;
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/** 머리글 기준시각: "2026.11.17 (화) 14:32:05 기준" (브라우저 시각) */
export function formatAsOf(date: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}.${p(date.getMonth() + 1)}.${p(date.getDate())} (${WEEKDAYS[date.getDay()]}) ` +
    `${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())} 기준`
  );
}
