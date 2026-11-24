// 오류 코드별 화면 문구(설계 3.12.3). 서버의 message는 개발자용 표현이 섞여 있어 보이지 않고 code로 고름
// 같은 code라도 부른 화면(맥락)에 따라 문구가 다름

import { ApiError } from './api';

export type ErrorContext =
  | 'query' // 조회
  | 'login'
  | 'signup'
  | 'sms' // 문자 인증(가입·잠금 해제·전화번호 변경)
  | 'me' // 내 정보 수정
  | 'account' // 계좌 비밀번호를 쓰는 돈 요청(출금·송금·주문·주식 이체)과 계좌 설정
  | 'deposit'
  | 'transfer'
  | 'order'
  | 'stocktransfer'
  | 'recipient' // 받는 계좌번호 확인
  | 'close' // 계좌 해지
  | 'leave' // 회원 탈퇴
  | 'cancel'; // 주문 취소

const MONEY_CONTEXTS: ErrorContext[] = ['account', 'deposit', 'transfer', 'order', 'stocktransfer'];

export const MESSAGES = {
  network: '서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요',
  moneyUnknown: '처리됐는지 확인하지 못했어요. 다시 시도해도 두 번 처리되지 않아요',
  accountLocked: '비밀번호를 5번 연속 틀려 거래정지됐어요',
  lockedBanner: '비밀번호를 5번 연속 틀려 거래정지된 계좌예요. 문자 인증으로 잠금을 풀 수 있어요',
  withdrawableNote: '지금은 주문 가능 금액과 같음. D+2 결제가 들어가면 달라짐',
  pwWarning: '5번 연속 틀리면 계좌가 거래정지됨',
} as const;

