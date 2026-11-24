// 화면 설정 메뉴: 잔액 숨기기, 큰 글씨 모드, 로그아웃(설계 3.15에서 추가한 요소)
// 보기 설정은 이 기기 브라우저에만 남김(localStorage). 저장이 막힌 브라우저에서도 화면은 그대로 동작함

import { useEffect, useRef, useState } from 'react';
import { IconEye, IconLogout, IconSliders, IconText } from './icons';

type Prefs = { privacy: boolean; large: boolean };
const KEY = 'sa-display';

function readPrefs(): Prefs {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<Prefs>;
    return { privacy: v.privacy === true, large: v.large === true };
  } catch {
    return { privacy: false, large: false };
  }
}

function apply(p: Prefs) {
  const root = document.documentElement;
  root.classList.toggle('privacy', p.privacy);
  root.classList.toggle('text-large', p.large);
}

/** 첫 화면을 그리기 전에 불러 깜빡임을 막음(main.tsx) */
export function applyDisplayPrefs() {
  apply(readPrefs());
}

export function DisplayMenu({ onLogout }: { onLogout: () => void }) {
  const [prefs, setPrefs] = useState(readPrefs);
  const ref = useRef<HTMLDetailsElement>(null);

  useEffect(() => {
    apply(prefs);
    try {
      localStorage.setItem(KEY, JSON.stringify(prefs));
    } catch {
      // 저장이 막혀도 이번 화면에는 적용됨
    }
  }, [prefs]);

  // 메뉴 밖을 누르거나 Esc를 누르면 닫음
  useEffect(() => {
    const close = (e: Event) => {
      const el = ref.current;
      if (!el?.open) return;
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !el.contains(e.target as Node)) el.open = false;
    };
    document.addEventListener('pointerdown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('pointerdown', close);
      document.removeEventListener('keydown', close);
    };
  }, []);

  return (
    <details className="display-menu" ref={ref}>
      {/* 휴대폰에서는 글자를 숨기고 아이콘만 보여서 이름을 aria-label로 고정함 */}
      <summary data-testid="app-display-menu" aria-label="화면 설정">
        <IconSliders />
        <span className="menu-text">화면 설정</span>
      </summary>
      <div className="menu-pop">
        <button
          type="button"
          data-testid="app-privacy-toggle"
          aria-pressed={prefs.privacy}
          onClick={() => setPrefs((p) => ({ ...p, privacy: !p.privacy }))}
        >
          <span className="menu-left">
            <IconEye />
            잔액 숨기기
          </span>
          <span className="state-text">{prefs.privacy ? '켬' : '끔'}</span>
        </button>
        <button type="button" data-testid="app-text-size-toggle" aria-pressed={prefs.large} onClick={() => setPrefs((p) => ({ ...p, large: !p.large }))}>
          <span className="menu-left">
            <IconText />
            큰 글씨 모드
          </span>
          <span className="state-text">{prefs.large ? '켬' : '끔'}</span>
        </button>
        <hr />
        <button type="button" data-testid="app-logout" onClick={onLogout}>
          <span className="menu-left">
            <IconLogout />
            로그아웃
          </span>
        </button>
      </div>
    </details>
  );
}
