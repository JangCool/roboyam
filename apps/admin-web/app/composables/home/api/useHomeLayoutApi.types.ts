/**
 * 관리자 홈 배치 API(`/api/admin/home-layouts/**`)가 주고받는 모양 — 서버 `HomeLayoutController`(site-theme-layout-contract 「API」).
 * 홈 배치는 **테마마다 따로** 둔다(템플릿 = 테마, 사용자 결정 2026-10-28). 배치 모양·고정값·계산은 공유 레이어 `@roboyam/home-layout`(`UtilHomeLayout`) — 공개 홈(user-web)과 같은 값이다.
 * 범위는 **위치·크기·칸 수와 가젯 넣기·빼기** + 내용 설정(`config` — 공구의 개수·정렬 2026-10-29, 글 목록·갤러리·게시판 카드의 대상 게시판·개수·정렬은 가젯 페이지 때 넓혔다)이다.
 * 2026-10-30: ⚙ 설정 창이 8종 전부의 내용 설정 + 공통 설정(제목 · 꾸미기 `style` · 노출 `desktop`·`mobile`)을 고친다(gadget-page-contract 「가젯 설정」 — 서버 `HomeLayoutGadgetView`·`HomeLayoutSaveRequest`).
 * 새로 넣은 가젯은 서버가 종류별 처음 설정(테마 기본 꾸미기)으로 만든다. 가젯 페이지(`useGadgetPageApi`)의 배치도 같은 모양이다.
 * ⚠ ID 는 문자열(Snowflake). 화면에서 새로 넣은 가젯은 저장 전까지 `new-<n>`, 테마 기본 배치에서 다시 넣은 가젯은 기본 배치의 ID 를 쓴다(`seed-<n>` — 서버가 그 시드 설정으로, 모르는 ID 면 종류별 처음 값으로 새로 만든다).
 */
import type { HomeLayout } from '#layers/home-layout/utils/util-home-layout';

/** 가젯 종류 8종 — user-web `GadgetKind` 와 같은 키(로보얌에 없는 핫딜·직접홍보·광고는 없다) */
export type HomeGadgetKind = 'HIGHLIGHT' | 'POST_LIST' | 'BOARD_CARDS' | 'GALLERY' | 'GROUPS' | 'GROUP_BUY' | 'QUICK_LINKS' | 'NOTICE_TEXT';

/** 공구 가젯 정렬 — 마감 임박 순(기본) · 새로 시작한 순 · 참여 많은 순(사용자 결정 2026-10-29) */
export type HomeGroupBuySort = 'DEADLINE' | 'NEWEST' | 'POPULAR';

/** 글 목록 가젯 정렬 — 서버 `HomeGadgetService` 가 읽는 값: 최신 · 인기(순위 스냅샷) · 조회 · 추천 · 댓글(셋은 이번 주 인기글 후보를 그 수로 다시 정렬) */
export type HomePostListSort = 'LATEST' | 'POPULAR' | 'VIEWS' | 'LIKES' | 'COMMENTS';

/**
 * 대상 게시판 — `ALL` 전체 게시판(최신글과 같은 범위, 홈의 처음 값) · `FOLDER` 이 페이지가 연결된 메뉴 폴더의 게시판 전체(가젯 페이지에서만 — 폴더에 게시판을 넣고 빼면 따라간다) ·
 * `PICK` 직접 고른 게시판(`boardIds` = 게시판 slug, 고른 차례). 사용자 결정 2026-10-29.
 * `BOARD` 지금 보는 게시판(게시판 레이아웃에서만 — 공개 화면이 그 게시판 slug 로 고른다, 게시판 레이아웃에서 새로 넣은 가젯의 처음 값. 2026-10-30).
 */
export type HomeBoardScope = 'ALL' | 'FOLDER' | 'PICK' | 'BOARD';

