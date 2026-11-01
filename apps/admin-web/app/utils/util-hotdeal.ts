import type { LocationQuery } from 'vue-router';
import type { AdminDealQuery, AdminDealRow, DealView, DealViewFilter, DomainRule } from '~/composables/hotdeal/api/useHotdealApi.types';
import { DEAL_VIEW_FILTERS } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealDomain, DomainRuleFilter } from '~/composables/hotdeal/api/useHotdealDomainApi.types';
import { DOMAIN_RULE_FILTERS } from '~/composables/hotdeal/api/useHotdealDomainApi.types';
import type { DealSuspicionStatus } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import { DEAL_SUSPICION_STATUSES } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import type { HotdealSettingsField, HotdealSettingsValues } from '~/composables/hotdeal/api/useHotdealSettingsApi.types';
import UtilMember from '~/utils/util-member';

/** 핫딜 글 목록의 주소 조건(`?view=&board=&q=&from=&to=&page=&size=&deal=`) */
export interface HotdealFilters {
    view: DealViewFilter;
    /** 게시판 ID(없으면 빈 글자 = 모든 핫딜 게시판) */
    board: string;
    q: string;
    /** 등록일 시작·끝(`YYYY-MM-DD`, 사이트 시간대의 날짜 — 없으면 빈 글자) */
    from: string;
    to: string;
    page: number;
    size: number;
}

/** 의심 후보 목록의 주소 조건(`?status=&page=&suspicion=`) */
export interface SuspicionFilters {
    status: DealSuspicionStatus;
    page: number;
}

/** 링크 규칙 시험 결과 — `host` 는 정규화한 호스트(읽을 수 없으면 null), `match` 는 걸린 규칙(가장 구체적인 것 — 없으면 null) */
export interface DomainMatch {
    host: string | null;
    match: DealDomain | null;
}

/** 링크 규칙 입력 검사 */
export type DomainProblem = 'REQUIRED' | 'INVALID' | 'TOO_LONG';

/** 메모·사유 칸 검사 */
export type TextProblem = 'TOO_LONG';

/** 설정 칸 하나의 범위·처음 값 */
export interface HotdealSettingSpec {
    field: HotdealSettingsField;
    min: number;
    max: number;
    initial: number;
}

/** 설정 칸 검사 — 비었다 · 정수가 아니다 · 범위 밖 */
export type HotdealSettingProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE';

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

const PAGE_SIZE = 20;
const PAGE_SIZES: readonly number[] = [10, 20, 50];
const SEARCH_MAX = 50;
const SEARCH_MIN = 2;
/** 조치 사유·무시 메모 최대(선택 칸) */
const REASON_MAX = 200;
/** 링크 규칙 메모 최대 */
const NOTE_MAX = 100;
/** 도메인 최대(RFC 1035 이름 길이) */
const DOMAIN_MAX = 253;
const POST_ID = /^\d{1,19}$/;
const ID = /^[A-Za-z0-9_-]{1,40}$/;
const YMD = /^\d{4}-\d{2}-\d{2}$/;
/** 라벨 1~63자(영숫자·한글·하이픈, 하이픈으로 시작·끝 안 함), 점으로 이은 둘 이상 — 서버 `DealDomainNormalizer` 후보와 같은 규칙 */
const DOMAIN = /^(?=.{1,253}$)(?:[a-z0-9가-힣](?:[a-z0-9가-힣-]{0,61}[a-z0-9가-힣])?\.)+[a-z가-힣][a-z0-9가-힣-]{0,62}$/;

/** 설정 칸 차례·범위·처음 값 — 서버 `DealSettings` 후보(03-hotdeal 1-1, 계약 「목업 가정」) */
const SETTINGS: readonly HotdealSettingSpec[] = [
    { field: 'endRequestThreshold', min: 1, max: 20, initial: 3 },
    { field: 'endGraceHours', min: 0, max: 168, initial: 12 },
    { field: 'endRequestDailyLimit', min: 1, max: 100, initial: 20 },
    { field: 'suspicionMinPosts', min: 0, max: 50, initial: 3 },
    { field: 'suspicionWindowDays', min: 1, max: 90, initial: 7 },
];

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

function len(text: string): number {
    return Array.from(text).length;
}

/**
 * 회원 핫딜(1차) 관리 화면의 순수 계산 — 주소 조건 읽기·쓰기, 상태 색, 링크 규칙 정규화·판정, 설정 범위 검사.
 * 판정은 서버다(계약 「할인율·제휴 강제·종료 판정은 서버가 정한다」). 여기 규칙은 미리 알려 주는 용도이고 서버 후보 클래스 이름을 주석에 적었다.
 */