export function errorMessage(error: unknown, context: ErrorContext): string {
  if (!(error instanceof ApiError)) return MESSAGES.network;
  const money = MONEY_CONTEXTS.includes(context);
  if (error.kind === 'network') return money ? MESSAGES.moneyUnknown : MESSAGES.network;

  switch (error.code) {
    case 'INVALID_ARGUMENT':
      return '입력값을 다시 확인해 주세요';
    case 'AMOUNT_OUT_OF_RANGE':
      return '처리할 수 있는 금액보다 커요. 금액이나 수량을 줄여 주세요';
    case 'INTERNAL_SERVER_ERROR':
      return money ? MESSAGES.moneyUnknown : '일시적인 오류가 났어요. 잠시 뒤 다시 시도해 주세요';
    case 'CONCURRENT_REQUEST':
      return '다른 거래를 처리하는 중이에요. 잠시 뒤 다시 시도해 주세요';
    case 'INVALID_TOKEN':
    case 'EXPIRED_TOKEN':
    case 'FAILED_VALIDATION':
      return '로그인이 끝났어요. 다시 로그인해 주세요';
    case 'ACCESS_DENIED':
      return '이 화면을 쓸 권한이 없어요';
    case 'INVALID_NAME_FORMAT':
      return '이름은 한글·영문·숫자 2~10자로 입력해 주세요';
    case 'MEMBER_NOT_EXIST':
      // 로그인에서 없는 번호와 틀린 비밀번호를 같은 문구로 보여 가입 여부를 드러내지 않음(사용자 결정)
      return context === 'login' ? '전화번호 또는 비밀번호가 맞지 않아요' : '로그인이 끝났어요. 다시 로그인해 주세요';
    case 'WITHDRAWN_MEMBER':
      return '탈퇴한 회원이에요';
    case 'PENDING_MEMBER':
      return '사용이 정지된 계정이에요';
    case 'DUPLICATED_PHONE_NUMBER':
      return '이미 가입된 전화번호예요';
    case 'UNMATCHED_MEMBER':
    case 'MISMATCHED_USER_ID':
      return '회원 정보를 확인하지 못했어요. 다시 로그인해 주세요';
    case 'UNMATCHED_PASSWORD':
      if (context === 'login') return '전화번호 또는 비밀번호가 맞지 않아요';
      if (context === 'me' || context === 'leave') return '현재 비밀번호가 맞지 않아요';
      return '계좌 비밀번호가 맞지 않아요';
    case 'UNMATCHED_VERIFICATION_CODE':
      return '인증번호가 맞지 않아요. 다시 확인해 주세요';
    case 'VERIFICATION_CODE_NOT_EXIST':
      return '인증번호가 만료됐어요. 다시 받아 주세요';
    case 'UNVERIFIED_PHONE_NUMBER':
      return '전화번호 인증이 만료됐어요. 인증부터 다시 해 주세요';
    case 'TOO_MANY_SMS_REQUESTS':
      return '인증번호를 너무 많이 요청했어요. 10분 뒤에 다시 시도해 주세요';
    case 'ACCOUNT_NOT_EXIST':
      return context === 'recipient' ? '계좌번호를 찾지 못했어요. 다시 확인해 주세요' : '이 계좌를 찾을 수 없어요';
    case 'DELETED_ACCOUNT':
      return '해지된 계좌예요';
    case 'PENDING_ACCOUNT':
      return '거래정지된 계좌예요. 문자 인증으로 잠금을 풀어 주세요';
    case 'LAST_ACCOUNT':
      return '마지막 남은 계좌는 해지할 수 없어요';
    case 'DUPLICATED_ACCOUNT_NUMBER':
      return '계좌번호가 겹쳐 만들지 못했어요. 다시 시도해 주세요';
    case 'EXCEED_BALANCE':
      if (context === 'order') return '주문 가능 금액보다 커요';
      if (context === 'stocktransfer') return '이체 수수료를 낼 돈이 부족해요';
      return '출금 가능 금액보다 커요';
    case 'INVALID_ACCOUNT':
      return context === 'deposit' ? '거래정지된 계좌에는 입금할 수 없어요' : '받는 계좌가 지금 거래할 수 없는 상태예요';
    case 'SAME_ACCOUNT_TRANSFER':
      return '같은 계좌로는 보낼 수 없어요';
    case 'IDEMPOTENCY_KEY_REUSED':
      return '요청 내용이 바뀌었어요. 처음부터 다시 입력해 주세요';
    case 'MISMATCH_ACCOUNT_OWNER':
      return '이 계좌를 찾을 수 없어요';
    case 'REMAINING_ASSET':
      return context === 'leave'
        ? '잔액이나 보유 주식이 남은 계좌가 있어 탈퇴할 수 없어요'
        : '잔액이나 보유 주식이 남아 있어 해지할 수 없어요';
    case 'STOCK_NOT_EXIST':
      return context === 'query' ? '이 종목을 찾을 수 없어요' : '종목을 찾을 수 없어요';
    case 'INSUFFICIENT_STOCK':
      return '팔 수 있는 수량보다 많아요';
    case 'NO_STOCK_BALANCE':
      return '이 계좌에 그 종목이 없어요';
    case 'INVALID_REQUEST_DATE':
      return '조회 기간을 다시 확인해 주세요';
    case 'DUPLICATED_STOCK_CODE':
      return '이미 등록된 종목코드예요';
    case 'ORDER_NOT_EXIST':
      return '주문을 찾을 수 없어요';
    case 'ORDER_NOT_PENDING':
      return '이미 체결됐거나 취소된 주문이에요';
    default:
      // 스프링이 거절한 요청(METHOD_NOT_ALLOWED 등)은 화면 코드의 잘못이라 콘솔에 남김
      console.error('처리하지 않은 오류 코드', error.status, error.code);
      return '요청을 처리하지 못했어요. 화면을 새로 고쳐 주세요';
  }
}

/** 돈 요청 실패 뒤 "다시 시도"를 보여 줄지. 같은 키로 다시 보내도 되는 경우만 */
export function isRetryable(error: unknown): boolean {
  if (!(error instanceof ApiError)) return true;
  return error.kind === 'network' || error.status >= 500 || error.code === 'CONCURRENT_REQUEST';
}

/** 주소의 계좌·종목이 없거나 남의 것일 때(구분해 보이지 않음) */
export function isNotFound(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    ['ACCOUNT_NOT_EXIST', 'DELETED_ACCOUNT', 'MISMATCH_ACCOUNT_OWNER', 'STOCK_NOT_EXIST'].includes(error.code)
  );
}
