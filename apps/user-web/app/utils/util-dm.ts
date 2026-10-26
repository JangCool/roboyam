import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';
import type { DmCanSend, DmMessage, DmPartner, DmReason, DmThreadItem } from '~/composables/dm/api/useDmApi.types';

/*
 * 쪽지 화면의 계산 — 주소·목록 합치기(주기 조회)·날짜 칩 줄 세우기·본문의 링크 쪼개기·진행 막대·프로필 단추 갈래(community `UtilDm`·`UtilDmProfile`).
 * 입력만 보고 답하는 순수 함수만 둔다. 화면 문구는 `UtilDmMessages`(`t` 를 받는다)다.
 * 쪽지 본문은 **일반 글자**다 — 화면은 `v-html` 을 쓰지 않고, `linkify` 가 쪼갠 조각에 `<a>` 를 끼워 그린다.
 */

/** 전송하지 못해 화면에만 남은 쪽지 — 서버에는 없다 */
export interface DmFailedMessage {
    key: string;
    body: string;
    createdAt: string;
    retrying: boolean;
}

/** 대화방 한 줄 — 날짜 칩 · 쪽지 · 전송 실패한 쪽지 */
export type DmTimelineRow =
    | { kind: 'day'; key: string; label: string }
    | { kind: 'message'; key: string; message: DmMessage }
    | { kind: 'failed'; key: string; failed: DmFailedMessage };

/** 본문 한 조각 — `href` 가 있으면 링크 */
export interface DmTextPart {
    text: string;
    href: string | null;
}

/** 대화방에 주기 조회로 받은 첫 쪽을 합친 결과 */
export interface DmMergeResult {
    messages: DmMessage[];
    added: number;
    /** 받은 쪽과 겹치지 않아 가진 것을 버리고 받은 쪽으로 바꿨는가 */
    replaced: boolean;
}

/** 쪽지함에 주기 조회로 받은 첫 쪽을 합친 결과 */
export interface DmThreadMerge {
    items: DmThreadItem[];
    /** 더 오래된 쪽까지 불러 둔 것이 남았는가 — 그러면 「더 보기」 커서를 그대로 둔다 */
    keptOlder: boolean;
}

/** 가입 후 일수 진행 */
export interface DmJoinProgress {
    day: number;
    left: number;
    percent: number;
}

/** 프로필의 쪽지 단추가 어떻게 보이는가 — 숨김 · 누를 수 있음(가는 곳) · 못 보냄(이유) */
export type DmProfileButton = { kind: 'hidden' } | { kind: 'enabled'; to: string } | { kind: 'disabled'; reason: DmReason | null };