export default class UtilHotdeal {
    static readonly PAGE_SIZE = PAGE_SIZE;
    static readonly PAGE_SIZES = PAGE_SIZES;
    static readonly SEARCH_MAX = SEARCH_MAX;
    static readonly REASON_MAX = REASON_MAX;
    static readonly NOTE_MAX = NOTE_MAX;
    static readonly VIEW_FILTERS = DEAL_VIEW_FILTERS;
    static readonly RULE_FILTERS = DOMAIN_RULE_FILTERS;
    static readonly SUSPICION_STATUSES = DEAL_SUSPICION_STATUSES;
    static readonly SETTINGS = SETTINGS;
    /** 링크 규칙 고르기 차례 — 차단 · 제휴 · 없음(대형몰만) */
    static readonly RULES: readonly ('BLOCK' | 'AFFILIATE' | 'NONE')[] = ['BLOCK', 'AFFILIATE', 'NONE'];
    /** 의심 후보 서랍에 보이는 글 수 */
    static readonly SUSPICION_POSTS = 10;

    /* ── 핫딜 글 목록 주소 ── */

    static emptyFilters(): HotdealFilters {
        return { view: 'ACTIVE', board: '', q: '', from: '', to: '', page: 1, size: PAGE_SIZE };
    }

    /** 주소 → 조건. 상태 처음 값은 「진행」(운영자가 먼저 볼 것) */
    static parse(query: LocationQuery): HotdealFilters {
        const view = first(query.view).toUpperCase() as DealViewFilter;
        const page = Number.parseInt(first(query.page), 10);
        const size = Number.parseInt(first(query.size), 10);
        const board = first(query.board).trim();
        const from = first(query.from).trim();
        const to = first(query.to).trim();
        return {
            view: DEAL_VIEW_FILTERS.includes(view) ? view : 'ACTIVE',
            board: ID.test(board) ? board : '',
            q: Array.from(first(query.q).trim()).slice(0, SEARCH_MAX).join(''),
            from: YMD.test(from) ? from : '',
            to: YMD.test(to) ? to : '',
            page: Number.isFinite(page) && page >= 1 ? page : 1,
            size: PAGE_SIZES.includes(size) ? size : PAGE_SIZE,
        };
    }

    /** 조건 → 주소(처음 값은 싣지 않는다) */
    static toQuery(f: HotdealFilters): Record<string, string> {
        const q: Record<string, string> = {};
        if (f.view !== 'ACTIVE') q.view = f.view;
        if (f.board) q.board = f.board;
        if (f.q) q.q = f.q;
        if (f.from) q.from = f.from;
        if (f.to) q.to = f.to;
        if (f.page > 1) q.page = String(f.page);
        if (f.size !== PAGE_SIZE) q.size = String(f.size);
        return q;
    }

    /** API 조건 — 쪽은 0부터, 등록일은 사이트 시간대 그날 00:00 ~ 23:59:59.999(글 관리와 같은 변환) */
    static toApi(f: HotdealFilters, zoneId: string): AdminDealQuery {
        return {
            view: f.view,
            board: f.board || undefined,
            q: f.q || undefined,
            createdFrom: f.from ? UtilMember.dayStart(f.from, zoneId) : undefined,
            createdTo: f.to ? UtilMember.dayEnd(f.to, zoneId) : undefined,
            page: f.page - 1,
            size: f.size,
        };
    }

    /** 바뀌면 목록을 다시 받는 열쇠(`deal` 은 들어가지 않는다) */
    static key(f: HotdealFilters): string {
        return JSON.stringify(UtilHotdeal.toQuery(f));
    }

    /** 일부를 바꾼다 — 쪽 말고 다른 것을 바꾸면 1쪽으로 */
    static change(f: HotdealFilters, patch: Partial<HotdealFilters>): HotdealFilters {
        const next = { ...f, ...patch };
        if (patch.page === undefined) next.page = 1;
        return next;
    }

    /** 상태(처음 값 「진행」)를 뺀 조건이 있는가 — 빈 결과 문구를 가른다 */
    static hasConditions(f: HotdealFilters): boolean {
        return Boolean(f.board || f.q || f.from || f.to);
    }

    /** 검색어 검사 — 비우면 검색을 푼다, 한 글자는 보내지 않는다 */
    static searchTooShort(text: string): boolean {
        const n = len(text.trim());
        return n > 0 && n < SEARCH_MIN;
    }

