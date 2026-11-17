// S-21 비밀번호 재설정(로그인 전, 2차). 화면 구성만 둠
// 발송·인증·재설정 API(N-14)가 아직 없어 모든 입력과 버튼을 막고 "준비 중"으로 보임. 요청을 하나도 보내지 않음
// API가 생기면 SignupPage의 문자 인증 흐름(useSmsCode)과 같은 방식으로 붙임

import { Link } from 'react-router';
import { Mark } from '../components/Layout';
import { PageHead, TextField } from '../components/ui';

const noop = () => {};

export function ResetPasswordPage() {
  return (
    <main className="auth">
      <Mark />
      <PageHead title="비밀번호 재설정" sub="가입한 전화번호로 문자 인증을 하고 새 비밀번호를 정해요" badge={<span className="badge badge-later">2차 · 준비 중</span>} />
      <p className="banner banner-later" data-testid="reset-preparing">
        아직 문자 인증으로 비밀번호를 바꾸는 서버 기능이 없어요. 지금은 화면만 미리 볼 수 있어요
      </p>
      <ol className="steps" data-testid="reset-steps">
        <li aria-current="step">1 전화번호</li>
        <li>2 인증번호</li>
        <li>3 새 비밀번호</li>
        <li>끝</li>
      </ol>

      <section className="panel" aria-label="1 전화번호">
        <TextField label="가입한 전화번호" testid="reset-phone-input" inputMode="tel" value="" onChange={noop} disabled placeholder="010-0000-0000" />
        <button type="button" className="primary block" data-testid="reset-send-code" disabled>
          인증번호 받기
        </button>
        <p className="hint">가입하지 않은 번호여도 같은 화면으로 넘어가요. 가입 여부는 알려 드리지 않아요</p>
      </section>

      <section className="panel" aria-label="2 인증번호">
        <div className="section-head">
          <h2>인증번호</h2>
          <span className="hint" data-testid="reset-code-timer">
            남은 시간 2:00
          </span>
        </div>
        <TextField label="인증번호" testid="reset-code-input" inputMode="numeric" value="" onChange={noop} disabled />
        <div className="actions">
          <button type="button" data-testid="reset-resend" disabled>
            인증번호 다시 받기
          </button>
          <button type="button" className="soft" data-testid="reset-verify" disabled>
            확인
          </button>
        </div>
      </section>

      <section className="panel" aria-label="3 새 비밀번호">
        <TextField label="새 로그인 비밀번호" testid="reset-new-pw-input" type="password" value="" onChange={noop} disabled />
        <TextField label="새 비밀번호 확인" testid="reset-new-pw-confirm-input" type="password" value="" onChange={noop} disabled />
        <button type="button" className="primary block" data-testid="reset-submit" disabled>
          비밀번호 재설정
        </button>
      </section>

      <nav className="auth-links" aria-label="계정">
        <Link to="/login" data-testid="reset-login-link">
          로그인하러 가기
        </Link>
      </nav>
    </main>
  );
}
