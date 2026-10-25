import type { AccountStatus, Restriction } from '~/composables/auth/api/useAuthApi.types';
import type { AppealRequest, MySanction, MySanctions, SanctionStatus } from '~/composables/sanction/api/useSanctionApi.types';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 제재 규칙 — 로그인 직후 안내 이동·전역 배너, 제재 안내(`/sanction`)·이의제기(`/sanction/appeal`) 화면의 갈래·이의제기 사유 검사. 상태도 요청도 없다.
 * 원본(community) `UtilSanction` 에서 게시판 한정 제재(게시판 지킴이)·소셜 로그인 직후 표시(`LOGIN_MARK_KEY`)·알림 문구 갈래를 뺐다
 * (연결된 소셜로 로그인하면 서버가 바로 `next` 로 보내므로 그 경우의 제재 안내 착륙은 아직 없다. 소셜 가입 마무리는 이 `afterLoginPath` 를 거친다).
 * 문구는 `UtilSanctionMessages`(i18n `sanction.*`), 서버 규칙은 community `docs/api-sanction.md` 1절(후보 서버 클래스 `SanctionPolicy`·`AppealService`).
 */

/** 로그인 뒤 보낼 곳 — 안내가 필요 없으면 그냥 경로(쿼리 포함 문자열), 필요하면 `/sanction?next=<가려던 곳>` */
export type SanctionDestination = string | { path: string; query: Record<string, string> };

/** 제재 안내 화면의 갈래 — 영구 정지 · 기간 정지 · 확인하지 않은 경고 · 제한 없음(이력만) */
export type SanctionScreen = 'BANNED' | 'SUSPENDED' | 'WARNING' | 'CLEAN';

/** 이의제기 화면의 갈래 — 없는 제재 · 접수 폼 · 방금 접수함 · 검토 중 · 인용 · 기각 · 무효가 된 제재 */
export type AppealScreen = 'MISSING' | 'FORM' | 'DONE' | 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'VOIDED';

/** 이의 사유가 걸리는 까닭 */
export type AppealReasonProblem = 'EMPTY' | 'TOO_SHORT' | 'TOO_LONG';

/** 이의제기 접수 실패의 갈래 — 오류 **코드**로 가른다 */
export type AppealOutcome = 'LOGIN' | 'ALREADY' | 'NOT_APPEALABLE' | 'NOT_FOUND' | 'VALIDATION' | 'NETWORK' | 'SERVER' | 'OTHER';

/** 태그 색 */
export type SanctionTone = 'amber' | 'red' | 'green' | 'gray';

const TONE_CLASS: Record<SanctionTone, string> = {
    amber: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
    red: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
    green: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300',
    gray: 'bg-surface-100 text-surface-600 dark:bg-surface-800 dark:text-surface-300',
};

const SUSPEND_DAYS: ReadonlyMap<string, number> = new Map([
    ['SUSPEND_3', 3],
    ['SUSPEND_7', 7],
    ['SUSPEND_30', 30],
]);

function ms(iso: string | null | undefined): number {
    const value = iso ? Date.parse(iso) : Number.NaN;
    return Number.isNaN(value) ? 0 : value;
}

/** 최근 것이 위(같은 시각이면 받은 차례) */
function newestFirst(items: readonly MySanction[]): MySanction[] {
    return items
        .map((item, index) => ({ item, index }))
        .sort((a, b) => ms(b.item.createdAt) - ms(a.item.createdAt) || a.index - b.index)
        .map((x) => x.item);
}

/** 널 문자를 빼고 앞뒤 공백을 뗀 사유 */
function cleanText(text: string): string {
    // eslint-disable-next-line no-control-regex
    return text.replace(/\u0000/g, '').trim();
}

export default class UtilSanction {
    /** 제재 안내 화면 주소 */
    static readonly PATH = '/sanction';

    /**
     * 제재 안내 화면(`/sanction`, `/sanction/…`)의 주소인가
     * @param {string} path - 라우트 경로(쿼리 제외).
     * @returns {boolean} 제재 안내 화면이면 true.
     */
    static isSanctionPath(path: string): boolean {
        return path === UtilSanction.PATH || path.startsWith(`${UtilSanction.PATH}/`);
    }

