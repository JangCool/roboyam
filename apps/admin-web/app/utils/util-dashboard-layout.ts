import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';
import type { DashboardGadgetColor, DashboardGadgetConfig, DashboardGadgetItem, DashboardGadgetType, DashboardLayoutBody } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import { DASHBOARD_GADGET_COLORS, DASHBOARD_GADGET_TYPES } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import type { AnalyticsSwitch } from '~/composables/analytics/api/useAnalyticsSettingsApi.types';
import UtilAdminMenu from '~/utils/util-admin-menu';

/**
 * 관리자 대시보드 구성의 순수 계산 — 가젯 종류표(묶음·아이콘·필요 권한·최소/기본 크기·설정 칸), 저장 값 읽기 검증, 권한으로 만드는 기본 구성,
 * 옮기기·크기(`setBox` — 부딪친 가젯은 밀리고 위로 당긴다)·넣기·빼기. 상태도 요청도 없다.
 * 위로 당기기·읽는 순서는 홈 배치와 같은 규칙(공유 레이어 `UtilHomeLayout.compact`·`byReadingOrder`)이고, 방식은 홈 편집기(`util-home-layout-editor`)와 같다.
 *
 *  - 격자 48칸 고정(12·24칸 때 저장한 구성은 읽을 때 늘린다) · 행 높이 12px · 간격 16px(가정). 가젯 높이 `h` 는 고정(내용이 넘치면 가젯 안에서 스크롤).
 *  - 구성은 늘 위로 당긴(빈 줄 없는) 모양이다.
 *
 * 서버 부담 원칙(가젯 추가 창 설명에도 적는다): 숫자형 = 상태별 COUNT 한 번(서버 60초 캐시 가정) · 목록형 = 최대 5~10행 · 추이형 = 배치가 만든 일별 집계만 읽음 ·
 * 배치된 가젯만 부르고 자동 새로 고침은 최소 1분·탭이 안 보이면 멈춘다.
 */

/** 가젯 추가 창의 업무 묶음(보일 순서) — 「방문 분석」(`ANALYTICS`)은 방문 수집 데이터를 읽는 가젯(방문 현황·인기 페이지·유입 경로, 2026-11-04) */
export const DASHBOARD_GROUPS = ['TODO', 'COMMUNITY', 'ANALYTICS', 'MEMBER', 'BIZ', 'COMMERCE', 'POINT', 'SYSTEM', 'PERSONAL'] as const;
export type DashboardGroup = (typeof DASHBOARD_GROUPS)[number];

/** 가젯 ⚙ 설정의 종류별 칸 */
export type DashboardOption = 'limit' | 'days' | 'text' | 'links';

/** 종류 하나의 값 — 종류표(`KINDS_12`)에는 12칸 기준으로 적고 격자에 맞춰 늘린다 */
export interface DashboardKindSpec {
    type: DashboardGadgetType;
    group: DashboardGroup;
    /** PrimeIcons 클래스 */
    icon: string;
    /** 이 중 하나라도 있으면 쓸 수 있다(빈 배열 = 누구나) */
    permissions: readonly string[];
    /** 같은 종류를 여러 개 둘 수 있나(메모·바로가기만) */
    multiple: boolean;
    /** 서버를 부르는 가젯인가 — 새로 고침 단추·자동 새로 고침·기준 시각 */
    refreshable: boolean;
    /** ⚙ 설정의 종류별 칸 */
    options: readonly DashboardOption[];
    /**
     * 이 가젯의 데이터를 만드는 「통계 수집」 항목(`/settings/analytics`) — 꺼져 있으면 서버가 묶음 응답 `disabled` 에 넣고 가젯은 「수집이 꺼져 있어요」.
     * 없으면 수집 설정과 무관한 가젯.
     */
    collect?: AnalyticsSwitch;
    minW: number;
    minH: number;
    /** 새로 넣을 때 폭·높이 */
    w: number;
    h: number;
}

/** 바로가기로 고를 수 있는 화면 하나 */
export interface DashboardShortcutChoice {
    to: string;
    /** 메뉴 이름 i18n 키 */
    labelKey: string;
    icon: string | null;
    /** 속한 메뉴 묶음 이름 i18n 키(낱개 항목이면 null) */
    groupKey: string | null;
}

/** 편집 중 자리(`UtilHomeLayout` 계산용으로 `gadgetId` 를 붙인다) */
type Placed = DashboardGadgetItem & { gadgetId: string };

