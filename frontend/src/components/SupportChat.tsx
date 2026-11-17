// 상담하기 버튼과 대화창(설계 3.15에서 추가한 요소). 자동응답 도움말과 상담사 연결 요청 두 갈래
// 서버 호출은 api/assistant.ts 한 곳에만 둠. 지금은 시연용이라 실제 상담사에게 가지 않음

import { useEffect, useRef, useState, type FormEvent } from 'react';
import { askAssistant, requestAgent, TOPICS, type Topic } from '../api/assistant';
import { IconChat, IconClose } from './icons';

type Message = { who: 'bot' | 'me' | 'system'; text: string };

const GREETING: Message = { who: 'bot', text: '안녕하세요. 궁금한 주제를 고르거나 질문을 적어 주세요.' };

export function SupportChat() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([GREETING]);
  const [text, setText] = useState('');
  const [agent, setAgent] = useState<'none' | 'preparing'>('none');
  const launchRef = useRef<HTMLButtonElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const wasOpen = useRef(false);

  useEffect(() => {
    if (open) closeRef.current?.focus();
    else if (wasOpen.current) launchRef.current?.focus();
    wasOpen.current = open;
  }, [open]);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [messages]);

  async function ask(input: { topic: Topic; label: string } | { text: string }) {
    const mine = 'topic' in input ? input.label : input.text;
    setMessages((m) => [...m, { who: 'me', text: mine }]);
    const reply = await askAssistant('topic' in input ? { topic: input.topic } : { text: input.text });
    setMessages((m) => [...m, { who: 'bot', text: reply.text }]);
  }

  async function onAgent() {
    setMessages((m) => [...m, { who: 'me', text: '상담사 연결' }]);
    const r = await requestAgent();
    setAgent(r.status);
    setMessages((m) => [...m, { who: 'system', text: r.message }]);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const t = text.trim();
    if (!t) return;
    setText('');
    void ask({ text: t });
  }

  return (
    <>
      {!open && (
        <button
          type="button"
          ref={launchRef}
          className="chat-launch"
          data-testid="support-launch"
          aria-label="상담하기"
          aria-expanded={false}
          aria-controls="support-window"
          onClick={() => setOpen(true)}
        >
          <span className="chat-icon">
            <IconChat />
          </span>
          <span className="chat-text">상담하기</span>
        </button>
      )}
      {open && (
        <section
          className="chat-window"
          id="support-window"
          data-testid="support-window"
          role="dialog"
          aria-labelledby="support-title"
          onKeyDown={(e) => e.key === 'Escape' && setOpen(false)}
        >
          <div className="chat-head">
            <div>
              <h2 id="support-title">상담하기</h2>
              <span className="badge badge-demo">시연용 자동응답</span>
            </div>
            <button type="button" ref={closeRef} className="chat-close" data-testid="support-close" aria-label="상담창 닫기" onClick={() => setOpen(false)}>
              <IconClose />
            </button>
          </div>
          <div className="chat-log" ref={logRef} role="log" aria-live="polite" data-testid="support-log">
            {messages.map((m, i) => (
              <p key={i} className={`bubble ${m.who === 'me' ? 'mine' : m.who === 'system' ? 'system' : ''}`}>
                {m.text}
              </p>
            ))}
          </div>
          <div className="chat-topics">
            {TOPICS.map((t) => (
              <button key={t.key} type="button" className="small" onClick={() => void ask({ topic: t.key, label: t.label })}>
                {t.label}
              </button>
            ))}
            <button type="button" className="small" data-testid="support-agent" disabled={agent === 'preparing'} onClick={() => void onAgent()}>
              {agent === 'preparing' ? '상담사 연결 준비 중' : '상담사 연결'}
            </button>
          </div>
          <form className="chat-form" onSubmit={onSubmit}>
            <div className="field">
              <label htmlFor="support-input">질문 입력</label>
              <div className="field-input">
                <input id="support-input" data-testid="support-input" autoComplete="off" value={text} onChange={(e) => setText(e.target.value)} placeholder="질문을 적어 주세요" />
              </div>
            </div>
            <button type="submit" className="primary" data-testid="support-send" disabled={!text.trim()}>
              보내기
            </button>
          </form>
          <p className="chat-foot">계좌 비밀번호·인증번호는 적지 마세요. 실제 상담사에게 연결되지 않아요.</p>
        </section>
      )}
    </>
  );
}
