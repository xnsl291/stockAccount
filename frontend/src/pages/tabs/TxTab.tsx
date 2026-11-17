// S-12 거래내역(S-06의 탭). 줄마다 금액과 거래 후 잔액을 나란히 보이고, 한 페이지 안에서 줄 사이 잔액이 이어지는지 확인함(설계 3.2.5의 3번)

import { useState } from 'react';
import type { Transaction, TransactionType } from '../../api/endpoints';
import { Amount, LoadError, Loading, SelectField, TextField } from '../../components/ui';
import { useTransactions } from '../../hooks/queries';
import { formatShortDateTime } from '../../lib/format';
import { TX_TYPE } from '../../lib/labels';

const PAGE_SIZE = 20;

type Continuity = 'linked' | 'broken' | 'unknown';

/**
 * 최근 순 목록에서 각 줄이 바로 아래(더 오래된) 줄과 이어지는지: 아래 줄 거래 후 잔액 + 이 줄 금액 = 이 줄 거래 후 잔액.
 * 마지막 쪽의 맨 아래 줄은 계좌의 첫 거래라 0원에서 시작했는지로 확인하고, 그 밖의 쪽 맨 아래 줄은 다음 쪽을 봐야 해서 확인하지 않음
 */
export function continuity(rows: Transaction[], isLastPage: boolean, filtered: boolean): Continuity[] {
  return rows.map((row, i) => {
    const older = rows[i + 1];
    if (older) return older.balanceAfter + row.amount === row.balanceAfter ? 'linked' : 'broken';
    if (isLastPage && !filtered) return row.amount === row.balanceAfter ? 'linked' : 'broken';
    return 'unknown';
  });
}

const CONTINUITY_LABEL: Record<Continuity, string> = {
  linked: '이전 거래와 이어짐',
  broken: '이전 거래와 끊김',
  unknown: '이전 거래는 다음 쪽에 있음',
};

export function TxTab({ accountId }: { accountId: number }) {
  const [type, setType] = useState<TransactionType | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [applied, setApplied] = useState({ type: '' as TransactionType | '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const query = useTransactions(accountId, { ...applied, page, size: PAGE_SIZE });
  const rangeError = from && to && from > to ? '조회 기간을 다시 확인해 주세요' : null;
  const filtered = applied.type !== '' || applied.from !== '' || applied.to !== '';

  return (
    <section className="tab-body">
      <form
        className="filters"
        onSubmit={(e) => {
          e.preventDefault();
          if (rangeError) return;
          setApplied({ type, from, to });
          setPage(0);
        }}
      >
        <SelectField
          label="거래 종류"
          testid="tx-type-select"
          value={type}
          onChange={(v) => setType(v as TransactionType | '')}
          placeholder="전체"
          options={Object.entries(TX_TYPE).map(([value, label]) => ({ value, label }))}
        />
        <TextField label="시작일" testid="tx-from-input" type="date" value={from} onChange={setFrom} />
        <TextField label="종료일" testid="tx-to-input" type="date" value={to} onChange={setTo} error={rangeError} />
        <button type="submit" className="soft" data-testid="tx-search">
          조회
        </button>
      </form>

      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <LoadError error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <p className="empty" data-testid="tx-empty">
          {filtered ? '조건에 맞는 거래가 없어요' : '아직 거래가 없어요'}
        </p>
      ) : (
        <>
          <p className="hint">줄 앞 점: 칠한 원은 바로 아래 거래의 잔액에서 이어짐, 빨간 원은 끊김, 빈 원은 다음 쪽에서 확인</p>
          <ul className="rows">
            {query.data.content.map((t, i, rows) => {
              const c = continuity(rows, query.data.last, filtered)[i];
              return (
                <li key={i} className="row tx-row" data-testid="tx-row" data-id={page * PAGE_SIZE + i}>
                  <span className={`continuity continuity-${c}`} data-testid="tx-row-continuity" data-state={c} role="img" aria-label={CONTINUITY_LABEL[c]} title={CONTINUITY_LABEL[c]} />
                  <span className="row-main">
                    <span className="row-title" data-testid="tx-row-type">
                      {TX_TYPE[t.type]}
                    </span>
                    <span className="row-sub">
                      <span className="nowrap" data-testid="tx-row-time">{formatShortDateTime(t.transactedAt)}</span>
                      {t.name ? <span data-testid="tx-row-name">{t.name}</span> : <span data-testid="tx-row-name" hidden />}
                      {t.memo ? <span data-testid="tx-row-memo">{t.memo}</span> : <span data-testid="tx-row-memo" hidden />}
                    </span>
                  </span>
                  <span className="row-side">
                    <span className="row-amount">
                      <Amount value={t.amount} signed tone testid="tx-row-amount" />
                    </span>
                    <span className="hint">
                      잔액 <Amount value={t.balanceAfter} testid="tx-row-balance-after" />
                    </span>
                  </span>
                </li>
              );
            })}
          </ul>
          <div className="actions pager">
            <button type="button" className="small" data-testid="tx-prev" disabled={query.data.first} onClick={() => setPage((p) => p - 1)}>
              이전 쪽
            </button>
            <span data-testid="tx-page" className="hint">
              {query.data.number + 1} / {Math.max(query.data.totalPages, 1)}쪽
            </span>
            <button type="button" className="small" data-testid="tx-next" disabled={query.data.last} onClick={() => setPage((p) => p + 1)}>
              다음 쪽
            </button>
          </div>
        </>
      )}
    </section>
  );
}
