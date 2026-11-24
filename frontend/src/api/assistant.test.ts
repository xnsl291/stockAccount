import { describe, expect, it } from 'vitest';
import { askAssistant, requestAgent, TOPICS } from './assistant';

describe('상담 자동응답', () => {
  it('주제마다 준비된 답이 있음', async () => {
    for (const t of TOPICS) {
      const r = await askAssistant({ topic: t.key });
      expect(r.suggestAgent).toBe(false);
      expect(r.text.length).toBeGreaterThan(10);
    }
  });

  it('글로 물으면 낱말로 주제를 찾음', async () => {
    const r = await askAssistant({ text: '출금이 왜 안 되나요' });
    expect(r.text).toContain('출금 가능 금액');
  });

  it('모르는 질문은 상담사 연결을 권함', async () => {
    const r = await askAssistant({ text: '날씨 어때요' });
    expect(r.suggestAgent).toBe(true);
  });

  it('상담사 연결은 서버 없이 준비 중으로 답함', async () => {
    expect((await requestAgent()).status).toBe('preparing');
  });
});
