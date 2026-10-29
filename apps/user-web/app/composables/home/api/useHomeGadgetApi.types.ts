/**
 * 공개 홈 가젯 API(`GET /api/home/gadgets`)가 주고받는 모양 — 서버 `HomeGadgetService`(site-theme-layout-contract 「API」, 2026-10-29).
 * `GROUP_BUY` 줄은 회원 공구 목록(`GET /api/group-buys`)과 같은 모양이다. `GalleryItem.kind` 는 null, `locked` 는 false 다(노출 기간·보는 사람 설정 없음).
 * community `docs/api-home-gadgets.md` 1·2·4절의 공개 응답을 로보얌으로 옮겼다. 다른 점:
 *  - **템플릿 = 사이트 테마**(사용자 결정 2026-10-28) — 응답의 배치는 서버가 지금 사이트 테마(`default` 기본형 · `daum` 다음식 · `roboyam` 로보얌형 · `naver` 네이버형)로 정한다.
 *    요청에 템플릿·테마를 싣지 않는다. 이용자 유형별 템플릿 규칙·「기본 템플릿」 고르기는 없다. 응답의 `theme` 은 이 배치가 어느 테마 것인지 알려 주는 값이다.
 *  - **배치 = 격자 좌표**(사용자 결정 2026-10-28 — community 의 고정 칸 이름(`MAIN`·`SIDE` …) 대신): 운영자가 관리자 홈 배치 편집기에서 테마마다 칸 수(12·24)와 가젯별 위치·크기를 정한다.
 *    모양·계산은 공유 레이어 `@roboyam/home-layout`(`UtilHomeLayout`) — 행 높이 15px · 간격 16px 고정, `h` 는 최소 높이. 모바일 한 열 순서는 (y, x) 순서다(따로 받지 않는다).
 *  - 가젯 종류는 8종(`HIGHLIGHT`·`POST_LIST`·`BOARD_CARDS`·`GALLERY`·`GROUPS`·`GROUP_BUY`·`QUICK_LINKS`·`NOTICE_TEXT`) — 로보얌에 없는 핫딜·직접홍보·광고(`DEAL_PLACEMENT`·`PROMO_TOP`·`AD`)는 없다.
 *  - `GROUPS`·`GROUP_BUY` 도 가젯 `data` 에 목록을 싣는다(community 는 `null` + 컴포넌트가 따로 받음) — 컴포넌트는 서버를 부르지 않는다(로보얌 `components.md`).
 *  - 노출 기간·보는 사람(`audience`)은 **서버가 걸러** 응답에서 뺀다. 로그인한 사람만 보는 가젯을 로그인하지 않은 방문자에게 안내로 보일 때만 `locked: true` 뼈대로 온다.
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO. 화면은 없는 칸·모르는 값에도 깨지지 않게 `UtilHomeGadget.sanitize` 로 한 번 읽는다(모르는 종류는 건너뛴다).
 */
import type { HomeLayout } from '#layers/home-layout/utils/util-home-layout';
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { GroupRow } from '~/composables/group/api/useGroupApi.types';
import type { GroupBuyListItem } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { PopularPeriod } from '~/composables/home/api/useHomeApi.types';

/** 가젯 종류 8종 — 서버 레지스트리의 키. 서버가 새 종류를 먼저 보내면 화면은 모르는 키로 받아 건너뛴다 */
export type GadgetKind = 'HIGHLIGHT' | 'POST_LIST' | 'BOARD_CARDS' | 'GALLERY' | 'GROUPS' | 'GROUP_BUY' | 'QUICK_LINKS' | 'NOTICE_TEXT';

/** 머리 모양 — `HIDDEN` 은 머리를 그리지 않는다(제목은 영역 이름으로만 읽힌다) */
export type GadgetHeaderStyle = 'BAR' | 'UNDERLINE' | 'FILLED' | 'PLAIN' | 'HIDDEN';
export type GadgetTitleSize = 'S' | 'M' | 'L';
/** 카드 모양 — `DEFAULT` 테마의 카드 · `FLAT` 바탕·테두리 없음 · `OUTLINE` 테두리 강조 · `TINT` 옅은 색 바탕 */
export type GadgetCardLook = 'DEFAULT' | 'FLAT' | 'OUTLINE' | 'TINT';
export type GadgetMobileFold = 'NONE' | 'OPEN' | 'CLOSED';

/** 비었을 때 — `keep` 영역(제목·틀)을 남기는가 · `message` 안내를 보이는가 · `text` 안내 문구(비우면 종류별 기본 문구) */
export interface GadgetEmptyStyle {
    keep: boolean;
    message: boolean;
    text: string;
}

