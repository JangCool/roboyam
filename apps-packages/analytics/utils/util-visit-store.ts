import UtilVisitSignal from './util-visit-signal';

/**
 * 방문자 ID·세션을 브라우저에 둔다 — 신호 규칙은 `util-visit-signal.ts`, 보내기는 `util-visit-transport.ts`.
 *  - 방문자 ID: 무작위 UUID 를 로컬 스토리지에(날마다 바뀌지 않아 신규·재방문을 가른다 — GA 의 쿠키 ID 와 같은 역할). 못 쓰면 세션 저장소 → 메모리.
 *  - 세션: 탭 단위(세션 저장소, 못 쓰면 메모리) — 마지막 신호에서 30분이 지나면 새 세션.
 * 브라우저 저장소 식별값이므로 개인정보 처리방침에 적어야 한다(계약 「방문자 식별」 — 운영자가 약관 화면에서 넣는다).
 * 수집이 꺼져 있으면 이 파일을 부르지 않는다 — 꺼진 사이트의 브라우저에는 아무 값도 남지 않는다.
 *  - 이용자 선택권(2단계): 「수집 거부」 표시(로컬 스토리지 — 거부하면 방문자 ID·세션도 지운다) · 방문자 ID 엿보기(만들지 않음) · 지우기. 처리방침 화면(user-web `pages/privacy.vue`)이 쓴다.
 * 이 파일은 `vue`·Nuxt 자동 임포트를 쓰지 않는다.
 */

/** 로컬 스토리지(또는 대신 세션 저장소)의 방문자 ID 키 */
const VISITOR_KEY = 'roboyam:analytics:vid';

/** 세션 저장소의 세션 키 — `{ id, lastAt, lastPath }` JSON */
const SESSION_KEY = 'roboyam:analytics:session';

/** 로컬 스토리지(또는 대신 세션 저장소)의 「이 브라우저는 방문 통계 수집 거부」 표시 — 값 `'1'`. 처리방침 화면의 스위치가 바꾼다 */
const OPT_OUT_KEY = 'roboyam:analytics:optout';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** 탭의 세션 — 마지막으로 보낸 신호의 시각·경로를 같이 둔다(직전 경로 · 2초 겹침 · 30분 쉼 판정) */
export interface VisitSession {
    id: string;
    /** 마지막으로 보낸 신호 시각(ms) */
    lastAt: number;
    /** 마지막으로 보낸 경로 — 아직 보낸 적 없으면 null */
    lastPath: string | null;
}

/** 저장소를 못 쓸 때의 대신 자리(이 탭이 열려 있는 동안만) */
const memory: { visitor: string | null; session: VisitSession | null; optOut: boolean } = { visitor: null, session: null, optOut: false };

/** 저장소 하나를 꺼낸다 — 막혀 있으면(사생활 보호 모드·쿠키 차단) 접근만으로 던지는 브라우저가 있다 */
function storage(kind: 'local' | 'session'): Storage | null {
    try {
        return kind === 'local' ? window.localStorage : window.sessionStorage;
    } catch {
        return null;
    }
}

function read(kind: 'local' | 'session', key: string): string | null {
    try {
        return storage(kind)?.getItem(key) ?? null;
    } catch {
        return null;
    }
}

/** 써 보고 다시 읽어 맞으면 true — 용량 초과·차단이면 false */
function write(kind: 'local' | 'session', key: string, value: string): boolean {
    try {
        const s = storage(kind);
        if (!s) return false;
        s.setItem(key, value);
        return s.getItem(key) === value;
    } catch {
        return false;
    }
}

