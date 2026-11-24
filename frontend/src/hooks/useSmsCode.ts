// 문자 인증번호 받기와 남은 시간(2분). 가입·잠금 해제·전화번호 변경이 같이 씀

import { useEffect, useState } from 'react';
import { sendCode } from '../api/endpoints';
import { errorMessage } from '../lib/messages';

const CODE_SECONDS = 120; // 서버 RedisTime.PHONE_VALID 2분. 화면 시계라 서버와 몇 초 다를 수 있음

export function useSmsCode() {
  const [sentAt, setSentAt] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (sentAt === null) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [sentAt]);

  const remaining = sentAt === null ? 0 : Math.max(0, CODE_SECONDS - Math.floor((now - sentAt) / 1000));
  const expired = sentAt !== null && remaining === 0;

  async function send(phoneNumber: string): Promise<boolean> {
    setSending(true);
    setError(null);
    try {
      await sendCode(phoneNumber);
      setSentAt(Date.now());
      setNow(Date.now());
      return true;
    } catch (e) {
      setError(errorMessage(e, 'sms'));
      return false;
    } finally {
      setSending(false);
    }
  }

  const timerText = `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, '0')}`;

  return {
    sent: sentAt !== null,
    sending,
    remaining,
    expired,
    timerText,
    error: expired ? '인증번호 입력 시간이 지났어요. 다시 받아 주세요' : error,
    setError,
    send,
    reset: () => {
      setSentAt(null);
      setError(null);
    },
  };
}
