import type { LocationQuery } from 'vue-router';
import type { AdminDealQuery, AdminDealRow, AffiliateMode, DealView, DealViewFilter, DomainRule } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { SiteAffiliateStore, SiteAffiliateTransform } from '~/composables/hotdeal/api/useHotdealSiteAffiliateApi.types';
import { DEAL_VIEW_FILTERS } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealDomain, DealDomainRequest, DomainRuleFilter } from '~/composables/hotdeal/api/useHotdealDomainApi.types';
import { DOMAIN_RULE_FILTERS } from '~/composables/hotdeal/api/useHotdealDomainApi.types';
import type { DealSuspicionKindFilter, DealSuspicionStatus } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import { DEAL_SUSPICION_KIND_FILTERS, DEAL_SUSPICION_STATUSES } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import type { HotdealSettingsField, HotdealSettingsNumbers } from '~/composables/hotdeal/api/useHotdealSettingsApi.types';
import UtilMember from '~/utils/util-member';

/** 핫딜 글 목록의 주소 조건(`?view=&board=&q=&from=&to=&blocked=1&page=&size=&deal=`) */
export interface HotdealFilters {
    view: DealViewFilter;
    /** 게시판 ID(없으면 빈 글자 = 모든 핫딜 게시판) */
    board: string;
    q: string;
    /** 등록일 시작·끝(`YYYY-MM-DD`, 사이트 시간대의 날짜 — 없으면 빈 글자) */
    from: string;
    to: string;
    /** 차단 도메인 진행 중만(`blockedActive`) */
    blocked: boolean;
    page: number;
    size: number;
}

/** 의심 후보 목록의 주소 조건(`?status=&kind=&page=&suspicion=`) */
export interface SuspicionFilters {
    status: DealSuspicionStatus;
    kind: DealSuspicionKindFilter;
    page: number;
}

/** 링크 규칙 시험 결과 — `host` 는 정규화한 호스트(읽을 수 없으면 null), `match` 는 걸린 규칙(가장 구체적인 것 — 없으면 null) */
export interface DomainMatch {
    host: string | null;
    match: DealDomain | null;
}

/** 리디렉션 체인 판정 — 단계마다 판정 + 전체(차단 > 제휴 > 없음). `decidedBy` 는 전체 판정을 정한 단계 */
export interface DomainChainMatch {
    hops: DomainMatch[];
    verdict: 'BLOCK' | 'AFFILIATE' | 'NONE' | 'INVALID';
    decidedBy: DomainMatch | null;
}

/** 링크 규칙 입력 검사 */
export type DomainProblem = 'REQUIRED' | 'INVALID' | 'TOO_LONG';

/** 같은 딜 판정 쿼리 이름 검사 — 형식 · 추적 파라미터(늘 지운다) · 이미 있음 · 너무 많음 */
export type KeyParamProblem = 'INVALID' | 'TRACKING' | 'DUPLICATE' | 'TOO_MANY';

/** 메모·사유 칸 검사 */
export type TextProblem = 'TOO_LONG';

/** 설정 칸 하나의 범위·처음 값 — `money` 는 천 단위 쉼표로 보이고, `zero` 는 0 의 뜻(없음 · 끔) */
export interface HotdealSettingSpec {
    field: HotdealSettingsField;
    min: number;
    max: number;
    initial: number;
    money?: boolean;
    zero?: 'NONE' | 'OFF';
}

/** 설정 칸 검사 — 비었다 · 정수가 아니다 · 범위 밖 */
export type HotdealSettingProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE';

/** 사이트 제휴 몰 — 제휴 프로그램 이름 검사 */
export type SiteProgramProblem = 'REQUIRED' | 'TOO_LONG';

/** 사이트 제휴 몰 — 링크 틀 검사(`{url}` 자리표시자 1개 · http(s) 주소 · 길이) */
export type SiteTemplateProblem = 'REQUIRED' | 'NO_PLACEHOLDER' | 'MULTIPLE_PLACEHOLDERS' | 'NOT_URL' | 'TOO_LONG';

