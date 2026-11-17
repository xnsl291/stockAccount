// 중복 요청 방지 키(Idempotency-Key) 생명주기(설계 3.5.5)
//  - 만듦: 확인 단계에서 "보내기"를 처음 누른 순간
//  - 다시 씀: 처리됐는지 모르는 모든 재전송(연결 실패, 5xx, CONCURRENT_REQUEST, 비밀번호 틀림 뒤 다시 입력)
//  - 버림: 성공, 업무 오류(4xx), IDEMPOTENCY_KEY_REUSED, 입력값이 하나라도 바뀜(메모 포함), 화면을 떠남
// 키와 요청 본문(비밀번호 포함)은 메모리에만 둠. 저장소에 쓰지 않음

import { ApiError } from './api';

export type KeyDisposition = 'keep' | 'discard';

/** 실패한 요청의 키를 다음 재전송에 그대로 쓸지 정함 */
export function keyDisposition(error: unknown): KeyDisposition {
  if (!(error instanceof ApiError)) return 'keep'; // 정체를 모르는 실패는 서버가 처리했을 수도 있다고 봄
  if (error.kind === 'network') return 'keep';
  if (error.status >= 500) return 'keep';
  if (error.code === 'CONCURRENT_REQUEST' || error.code === 'EXPIRED_TOKEN') return 'keep';
  // 비밀번호는 서버가 비교하는 내용에 없으므로 비밀번호만 다시 넣는 것은 같은 작업임
  if (error.code === 'UNMATCHED_PASSWORD') return 'keep';
  return 'discard';
}

/** 작업 하나에 키 하나. 처음 acquire 때 만들고 discard 전까지 같은 값을 돌려줌 */
export class IdempotencyKeyHolder {
  private key: string | null = null;

  constructor(private readonly generate: () => string = () => crypto.randomUUID()) {}

  acquire(): string {
    if (this.key === null) this.key = this.generate();
    return this.key;
  }

  discard(): void {
    this.key = null;
  }

  get current(): string | null {
    return this.key;
  }

  /** 키를 붙여 보내고, 결과에 따라 키를 버리거나 남김 */
  async run<T>(send: (key: string) => Promise<T>): Promise<T> {
    const key = this.acquire();
    try {
      const result = await send(key);
      this.discard();
      return result;
    } catch (error) {
      if (keyDisposition(error) === 'discard') this.discard();
      throw error;
    }
  }
}
