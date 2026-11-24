// 상담하기의 서버 경계. 지금은 백엔드 API가 없어 이 파일 안에서 정해 둔 도움말로 답함(시연용)
// 나중에 상담 API가 생기면 이 파일의 두 함수 몸통만 서버 호출로 바꾸면 되고, 화면(SupportChat)은 고치지 않음
// 계좌·거래 데이터는 보내지 않고 읽지도 않음

export type Topic = 'withdrawable' | 'pending' | 'locked' | 'fee';

export const TOPICS: { key: Topic; label: string }[] = [
  { key: 'withdrawable', label: '출금 가능 금액' },
  { key: 'pending', label: '미체결 주문' },
  { key: 'locked', label: '계좌 거래정지' },
  { key: 'fee', label: '수수료' },
];

const ANSWERS: Record<Topic, string> = {
  withdrawable:
    '출금 가능 금액은 잔액에서 미체결 매수 주문에 묶인 돈을 뺀 금액이에요. 지금은 주문 가능 금액과 같고, 매도 대금 결제(D+2)가 생기면 달라져요.',
  pending: '미체결 주문은 주문 내역에서 볼 수 있어요. 취소하면 묶여 있던 돈이나 주식이 바로 풀려요. 취소한 주문도 기록에는 남아요.',
  locked: '계좌 비밀번호를 5번 연속 틀리면 계좌가 거래정지돼요. 계좌 상세의 "잠금 해제"에서 문자 인증을 하면 풀려요.',
  fee: '매수·매도 수수료는 주문 금액의 0.015%(원 미만 버림)예요. 다른 회원에게 주식을 보내면 현재가 × 수량의 1%가 빠지고, 내 계좌끼리는 무료예요.',
};

const KEYWORDS: [RegExp, Topic][] = [
  [/출금|인출|빼/, 'withdrawable'],
  [/미체결|취소|주문/, 'pending'],
  [/정지|잠금|잠겼|비밀번호/, 'locked'],
  [/수수료|이체/, 'fee'],
];

export interface AssistantReply {
  text: string;
  /** 답을 찾지 못해 상담사 연결을 권할 때 true */
  suggestAgent: boolean;
}

/** 자동응답 도움말. topic이 있으면 그 답을, 글로 물으면 낱말로 주제를 찾음 */
export async function askAssistant(input: { topic: Topic } | { text: string }): Promise<AssistantReply> {
  const topic = 'topic' in input ? input.topic : KEYWORDS.find(([re]) => re.test(input.text))?.[1];
  if (topic) return { text: ANSWERS[topic], suggestAgent: false };
  return { text: '이 질문에는 아직 준비된 답이 없어요. 아래 주제를 고르거나 상담사 연결을 요청해 주세요.', suggestAgent: true };
}

export type AgentRequestStatus = 'preparing';

/** 상담사 연결 요청. 상담사 배정 API가 없어 항상 "준비 중"으로 답함(요청을 어디에도 보내지 않음) */
export async function requestAgent(): Promise<{ status: AgentRequestStatus; message: string }> {
  return {
    status: 'preparing',
    message: '상담사 연결은 준비 중이에요. 지금은 요청이 접수되지 않아요. 급한 일은 고객센터로 연락해 주세요(시연용 안내).',
  };
}
