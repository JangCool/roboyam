/**
 * 관리자 대시보드 구성(운영자마다 따로) — `GET/PUT /api/admin/me/dashboard` 의 모양(서버 `DashboardLayoutRequest`·`DashboardLayoutView`).
 * 격자는 48칸 고정, 가젯 하나 = `{ id, type, x, y, w, h, config }`(칸·행 단위, 0부터). `h` 는 **고정 높이**다(홈과 달리 내용 따라 늘지 않고 가젯 안에서 스크롤).
 * 계산(읽기 검증·기본 구성·옮기기·크기)은 `utils/util-dashboard-layout.ts`.
 */

/** 가젯 종류 — 보일 순서(가젯 추가 창·기본 구성의 차례) */
export const DASHBOARD_GADGET_TYPES = [
    'TODO',
    'COMMERCE_TODO',
    'TODAY_SCHEDULE',
    'REPORTS',
    'POST_ACTIVITY',
    'RECENT_POSTS',
    'BOARD_RANKING',
    'POPULAR_NOW',
    'VISITORS',
    'GROUP_REQUESTS',
    'MEMBER_STATS',
    'SIGNUP_TREND',
    'RECENT_SIGNUPS',
    'SANCTIONS',
    'NICKNAME_IMAGES',
    'HOTDEAL_STATUS',
    'HOTDEAL_TOP',
    'AD_PERFORMANCE',
    'AD_SCHEDULE',
    'GROUPBUY_PROGRESS',
    'GROUPBUY_QNA',
    'SALES_SUMMARY',
    'ORDER_TREND',
    'RECONCILE',
    'POINT_SUMMARY',
    'POINTSHOP',
    'BATCH_HEALTH',
    'NOTIFICATION_HEALTH',
    'AUDIT_RECENT',
    'OPERATOR_SECURITY',
    'MEMO',
    'SHORTCUTS',
] as const;

export type DashboardGadgetType = (typeof DASHBOARD_GADGET_TYPES)[number];

/** 자동 새로 고침(초) — 0 = 끔. 최소 1분(서버 부담) */
export type DashboardRefresh = 0 | 60 | 300;
/** 목록 개수 */
export type DashboardListLimit = 5 | 10;
/** 추이 기간(일) */
export type DashboardTrendDays = 7 | 30;

/** 가젯 하나의 설정 — 종류에 해당하는 칸만 쓴다(`UtilDashboardLayout.KINDS` 의 `options`) */
export interface DashboardGadgetConfig {
    /** 제목 — 비우면 종류 이름(최대 40자) */
    title?: string;
    /** 자동 새로 고침(초) — 기본 0(끔). 탭이 안 보이면 멈춘다 */
    refresh?: DashboardRefresh;
    /** 목록 개수(목록형) */
    limit?: DashboardListLimit;
    /** 추이 기간(가입 추이) */
    days?: DashboardTrendDays;
    /** 개인 메모 본문(평문 0~1000자) — `MEMO` */
    text?: string;
    /** 바로가기 화면 주소(관리자 메뉴의 화면 항목 `to`, 최대 12) — `SHORTCUTS` */
    links?: string[];
    /** 배경색 — 없으면 업무 묶음별 추천 색(`UtilDashboardLayout.colorOf`), `NONE` 이면 흰 카드 */
    color?: DashboardGadgetColor;
}

/** 가젯 배경색(옅은 바탕) — 보일 차례 */
export const DASHBOARD_GADGET_COLORS = ['NONE', 'SLATE', 'BLUE', 'SKY', 'TEAL', 'GREEN', 'AMBER', 'ORANGE', 'ROSE', 'VIOLET'] as const;
export type DashboardGadgetColor = (typeof DASHBOARD_GADGET_COLORS)[number];

/** 배치된 가젯 하나 */
export interface DashboardGadgetItem {
    /** 가젯 ID — 화면이 만든 문자열(같은 구성 안에서 유일) */
    id: string;
    type: DashboardGadgetType;
    /** 왼쪽 칸(0 ~ 12 - w) */
    x: number;
    /** 위 행(0부터) */
    y: number;
    /** 폭(칸 수) */
    w: number;
    /** 높이(행 수, 고정) */
    h: number;
    config: DashboardGadgetConfig;
}

/** `GET`·`PUT /api/admin/me/dashboard` 본문 */
export interface DashboardLayoutBody {
    /** 저장 모양 판(지금 2 — 1 은 행 40px) — 모양이 바뀌면 올린다 */
    version: number;
    /** 격자 칸 수 — 48(예전에 저장한 값은 12·24, 읽을 때 늘린다) */
    columns: 48;
    /** 저장한 적 없으면 null — 화면이 권한으로 기본 구성을 만든다 */
    gadgets: DashboardGadgetItem[] | null;
}