/** 글 목록 줄 모양 — 헤드라인(제목 2줄 + 오른쪽 사진) · 순위(번호) · 기사(왼쪽 사진) · 촘촘(한 줄). user-web `PostListRowStyle` 과 같다 */
export type HomePostRowStyle = 'HEADLINE' | 'RANK' | 'ARTICLE' | 'COMPACT';
/** 글 목록 사진 자리 — 자동(줄 모양을 따름) · 없음 · 오른쪽 · 왼쪽 */
export type HomePostThumbnail = 'AUTO' | 'NONE' | 'RIGHT' | 'LEFT';
/** 글 줄에 보일 정보 — 게시판 · 말머리 · 댓글 수 · 시각 · 글쓴이 */
export type HomePostMeta = 'BOARD' | 'PREFIX' | 'COMMENTS' | 'TIME' | 'AUTHOR';
/** 바로가기 한 항목의 종류 — user-web `UtilHomeGadget.linkOf` 가 주소를 만든다(`BOARD` 값 = 게시판 slug, `PATH` 값 = `/` 로 시작하는 사이트 안 경로) */
export type HomeQuickLinkKind = 'ALL' | 'POPULAR' | 'LATEST' | 'GROUPS' | 'GROUP_BUY' | 'BOARD' | 'PATH';

/** 바로가기 한 항목 */
export interface HomeQuickLink {
    /** 이름 1~12자 */
    label: string;
    kind: HomeQuickLinkKind;
    /** `BOARD` 면 게시판 slug, `PATH` 면 사이트 안 경로, 나머지는 null */
    value: string | null;
}

/**
 * 가젯 내용 설정(실제 화면의 ⚙ → 설정 창 「내용」) — 서버 JSON 그대로의 평평한 모양이고 종류마다 쓰는 칸만 채운다. 고칠 수 있는 칸·범위·처음 값은 `UtilGadgetConfig`.
 *   · `GROUP_BUY` `count`(1~10) · `sort`(HomeGroupBuySort)
 *   · `POST_LIST` `boardScope`·`boardIds` · `count`(1~30) · `sort`(HomePostListSort) · `periodTabs`(인기일 때 실시간·어제·이번 주 탭)
 *       + 보기 모양 `rowStyle` · `columns` 1·2 · `thumbnail` · `titleLines` 0·1·2 · `lead` NONE·HERO · `meta` · `dividers` · `newBadgeHours` 0~72 · `hotBadgeLikes` 0~1000
 *   · `GALLERY` `boardScope`·`boardIds` · `count`(1~24)
 *   · `BOARD_CARDS` `boardScope`·`boardIds`(카드로 보일 게시판) · `perBoard`(카드마다 제목 수 1~10) · `cardHead`(카드 머리·「게시판 가기」 보이기, 기본 켬) + `columns` AUTO·2·3
 *   · `HIGHLIGHT` `count` 1~6 · `lead` HERO·NONE · `thumbs` ALL·FIRST·NONE / `GROUPS` `count` 1~6 /
 *     `QUICK_LINKS` `shape` PILL·TAB · `items`(12개까지) / `NOTICE_TEXT` `text`(0~300자) · `format` PLAIN·BULLETS
 * 관리자 응답은 그 종류의 표 칸 전부(저장 값, 없으면 처음 값 — 서버 `GadgetConfigs.view`)를 준다. 게시판 본문(`BOARD_BODY`)은 null.
 */
export interface HomeGadgetConfig {
    count?: number;
    sort?: HomeGroupBuySort | HomePostListSort;
    boardScope?: HomeBoardScope;
    boardIds?: string[];
    periodTabs?: boolean;
    perBoard?: number;
    cardHead?: boolean;
    rowStyle?: HomePostRowStyle;
    /** 글 목록 1·2 · 게시판 카드 `AUTO`·2·3 */
    columns?: number | 'AUTO';
    thumbnail?: HomePostThumbnail;
    titleLines?: 0 | 1 | 2;
    lead?: 'NONE' | 'HERO';
    meta?: HomePostMeta[];
    dividers?: boolean;
    newBadgeHours?: number;
    hotBadgeLikes?: number;
    thumbs?: 'ALL' | 'FIRST' | 'NONE';
    shape?: 'PILL' | 'TAB';
    items?: HomeQuickLink[];
    text?: string;
    format?: 'PLAIN' | 'BULLETS';
}

