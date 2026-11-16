import type { HomeLayout } from '#layers/home-layout/utils/util-home-layout';

/** 공구 가젯 정렬 — 마감 임박 순(기본) · 새로 시작한 순 · 참여 많은 순(사용자 결정 2026-10-29) */
export type AdminPreviewGroupBuySort = 'DEADLINE' | 'NEWEST' | 'POPULAR';

/**
 * 가젯 내용 설정(관리자 ⚙ 설정 창 — admin-web `UtilGadgetConfig`) — 서버 JSON 그대로의 평평한 모양, 종류마다 쓰는 칸만(보낸 칸만 덮는다 — 없는 칸은 공개 응답 그대로):
 * 공구 `count`·`sort` · 글 목록 `boardScope`·`boardIds`·`excludeBoardIds`·`count`·`sort`·`periodTabs`·`tagTabs` + 보기 모양(`rowStyle`·`columns`·`thumbnail`·`titleLines`·`lead`·`meta`·`dividers`·`newBadgeHours`·`hotBadgeLikes`·`likes`·`rowSpacing`·`mobilePages`·`mobilePageSize`) ·
 * 갤러리 `boardScope`·`boardIds`·`count` · 게시판 카드 `boardScope`·`boardIds`·`perBoard`·`cardHead`·`columns`·`outline`·`dividers`·`leadPlaceholder` · 하이라이트 `count`·`lead`·`thumbs`·`showAuthor`·`narrow` · 소모임 `count` ·
 * 바로가기 `shape`·`items` · 안내 글 `text`·`format`(2026-10-30 — 8종 전부) · 광고 배너 `adType` · 한 줄 광고 `count`.
 */
export interface AdminPreviewGadgetConfig {
    count?: number;
    sort?: string;
    /** `BOARD` = 지금 보는 게시판(게시판 레이아웃에서만, 2026-10-30) */
    boardScope?: 'ALL' | 'FOLDER' | 'PICK' | 'BOARD';
    boardIds?: string[];
    /** 글 목록 — 전체 게시판(`ALL`)에서 뺄 게시판 slug(20개까지, 다른 범위면 무시 — home-news-contract) */
    excludeBoardIds?: string[];
    periodTabs?: boolean;
    /** 글 목록 — 태그 탭(post-tags-contract, 최대 8개 — 저장 전 탭은 받은 `tagged` 에 없으면 빈 탭) */
    tagTabs?: string[];
    perBoard?: number;
    cardHead?: boolean;
    outline?: boolean;
    leadPlaceholder?: boolean;
    /** 핫딜 줄 사진(`HOT_DEALS` — 기본 켬) */
    thumbnails?: boolean;
    rowStyle?: string;
    columns?: number | 'AUTO';
    thumbnail?: string;
    titleLines?: number;
    lead?: string;
    meta?: string[];
    dividers?: boolean;
    newBadgeHours?: number;
    hotBadgeLikes?: number;
    /** 글 목록 — 추천 표시 · 두 줄 줄 여백 · 모바일 쪽 넘기기(쪽 수 · 쪽당 글 수) */
    likes?: string;
    rowSpacing?: string;
    mobilePages?: number;
    mobilePageSize?: number;
    thumbs?: string;
    /** 하이라이트 — 작성자(아바타·닉네임) 보이기 */
    showAuthor?: boolean;
    /** 하이라이트 — 좁은 칸 모양 */
    narrow?: string;
    shape?: string;
    items?: { label: string; kind: string; value: string | null }[];
    text?: string;
    format?: string;
    /** 마우스 올림 효과(항목이 있는 종류 공통 — `HOVER_KINDS`) */
    hover?: boolean;
    /** 가젯 페이지 제목 카드에 넣기(관리자 ⚙ 「노출」) */
    pageHead?: boolean;
    /** `AD` 배너 유형 */
    adType?: string;
}

/** 가젯 페이지 머리(관리자의 저장 전 값 — 가젯 페이지 미리보기일 때만) */
export interface AdminPreviewPage {
    name: string;
    description: string;
    writeButton: boolean;
}

/** 게시판 레이아웃 미리보기 값(관리자의 저장 전 값 — 게시판 목록·글 보기를 띄웠을 때만) — 적용 화면. 배치는 `home` 자리로 온다(본문 칸 `BOARD_BODY` 포함) */
export interface AdminPreviewBoard {
    screens: { list: boolean; post: boolean };
}

/**
 * 관리자 미리보기의 홈 구성 — 관리자 홈 구성 편집기의 저장 전 값(가젯은 id·종류·제목·보이는 폭 + 꾸미기 `style`(관리자가 고친 것만 — 없으면 공개 응답 그대로) + 내용 설정 `config`).
 * `style` 은 받은 모양 그대로 두고, 그릴 때 `UtilGadget.sanitize` 가 공개 응답과 같은 규칙으로 읽는다.
 */
export interface AdminPreviewHome {
    layout: HomeLayout;
    gadgets: {
        id: string;
        type: string;
        title: string;
        desktop: boolean;
        mobile: boolean;
        style?: Record<string, unknown> | null;
        config?: AdminPreviewGadgetConfig | null;
        /** 편집기의 종류별 최소 높이(행) — 「n칸 줄여도」가 그 아래로 권하지 않게 */
        minH?: number;
    }[];
}