    /**
     * 이용이 제한된 계정인가 — 기간 정지 중이거나 영구 정지
     * @param {Restriction | null | undefined} restriction - 로그인 정보의 `restriction`.
     * @returns {boolean} 정지·영구 정지면 true.
     */
    static isRestricted(restriction: Restriction | null | undefined): boolean {
        const status: AccountStatus | undefined = restriction?.status;
        return status === 'SUSPENDED' || status === 'BANNED';
    }

    /**
     * 로그인 직후 제재 안내 화면을 먼저 보여야 하는가 — 정지·영구 정지 중이거나 확인하지 않은 경고가 있다
     * @param {Restriction | null | undefined} restriction - 로그인 정보의 `restriction`.
     * @returns {boolean} 안내를 먼저 보여야 하면 true.
     */
    static needsNotice(restriction: Restriction | null | undefined): boolean {
        if (!restriction) return false;
        return UtilSanction.isRestricted(restriction) || (typeof restriction.pendingNotices === 'number' && restriction.pendingNotices > 0);
    }

    /**
     * 로그인이 끝난 뒤 보낼 곳 — 안내가 필요하면(정지·영구 정지 중이거나 확인하지 않은 경고) `/sanction?next=<가려던 곳>`, 아니면 가려던 곳(없거나 위험하면 `/`).
     * 가려던 곳이 이미 제재 안내 화면이면 겹치지 않는다. `next` 는 같은 사이트 안의 경로만 쓴다(`UtilRedirect.safeNext`).
     * @param {Restriction | null | undefined} restriction - 로그인 정보의 `restriction`.
     * @param {unknown} next - 원래 가려던 곳(주소의 `next` 쿼리 값 — 배열일 수도 있다).
     * @returns {SanctionDestination} `navigateTo` 에 넘길 곳.
     */
    static afterLoginPath(restriction: Restriction | null | undefined, next: unknown): SanctionDestination {
        const target = UtilRedirect.safeNext(next);
        const path = target.split(/[?#]/)[0] ?? '/';
        if (!UtilSanction.needsNotice(restriction) || UtilSanction.isSanctionPath(path)) return target;
        return target === '/' ? { path: UtilSanction.PATH, query: {} } : { path: UtilSanction.PATH, query: { next: target } };
    }

    /* ── 제재 안내·이의제기 화면 ── */

    /** 이의제기 화면 주소 */
    static readonly APPEAL_PATH = '/sanction/appeal';
    static readonly APPEAL_REASON_MIN = 10;
    static readonly APPEAL_REASON_MAX = 500;
    /** 정지 전까지 쌓이는 경고 수 */
    static readonly WARNING_LIMIT = 2;

    /**
     * 「확인하고 계속」·「홈으로」 뒤에 갈 곳 — 주소의 `next`(같은 사이트 안의 경로만). 제재 안내 화면 자체는 다시 오지 않게 홈으로 바꾼다
     * @param {unknown} next - 주소의 `next` 쿼리 값.
     * @returns {string} 갈 경로.
     */
    static continuePath(next: unknown): string {
        const target = UtilRedirect.safeNext(next);
        const path = target.split(/[?#]/)[0] ?? '/';
        return UtilSanction.isSanctionPath(path) ? '/' : target;
    }

    /**
     * 이의제기 화면으로 가는 주소
     * @param {string} sanctionId - 제재 ID.
     * @returns {{ path: string; query: { sanctionId: string } }} 주소.
     */
    static appealTo(sanctionId: string): { path: string; query: { sanctionId: string } } {
        return { path: UtilSanction.APPEAL_PATH, query: { sanctionId } };
    }

    /**
     * 주소의 제재 ID — 숫자 글자(Snowflake)만
     * @param {unknown} value - 쿼리 값.
     * @returns {string | null} ID. 없거나 틀리면 null.
     */
    static sanctionIdOf(value: unknown): string | null {
        const raw = Array.isArray(value) ? value[0] : value;
        return typeof raw === 'string' && /^\d{1,20}$/.test(raw) ? raw : null;
    }

    /**
     * 기간 정지의 날 수
     * @param {string} kind - 제재 종류.
     * @returns {number | null} 3·7·30, 기간 정지가 아니면 null.
     */
    static daysOf(kind: string): number | null {
        return SUSPEND_DAYS.get(kind) ?? null;
    }

    /**
     * 종류 태그 색 — 경고는 노랑, 정지는 빨강
     * @param {string} kind - 제재 종류.
     * @returns {SanctionTone} 색.
     */
    static kindTone(kind: string): SanctionTone {
        return kind === 'WARNING' ? 'amber' : 'red';
    }

    /**
     * 상태 태그 색 — 유효는 종류 색, 해제됨은 초록, 끝남·무효는 회색
     * @param {string} kind - 제재 종류.
     * @param {SanctionStatus | string} status - 제재 상태.
     * @returns {SanctionTone} 색.
     */
    static statusTone(kind: string, status: SanctionStatus | string): SanctionTone {
        if (status === 'ACTIVE') return UtilSanction.kindTone(kind);
        if (status === 'LIFTED') return 'green';
        return 'gray';
    }

    /**
     * 이의제기 상태 색 — 인용 초록 · 기각 빨강 · 검토 중 노랑
     * @param {string} status - 이의제기 상태.
     * @returns {SanctionTone} 색.
     */
    static appealTone(status: string): SanctionTone {
        if (status === 'ACCEPTED') return 'green';
        if (status === 'REJECTED') return 'red';
        return 'amber';
    }

    /**
     * 태그 색 클래스
     * @param {SanctionTone} tone - 색.
     * @returns {string} Tailwind 클래스.
     */
    static toneClass(tone: SanctionTone): string {
        return TONE_CLASS[tone];
    }

    /**
     * 확인하지 않은 경고(무효 제외) — 최근 것이 위
     * @param {readonly MySanction[]} items - 이력.
     * @returns {MySanction[]} 확인할 경고들.
     */
    static pendingWarnings(items: readonly MySanction[]): MySanction[] {
        return newestFirst(items.filter((s) => s.kind === 'WARNING' && s.status !== 'VOIDED' && !s.acknowledged));
    }

    /**
     * 안내 갈래 — 영구 정지 → 기간 정지 → 확인하지 않은 경고 → 제한 없음
     * @param {MySanctions} mine - 내 제재.
     * @returns {SanctionScreen} 갈래.
     */
    static screenOf(mine: MySanctions): SanctionScreen {
        if (mine.status === 'BANNED') return 'BANNED';
        if (mine.status === 'SUSPENDED') return 'SUSPENDED';
        return UtilSanction.pendingWarnings(mine.items).length > 0 ? 'WARNING' : 'CLEAN';
    }

    /**
     * 안내가 말하는 제재 하나 — 경고 안내면 가장 최근의 확인할 경고, 정지면 진행 중인 정지
     * @param {MySanctions} mine - 내 제재.
     * @param {SanctionScreen} screen - 갈래.
     * @returns {MySanction | null} 제재. 제한 없음이면 null.
     */
    static focusOf(mine: MySanctions, screen: SanctionScreen): MySanction | null {
        if (screen === 'WARNING') return UtilSanction.pendingWarnings(mine.items)[0] ?? null;
        if (screen === 'SUSPENDED' || screen === 'BANNED') {
            return mine.current ?? newestFirst(mine.items).find((s) => s.kind !== 'WARNING' && s.status === 'ACTIVE') ?? null;
        }
        return null;
    }

    /**
     * 누적 경고 수(0~2) — 서버 값이 없거나 0인데 경고 안내라면 그 경고의 번호로
     * @param {MySanctions} mine - 내 제재.
     * @param {MySanction | null} focus - 안내가 말하는 제재.
     * @returns {number} 0~2.
     */
    static warningCount(mine: MySanctions, focus: MySanction | null = null): number {
        const raw = typeof mine.warningCount === 'number' && Number.isFinite(mine.warningCount) ? Math.floor(mine.warningCount) : 0;
        const count = Math.min(UtilSanction.WARNING_LIMIT, Math.max(0, raw));
        if (count === 0 && focus?.kind === 'WARNING') return Math.min(UtilSanction.WARNING_LIMIT, Math.max(1, focus.warningNo ?? 1));
        return count;
    }

    /**
     * 누적 경고 막대의 퍼센트
     * @param {number} count - 경고 수.
     * @returns {number} 0~100.
     */
    static warningPercent(count: number): number {
        const n = Math.min(UtilSanction.WARNING_LIMIT, Math.max(0, Math.floor(Number.isFinite(count) ? count : 0)));
        return Math.round((n / UtilSanction.WARNING_LIMIT) * 100);
    }

    /**
     * 이의제기를 접수할 수 있는가 — 아직 접수하지 않았고 무효가 아니다(제재 하나에 1번, 기각 뒤에도 다시 못 한다)
     * @param {Pick<MySanction, 'appeal' | 'status'> | null | undefined} sanction - 제재.
     * @returns {boolean} 접수할 수 있으면 true.
     */
    static canAppeal(sanction: Pick<MySanction, 'appeal' | 'status'> | null | undefined): boolean {
        return Boolean(sanction) && sanction!.appeal === null && sanction!.status !== 'VOIDED';
    }

    /**
     * 이력에서 ID 로 찾기
     * @param {readonly MySanction[]} items - 이력.
     * @param {string | null} id - 제재 ID.
     * @returns {MySanction | null} 제재. 없으면 null.
     */
    static findById(items: readonly MySanction[], id: string | null): MySanction | null {
        return id ? (items.find((s) => s.id === id) ?? null) : null;
    }

    /**
     * 이의제기 화면 갈래
     * @param {MySanction | null} sanction - 그 제재(없으면 null).
     * @param {boolean} submitted - 방금 접수했는가.
     * @returns {AppealScreen} 갈래.
     */
    static appealScreenOf(sanction: MySanction | null, submitted: boolean): AppealScreen {
        if (submitted) return 'DONE';
        if (!sanction) return 'MISSING';
        if (sanction.appeal) {
            if (sanction.appeal.status === 'ACCEPTED') return 'ACCEPTED';
            if (sanction.appeal.status === 'REJECTED') return 'REJECTED';
            return 'PENDING';
        }
        return sanction.status === 'VOIDED' ? 'VOIDED' : 'FORM';
    }

    /**
     * 이의 사유 글자 수(앞뒤 공백 뺀 유니코드 글자)
     * @param {string} text - 사유.
     * @returns {number} 글자 수.
     */
    static appealReasonLength(text: string): number {
        return Array.from(cleanText(text)).length;
    }

    /**
     * 이의 사유 검사 — 10~500자
     * @param {string} text - 사유.
     * @returns {AppealReasonProblem | null} 걸리는 까닭. 괜찮으면 null.
     */
    static appealReasonProblem(text: string): AppealReasonProblem | null {
        const n = UtilSanction.appealReasonLength(text);
        if (n === 0) return 'EMPTY';
        if (n < UtilSanction.APPEAL_REASON_MIN) return 'TOO_SHORT';
        if (n > UtilSanction.APPEAL_REASON_MAX) return 'TOO_LONG';
        return null;
    }

    /**
     * 접수 요청 — 사유는 앞뒤 공백을 뗀다
     * @param {string} sanctionId - 제재 ID.
     * @param {string} text - 사유.
     * @returns {AppealRequest} 요청 본문.
     */
    static toAppealRequest(sanctionId: string, text: string): AppealRequest {
        return { sanctionId, reason: cleanText(text) };
    }

    /**
     * 접수 실패의 갈래 — 문구가 아니라 `code`·`status` 로 가른다
     * @param {unknown} error - 잡은 오류.
     * @returns {AppealOutcome} 갈래.
     */
    static appealOutcomeOf(error: unknown): AppealOutcome {
        const e = (typeof error === 'object' && error !== null ? error : {}) as { status?: unknown; code?: unknown };
        const status = typeof e.status === 'number' ? e.status : 0;
        const code = typeof e.code === 'string' ? e.code : '';
        if (code === 'UNAUTHENTICATED' || status === 401) return 'LOGIN';
        if (code === 'APPEAL_ALREADY') return 'ALREADY';
        if (code === 'SANCTION_NOT_APPEALABLE') return 'NOT_APPEALABLE';
        if (code === 'SANCTION_NOT_FOUND') return 'NOT_FOUND';
        if (code === 'VALIDATION') return 'VALIDATION';
        if (status === 0) return 'NETWORK';
        if (status >= 500) return 'SERVER';
        return 'OTHER';
    }
}