/**
 * 격자 칸 수(사용자 결정 2026-11-03 「24그리드」 → 「세밀한 제어」로 48 — 1/2·1/3·1/4·1/6·1/8 이 고르게 나뉘고 24칸 저장본이 정확히 2배) — 아래 종류표의 폭은 12칸 기준으로 적고 `SCALE` 배 한다.
 * 행 높이 12px + 간격 16px = 한 걸음 28px(예전 40px 행의 절반 — 예전 `h`·`y` 를 2배 하면 같은 px).
 */
const COLUMNS = 48;
const SCALE = COLUMNS / 12;
const ROW_HEIGHT = 12;
const GAP = 16;
const MAX_H = 80;
/** 높이를 마음대로 줄일 수 있게 모든 종류의 최소 높이(행) — 5행 = 124px(머리 + 한 줄). 넘치는 내용은 가젯 안에서 스크롤 */
const MIN_H = 5;
/** 저장 모양 판 — 1: 행 40px(`h`·`y` 를 2배로 읽는다) · 2: 행 12px */
const VERSION = 2;
const MAX_Y = 2000;
const MAX_GADGETS = 40;
const TITLE_MAX = 40;
const MEMO_MAX = 1000;
const LINKS_MAX = 12;

const KINDS_12: readonly DashboardKindSpec[] = [
    { type: 'TODO', group: 'TODO', icon: 'pi pi-check-square', permissions: ['ad.read', 'hotdeal.read', 'business.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 3, h: 8 },
    { type: 'COMMERCE_TODO', group: 'TODO', icon: 'pi pi-shopping-cart', permissions: ['commerce.order.read', 'commerce.product.read', 'commerce.payment.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 3, h: 8 },
    // 오늘·내일 일정 — 항목마다 그 종류 권한이 있을 때만(광고 · 공구 · 포인트 상점 · 배치)
    { type: 'TODAY_SCHEDULE', group: 'TODO', icon: 'pi pi-calendar-clock', permissions: ['ad.read', 'commerce.product.read', 'pointshop.read', 'batch.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 4, h: 7 },
    { type: 'REPORTS', group: 'COMMUNITY', icon: 'pi pi-flag', permissions: ['report.read'], multiple: false, refreshable: true, options: ['limit'], minW: 3, minH: 5, w: 4, h: 7 },
    { type: 'POST_ACTIVITY', group: 'COMMUNITY', icon: 'pi pi-comments', permissions: ['post.read'], multiple: false, refreshable: true, options: [], collect: 'boardActivity', minW: 3, minH: 4, w: 4, h: 5 },
    { type: 'RECENT_POSTS', group: 'COMMUNITY', icon: 'pi pi-file', permissions: ['post.read'], multiple: false, refreshable: true, options: ['limit'], minW: 3, minH: 4, w: 4, h: 8 },
    { type: 'BOARD_RANKING', group: 'COMMUNITY', icon: 'pi pi-sort-amount-down', permissions: ['post.read'], multiple: false, refreshable: true, options: [], collect: 'boardActivity', minW: 3, minH: 4, w: 4, h: 6 },
    { type: 'POPULAR_NOW', group: 'COMMUNITY', icon: 'pi pi-star', permissions: ['popular.read'], multiple: false, refreshable: true, options: ['limit'], minW: 3, minH: 4, w: 4, h: 8 },
    { type: 'GROUP_REQUESTS', group: 'COMMUNITY', icon: 'pi pi-users', permissions: ['group.read'], multiple: false, refreshable: true, options: ['limit'], minW: 3, minH: 4, w: 4, h: 7 },
    // 방문 분석 — 방문 수집(`/settings/analytics`)이 만든 집계만 읽는다(원본 로그 GROUP BY 없음). 이동할 방문 분석 화면이 아직 없어 링크 없음
    // 방문 현황 = 시간별 집계 · 인기 페이지 = 원본 기록을 1분마다 몰아 센 오늘 페이지별 조회 · 유입 경로 = 오늘 유입처 분류·도메인 집계
    { type: 'VISITORS', group: 'ANALYTICS', icon: 'pi pi-eye', permissions: ['site.read'], multiple: false, refreshable: true, options: [], collect: 'visits', minW: 3, minH: 5, w: 4, h: 7 },
    { type: 'POPULAR_PAGES', group: 'ANALYTICS', icon: 'pi pi-desktop', permissions: ['site.read'], multiple: false, refreshable: true, options: ['limit'], collect: 'rawEvents', minW: 3, minH: 4, w: 4, h: 8 },
    { type: 'REFERRERS', group: 'ANALYTICS', icon: 'pi pi-directions', permissions: ['site.read'], multiple: false, refreshable: true, options: [], collect: 'referrers', minW: 3, minH: 5, w: 4, h: 8 },
    { type: 'MEMBER_STATS', group: 'MEMBER', icon: 'pi pi-user', permissions: ['member.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 3, h: 8 },
    { type: 'SIGNUP_TREND', group: 'MEMBER', icon: 'pi pi-chart-bar', permissions: ['member.read'], multiple: false, refreshable: true, options: ['days'], collect: 'boardActivity', minW: 3, minH: 4, w: 4, h: 5 },
    { type: 'RECENT_SIGNUPS', group: 'MEMBER', icon: 'pi pi-user-plus', permissions: ['member.read'], multiple: false, refreshable: true, options: ['limit'], minW: 3, minH: 4, w: 4, h: 7 },
    // 제재 — 권한 sanction.read 는 카탈로그에 있으나 아직 화면 없음(featureReady false) — 이동은 회원 관리
    { type: 'SANCTIONS', group: 'MEMBER', icon: 'pi pi-ban', permissions: ['sanction.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 5, w: 4, h: 7 },
    { type: 'NICKNAME_IMAGES', group: 'MEMBER', icon: 'pi pi-image', permissions: ['member.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 4, h: 5 },
    { type: 'HOTDEAL_STATUS', group: 'BIZ', icon: 'pi pi-ticket', permissions: ['hotdeal.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 4, h: 5 },
    { type: 'HOTDEAL_TOP', group: 'BIZ', icon: 'pi pi-bolt', permissions: ['hotdeal.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 4, h: 7 },
    { type: 'AD_PERFORMANCE', group: 'BIZ', icon: 'pi pi-chart-line', permissions: ['ad.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 5, w: 4, h: 6 },
    { type: 'AD_SCHEDULE', group: 'BIZ', icon: 'pi pi-calendar', permissions: ['ad.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 4, h: 8 },
    { type: 'GROUPBUY_PROGRESS', group: 'COMMERCE', icon: 'pi pi-shopping-bag', permissions: ['commerce.product.read'], multiple: false, refreshable: true, options: ['limit'], minW: 3, minH: 4, w: 4, h: 8 },
    { type: 'GROUPBUY_QNA', group: 'COMMERCE', icon: 'pi pi-question-circle', permissions: ['commerce.product.read'], multiple: false, refreshable: true, options: ['limit'], minW: 3, minH: 4, w: 4, h: 7 },
    { type: 'SALES_SUMMARY', group: 'COMMERCE', icon: 'pi pi-wallet', permissions: ['commerce.payment.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 3, h: 8 },
    { type: 'ORDER_TREND', group: 'COMMERCE', icon: 'pi pi-chart-bar', permissions: ['commerce.payment.read'], multiple: false, refreshable: true, options: ['days'], minW: 3, minH: 4, w: 4, h: 5 },
    { type: 'RECONCILE', group: 'COMMERCE', icon: 'pi pi-sync', permissions: ['commerce.payment.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 4, h: 5 },
    { type: 'POINT_SUMMARY', group: 'POINT', icon: 'pi pi-database', permissions: ['point.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 4, h: 5 },
    { type: 'POINTSHOP', group: 'POINT', icon: 'pi pi-gift', permissions: ['pointshop.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 4, h: 6 },
    { type: 'BATCH_HEALTH', group: 'SYSTEM', icon: 'pi pi-server', permissions: ['batch.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 4, w: 4, h: 6 },
    { type: 'NOTIFICATION_HEALTH', group: 'SYSTEM', icon: 'pi pi-bell', permissions: ['notification.read'], multiple: false, refreshable: true, options: [], collect: 'notificationSends', minW: 3, minH: 5, w: 4, h: 7 },
    { type: 'AUDIT_RECENT', group: 'SYSTEM', icon: 'pi pi-history', permissions: ['audit.read'], multiple: false, refreshable: true, options: ['limit'], minW: 3, minH: 4, w: 4, h: 7 },
    { type: 'OPERATOR_SECURITY', group: 'SYSTEM', icon: 'pi pi-shield', permissions: ['operator.read'], multiple: false, refreshable: true, options: [], minW: 3, minH: 3, w: 4, h: 4 },
    { type: 'MEMO', group: 'PERSONAL', icon: 'pi pi-pencil', permissions: [], multiple: true, refreshable: false, options: ['text'], minW: 2, minH: 3, w: 4, h: 5 },
    { type: 'SHORTCUTS', group: 'PERSONAL', icon: 'pi pi-link', permissions: [], multiple: true, refreshable: false, options: ['links'], minW: 2, minH: 3, w: 4, h: 4 },
];

/**
 * 격자에 맞춘 종류표 — 폭은 `SCALE` 배, 높이는 2배(종류표는 40px 행 기준). 최소 폭은 3분의 2 만큼만 늘린다(12칸 3 → 48칸 8 = 6분의 1 폭, 메모·바로가기 2 → 6):
 * 그대로 늘리면 기본 폭(4분의 1)이 곧 최소 폭이라 좌우로 줄일 수 없다(사용자 지적 2026-11-03 「위아래는 바뀌는데 좌우는 안 바뀐다」).
 * 최소 높이는 종류와 무관하게 `MIN_H`(사용자 지적 2026-11-03 「높이가 최소 영역에 고정돼 더 안 줄어든다」).
 */
const KINDS: readonly DashboardKindSpec[] = KINDS_12.map((k) => ({ ...k, minW: Math.ceil((k.minW * SCALE * 2) / 3), w: k.w * SCALE, minH: MIN_H, h: k.h * 2 }));

/**
 * 기본 구성(사용자 결정 2026-11-03 「지금 설정한 대시보드가 기본값」 — 그때 운영자가 꾸민 배치를 그대로, 48칸 · 28px 걸음).
 *  위: 회원 현황 · 방문 현황 · 커머스 · 매출 요약 / 제재 · 신고 · 처리할 일 · 결제 대사(매출 요약 아래)
 *  아래: 오늘 일정(왼쪽 긴 열) · 최근 글 · 모집 중 공구 / 공구 Q&A · 닉네임 이미지 심사 · 배치 상태
 * 볼 수 없는 종류는 빠지고 위로 당긴다(옆 빈칸은 메우지 않는다 — 운영자가 옮긴다).
 */
const DEFAULT_LAYOUT: readonly Omit<DashboardGadgetItem, 'id' | 'config'>[] = [
    { type: 'MEMBER_STATS', x: 0, y: 0, w: 8, h: 9 },
    { type: 'VISITORS', x: 8, y: 0, w: 12, h: 9 },
    { type: 'COMMERCE_TODO', x: 20, y: 0, w: 17, h: 9 },
    { type: 'SALES_SUMMARY', x: 37, y: 0, w: 11, h: 12 },
    { type: 'SANCTIONS', x: 0, y: 9, w: 10, h: 9 },
    { type: 'REPORTS', x: 10, y: 9, w: 10, h: 9 },
    { type: 'TODO', x: 20, y: 9, w: 17, h: 9 },
    { type: 'RECONCILE', x: 37, y: 12, w: 11, h: 6 },
    { type: 'TODAY_SCHEDULE', x: 0, y: 18, w: 9, h: 20 },
    { type: 'RECENT_POSTS', x: 9, y: 18, w: 19, h: 10 },
    { type: 'GROUPBUY_PROGRESS', x: 28, y: 18, w: 20, h: 10 },
    { type: 'GROUPBUY_QNA', x: 9, y: 28, w: 19, h: 10 },
    { type: 'NICKNAME_IMAGES', x: 28, y: 28, w: 10, h: 10 },
    { type: 'BATCH_HEALTH', x: 38, y: 28, w: 10, h: 10 },
];

function int(value: unknown, fallback: number, min: number, max: number): number {
    const n = typeof value === 'number' && Number.isFinite(value) ? Math.round(value) : fallback;
    return Math.min(max, Math.max(min, n));
}

function toPlaced(items: readonly DashboardGadgetItem[]): Placed[] {
    return items.map((it) => ({ ...it, gadgetId: it.id }));
}

function fromPlaced(items: readonly Placed[]): DashboardGadgetItem[] {
    return items.map(({ gadgetId: _gadgetId, ...rest }) => rest);
}

/** 위로 당긴다 — `first` 를 주면 그 가젯이 같은 행에서 먼저 놓인다(옮긴·키운 가젯이 이기고 부딪친 가젯이 밀린다) */
function settle(items: readonly DashboardGadgetItem[], first?: string): DashboardGadgetItem[] {
    const prepared = toPlaced(items).map((it) => (it.id === first ? { ...it, y: it.y - 0.25 } : it));
    return fromPlaced(UtilHomeLayout.compact(prepared));
}

function overlapsX(a: { x: number; w: number }, b: { x: number; w: number }): boolean {
    return a.x < b.x + b.w && b.x < a.x + a.w;
}

/** 배경색의 기준색(Tailwind 500 단계) — 바탕·선은 이 색을 흰 바탕·어두운 면에 옅게 섞는다(`DashboardGrid` style). 흰색(`NONE`)은 없음 */
const COLOR_HEX: Record<Exclude<DashboardGadgetColor, 'NONE'>, string> = {
    SLATE: '#64748b',
    BLUE: '#3b82f6',
    SKY: '#0ea5e9',
    TEAL: '#14b8a6',
    GREEN: '#22c55e',
    AMBER: '#f59e0b',
    ORANGE: '#f97316',
    ROSE: '#f43f5e',
    VIOLET: '#8b5cf6',
};

/**
 * 추천 배경색(사용자 결정 2026-11-03 「모든 가젯에 색을 주지 말고 중요한 것만」) — 바로 처리해야 하는 가젯만 칠한다.
 * 처리할 일·커머스 = 호박(먼저 볼 것) · 신고 = 장미(안전). 나머지는 흰 카드이고, 운영자가 ⚙ 에서 고른다.
 */
const RECOMMENDED_COLOR: Partial<Record<DashboardGadgetType, DashboardGadgetColor>> = {
    TODO: 'AMBER',
    COMMERCE_TODO: 'AMBER',
    REPORTS: 'ROSE',
};

/** 저장 값의 설정을 읽는다 — 종류에 없는 칸·모르는 값은 버린다 */

function readConfig(raw: unknown, spec: DashboardKindSpec): DashboardGadgetConfig {
    const r = raw !== null && typeof raw === 'object' ? (raw as Record<string, unknown>) : {};
    const out: DashboardGadgetConfig = {};
    if (typeof r.title === 'string' && r.title.trim()) out.title = r.title.trim().slice(0, TITLE_MAX);
    if (spec.refreshable && (r.refresh === 60 || r.refresh === 300)) out.refresh = r.refresh;
    if (spec.options.includes('limit') && (r.limit === 5 || r.limit === 10)) out.limit = r.limit;
    if (spec.options.includes('days') && (r.days === 7 || r.days === 30)) out.days = r.days;
    if (spec.options.includes('text') && typeof r.text === 'string') out.text = r.text.slice(0, MEMO_MAX);
    if (spec.options.includes('links') && Array.isArray(r.links)) out.links = [...new Set(r.links.filter((v): v is string => typeof v === 'string' && v.startsWith('/')))].slice(0, LINKS_MAX);
    if ((DASHBOARD_GADGET_COLORS as readonly unknown[]).includes(r.color)) out.color = r.color as DashboardGadgetColor;
    return out;
}

export default class UtilDashboardLayout {
    static readonly COLUMNS = COLUMNS;
    /** 행 높이(px) */
    static readonly ROW_HEIGHT = ROW_HEIGHT;
    /** 칸 사이 간격(px, 가로·세로) */
    static readonly GAP = GAP;
    static readonly MAX_H = MAX_H;
    /** 한 구성에 둘 수 있는 가젯 수 */
    static readonly MAX_GADGETS = MAX_GADGETS;
    static readonly TITLE_MAX = TITLE_MAX;
    static readonly MEMO_MAX = MEMO_MAX;
    static readonly LINKS_MAX = LINKS_MAX;
    /** 저장 모양 판 */
    static readonly VERSION = VERSION;

    /** 종류표(보일 순서) */
    static readonly KINDS: readonly DashboardKindSpec[] = KINDS;

    /**
     * 종류의 값
     * @param {DashboardGadgetType} type - 종류.
     * @returns {DashboardKindSpec} 값.
     */
    static spec(type: DashboardGadgetType): DashboardKindSpec {
        return KINDS.find((k) => k.type === type)!;
    }

    /**
     * 아는 종류인가
     * @param {unknown} type - 값.
     * @returns {boolean} 종류표에 있으면 true.
     */
    static isKnown(type: unknown): type is DashboardGadgetType {
        return typeof type === 'string' && (DASHBOARD_GADGET_TYPES as readonly string[]).includes(type);
    }

    /**
     * 이 운영자가 쓸 수 있는 종류인가 — 필요 권한 중 하나라도 있으면(권한이 없는 종류는 누구나)
     * @param {DashboardGadgetType} type - 종류.
     * @param {(code: string) => boolean} can - 권한 판정.
     * @returns {boolean} 쓸 수 있으면 true.
     */
    static allowed(type: DashboardGadgetType, can: (code: string) => boolean): boolean {
        const p = UtilDashboardLayout.spec(type).permissions;
        return p.length === 0 || p.some(can);
    }

    /**
     * 행 수 → 높이(px, 사이 간격 포함)
     * @param {number} h - 행 수.
     * @returns {number} px.
     */
    static px(h: number): number {
        const rows = Math.max(1, h);
        return rows * ROW_HEIGHT + (rows - 1) * GAP;
    }

    /**
     * 가장 아래 가젯의 아래 끝(행)
     * @param {readonly { y: number; h: number }[]} items - 자리들.
     * @returns {number} 행.
     */
    static bottom(items: readonly { y: number; h: number }[]): number {
        return UtilHomeLayout.bottom(items);
    }

    /**
     * 읽는 순서(위 → 아래, 같은 행이면 왼쪽 → 오른쪽) — 좁은 화면 한 열·Tab 순서
     * @param {readonly DashboardGadgetItem[]} items - 자리들.
     * @returns {DashboardGadgetItem[]} 정렬한 새 배열.
     */
    static byReadingOrder(items: readonly DashboardGadgetItem[]): DashboardGadgetItem[] {
        return fromPlaced(UtilHomeLayout.byReadingOrder(toPlaced(items)));
    }

    /**
     * 위로 당기고 읽는 순서로 정렬한다
     * @param {readonly DashboardGadgetItem[]} items - 자리들.
     * @returns {DashboardGadgetItem[]} 새 자리.
     */
    static compact(items: readonly DashboardGadgetItem[]): DashboardGadgetItem[] {
        return settle(items);
    }

    /**
     * 저장 값을 읽는다 — 모르는 종류·겹친 ID·한 개만 되는 종류의 두 번째는 버리고, 범위 밖 값은 범위 안으로(종류별 최소 크기), 위로 당긴다.
     * 저장한 적 없으면(`gadgets` 가 배열이 아니면) null.
     * @param {unknown} raw - 서버 값(`DashboardLayoutBody`).
     * @returns {DashboardGadgetItem[] | null} 읽은 구성.
     */
    static read(raw: unknown): DashboardGadgetItem[] | null {
        const r = raw !== null && typeof raw === 'object' ? (raw as Record<string, unknown>) : {};
        if (!Array.isArray(r.gadgets)) return null;
        // 칸 수가 다른 때 저장한 구성(`columns` 12·24)은 위치·폭을, 40px 행 때 저장한 구성(`version` 1)은 위치·높이를 늘려 읽는다(같은 자리·같은 px)
        const factor = r.columns === 12 || r.columns === 24 ? COLUMNS / r.columns : 1;
        const factorY = r.version === 1 ? 2 : 1;
        const ids = new Set<string>();
        const singles = new Set<DashboardGadgetType>();
        const out: DashboardGadgetItem[] = [];
        for (const rawItem of r.gadgets) {
            if (out.length >= MAX_GADGETS) break;
            const it = rawItem !== null && typeof rawItem === 'object' ? (rawItem as Record<string, unknown>) : {};
            const id = typeof it.id === 'string' ? it.id : '';
            if (!id || ids.has(id) || !UtilDashboardLayout.isKnown(it.type)) continue;
            const spec = UtilDashboardLayout.spec(it.type);
            if (!spec.multiple) {
                if (singles.has(spec.type)) continue;
                singles.add(spec.type);
            }
            ids.add(id);
            const w = int(typeof it.w === 'number' ? it.w * factor : it.w, spec.w, spec.minW, COLUMNS);
            out.push({ id, type: spec.type, x: int(typeof it.x === 'number' ? it.x * factor : it.x, 0, 0, COLUMNS - w), y: int(typeof it.y === 'number' ? it.y * factorY : it.y, 0, 0, MAX_Y), w, h: int(typeof it.h === 'number' ? it.h * factorY : it.h, spec.h, spec.minH, MAX_H), config: readConfig(it.config, spec) });
        }
        return settle(out);
    }

    /**
     * 저장 모양으로 — 읽는 순서로 정렬해 보낸다
     * @param {readonly DashboardGadgetItem[]} items - 구성.
     * @returns {DashboardLayoutBody} 본문.
     */
    static toBody(items: readonly DashboardGadgetItem[]): DashboardLayoutBody {
        return {
            version: UtilDashboardLayout.VERSION,
            columns: COLUMNS,
            gadgets: UtilDashboardLayout.byReadingOrder(items).map(({ id, type, x, y, w, h, config }) => ({ id, type, x, y, w, h, config: UtilDashboardLayout.sortedConfig(config) })),
        };
    }

    /**
     * 기본 구성 — `DEFAULT_LAYOUT` 중 이 운영자가 볼 수 있는 종류만 그 자리에 두고 위로 당긴다.
     * 하나도 볼 수 없으면 바로가기 하나를 둔다. 나머지 종류는 「가젯 추가」로 넣는다.
     * @param {(code: string) => boolean} can - 권한 판정.
     * @param {(type: DashboardGadgetType) => string} newId - 새 가젯 ID.
     * @returns {DashboardGadgetItem[]} 구성.
     */
    static defaults(can: (code: string) => boolean, newId: (type: DashboardGadgetType) => string): DashboardGadgetItem[] {
        const items = DEFAULT_LAYOUT.filter((it) => UtilDashboardLayout.allowed(it.type, can)).map((it) => ({ ...it, id: newId(it.type), config: {} }));
        if (!items.length) {
            const spec = UtilDashboardLayout.spec('SHORTCUTS');
            return [{ id: newId('SHORTCUTS'), type: 'SHORTCUTS', x: 0, y: 0, w: spec.w, h: spec.h, config: {} }];
        }
        return settle(items);
    }

    /**
     * 위치·크기를 정한다 — 범위 밖 값은 범위 안으로(종류별 최소 크기). `y` 를 주면 「그 행에 있는 가젯 앞」으로 놓고 당긴다.
     * @param {readonly DashboardGadgetItem[]} items - 지금 자리.
     * @param {string} id - 가젯 ID.
     * @param {Partial<Pick<DashboardGadgetItem, 'x' | 'y' | 'w' | 'h'>>} box - 바꿀 값.
     * @returns {DashboardGadgetItem[]} 새 자리.
     */
    static setBox(items: readonly DashboardGadgetItem[], id: string, box: Partial<Pick<DashboardGadgetItem, 'x' | 'y' | 'w' | 'h'>>): DashboardGadgetItem[] {
        const me = items.find((it) => it.id === id);
        if (!me) return [...items];
        const spec = UtilDashboardLayout.spec(me.type);
        const w = Math.min(COLUMNS, Math.max(spec.minW, Math.round(box.w ?? me.w)));
        const x = Math.min(COLUMNS - w, Math.max(0, Math.round(box.x ?? me.x)));
        const h = Math.min(MAX_H, Math.max(spec.minH, Math.round(box.h ?? me.h)));
        const y = box.y === undefined ? me.y : Math.max(0, box.y) - 0.5;
        return settle(
            items.map((it) => (it.id === id ? { ...it, x, y, w, h } : it)),
            box.y === undefined ? id : undefined,
        );
    }

    /**
     * 키보드 옮기기 — 왼쪽·오른쪽은 한 칸, 위·아래는 가로로 겹치는 바로 위·아래 가젯과 자리를 바꾼다(위로 당긴 구성이라 한 행씩은 움직이지 않는다).
     * @param {readonly DashboardGadgetItem[]} items - 지금 자리.
     * @param {string} id - 가젯 ID.
     * @param {'left' | 'right' | 'up' | 'down'} dir - 방향.
     * @returns {DashboardGadgetItem[]} 새 자리(움직일 수 없으면 그대로).
     */
    static nudge(items: readonly DashboardGadgetItem[], id: string, dir: 'left' | 'right' | 'up' | 'down'): DashboardGadgetItem[] {
        const me = items.find((it) => it.id === id);
        if (!me) return [...items];
        if (dir === 'left' || dir === 'right') return UtilDashboardLayout.setBox(items, id, { x: me.x + (dir === 'left' ? -1 : 1) });
        const others = items.filter((it) => it.id !== id && overlapsX(it, me));
        if (dir === 'up') {
            const above = others.filter((it) => it.y < me.y).sort((a, b) => b.y + b.h - (a.y + a.h) || b.y - a.y)[0];
            return above ? UtilDashboardLayout.setBox(items, id, { y: above.y }) : [...items];
        }
        const below = others.filter((it) => it.y > me.y).sort((a, b) => a.y - b.y)[0];
        return below ? UtilDashboardLayout.setBox(items, id, { y: below.y + below.h + 0.25 }) : [...items];
    }

    /**
     * 가젯을 맨 아래에 넣는다(종류의 기본 크기)
     * @param {readonly DashboardGadgetItem[]} items - 지금 자리.
     * @param {string} id - 새 가젯 ID.
     * @param {DashboardGadgetType} type - 종류.
     * @returns {DashboardGadgetItem[]} 새 자리.
     */
    static add(items: readonly DashboardGadgetItem[], id: string, type: DashboardGadgetType): DashboardGadgetItem[] {
        const spec = UtilDashboardLayout.spec(type);
        return settle([...items, { id, type, x: 0, y: UtilHomeLayout.bottom(items), w: spec.w, h: spec.h, config: {} }]);
    }

    /**
     * 가젯을 뺀다(아래 가젯이 당겨 올라온다)
     * @param {readonly DashboardGadgetItem[]} items - 지금 자리.
     * @param {string} id - 뺄 가젯 ID.
     * @returns {DashboardGadgetItem[]} 새 자리.
     */
    static remove(items: readonly DashboardGadgetItem[], id: string): DashboardGadgetItem[] {
        return settle(items.filter((it) => it.id !== id));
    }

    /**
     * 더 넣을 수 있는 종류인가 — 여러 개 되는 종류(메모·바로가기)거나 아직 없으면
     * @param {readonly DashboardGadgetItem[]} items - 지금 자리.
     * @param {DashboardGadgetType} type - 종류.
     * @returns {boolean} 넣을 수 있으면 true.
     */
    static addable(items: readonly DashboardGadgetItem[], type: DashboardGadgetType): boolean {
        if (items.length >= MAX_GADGETS) return false;
        return UtilDashboardLayout.spec(type).multiple || !items.some((it) => it.type === type);
    }

    /**
     * 두 구성이 같은가(가젯 순서 무관, 설정 포함) — 저장하지 않은 변경 표시
     * @param {readonly DashboardGadgetItem[]} a - 구성.
     * @param {readonly DashboardGadgetItem[]} b - 구성.
     * @returns {boolean} 같으면 true.
     */
    static same(a: readonly DashboardGadgetItem[], b: readonly DashboardGadgetItem[]): boolean {
        if (a.length !== b.length) return false;
        const key = (l: readonly DashboardGadgetItem[]) =>
            [...l]
                .map((it) => `${it.id}:${it.type}:${it.x},${it.y},${it.w},${it.h}:${JSON.stringify(UtilDashboardLayout.sortedConfig(it.config))}`)
                .sort()
                .join('|');
        return key(a) === key(b);
    }

    /**
     * 설정을 비교·저장하기 좋게 — 빈 칸을 빼고 키 차례를 고정한다
     * @param {DashboardGadgetConfig} config - 설정.
     * @returns {DashboardGadgetConfig} 정리한 새 설정.
     */
    static sortedConfig(config: DashboardGadgetConfig): DashboardGadgetConfig {
        const out: DashboardGadgetConfig = {};
        if (config.title) out.title = config.title;
        if (config.refresh) out.refresh = config.refresh;
        if (config.limit) out.limit = config.limit;
        if (config.days) out.days = config.days;
        if (config.text) out.text = config.text;
        if (config.links?.length) out.links = [...config.links];
        if (config.color) out.color = config.color;
        return out;
    }

    /**
     * 종류의 추천 배경색(중요한 가젯만 — 나머지는 흰색)
     * @param {DashboardGadgetType} type - 종류.
     * @returns {DashboardGadgetColor} 색.
     */
    static recommendedColor(type: DashboardGadgetType): DashboardGadgetColor {
        return RECOMMENDED_COLOR[type] ?? 'NONE';
    }

    /**
     * 가젯이 칠할 배경색 — 고른 색, 없으면 추천 색
     * @param {DashboardGadgetItem} item - 가젯.
     * @returns {DashboardGadgetColor} 색.
     */
    static colorOf(item: DashboardGadgetItem): DashboardGadgetColor {
        return item.config.color ?? UtilDashboardLayout.recommendedColor(item.type);
    }

    /**
     * 배경색의 기준색(`#rrggbb`) — 흰색이면 null
     * @param {DashboardGadgetColor} color - 색.
     * @returns {string | null} 기준색.
     */
    static colorHex(color: DashboardGadgetColor): string | null {
        return color === 'NONE' ? null : COLOR_HEX[color];
    }

    /**
     * 바로가기로 고를 수 있는 화면 — 관리자 메뉴(`UtilAdminMenu.ITEMS`)에서 이 운영자가 열 수 있는 화면 항목(잎)만, 메뉴 차례. 대시보드 자신은 뺀다.
     * @param {(code: string) => boolean} can - 권한 판정.
     * @returns {DashboardShortcutChoice[]} 고를 수 있는 화면.
     */
    static shortcutChoices(can: (code: string) => boolean): DashboardShortcutChoice[] {
        const out: DashboardShortcutChoice[] = [];
        for (const node of UtilAdminMenu.visible(can)) {
            const groupKey = UtilAdminMenu.isGroup(node) ? node.labelKey : null;
            const items = UtilAdminMenu.leaves(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            for (const item of items) {
                if (item.to === '/' || out.some((c) => c.to === item.to)) continue;
                out.push({ to: item.to, labelKey: item.labelKey, icon: item.icon ?? null, groupKey });
            }
        }
        return out;
    }

    /**
     * 이 행·칸 자리 글자에 쓸 1부터 센 값
     * @param {DashboardGadgetItem} it - 자리.
     * @returns {{ col: number; row: number; w: number; h: number }} 1부터 센 칸·행과 크기.
     */
    static position(it: DashboardGadgetItem): { col: number; row: number; w: number; h: number } {
        return { col: it.x + 1, row: it.y + 1, w: it.w, h: it.h };
    }
}
