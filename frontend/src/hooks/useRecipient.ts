// 받는 계좌번호 → 계좌 ID와 이름 확인(C7). 송금·주식 이체가 같이 씀
// 번호를 바꾸거나 보내는 계좌를 바꾸면 확인을 지우고 다시 확인하게 함

import { useEffect, useState } from 'react';
import { searchAccount, type Account, type AccountSearch } from '../api/endpoints';
import { digitsOnly } from '../lib/format';
import { errorMessage } from '../lib/messages';
import { ACCOUNT_NUMBER_LENGTH } from '../lib/validation';

export function useRecipient(fromId: number | null, myAccounts: Account[]) {
  const [number, setNumberRaw] = useState('');
  const [found, setFound] = useState<AccountSearch | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);

  useEffect(() => {
    setFound(null);
    setError(null);
  }, [fromId]);

  const mine = found ? myAccounts.find((a) => a.id === found.accountId) : undefined;

  return {
    number,
    setNumber(v: string) {
      setNumberRaw(digitsOnly(v).slice(0, ACCOUNT_NUMBER_LENGTH));
      setFound(null);
      setError(null);
    },
    canCheck: number.length === ACCOUNT_NUMBER_LENGTH && !checking,
    found,
    isMine: !!mine,
    displayName: found ? (mine ? `${found.ownerName} (내 계좌) ${mine.nickname ?? ''}`.trim() : found.ownerName) : '',
    error,
    async check() {
      setChecking(true);
      setError(null);
      setFound(null);
      try {
        const result = await searchAccount(number);
        if (result.accountId === fromId) setError('같은 계좌로는 보낼 수 없어요');
        else setFound(result);
      } catch (e) {
        setError(errorMessage(e, 'recipient'));
      } finally {
        setChecking(false);
      }
    },
  };
}