/**
 * 가젯 공통 꾸미기(`style`) — community 의 칸 중 공개 화면이 그리는 것만. `density`(줄 간격)는 로보얌에 목록 밀도 보기 설정이 없어 뺐고,
 * `more`(「더보기」 설정)는 서버가 계산한 `HomeGadget.more` 만 쓰므로 `style` 에는 두지 않는다.
 */
export interface GadgetStyle {
    /** `pi-…` PrimeIcons 이름 또는 null */
    icon: string | null;
    headerStyle: GadgetHeaderStyle;
    titleSize: GadgetTitleSize;
    /** 색 값(프리셋 12색 이름 또는 `#rrggbb` — `UtilColor`). null 이면 자동(주 색상) */
    titleColor: string | null;
    subtitle: string;
    cardLook: GadgetCardLook;
    /** null 이면 `titleColor` 를 따른다 */
    cardColor: string | null;
    mobileFold: GadgetMobileFold;
    empty: GadgetEmptyStyle;
}

/** 서버가 계산한 「더보기」 — `label` 이 null 이면 화면의 기본 문구(「더보기」). 없으면 가젯이 null */
export interface GadgetMore {
    label: string | null;
    /** 이 사이트 안 경로 */
    to: string;
}

/**
 * 가젯 한 개. `config`·`data` 의 모양은 종류마다 다르다(아래 `*Config`·`*Data`, 읽는 곳은 `UtilHomeGadget`).
 * `type` 은 string — 모르는 키도 받는다(그리지 않는다).
 */
export interface HomeGadget {
    id: string;
    type: string;
    /** 서버가 늘 채운다(기능 이름을 따르는 가젯은 그 이름으로) */
    title: string;
    /** 데스크톱(lg 이상)에서 그리는가 — 서버는 기기를 모르므로 화면이 폭에 따라 거른다 */
    desktop: boolean;
    mobile: boolean;
    /** 내용이 없는가(서버 판정) — `style.empty.keep` 이 꺼져 있을 때만 자리째 생략한다 */
    empty: boolean;
    /** 로그인한 사람만 보는 가젯을 로그인하지 않은 방문자가 볼 때 — `config` 는 `{}`·`data` 는 null·`more` 는 null. 머리·틀 + 로그인 안내만 그린다 */
    locked: boolean;
    style: GadgetStyle;
    config: Record<string, unknown>;
    more: GadgetMore | null;
    data: unknown;
}

/** `GET /api/home/gadgets` 응답 */
export interface HomeGadgetsResponse {
    /** 이 배치가 어느 사이트 테마의 것인가(`default`·`daum`·`roboyam`·`naver`) — 화면은 지금 테마의 템플릿(겉모양)으로 그린다 */
    theme: string;
    /** 격자 배치 — 칸 수(12·24) + 가젯마다 `{ gadgetId, x, y, w, h }`. 배치에 자리가 없는 가젯은 화면이 맨 아래에 붙인다(`UtilHomeLayout.read`) */
    layout: HomeLayout;
    /** 가젯 목록(이 테마 배치에 놓인 것). 보이는 폭(`desktop`·`mobile`)은 가젯마다 */
    gadgets: HomeGadget[];
}

/* ────────────── 종류별 config (정규화한 모양 — `UtilHomeGadget` 가 읽는다) ────────────── */

export type PostListSort = 'LATEST' | 'POPULAR' | 'COMMENTS' | 'VIEWS' | 'LIKES';
/** 줄 모양 — 헤드라인(제목 2줄 + 오른쪽 사진) · 순위(번호) · 기사(왼쪽 사진 + 말풍선 댓글 수) · 촘촘(한 줄) */
export type PostListRowStyle = 'HEADLINE' | 'RANK' | 'ARTICLE' | 'COMPACT';
export type PostListThumbnail = 'AUTO' | 'NONE' | 'RIGHT' | 'LEFT';
export type PostListMeta = 'BOARD' | 'PREFIX' | 'COMMENTS' | 'TIME' | 'AUTHOR';
/** 게시판 이름·로고 색 — 자동(게시판 표시색) · 고정 색 · 회색 */
export type GadgetBadgeMode = 'AUTO' | 'FIXED' | 'NEUTRAL';

export interface GadgetBadgeConfig {
    mode: GadgetBadgeMode;
    /** `FIXED` 일 때만 */
    color: string | null;
}

export interface PostListConfig {
    sort: PostListSort;
    rowStyle: PostListRowStyle;
    thumbnail: PostListThumbnail;
    meta: PostListMeta[];
    badge: GadgetBadgeConfig;
    lead: 'NONE' | 'HERO';
    columns: 1 | 2;
    /** 순위 번호 — `show` null 이면 줄 모양이 `RANK` 일 때만. `color` 는 1~3위 색(없으면 주 색상) */
    rank: { show: boolean | null; color: string | null };
    /** 0 이면 줄 모양에 따른다 */
    titleLines: 0 | 1 | 2;
    /** 최근 N시간 글에 NEW(0 끔) */
    newBadgeHours: number;
    /** 추천 N 이상에 HOT(0 끔) */
    hotBadgeLikes: number;
    dividers: boolean;
    /** 게시판 필터가 비었는가(전체 게시판 — 투자 고지 판정 참고용) */
    allBoards: boolean;
}