/** 머리 모양 — 막대 · 밑줄 · 색 채움 · 장식 없음 · 머리 숨김 */
export type HomeGadgetHeaderStyle = 'BAR' | 'UNDERLINE' | 'FILLED' | 'PLAIN' | 'HIDDEN';
/** 카드 모양 — 테마 카드 · 평평 · 테두리 강조 · 옅은 색 바탕 */
export type HomeGadgetCardLook = 'DEFAULT' | 'FLAT' | 'OUTLINE' | 'TINT';
/** 모바일 접기 — 없음 · 펼친 채 · 접은 채 */
export type HomeGadgetMobileFold = 'NONE' | 'OPEN' | 'CLOSED';

/**
 * 가젯 공통 꾸미기(⚙ 설정 창 「모양」·「노출」) — user-web `GadgetStyle`(공개 응답 `style`)과 같은 칸·값. 처음 값·범위·읽기는 `UtilGadgetStyle`.
 * 관리자 응답은 칸 전부(저장 값, 없으면 테마 기본 꾸미기 — 서버 `GadgetStyles.view`). 저장 때는 보낸 칸만 덮는다(`icon`·`titleColor`·`cardColor` 의 null 은 「없음·자동」으로 저장된다).
 */
export interface HomeGadgetStyle {
    /** `pi-…` PrimeIcons 이름 또는 null(없음) */
    icon: string | null;
    headerStyle: HomeGadgetHeaderStyle;
    titleSize: 'S' | 'M' | 'L';
    /** 프리셋 12색 이름 또는 `#rrggbb`. null 이면 자동(주 색상) */
    titleColor: string | null;
    /** 부제 0~40자 */
    subtitle: string;
    cardLook: HomeGadgetCardLook;
    /** null 이면 제목 색을 따른다 */
    cardColor: string | null;
    mobileFold: HomeGadgetMobileFold;
    /** 비었을 때 — 자리 남기기 · 안내 보이기 · 안내 문구(0~40자, 비우면 종류별 기본 문구) */
    empty: { keep: boolean; message: boolean; text: string };
}

/** 배치 편집기가 보는 가젯 한 개 */
export interface HomeLayoutGadget {
    id: string;
    /** 종류 — 모르는 키도 받는다(편집기는 기본 아이콘으로 그린다) */
    type: string;
    /** 가젯 제목 1~40자 — ⚙ 설정 창 「모양」에서 고친다 */
    title: string;
    /** 데스크톱(lg 이상)에서 보이는가 — ⚙ 「노출」 */
    desktop: boolean;
    /** 모바일에서 보이는가 — ⚙ 「노출」(둘 다 끌 수 없다) */
    mobile: boolean;
    /**
     * 꾸미기 — 관리자 응답은 칸 전부(게시판 본문은 null). 화면에서 새로 넣은 가젯은 저장 전까지 없다(서버가 테마 기본 꾸미기로 만든다).
     * 저장 때 null 이면 그대로 둔다.
     */
    style?: HomeGadgetStyle | null;
    /** 내용 설정 — 관리자 응답은 그 종류의 표 칸 전부(게시판 본문은 null). 저장 때 보낸 칸만 덮고 null 이면 그대로 */
    config?: HomeGadgetConfig | null;
}

/** 배치 + 가젯 목록 */
export interface HomeLayoutBody {
    layout: HomeLayout;
    gadgets: HomeLayoutGadget[];
}

/** `GET /api/admin/home-layouts/{theme}` · `PUT` 응답 */
export interface HomeLayoutView extends HomeLayoutBody {
    theme: string;
    /** 마지막 저장 시각(UTC ISO) — 저장한 적 없으면(테마 기본 배치) null */
    updatedAt: string | null;
    /** 마지막으로 저장한 운영자 이름 */
    updatedBy: string | null;
}

/**
 * `PUT /api/admin/home-layouts/{theme}` 본문 — 가젯은 넣고 뺀 결과 전체(빠진 기존 가젯은 지운다). 가젯마다 `{ id, type, title, desktop, mobile, style, config }`.
 * 저장 가젯·시드 가젯은 `title`·`desktop`·`mobile` 을 보낸 값으로 바꾸고 `style`·`config` 는 보낸 칸만 덮는다(null 이면 그대로). 새 가젯(`new-<n>`)은 `type`·`title` 필수.
 * 400 `fields.gadgets[i].title|desktop|type|style.<칸>|config.<칸>|config.items[j].label|kind|value`(`i` = 보낸 가젯 차례).
 */
export type HomeLayoutSaveRequest = HomeLayoutBody;
