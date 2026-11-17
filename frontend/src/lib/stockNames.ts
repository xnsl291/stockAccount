// 종목 이름 임시 저장소(설계 3.9.1). 보유 주식·주문 내역 응답에 종목 ID만 있어 화면에서 본 종목의 이름을 모아 둠
// 화면 표시용일 뿐이라 틀려도 원장에는 영향이 없음. 백엔드에 종목 정보 조회(N-04·N-05)가 생기면 지움

export interface StockLabel {
  id: number;
  code: string;
  name: string;
}

const KEY = 'stockaccount.stockNames';

function readAll(): Record<string, StockLabel> {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? '{}') as Record<string, StockLabel>;
  } catch {
    return {};
  }
}

export function rememberStocks(stocks: StockLabel[]): void {
  if (stocks.length === 0) return;
  const all = readAll();
  for (const s of stocks) all[String(s.id)] = { id: s.id, code: s.code, name: s.name };
  try {
    localStorage.setItem(KEY, JSON.stringify(all));
  } catch {
    // 저장소가 막혀 있어도 화면은 "종목 #ID"로 그리면 됨
  }
}

export function stockLabel(id: number): StockLabel | null {
  return readAll()[String(id)] ?? null;
}

/** 이름을 모르면 "종목 #12" */
export function stockName(id: number): string {
  return stockLabel(id)?.name ?? `종목 #${id}`;
}
