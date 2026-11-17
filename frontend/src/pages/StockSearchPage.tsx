// S-13 종목 검색. 검색어가 있어야 하는 API(K1)라 검색 전에는 빈 화면. 결과는 종목 이름 임시 저장소에 넣음

import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { searchStocks } from '../api/endpoints';
import { Amount, LoadError, Loading, TextField } from '../components/ui';
import { rememberStocks } from '../lib/stockNames';

export function StockSearchPage() {
  const navigate = useNavigate();
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
      <h1>종목 검색</h1>
      <form className="filters" onSubmit={onSubmit}>
        <TextField label="종목 검색어" testid="search-keyword-input" value={input} onChange={setInput} placeholder="종목코드나 이름 일부" />
        <button type="submit" data-testid="search-submit" disabled={!input.trim()}>
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
        <table className="ledger">
          <thead>
            <tr>
              <th>코드</th>
              <th>이름</th>
              <th className="num">현재가</th>
            </tr>
          </thead>
          <tbody>
            {query.data.map((s) => (
              <tr
                key={s.id}
                data-testid="search-row"
                data-id={s.id}
                aria-label={`${s.name} ${s.code}`}
                tabIndex={0}
                className="clickable"
                onClick={() => navigate(`/stocks/${s.id}`)}
                onKeyDown={(e) => e.key === 'Enter' && navigate(`/stocks/${s.id}`)}
              >
                <td data-testid="search-row-code">{s.code}</td>
                <td data-testid="search-row-name">{s.name}</td>
                <td className="num">
                  <Amount value={s.currentPrice} testid="search-row-price" />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
