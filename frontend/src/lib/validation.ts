// 서버 DTO와 같은 검사 규칙. 서버가 최종 판정하고, 화면은 보내기 전에 미리 걸러 줌

/** SignInDto·SendCodeDto·SignUpDto·UpdateMemberDto의 @Pattern 그대로 */
export const PHONE_PATTERN = /^01([0-9])[ -.]?([0-9]{3,4})[ -.]?([0-9]{4})$/;

/** 가입 서비스의 이름 규칙(AuthenticationService). 이름 변경(M2)에는 서버 검사가 없어 화면이 같은 규칙을 씀 */
export const NAME_PATTERN = /^[가-힣a-zA-Z0-9]{2,10}$/;

/** 서버가 만드는 인증번호는 숫자 5자리 */
export const CODE_PATTERN = /^\d{5}$/;

/** 계좌번호는 숫자 14자리 */
export const ACCOUNT_NUMBER_LENGTH = 14;

/** 화면 상한(서버 상한 없음, DB 칸 기본 길이 255자 추정). 사용자 결정: 메모 50자, 별칭 20자 */
export const MEMO_MAX = 50;
export const NICKNAME_MAX = 20;

/** 자바스크립트가 정확히 다루는 가장 큰 정수 */
export const AMOUNT_MAX = Number.MAX_SAFE_INTEGER;

export const isPhone = (v: string) => PHONE_PATTERN.test(v);
export const isName = (v: string) => NAME_PATTERN.test(v);
export const isBlank = (v: string) => v.trim() === '';