/** 관리자가 보내는 미리보기 값 — 메뉴 칸은 운영자 설정 모양 그대로(`THEME` 또는 값, 읽기는 `UtilMenuPosition` 이 한다) */
export interface AdminPreviewMessage {
    theme: string;
    menu: {
        menuPosition: unknown;
        topItems: unknown;
        barGroupIds: unknown;
        menuWidth: unknown;
        menuOverflow: unknown;
        menuStyle: Record<string, unknown>;
        /** 메뉴 막대 글자 기본 굵게(켬·끔) — 불리언이 아니면 덮지 않는다 */
        menuBold?: unknown;
    } | null;
    home: AdminPreviewHome | null;
    /** 홈 구성 편집 중인가 — 격자에 손잡이를 얹는다 */
    editing: boolean;
    /** 가젯 페이지 머리(가젯 페이지 `/p/{key}` 를 띄웠을 때) — 없으면 null */
    page: AdminPreviewPage | null;
    /** 게시판 레이아웃의 적용 화면(게시판 목록·글 보기를 띄웠을 때) — 없으면 null */
    board: AdminPreviewBoard | null;
}

/** 편집 중 격자가 관리자에게 알리는 일 — 옮기기(놓은 칸) · 크기(칸·행) · 빼기 · 설정 열기(⚙) · 모바일 한 열 차례(`before` 가젯 앞으로, null = 맨 뒤) */
export type AdminPreviewLayoutOp =
    | { op: 'place'; id: string; x: number; y: number }
    | { op: 'order'; id: string; before: string | null }
    | { op: 'size'; id: string; w: number; h: number }
    | { op: 'remove'; id: string }
    | { op: 'settings'; id: string };

/** 관리자에서 설정(⚙)을 고칠 수 있는 가젯 종류 — 8종 전부(2026-10-30) + 광고 2종. admin-web `UtilGadgetConfig` 의 종류와 같다 */
export const SETTABLE_KINDS: readonly string[] = ['HIGHLIGHT', 'POST_LIST', 'BOARD_CARDS', 'GALLERY', 'GROUPS', 'GROUP_BUY', 'HOT_DEALS', 'QUICK_LINKS', 'NOTICE_TEXT', 'AD', 'AD_TEXT_LINE'];
/** 팁·강좌·사용기 가젯 — 내용은 /tips 화면이 받아 나눠 주므로 응답에 같은 종류가 없어도 새 가젯을 그린다 */
export const TIPS_KINDS: readonly string[] = ['TIPS_HEADLINE', 'TIPS_LATEST', 'TIPS_POPULAR', 'TIPS_TAGS'];
/** 게시판 레이아웃의 고정 칸 「게시판 본문」 — 그 자리에 게시판 목록(·글 보기) 화면이 그려진다(admin-web `UtilBoardLayout.BODY` 와 같은 값). 빼기·⚙ 가 없다 */
export const BOARD_BODY = 'BOARD_BODY';


/** 관리자 → 공개 사이트 메시지 종류 */
export const ADMIN_PREVIEW_APPLY = 'irobit:admin-preview';
/** 공개 사이트 → 관리자 메시지 종류 — 준비됨 · 메뉴 자리를 눌렀다 · 홈 격자 편집(`AdminPreviewLayoutOp`) */
export const ADMIN_PREVIEW_READY = 'irobit:admin-preview-ready';
export const ADMIN_PREVIEW_EDIT_MENU = 'irobit:admin-preview-edit-menu';
export const ADMIN_PREVIEW_LAYOUT = 'irobit:admin-preview-layout';

/**
 * 관리자 미리보기(관리자 테마 화면의 iframe, 사용자 결정 2026-10-28 — 미리보기는 실제 공개 화면과 똑같아야 한다)의 **가벼운 판정** — 창 판정·편집 손잡이의 빼기·⚙.
 * 메시지 읽기와 홈 구성 덮기는 미리보기 창에서만 받는 `util-admin-preview-apply.ts`(`UtilAdminPreviewApply`)에 있다(첫 화면 조각에서 뺀다 — 관리자 QA 2026-11-17 E11).
 * 받는 쪽은 `plugins/03.admin-preview.client.ts`, 보내는 쪽은 admin-web `components/theme/ThemeLivePreview.vue`.
 */
export default class UtilAdminPreview {
    /**
     * 이 창이 관리자 미리보기로 열렸는가 — iframe 안이고 주소에 `adminPreview=1` 이 있다(관리자 `ThemeLivePreview` 가 붙인다).
     * 미리보기 켜기(`plugins/03.admin-preview.client.ts`)와 방문 신호 빼기(`plugins/04.analytics.client.ts`)가 같은 판정을 쓴다 — 앱이 뜰 때의 주소로 한 번 본다.
     * @param {boolean} inFrame - iframe 안인가(`window.parent !== window`).
     * @param {string} search - 처음 주소의 쿼리(`window.location.search`).
     * @returns {boolean} 미리보기 창이면 true.
     */
    static isPreviewWindow(inFrame: boolean, search: string): boolean {
        return inFrame && new URLSearchParams(search).get('adminPreview') === '1';
    }

    /**
     * 편집 손잡이에 × 빼기를 다는가 — 고정 칸(게시판 본문)은 뺄 수 없다.
     * @param {string} type - 가젯 종류.
     * @returns {boolean} 뺄 수 있으면 true.
     */
    static removable(type: string): boolean {
        return type !== BOARD_BODY;
    }

    /**
     * 관리자에서 내용 설정(⚙)을 고칠 수 있는 종류인가 — 편집 손잡이에 ⚙ 단추를 단다.
     * @param {string} type - 가젯 종류.
     * @returns {boolean} 고칠 수 있으면 true.
     */
    static settable(type: string): boolean {
        return SETTABLE_KINDS.includes(type);
    }
}