/** 무작위 UUID(v4) — `crypto.randomUUID` 가 없는 곳(보안 연결이 아닌 주소·옛 브라우저)은 `getRandomValues` 로 같은 모양을 만든다 */
function newUuid(): string {
    if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6]! & 0x0f) | 0x40;
    bytes[8] = (bytes[8]! & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function remove(kind: 'local' | 'session', key: string): void {
    try {
        storage(kind)?.removeItem(key);
    } catch {
        // 막힌 저장소 — 지울 것도 없다
    }
}

function readSession(raw: string | null): VisitSession | null {
    if (!raw) return null;
    try {
        const value: unknown = JSON.parse(raw);
        if (typeof value !== 'object' || value === null) return null;
        const { id, lastAt, lastPath } = value as Record<string, unknown>;
        if (typeof id !== 'string' || !UUID.test(id) || typeof lastAt !== 'number' || !Number.isFinite(lastAt)) return null;
        return { id, lastAt, lastPath: typeof lastPath === 'string' ? lastPath : null };
    } catch {
        return null;
    }
}

export default class UtilVisitStore {
    /**
     * 방문자 ID — 있으면 그대로, 없으면 만들어 둔다(로컬 스토리지 → 세션 저장소 → 메모리 순으로 쓸 수 있는 곳에).
     * @returns `id` 방문자 ID · `created` 이번에 처음 만들었다(이 값이 true 인 신호가 「신규 방문자」).
     */
    static visitor(): { id: string; created: boolean } {
        for (const kind of ['local', 'session'] as const) {
            const saved = read(kind, VISITOR_KEY);
            if (saved && UUID.test(saved)) return { id: saved, created: false };
        }
        if (memory.visitor) return { id: memory.visitor, created: false };

        const id = newUuid();
        if (!write('local', VISITOR_KEY, id) && !write('session', VISITOR_KEY, id)) memory.visitor = id;
        return { id, created: true };
    }

    /**
     * 이 탭의 세션 — 없거나 30분 쉬었으면 새로 연다(아직 저장하지 않는다 — 신호를 보낼 때 `saveSession`).
     * @param {number} now - 지금(ms).
     * @returns `session` 세션 · `started` 이번에 새로 열었다(이번 신호가 세션 첫 페이지).
     */
    static session(now: number): { session: VisitSession; started: boolean } {
        const saved = readSession(read('session', SESSION_KEY)) ?? memory.session;
        if (saved && !UtilVisitSignal.isSessionOver(saved.lastAt, now)) return { session: saved, started: false };
        return { session: { id: newUuid(), lastAt: now, lastPath: null }, started: true };
    }

    /**
     * 신호를 보낸 뒤 세션을 적는다(마지막 시각·경로).
     * @param {VisitSession} session - 적을 세션.
     */
    static saveSession(session: VisitSession): void {
        if (write('session', SESSION_KEY, JSON.stringify(session))) {
            memory.session = null;
            return;
        }
        memory.session = { ...session };
    }

    // ── 이용자 선택권(2단계 2026-11-04 — 개인정보 처리방침 화면의 「방문 통계」 칸) ──

    /**
     * 지금 방문자 ID — **만들지 않고** 읽기만 한다(처리방침 화면의 「방문 기록 지우기」가 서버에 보낼 값).
     * @returns {string | null} 방문자 ID. 이 브라우저에 아직 없으면 null(지울 기록도 없다).
     */
    static peekVisitorId(): string | null {
        for (const kind of ['local', 'session'] as const) {
            const saved = read(kind, VISITOR_KEY);
            if (saved && UUID.test(saved)) return saved;
        }
        return memory.visitor;
    }

    /**
     * 이 브라우저의 방문자 ID·세션을 지운다 — 다음 신호(수집 중이고 거부하지 않았을 때)는 새 방문자 ID 로 시작한다(서버가 보기에 새 방문자).
     */
    static forget(): void {
        remove('local', VISITOR_KEY);
        remove('session', VISITOR_KEY);
        remove('session', SESSION_KEY);
        memory.visitor = null;
        memory.session = null;
    }

    /**
     * 이용자가 이 브라우저에서 방문 통계 수집을 거부했나 — 거부했으면 신호를 보내지 않는다(`useVisitTracking`).
     * @returns {boolean} 거부했으면 true.
     */
    static optedOut(): boolean {
        return read('local', OPT_OUT_KEY) === '1' || read('session', OPT_OUT_KEY) === '1' || memory.optOut;
    }

    /**
     * 거부 표시를 바꾼다 — 거부하면 방문자 ID·세션도 지운다(이미 서버에 쌓인 기록은 「방문 기록 지우기」로 따로 지운다).
     * 로컬 스토리지에 못 쓰면 세션 저장소 → 메모리(이 탭에서만) 순으로 둔다.
     * @param {boolean} value - 거부하면 true, 다시 허용하면 false.
     */
    static setOptedOut(value: boolean): void {
        if (value) {
            if (!write('local', OPT_OUT_KEY, '1') && !write('session', OPT_OUT_KEY, '1')) memory.optOut = true;
            UtilVisitStore.forget();
            return;
        }
        remove('local', OPT_OUT_KEY);
        remove('session', OPT_OUT_KEY);
        memory.optOut = false;
    }
}