/** 사이트 제휴 몰 — 붙일 쿼리 검사(`k=v&…`) */
export type SiteQueryProblem = 'REQUIRED' | 'INVALID' | 'TOO_LONG';

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
/** 같은 딜 판정 쿼리 이름 최대 개수(서버 `fields.keyParams` 와 같은 한도) */
const KEY_PARAMS_MAX = 10;
const POST_ID = /^\d{1,19}$/;
const ID = /^[A-Za-z0-9_-]{1,40}$/;
const YMD = /^\d{4}-\d{2}-\d{2}$/;
/** 라벨 1~63자(영숫자·한글·하이픈, 하이픈으로 시작·끝 안 함), 점으로 이은 둘 이상 — 서버 `DealDomainNormalizer` 후보와 같은 규칙 */
const DOMAIN = /^(?=.{1,253}$)(?:[a-z0-9가-힣](?:[a-z0-9가-힣-]{0,61}[a-z0-9가-힣])?\.)+[a-z가-힣][a-z0-9가-힣-]{0,62}$/;
/** 쿼리 이름 1~40자(영숫자 · `_` · `-` · `.` · `[]`) */
const KEY_PARAM = /^[A-Za-z0-9_.[\]-]{1,40}$/;
/** 늘 지우는 추적 파라미터(서버 규칙 3) — 앞 일치(`utm_`·`aff`)와 같은 이름 */
const TRACKING_PREFIX = ['utm_', 'aff'];
const TRACKING_EXACT = new Set(['fbclid', 'gclid', 'igshid', 'ref', 'tag', 'spm', '_trkparms', 'sub_id']);
/** 사이트 제휴 몰 — 프로그램 이름 · 링크 틀 · 붙일 쿼리 최대(서버 `fields.*` 와 같은 한도) */
const SITE_PROGRAM_MAX = 40;
const SITE_TEMPLATE_MAX = 1000;
const SITE_QUERY_MAX = 300;
/** 링크 틀의 원래 상품 주소 자리 */
const URL_PLACEHOLDER = '{url}';
/** 링크 틀은 http(s) 주소로 시작하고 호스트는 글자 그대로(자리표시자 아님) */
const TEMPLATE_HEAD = /^https?:\/\/[^\s/?#{}]+/i;
/** 붙일 쿼리 한 쌍 — 이름(쿼리 이름 규칙) = 값(공백·`&`·`=`·`#` 없음) */
const QUERY_PAIR = /^[A-Za-z0-9_.[\]-]{1,40}=[^\s&=#]+$/;

/** 설정 칸 차례·범위·처음 값 — 서버 `DealSettings` 후보(계약 「핫딜 설정」 표) */
const SETTINGS: readonly HotdealSettingSpec[] = [
    { field: 'endRequestThreshold', min: 1, max: 20, initial: 3 },
    { field: 'endGraceHours', min: 0, max: 168, initial: 12, zero: 'OFF' },
    { field: 'endRequestDailyLimit', min: 1, max: 100, initial: 20 },
    { field: 'endRequestPerAuthorLimit', min: 0, max: 20, initial: 3, zero: 'NONE' },
    { field: 'endRequestPerAuthorDays', min: 1, max: 30, initial: 7 },
    { field: 'keepMaxCount', min: 0, max: 10, initial: 2, zero: 'NONE' },
    { field: 'memberDailyPostLimit', min: 0, max: 50, initial: 5, zero: 'NONE' },
    { field: 'sameStoreIntervalMinutes', min: 0, max: 1440, initial: 10, zero: 'NONE' },
    { field: 'autoExpireDays', min: 0, max: 365, initial: 30, zero: 'OFF' },
    { field: 'priceMaxWon', min: 1_000_000, max: 1_000_000_000, initial: 100_000_000, money: true },
    { field: 'discountWarnPercent', min: 50, max: 99, initial: 90 },
    { field: 'hotLikeThreshold', min: 1, max: 100, initial: 10 },
    { field: 'suspicionMinPosts', min: 0, max: 50, initial: 3, zero: 'OFF' },
    { field: 'suspicionWindowDays', min: 1, max: 90, initial: 7 },
    { field: 'storeSuspicionMembers', min: 0, max: 20, initial: 3, zero: 'OFF' },
    { field: 'storeSuspicionWindowDays', min: 1, max: 90, initial: 30 },
    { field: 'storeSuspicionOtherRatioPercent', min: 0, max: 100, initial: 20 },
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
    static readonly KEY_PARAMS_MAX = KEY_PARAMS_MAX;
    static readonly VIEW_FILTERS = DEAL_VIEW_FILTERS;
    static readonly RULE_FILTERS = DOMAIN_RULE_FILTERS;
    static readonly SUSPICION_STATUSES = DEAL_SUSPICION_STATUSES;
    static readonly SUSPICION_KINDS = DEAL_SUSPICION_KIND_FILTERS;
    static readonly SETTINGS = SETTINGS;
    /** 링크 규칙 고르기 차례 — 차단 · 제휴 · 없음(대형몰만) */
    static readonly RULES: readonly ('BLOCK' | 'AFFILIATE' | 'NONE')[] = ['BLOCK', 'AFFILIATE', 'NONE'];
    /** 의심 후보 서랍에 보이는 글 수 — 회원 후보 · 판매처 후보 */
    static readonly SUSPICION_POSTS = 10;
    static readonly STORE_SUSPICION_POSTS = 30;
    /** 작성자 되돌리기 기한(D2) — 종료 뒤 24시간 */
    static readonly AUTHOR_REOPEN_HOURS = 24;
    /** 제휴 링크 방식 차례 — 개인 제휴(기본) · 사이트 제휴(D5) */
    static readonly AFFILIATE_MODES: readonly AffiliateMode[] = ['MEMBER', 'SITE'];
    static readonly SITE_PROGRAM_MAX = SITE_PROGRAM_MAX;
    static readonly SITE_TEMPLATE_MAX = SITE_TEMPLATE_MAX;
    static readonly SITE_QUERY_MAX = SITE_QUERY_MAX;
    static readonly URL_PLACEHOLDER = URL_PLACEHOLDER;

    /* ── 핫딜 글 목록 주소 ── */

    static emptyFilters(): HotdealFilters {
        return { view: 'ACTIVE', board: '', q: '', from: '', to: '', blocked: false, page: 1, size: PAGE_SIZE };
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
            blocked: first(query.blocked) === '1',
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
        if (f.blocked) q.blocked = '1';
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
            blockedActive: f.blocked || undefined,
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
        return Boolean(f.board || f.q || f.from || f.to || f.blocked);
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

    /** 작성자가 아직 직접 되돌릴 수 있는가(D2 — 작성자 종료 · 기한 안 · 아직 안 씀) */
    static authorCanReopen(reopenUntil: string | null, now: number = Date.now()): boolean {
        return reopenUntil !== null && Date.parse(reopenUntil) > now;
    }

    /** keep 한도에 닿았나(0 = 한도 없음) — 넘으면 공개 화면은 「운영자 확인 중」 */
    static keepLimitReached(keepCount: number, keepMax: number): boolean {
        return keepMax > 0 && keepCount >= keepMax;
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
        const kind = first(query.kind).toUpperCase() as DealSuspicionKindFilter;
        const page = Number.parseInt(first(query.page), 10);
        return {
            status: DEAL_SUSPICION_STATUSES.includes(status) ? status : 'OPEN',
            kind: DEAL_SUSPICION_KIND_FILTERS.includes(kind) ? kind : 'ALL',
            page: Number.isFinite(page) && page >= 1 ? page : 1,
        };
    }

    static suspicionQuery(f: SuspicionFilters): Record<string, string> {
        const q: Record<string, string> = {};
        if (f.status !== 'OPEN') q.status = f.status;
        if (f.kind !== 'ALL') q.kind = f.kind;
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

    /**
     * 리디렉션 체인 판정 — 링크를 `→`·`->`·공백으로 나눈 단계마다 `match` 를 하고, **체인의 어느 호스트든 차단이면 차단 > 제휴 > 없음**(서버 규칙 2 — 응답 `finalHost` 는 마지막 단계).
     * 한 단계라도 읽을 수 없으면 `INVALID`.
     */
    static matchChain(raw: string, domains: readonly DealDomain[]): DomainChainMatch {
        const parts = raw
            .split(/\s*(?:→|->|=>)\s*|\s+/)
            .map((p) => p.trim())
            .filter(Boolean);
        const hops = parts.map((p) => UtilHotdeal.match(p, domains));
        if (hops.length === 0 || hops.some((h) => !h.host)) return { hops, verdict: 'INVALID', decidedBy: null };
        const block = hops.find((h) => h.match?.rule === 'BLOCK');
        if (block) return { hops, verdict: 'BLOCK', decidedBy: block };
        const affiliate = hops.find((h) => h.match?.rule === 'AFFILIATE');
        if (affiliate) return { hops, verdict: 'AFFILIATE', decidedBy: affiliate };
        return { hops, verdict: 'NONE', decidedBy: null };
    }

    /** 규칙 거르기 — 화면이 전부 받아 바로 거른다(서버도 같은 뜻의 `rule`·`q` 조건이 있지만 쓰지 않는다) */
    static filterDomains(items: readonly DealDomain[], rule: DomainRuleFilter, q: string): DealDomain[] {
        const needle = q.trim().toLowerCase();
        return items.filter((d) => {
            if (rule === 'BLOCK' && d.rule !== 'BLOCK') return false;
            if (rule === 'AFFILIATE' && d.rule !== 'AFFILIATE') return false;
            if (rule === 'NONE' && d.rule !== null) return false;
            if (rule === 'COMMON' && !d.common) return false;
            return !needle || d.domain.includes(needle) || (d.note ?? '').toLowerCase().includes(needle) || d.keyParams.some((k) => k.toLowerCase().includes(needle));
        });
    }

    /**
     * 저장 전에 「진행 중 글 N건」 미리 보기가 필요한가 — 새로 차단을 만들거나, 차단이 아니던 규칙을 차단으로 바꾸거나, 차단 규칙의 도메인을 바꿀 때.
     * @param {DealDomainRequest} body - 저장할 값.
     * @param {DealDomain | null} before - 고치기 전 규칙(추가면 null).
     */
    static needsImpact(body: Pick<DealDomainRequest, 'domain' | 'rule'>, before: Pick<DealDomain, 'domain' | 'rule'> | null): boolean {
        if (body.rule !== 'BLOCK') return false;
        if (!before || before.rule !== 'BLOCK') return true;
        return UtilHotdeal.normalizeDomain(body.domain) !== before.domain;
    }

    /** 추적 파라미터인가 — 같은 딜 판정에서 늘 지워서 남길 수 없다 */
    static isTrackingParam(name: string): boolean {
        const n = name.trim().toLowerCase();
        return TRACKING_EXACT.has(n) || TRACKING_PREFIX.some((p) => n.startsWith(p));
    }

    /**
     * 같은 딜 판정 쿼리 이름 하나를 더할 때의 검사 — 형식 · 추적 파라미터 · 이미 있음(대소문자는 그대로 비교 — 쿼리 이름은 대소문자를 가린다) · 개수.
     * @param {string} name - 더하려는 이름(앞뒤 공백은 뗀다).
     * @param {readonly string[]} current - 지금 목록.
     */
    static keyParamProblem(name: string, current: readonly string[]): KeyParamProblem | null {
        const n = name.trim();
        if (!KEY_PARAM.test(n)) return 'INVALID';
        if (UtilHotdeal.isTrackingParam(n)) return 'TRACKING';
        if (current.includes(n)) return 'DUPLICATE';
        if (current.length >= KEY_PARAMS_MAX) return 'TOO_MANY';
        return null;
    }

    /** 칸에 붙여 넣은 글자를 이름들로 — 쉼표·공백·`&`·`=` 뒤 값은 버린다(`prdNo=123&x=1` 을 붙여도 `prdNo`·`x`) */
    static splitKeyParams(raw: string): string[] {
        return raw
            .split(/[\s,&]+/)
            .map((p) => (p.split('=', 1)[0] ?? '').replace(/^\?/, '').trim())
            .filter(Boolean);
    }

    /* ── 설정 ── */

    static settingSpec(field: HotdealSettingsField): HotdealSettingSpec {
        return SETTINGS.find((s) => s.field === field) as HotdealSettingSpec;
    }

    /** 칸 글자 → 정수(읽을 수 없으면 null). 천 단위 쉼표는 떼고, 앞의 0 은 같은 수로 본다 */
    static parseInt(text: string): number | null {
        const s = text.trim().replace(/,/g, '');
        if (!/^\d{1,10}$/.test(s)) return null;
        return Number.parseInt(s, 10);
    }

    /** 저장된 값 → 칸 글자(금액은 천 단위 쉼표) */
    static formatSetting(field: HotdealSettingsField, n: number): string {
        return UtilHotdeal.settingSpec(field).money ? UtilHotdeal.groupDigits(n) : String(n);
    }

    /** 천 단위 쉼표(언어와 상관없이 `,`) */
    static groupDigits(n: number): string {
        return String(Math.trunc(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    }

    static settingProblem(field: HotdealSettingsField, text: string): HotdealSettingProblem | null {
        if (!text.trim()) return 'REQUIRED';
        const n = UtilHotdeal.parseInt(text);
        if (n === null) return 'NOT_INTEGER';
        const spec = UtilHotdeal.settingSpec(field);
        return n < spec.min || n > spec.max ? 'OUT_OF_RANGE' : null;
    }

    /** 편집 중 글자 → 보낼 숫자 값(하나라도 틀리면 null) */
    static settingValues(texts: Record<HotdealSettingsField, string>): HotdealSettingsNumbers | null {
        const out = {} as HotdealSettingsNumbers;
        for (const s of SETTINGS) {
            if (UtilHotdeal.settingProblem(s.field, texts[s.field])) return null;
            out[s.field] = UtilHotdeal.parseInt(texts[s.field]) as number;
        }
        return out;
    }

    /** 바뀐 숫자 칸 수(숫자로 비교 — 읽을 수 없는 글자는 바뀐 것으로) */
    static changedCount(texts: Record<HotdealSettingsField, string>, saved: HotdealSettingsNumbers): number {
        return SETTINGS.filter((s) => UtilHotdeal.parseInt(texts[s.field]) !== saved[s.field]).length;
    }

    /* ── 사이트 제휴 몰(D5) ── */

    static programProblem(text: string): SiteProgramProblem | null {
        const n = len(text.trim());
        if (n === 0) return 'REQUIRED';
        return n > SITE_PROGRAM_MAX ? 'TOO_LONG' : null;
    }

    /**
     * 링크 틀 검사 — 서버 `fields.template` 후보와 같은 규칙: 비었다 · 1000자 넘음 · `{url}` 이 없다 · 둘 이상 · http(s) 주소로 시작하지 않는다(호스트는 글자 그대로).
     * @param {string} template - 예 `https://link.example.com/deeplink?url={url}&id=AF123`.
     */
    static templateProblem(template: string): SiteTemplateProblem | null {
        const s = template.trim();
        if (!s) return 'REQUIRED';
        if (len(s) > SITE_TEMPLATE_MAX) return 'TOO_LONG';
        const count = s.split(URL_PLACEHOLDER).length - 1;
        if (count === 0) return 'NO_PLACEHOLDER';
        if (count > 1) return 'MULTIPLE_PLACEHOLDERS';
        return TEMPLATE_HEAD.test(s) && !/\s/.test(s) ? null : 'NOT_URL';
    }

    /** 붙일 쿼리 다듬기 — 앞뒤 공백과 앞의 `?`·`&` 를 뗀다 */
    static normalizeQuery(query: string): string {
        return query.trim().replace(/^[?&]+/, '');
    }

    /** 붙일 쿼리 검사 — `k=v` 를 `&` 로 이은 모양(이름은 쿼리 이름 규칙, 값은 공백·`&`·`=`·`#` 없음), 300자까지 */
    static queryProblem(query: string): SiteQueryProblem | null {
        const s = UtilHotdeal.normalizeQuery(query);
        if (!s) return 'REQUIRED';
        if (len(s) > SITE_QUERY_MAX) return 'TOO_LONG';
        return s.split('&').every((pair) => QUERY_PAIR.test(pair)) ? null : 'INVALID';
    }

    /** 변환 방식 칸 검사(고른 방식의 칸만) */
    static transformProblem(transform: SiteAffiliateTransform): SiteTemplateProblem | SiteQueryProblem | null {
        return transform.type === 'TEMPLATE' ? UtilHotdeal.templateProblem(transform.template) : UtilHotdeal.queryProblem(transform.query);
    }

    /**
     * 변환 미리 보기 — 서버 `SiteAffiliateLinker`(후보)와 같은 규칙: 링크 틀은 `{url}` 자리에 원래 주소를 URL 인코딩해 넣고, 붙일 쿼리는 원래 주소에 붙인다(같은 이름은 바꾼다).
     * 개인 제휴 파라미터 떼기는 서버가 클릭 때 먼저 한다(여기서는 하지 않는다). 칸이 맞지 않거나 주소를 읽을 수 없으면 null.
     * @param {string} productUrl - 원래 상품 주소.
     * @param {SiteAffiliateTransform} transform - 변환 방식.
     */
    static applySiteTransform(productUrl: string, transform: SiteAffiliateTransform): string | null {
        if (UtilHotdeal.transformProblem(transform)) return null;
        let url: URL;
        try {
            url = new URL(productUrl.trim());
        } catch {
            return null;
        }
        if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
        if (transform.type === 'TEMPLATE') return transform.template.trim().replace(URL_PLACEHOLDER, encodeURIComponent(url.toString()));
        for (const [k, v] of new URLSearchParams(UtilHotdeal.normalizeQuery(transform.query))) url.searchParams.set(k, v);
        return url.toString();
    }

    /** 변환 미리 보기의 처음 예시 상품 주소 — 몰 도메인이 있으면 그 도메인으로 */
    static exampleProductUrl(domain: string | null): string {
        return `https://${domain ?? 'shop.example.com'}/products/12345`;
    }

    /** 이 호스트가 걸리는 켠 사이트 제휴 몰(접미사 일치 · 더 구체적인(긴) 것 우선 — 링크 규칙과 같은 판정, 없으면 null) */
    static matchSiteStore(raw: string | null, stores: readonly SiteAffiliateStore[]): SiteAffiliateStore | null {
        const host = raw ? UtilHotdeal.normalizeDomain(raw) : null;
        if (!host) return null;
        let best: SiteAffiliateStore | null = null;
        for (const s of stores) {
            if (!s.enabled || (host !== s.domain && !host.endsWith(`.${s.domain}`))) continue;
            if (!best || s.domain.length > best.domain.length) best = s;
        }
        return best;
    }
}