    /** 주소의 `deal`(열린 서랍의 글 번호) */
    static parseDeal(query: LocationQuery): string | null {
        const id = first(query.deal).trim();
        return POST_ID.test(id) ? id : null;
    }

    /** 지금 쪽에서 앞뒤 글 */
    static neighbors(items: readonly AdminDealRow[], id: string | null): { prev: AdminDealRow | null; next: AdminDealRow | null } {
        const at = id ? items.findIndex((it) => it.postId === id) : -1;
        if (at < 0) return { prev: null, next: null };
        return { prev: items[at - 1] ?? null, next: items[at + 1] ?? null };
    }

    static pageCount(total: number, size: number): number {
        return Math.max(1, Math.ceil(total / Math.max(1, size)));
    }

    /* ── 상태 ── */

    static viewSeverity(view: DealView): Severity {
        if (view === 'ACTIVE') return 'success';
        if (view === 'FLAGGED') return 'warn';
        if (view === 'EXPIRED') return 'secondary';
        return 'contrast';
    }

    static viewIcon(view: DealView): string {
        if (view === 'ACTIVE') return 'pi pi-bolt';
        if (view === 'FLAGGED') return 'pi pi-exclamation-triangle';
        if (view === 'EXPIRED') return 'pi pi-clock';
        return 'pi pi-ban';
    }

    /** [종료]를 누를 수 있는 상태 — 저장 상태가 ACTIVE(진행 · 의심 · 기간 지남) */
    static canEnd(view: DealView): boolean {
        return view !== 'ENDED';
    }

    /** [재개]를 누를 수 있는 상태 — ENDED 만(운영진만 재개, 의심·요청 리셋) */
    static canReopen(view: DealView): boolean {
        return view === 'ENDED';
    }

    /** 재개해도 마감이 이미 지났는가 — 재개 대화창의 「작성자가 마감을 고쳐야 해요」 안내 */
    static endsAtPassed(endsAt: string | null, now: number = Date.now()): boolean {
        return endsAt !== null && Date.parse(endsAt) <= now;
    }

    /** 사유·메모(선택 칸) 검사 */
    static textProblem(text: string, max: number = REASON_MAX): TextProblem | null {
        return len(text.trim()) > max ? 'TOO_LONG' : null;
    }

    static textLength(text: string): number {
        return len(text.trim());
    }

    /* ── 의심 후보 주소 ── */

    static parseSuspicionFilters(query: LocationQuery): SuspicionFilters {
        const status = first(query.status).toUpperCase() as DealSuspicionStatus;
        const page = Number.parseInt(first(query.page), 10);
        return { status: DEAL_SUSPICION_STATUSES.includes(status) ? status : 'OPEN', page: Number.isFinite(page) && page >= 1 ? page : 1 };
    }

    static suspicionQuery(f: SuspicionFilters): Record<string, string> {
        const q: Record<string, string> = {};
        if (f.status !== 'OPEN') q.status = f.status;
        if (f.page > 1) q.page = String(f.page);
        return q;
    }

    static parseSuspicion(query: LocationQuery): string | null {
        const id = first(query.suspicion).trim();
        return ID.test(id) ? id : null;
    }

    /* ── 링크 규칙 ── */