const DAY_MS = 86_400_000;
const PREVIEW_CHARS = 40;
const URL_PATTERN = /(?:https?:\/\/|(?<![A-Za-z0-9])www\.)[^\s<>"'`]+/gi;
const TRAILING_PUNCTUATION = /[.,;:!?'"”’」』…>\]}]$/;
const HAS_HOST = /^(?:https?:\/\/|www\.)[^\s/?#]/i;

function stripTrailing(url: string): string {
    let out = url;
    for (;;) {
        if (out.endsWith(')') && (out.match(/\)/g)?.length ?? 0) <= (out.match(/\(/g)?.length ?? 0)) break;
        if (out.endsWith(')') || TRAILING_PUNCTUATION.test(out)) {
            out = out.slice(0, -1);
            continue;
        }
        break;
    }
    return out;
}

export default class UtilDm {
    /** 쪽지함이 한 번에 받는 대화 수(서버 기본값) */
    static readonly INBOX_PAGE_SIZE = 20;
    /** 대화방이 한 번에 받는 쪽지 수(서버 기본값) */
    static readonly ROOM_PAGE_SIZE = 30;
    /** 쪽지함을 다시 받는 간격(보이는 동안) */
    static readonly INBOX_POLL_MS = 60_000;
    /** 열려 있는 대화방을 다시 받는 간격(탭이 보이는 동안) */
    static readonly ROOM_POLL_MS = 10_000;
    /** 맨 아래에서 이만큼 안이면 맨 아래를 보고 있다고 본다(px) */
    static readonly BOTTOM_THRESHOLD_PX = 80;
    /** 대화방 입력창이 늘어나는 최대 줄 수 */
    static readonly COMPOSE_MAX_LINES = 5;
    /** 안 읽은 수가 이보다 크면 "99+" */
    static readonly BADGE_MAX = 99;

    static readonly INBOX_PATH = '/messages';
    static readonly SETTINGS_PATH = '/settings/messages';

    /**
     * 대화방 주소 — ID 는 문자열 그대로.
     * @param {string} id - 대화 ID.
     * @returns {string} `/messages/{id}`.
     */
    static threadPath(id: string): string {
        return `/messages/${encodeURIComponent(id)}`;
    }

    /**
     * 새 쪽지 주소 — 받는 사람을 알면 `?to=` 로 미리 고른다.
     * @param {string | null} to - 받는 사람 회원 ID.
     * @returns {string} `/messages/new` 또는 `/messages/new?to={id}`.
     */
    static newPath(to?: string | null): string {
        return to ? `/messages/new?to=${encodeURIComponent(to)}` : '/messages/new';
    }

    /**
     * Snowflake 십진 문자열 비교 — 자리 수가 많으면 크다(숫자로 바꾸지 않는다).
     * @param {string} a - 앞.
     * @param {string} b - 뒤.
     * @returns {number} a 가 작으면 음수.
     */
    static compareId(a: string, b: string): number {
        if (a.length !== b.length) return a.length < b.length ? -1 : 1;
        return a < b ? -1 : a > b ? 1 : 0;
    }

    /**
     * 쪽지를 오래된 것이 앞으로 — 서버는 최근이 앞으로 준다.
     * @param {DmMessage[]} items - 쪽지.
     * @returns {DmMessage[]} 새 배열.
     */
    static chronological(items: readonly DmMessage[]): DmMessage[] {
        return [...items].sort((a, b) => UtilDm.compareId(a.id, b.id));
    }

    /**
     * 주기 조회로 받은 첫 쪽(최근)을 합친다 — ID 로 겹침 제거. 하나도 안 겹치는데 더 오래된 쪽이 남았으면 받은 쪽으로 바꾼다(구멍 방지).
     * @param {DmMessage[]} current - 화면의 쪽지(오래된 것이 앞).
     * @param {DmMessage[]} incomingNewestFirst - 받은 첫 쪽(최근이 앞).
     * @param {boolean} incomingHasNext - 받은 쪽보다 오래된 쪽지가 더 있는가.
     * @returns {DmMergeResult} 합친 결과.
     */
    static mergeLatest(current: readonly DmMessage[], incomingNewestFirst: readonly DmMessage[], incomingHasNext: boolean): DmMergeResult {
        const incoming = UtilDm.chronological(incomingNewestFirst);
        if (current.length === 0) return { messages: incoming, added: incoming.length, replaced: false };
        const have = new Set(current.map((m) => m.id));
        const overlaps = incoming.some((m) => have.has(m.id));
        const newestHave = current[current.length - 1]!.id;
        const newestIncoming = incoming[incoming.length - 1]?.id;
        if (!overlaps && incomingHasNext && newestIncoming !== undefined && UtilDm.compareId(newestIncoming, newestHave) > 0) {
            return { messages: incoming, added: incoming.length, replaced: true };
        }
        const fresh = incoming.filter((m) => !have.has(m.id));
        if (fresh.length === 0) return { messages: current as DmMessage[], added: 0, replaced: false };
        return { messages: UtilDm.chronological([...current, ...fresh]), added: fresh.length, replaced: false };
    }

    /**
     * 「이전 쪽지 더 보기」로 받은 쪽을 앞에 붙인다.
     * @param {DmMessage[]} current - 화면의 쪽지.
     * @param {DmMessage[]} olderNewestFirst - 더 오래된 쪽(최근이 앞).
     * @returns {DmMessage[]} 합친 배열.
     */
    static mergeOlder(current: readonly DmMessage[], olderNewestFirst: readonly DmMessage[]): DmMessage[] {
        const have = new Set(current.map((m) => m.id));
        return UtilDm.chronological([...current, ...olderNewestFirst.filter((m) => !have.has(m.id))]);
    }

    /**
     * 방금 보낸 쪽지를 맨 아래에 — 이미 있으면 그대로.
     * @param {DmMessage[]} current - 화면의 쪽지.
     * @param {DmMessage} message - 보낸 쪽지.
     * @returns {DmMessage[]} 새 배열.
     */
    static appendMessage(current: readonly DmMessage[], message: DmMessage): DmMessage[] {
        if (current.some((m) => m.id === message.id)) return current as DmMessage[];
        return UtilDm.chronological([...current, message]);
    }

    /**
     * 상대가 보낸 가장 최근 쪽지 — 읽음 처리가 필요한지 가른다.
     * @param {DmMessage[]} messages - 화면의 쪽지.
     * @returns {DmMessage | null} 쪽지.
     */
    static latestFromPartner(messages: readonly DmMessage[]): DmMessage | null {
        for (let i = messages.length - 1; i >= 0; i--) if (!messages[i]!.mine) return messages[i]!;
        return null;
    }

    /**
     * 쪽지함 미리보기 — 서버와 같은 규칙(줄바꿈·연속 공백은 한 칸, 앞 40자, 넘으면 …).
     * @param {string} body - 본문.
     * @returns {string} 한 줄.
     */
    static preview(body: string): string {
        const flat = (body ?? '').replace(/\s+/g, ' ').trim();
        const chars = Array.from(flat);
        return chars.length <= PREVIEW_CHARS ? flat : `${chars.slice(0, PREVIEW_CHARS).join('')}…`;
    }

    /**
     * 안 읽은 수 뱃지 글자 — 0 이면 빈 문자열, 99 를 넘으면 "99+".
     * @param {number} count - 수.
     * @returns {string} 글자.
     */
    static unreadText(count: number): string {
        const n = Number.isFinite(count) ? Math.max(0, Math.floor(count)) : 0;
        if (n === 0) return '';
        return n > UtilDm.BADGE_MAX ? `${UtilDm.BADGE_MAX}+` : String(n);
    }

    /**
     * 같은 날인지 가르는 열쇠 — 표시 시간대의 날짜 "2026.09.29".
     * @param {string | null | undefined} iso - 시각.
     * @param {DisplayTime} time - 표시 시각 함수.
     * @returns {string} 열쇠(시각이 없으면 빈 문자열).
     */
    static dayKey(iso: string | null | undefined, time: DisplayTime): string {
        const p = time.parts(iso);
        return p ? `${p.y}.${String(p.mo).padStart(2, '0')}.${String(p.d).padStart(2, '0')}` : '';
    }

    /**
     * 표시 시간대 달력으로 두 시각이 며칠 차이인가(오늘 0 · 어제 1).
     * @param {string | null | undefined} iso - 볼 시각.
     * @param {DisplayTime} time - 표시 시각 함수.
     * @param {number} now - 지금(밀리초).
     * @returns {number | null} 일 수(시각이 없으면 null).
     */
    static daysAgo(iso: string | null | undefined, time: DisplayTime, now: number = Date.now()): number | null {
        const then = time.parts(iso);
        const today = time.parts(new Date(now).toISOString());
        if (!then || !today) return null;
        return Math.floor(Date.UTC(today.y, today.mo - 1, today.d) / DAY_MS) - Math.floor(Date.UTC(then.y, then.mo - 1, then.d) / DAY_MS);
    }

    /**
     * 대화방에 그릴 줄들 — 날짜가 바뀔 때마다 날짜 칩, 전송 실패한 쪽지는 맨 아래.
     * @param {DmMessage[]} messages - 쪽지(오래된 것이 앞).
     * @param {DmFailedMessage[]} failed - 화면에만 남은 쪽지.
     * @param {DisplayTime} time - 표시 시각 함수.
     * @param {(iso: string) => string} dayLabel - 날짜 칩 글자(`UtilDmMessages.dayLabel` 을 묶어 넘긴다).
     * @returns {DmTimelineRow[]} 위에서 아래로 그릴 줄.
     */
    static timeline(messages: readonly DmMessage[], failed: readonly DmFailedMessage[], time: DisplayTime, dayLabel: (iso: string) => string): DmTimelineRow[] {
        const rows: DmTimelineRow[] = [];
        let lastDay = '';
        const chipFor = (iso: string) => {
            const day = UtilDm.dayKey(iso, time);
            if (day && day !== lastDay) {
                lastDay = day;
                rows.push({ kind: 'day', key: `day-${day}`, label: dayLabel(iso) });
            }
        };
        for (const message of messages) {
            chipFor(message.createdAt);
            rows.push({ kind: 'message', key: message.id, message });
        }
        for (const f of failed) {
            chipFor(f.createdAt);
            rows.push({ kind: 'failed', key: `failed-${f.key}`, failed: f });
        }
        return rows;
    }

    /**
     * 본문을 글자·링크 조각으로 — http(s)://·www. 만 링크, 끝의 문장부호는 뺀다(`v-html` 금지).
     * @param {string} text - 본문.
     * @returns {DmTextPart[]} 조각(이어 붙이면 원문).
     */
    static linkify(text: string): DmTextPart[] {
        if (!text) return [];
        const parts: DmTextPart[] = [];
        let last = 0;
        for (const match of text.matchAll(URL_PATTERN)) {
            const url = stripTrailing(match[0]);
            if (!HAS_HOST.test(url)) continue;
            const start = match.index ?? 0;
            if (start < last) continue;
            if (start > last) parts.push({ text: text.slice(last, start), href: null });
            parts.push({ text: url, href: /^https?:\/\//i.test(url) ? url : `https://${url}` });
            last = start + url.length;
        }
        if (last < text.length) parts.push({ text: text.slice(last), href: null });
        return parts;
    }

    /**
     * 스크롤이 맨 아래 근처인가.
     * @param {number} scrollHeight - 전체 높이.
     * @param {number} scrollTop - 위에서 내려온 거리.
     * @param {number} clientHeight - 보이는 높이.
     * @returns {boolean} 맨 아래를 보고 있으면 true.
     */
    static isNearBottom(scrollHeight: number, scrollTop: number, clientHeight: number): boolean {
        return scrollHeight - scrollTop - clientHeight <= UtilDm.BOTTOM_THRESHOLD_PX;
    }

    /**
     * 가입 후 일수 진행 — 보낼 수 있게 되는 날까지.
     * @param {string | null | undefined} availableFrom - 보낼 수 있게 되는 시각.
     * @param {number} minDays - 일수(7).
     * @param {number} now - 지금(밀리초).
     * @returns {DmJoinProgress | null} 진행.
     */
    static joinProgress(availableFrom: string | null | undefined, minDays: number, now: number = Date.now()): DmJoinProgress | null {
        const ms = availableFrom ? Date.parse(availableFrom) : Number.NaN;
        if (Number.isNaN(ms) || minDays <= 0) return null;
        const left = Math.max(0, Math.min(minDays, Math.ceil((ms - now) / DAY_MS)));
        const day = minDays - left;
        return { day, left, percent: Math.round((day / minDays) * 100) };
    }

    /**
     * 쪽지함 줄을 더하거나 고친다 — 마지막 쪽지가 최근인 순서로.
     * @param {DmThreadItem[]} items - 줄들.
     * @param {DmThreadItem} row - 더하거나 바꿀 줄.
     * @returns {DmThreadItem[]} 새 배열.
     */
    static upsertThread(items: readonly DmThreadItem[], row: DmThreadItem): DmThreadItem[] {
        return [...items.filter((t) => t.id !== row.id), row].sort((a, b) => UtilDm.compareId(b.last.id, a.last.id));
    }

    /**
     * 주기 조회로 받은 쪽지함 첫 쪽을 합친다 — 받은 쪽이 앞, 더 오래된 쪽까지 불러 둔 줄은 뒤에 남긴다.
     * @param {DmThreadItem[]} current - 지금 줄들.
     * @param {DmThreadItem[]} incoming - 받은 첫 쪽.
     * @param {boolean} incomingHasNext - 첫 쪽 뒤에 더 있는가.
     * @returns {DmThreadMerge} 합친 결과.
     */
    static mergeThreads(current: readonly DmThreadItem[], incoming: readonly DmThreadItem[], incomingHasNext: boolean): DmThreadMerge {
        if (!incomingHasNext || incoming.length === 0) return { items: [...incoming], keptOlder: false };
        const boundary = incoming[incoming.length - 1]!.last.id;
        const ids = new Set(incoming.map((t) => t.id));
        const older = current.filter((t) => !ids.has(t.id) && UtilDm.compareId(t.last.id, boundary) < 0);
        return { items: [...incoming, ...older], keptOlder: older.length > 0 };
    }

    /**
     * 읽음 처리 뒤의 쪽지함 — 그 대화의 안 읽은 수를 0 으로.
     * @param {DmThreadItem[]} items - 줄들.
     * @param {string} id - 읽은 대화.
     * @returns {DmThreadItem[]} 바뀐 배열(바뀐 것이 없으면 그대로).
     */
    static withRead(items: DmThreadItem[], id: string): DmThreadItem[] {
        if (!items.some((t) => t.id === id && t.unreadCount > 0)) return items;
        return items.map((t) => (t.id === id ? { ...t, unreadCount: 0 } : t));
    }

    /**
     * 대화 상대를 작성자 표시(`PostAuthorLabel`)의 모양으로 — 탈퇴한 회원은 회원 ID·등급 없이 이름만(프로필 링크 없음). 메모는 그대로 싣는다.
     * @param {DmPartner} partner - 상대.
     * @returns {PostAuthor} 작성자 표시.
     */
    static partnerAuthor(partner: DmPartner): PostAuthor {
        return {
            kind: 'MEMBER',
            memberId: partner.withdrawn ? null : partner.id,
            name: partner.nickname,
            grade: partner.withdrawn ? null : partner.grade,
            gradeLabel: partner.withdrawn ? null : partner.gradeLabel,
            role: partner.role,
            ipMasked: null,
            memo: partner.memo,
            // 닉네임 색(포인트 상점) — 탈퇴한 상대는 장식 없음
            decoration: partner.withdrawn ? null : (partner.decoration ?? null),
            avatarUrl: partner.withdrawn ? null : partner.avatarUrl,
        };
    }

    /**
     * 프로필의 쪽지 단추가 갈 곳 — 대화가 있으면 그 대화방, 없으면 새 쪽지(받는 사람을 미리 고른 채).
     * @param {string} userId - 받을 회원.
     * @param {string | null} threadId - 두 사람 사이의 대화.
     * @returns {string} 주소.
     */
    static composePath(userId: string, threadId: string | null | undefined): string {
        return threadId ? UtilDm.threadPath(threadId) : UtilDm.newPath(userId);
    }

    /**
     * 「이 회원에게 보낼 수 있나」를 단추 모양으로 — 못 읽었으면 숨김, `SELF` 도 숨김(내 프로필은 「쪽지함」 단추).
     * @param {DmCanSend | null | undefined} info - `GET /api/users/{id}/dm` 응답.
     * @param {string} userId - 프로필의 회원 ID.
     * @returns {DmProfileButton} 갈래.
     */
    static button(info: DmCanSend | null | undefined, userId: string): DmProfileButton {
        if (!info) return { kind: 'hidden' };
        if (info.canSend) return { kind: 'enabled', to: UtilDm.composePath(userId, info.threadId) };
        if (info.reason === 'SELF') return { kind: 'hidden' };
        return { kind: 'disabled', reason: info.reason };
    }
}