export interface BoardCardsConfig {
    columns: 'AUTO' | 2 | 3;
    badge: GadgetBadgeConfig;
}

export interface HighlightConfig {
    lead: 'NONE' | 'HERO';
    /** 사진을 보일 글 — 모두 · 맨 위 글만 · 없음 */
    thumbs: 'ALL' | 'FIRST' | 'NONE';
}

export interface QuickLinksConfig {
    shape: 'PILL' | 'TAB';
}

/* ────────────── 종류별 data ────────────── */

/** `POST_LIST` — 게시판·정렬·개수는 서버가 정해 준다(화면은 자르지 않는다) */
export interface PostListData {
    items: PostFeedRow[];
    /**
     * 인기글 기간 탭(`config.periodTabs`)의 기간별 순위 — `REALTIME`(실시간)·`YESTERDAY`(어제)·`WEEK`(이번 주). 가젯은 서버를 부르지 않으므로 세 기간을 한 번에 받는다.
     * 없으면 탭 없이 `items` 만 그린다. `items` 는 실시간과 같다(탭을 모르는 화면용).
     */
    periods?: Partial<Record<PopularPeriod, PostFeedRow[]>> | null;
}

/** `BOARD_CARDS` 카드 한 장 — 게시판 하나(대표 글 + 제목들). 글이 없는 게시판도 카드는 온다 */
export interface BoardCard {
    board: {
        slug: string;
        name: string;
        nameEn: string | null;
        /** 운영자가 고른 표시색(프리셋 이름) 또는 null(자동 — 순서로 정한 색) */
        color: string | null;
        /** 투자 고지 게시판(산업·주식) — 카드 아래에 고지 한 줄 */
        investmentNotice: boolean;
    };
    lead: PostFeedRow | null;
    items: PostFeedRow[];
}

export interface BoardCardsData {
    cards: BoardCard[];
}

/** `GALLERY` 한 칸 — 사진이 있는 정상 공개 글만 */
export interface GalleryItem {
    postId: string;
    title: string;
    thumbnailUrl: string | null;
    /** 움직이는 그림·영상 글 표시 */
    kind: 'GIF' | 'VIDEO' | null;
    board: { slug: string; name: string; nameEn: string | null };
    likeCount: number;
    createdAt: string;
}

export interface GalleryData {
    items: GalleryItem[];
}

/**
 * `HIGHLIGHT` 한 칸 — 운영자가 고른 글(관리자 하이라이트 `/api/admin/highlights`, `popular-highlight-contract.md`). 노출 중인 것만, 관리자 순서.
 * `title`·`summary`·`imageUrl` 은 운영자가 적은 노출 제목·요약·대표 이미지(없으면 글 제목·요약 없음·글 썸네일).
 */
export interface HomeHighlight {
    id: string;
    post: PostFeedRow;
    title: string | null;
    summary: string | null;
    imageUrl: string | null;
}

export interface HighlightData {
    picks: HomeHighlight[];
}

/** `GROUPS` — 인기 소모임(`GET /api/groups/trending` 과 같은 줄 모양을 가젯 개수만큼) */
export interface GroupsData {
    items: GroupRow[];
}

/**
 * `GROUP_BUY` — 모집 중인 공개 공구를 가젯 `sort` 차례(마감 이른 순 기본 · 새로 시작한 순 · 참여 많은 순)로 `count`(기본 5, 1~10)개. 줄은 회원 공구 목록(`GET /api/group-buys`)의 줄 그대로다.
 * 판매 스위치가 꺼져도 목록처럼 공구는 오고 `salesOpen: false` 다(화면이 「지금은 주문을 받지 않아요」를 덧붙인다).
 */
export interface GroupBuyData {
    items: GroupBuyListItem[];
    salesOpen: boolean;
}

/** `QUICK_LINKS` 한 항목 — `kind` 로 주소를 화면이 만든다(`UtilHomeGadget.linkOf`). `BOARD` 의 `value` 는 게시판 slug, `PATH` 는 사이트 안 경로 */
export interface QuickLinkItem {
    label: string;
    kind: string;
    value: string | null;
}

export interface QuickLinksData {
    items: QuickLinkItem[];
}

/** `NOTICE_TEXT` — 운영자가 적은 평문(HTML 아님, 줄바꿈 가능) */
export interface NoticeTextData {
    text: string;
    format: 'PLAIN' | 'BULLETS';
}