    /**
     * 도메인 정규화 미리보기 — 서버 `DealDomainNormalizer`(후보)와 같은 규칙: 앞뒤 공백 · 소문자 · 스킴(`https://`) · 사용자 정보(`a@`) · 경로·쿼리·조각(`/…` `?…` `#…`) · 포트(`:8080`) · 앞 `www.` · 끝 점을 뗀다.
     * 형식이 맞지 않으면 null(그대로 보내면 서버가 400 `fields.domain`).
     * @param {string} raw - 입력한 글자(주소 통째로 붙여도 된다).
     * @returns {string | null} 저장될 도메인.
     */
    static normalizeDomain(raw: string): string | null {
        let s = raw.trim().toLowerCase();
        if (!s) return null;
        s = s.replace(/^[a-z][a-z0-9+.-]*:\/\//, '').replace(/^\/\//, '');
        s = s.split(/[/?#]/, 1)[0] ?? '';
        const at = s.lastIndexOf('@');
        if (at >= 0) s = s.slice(at + 1);
        s = s.replace(/:\d*$/, '').replace(/\.+$/, '');
        if (s.startsWith('www.')) s = s.slice(4);
        return DOMAIN.test(s) ? s : null;
    }

    /** 링크 규칙 도메인 칸 검사 */
    static domainProblem(raw: string): DomainProblem | null {
        if (!raw.trim()) return 'REQUIRED';
        if (len(raw.trim()) > 1000) return 'TOO_LONG';
        const host = UtilHotdeal.normalizeDomain(raw);
        if (!host) return 'INVALID';
        return host.length > DOMAIN_MAX ? 'TOO_LONG' : null;
    }

    /** 고르기 값(`NONE`) ↔ 규칙(null) */
    static ruleOf(choice: 'BLOCK' | 'AFFILIATE' | 'NONE'): DomainRule {
        return choice === 'NONE' ? null : choice;
    }

    static choiceOf(rule: DomainRule): 'BLOCK' | 'AFFILIATE' | 'NONE' {
        return rule ?? 'NONE';
    }

    static ruleSeverity(rule: DomainRule): Severity {
        if (rule === 'BLOCK') return 'danger';
        if (rule === 'AFFILIATE') return 'info';
        return 'secondary';
    }

    /**
     * 어떤 규칙이 걸리나 — 링크(또는 호스트)를 정규화하고, 규칙 도메인과 같거나 그 하위 도메인이면 걸린다(접미사 일치).
     * 여럿이 걸리면 **가장 구체적인(긴) 규칙 하나**가 이긴다(`link.coupang.com` 규칙이 있으면 `coupang.com` 보다 먼저 — 계약 「링크 판정」). 서버 `DealLinkChecker`(후보)와 같은 규칙.
     * @param {string} raw - 시험할 링크.
     * @param {readonly DealDomain[]} domains - 지금 규칙 목록.
     * @returns {DomainMatch} 판정.
     */
    static match(raw: string, domains: readonly DealDomain[]): DomainMatch {
        const host = UtilHotdeal.normalizeDomain(raw);
        if (!host) return { host: null, match: null };
        let best: DealDomain | null = null;
        for (const d of domains) {
            if (host !== d.domain && !host.endsWith(`.${d.domain}`)) continue;
            if (!best || d.domain.length > best.domain.length) best = d;
        }
        return { host, match: best };
    }

    /** 규칙 거르기 — 화면에서 바로 거른다(목업 · 서버도 같은 뜻의 `rule` 조건) */
    static filterDomains(items: readonly DealDomain[], rule: DomainRuleFilter, q: string): DealDomain[] {
        const needle = q.trim().toLowerCase();
        return items.filter((d) => {
            if (rule === 'BLOCK' && d.rule !== 'BLOCK') return false;
            if (rule === 'AFFILIATE' && d.rule !== 'AFFILIATE') return false;
            if (rule === 'NONE' && d.rule !== null) return false;
            if (rule === 'COMMON' && !d.common) return false;
            return !needle || d.domain.includes(needle) || (d.note ?? '').toLowerCase().includes(needle);
        });
    }

    /* ── 설정 ── */

    static settingSpec(field: HotdealSettingsField): HotdealSettingSpec {
        return SETTINGS.find((s) => s.field === field) as HotdealSettingSpec;
    }

    /** 칸 글자 → 정수(읽을 수 없으면 null). 앞의 0 은 같은 수로 본다 */
    static parseInt(text: string): number | null {
        const s = text.trim();
        if (!/^\d{1,6}$/.test(s)) return null;
        return Number.parseInt(s, 10);
    }

    static settingProblem(field: HotdealSettingsField, text: string): HotdealSettingProblem | null {
        if (!text.trim()) return 'REQUIRED';
        const n = UtilHotdeal.parseInt(text);
        if (n === null) return 'NOT_INTEGER';
        const spec = UtilHotdeal.settingSpec(field);
        return n < spec.min || n > spec.max ? 'OUT_OF_RANGE' : null;
    }

    /** 편집 중 글자 → 보낼 값(하나라도 틀리면 null) */
    static settingValues(texts: Record<HotdealSettingsField, string>): HotdealSettingsValues | null {
        const out = {} as HotdealSettingsValues;
        for (const s of SETTINGS) {
            if (UtilHotdeal.settingProblem(s.field, texts[s.field])) return null;
            out[s.field] = UtilHotdeal.parseInt(texts[s.field]) as number;
        }
        return out;
    }

    /** 바뀐 칸 수(숫자로 비교 — 읽을 수 없는 글자는 바뀐 것으로) */
    static changedCount(texts: Record<HotdealSettingsField, string>, saved: HotdealSettingsValues): number {
        return SETTINGS.filter((s) => UtilHotdeal.parseInt(texts[s.field]) !== saved[s.field]).length;
    }
}
