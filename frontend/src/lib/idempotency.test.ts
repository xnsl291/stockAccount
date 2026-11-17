import { describe, expect, it } from 'vitest';
import { ApiError } from './api';
import { IdempotencyKeyHolder, keyDisposition } from './idempotency';

function counterKeys() {
  let n = 0;
  return () => `key-${++n}`;
}

const http = (status: number, code: string) => new ApiError('http', status, code);

describe('중복 요청 방지 키 생명주기', () => {
  it('처음 보낼 때 만들고, 연결 실패 뒤 다시 시도하면 같은 키를 씀', async () => {
    const holder = new IdempotencyKeyHolder(counterKeys());
    const sent: string[] = [];

    await expect(
      holder.run(async (key) => {
        sent.push(key);
        throw new ApiError('network', 0, 'NETWORK_ERROR');
      }),
    ).rejects.toBeInstanceOf(ApiError);
    await holder.run(async (key) => {
      sent.push(key);
      return '출금완료';
    });

    expect(sent).toEqual(['key-1', 'key-1']);
  });

  it('성공하면 버려서 다음 작업은 새 키', async () => {
    const holder = new IdempotencyKeyHolder(counterKeys());
    await holder.run(async () => 'ok');
    expect(holder.current).toBeNull();
    expect(holder.acquire()).toBe('key-2');
  });

  it.each([
    ['비밀번호 틀림', http(400, 'UNMATCHED_PASSWORD')],
    ['잠금 충돌', http(409, 'CONCURRENT_REQUEST')],
    ['서버 오류', http(500, 'INTERNAL_SERVER_ERROR')],
    ['프록시 실패', new ApiError('network', 504, 'NETWORK_ERROR')],
  ])('%s 뒤에는 키를 남김', async (_label, error) => {
    const holder = new IdempotencyKeyHolder(counterKeys());
    await expect(holder.run(async () => Promise.reject(error))).rejects.toBe(error);
    expect(holder.current).toBe('key-1');
  });

  it.each([
    ['잔액 초과', http(400, 'EXCEED_BALANCE')],
    ['거래정지', http(400, 'PENDING_ACCOUNT')],
    ['같은 키 다른 내용', http(409, 'IDEMPOTENCY_KEY_REUSED')],
    ['형식 오류', http(400, 'INVALID_ARGUMENT')],
  ])('%s 뒤에는 키를 버림', async (_label, error) => {
    const holder = new IdempotencyKeyHolder(counterKeys());
    await expect(holder.run(async () => Promise.reject(error))).rejects.toBe(error);
    expect(holder.current).toBeNull();
  });

  it('입력이 바뀌어 discard하면 다음 보내기는 새 키', async () => {
    const holder = new IdempotencyKeyHolder(counterKeys());
    await expect(holder.run(async () => Promise.reject(http(400, 'UNMATCHED_PASSWORD')))).rejects.toBeTruthy();
    holder.discard(); // 사용자가 메모를 고침
    const sent: string[] = [];
    await holder.run(async (key) => {
      sent.push(key);
    });
    expect(sent).toEqual(['key-2']);
  });

  it('정체를 모르는 실패는 서버가 처리했을 수 있다고 보고 남김', () => {
    expect(keyDisposition(new Error('?'))).toBe('keep');
  });
});
