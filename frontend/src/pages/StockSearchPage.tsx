// S-13 종목 검색. 검색어가 있어야 하는 API(K1)라 검색 전에는 빈 화면. 결과는 종목 이름 임시 저장소에 넣음
// 결과 줄 하나가 종목 상세로 가는 링크임

import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { searchStocks } from '../api/endpoints';
import { IconChevron } from '../components/icons';
import { Amount, LoadError, Loading, PageHead, TextField } from '../components/ui';
import { rememberStocks } from '../lib/stockNames';

export function StockSearchPage() {
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const query = useQuery({
    queryKey: ['stocks', keyword],
    queryFn: async () => {
      const list = await searchStocks(keyword);
      rememberStocks(list);
      return list;
    },
    enabled: keyword !== '',
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (input.trim()) setKeyword(input.trim());
  }

  return (
    <>
      <PageHead title="종목 검색" sub="현재가는 관리자가 넣은 값이며 실시간 시세가 아니에요" />
      <section className="panel">
        <form className="filters" onSubmit={onSubmit}>
          <TextField label="종목 검색어" testid="search-keyword-input" value={input} onChange={setInput} placeholder="종목코드나 이름 일부" />
          <button type="submit" className="primary" data-testid="search-submit" disabled={!input.trim()}>
            검색
          </button>
        </form>
        {keyword === '' ? (
          <p className="empty" data-testid="search-idle">
            종목코드나 종목 이름을 입력해 주세요
          </p>
        ) : query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <LoadError error={query.error} onRetry={() => void query.refetch()} />
        ) : query.data.length === 0 ? (
          <p className="empty" data-testid="search-empty">
            '{keyword}'에 맞는 종목이 없어요
          </p>
        ) : (
          <ul className="rows">
            {query.data.map((s) => (
              <li key={s.id}>
                <Link to={`/stocks/${s.id}`} className="row" data-testid="search-row" data-id={s.id} aria-label={`${s.name} ${s.code}`}>
                  <span className="row-icon" aria-hidden="true">
                    {s.name.slice(0, 1)}
                  </span>
                  <span className="row-main">
                    <span className="row-title" data-testid="search-row-name">
                      {s.name}
                    </span>
                    <span className="row-sub">
                      <span data-testid="search-row-code">{s.code}</span>
                    </span>
                  </span>
                  <span className="row-side row-amount">
                    <Amount value={s.currentPrice} testid="search-row-price" />
                  </span>
                  <IconChevron className="row-chevron" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
