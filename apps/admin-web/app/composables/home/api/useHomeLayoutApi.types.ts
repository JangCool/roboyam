/**
 * 관리자 홈 배치 API(`/api/admin/home-layouts/**`)가 주고받는 모양 — 서버 `HomeLayoutController`(site-theme-layout-contract 「API」).
 * 홈 배치는 **테마마다 따로** 둔다(템플릿 = 테마, 사용자 결정 2026-10-28). 배치 모양·고정값·계산은 공유 레이어 `@roboyam/home-layout`(`UtilHomeLayout`) — 공개 홈(user-web)과 같은 값이다.
 * 범위는 **위치·크기·칸 수와 가젯 넣기·빼기** + 내용 설정(`config` — 공구의 개수·정렬 2026-10-29, 글 목록·갤러리·게시판 카드의 대상 게시판·개수·정렬은 가젯 페이지 때 넓혔다)이다.
 * 꾸미기(머리 모양·색 …)는 다루지 않는다 — 새로 넣은 가젯은 종류별 처음 설정으로 만든다. 가젯 페이지(`useGadgetPageApi`)의 배치도 같은 모양이다.
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
 */
export type HomeBoardScope = 'ALL' | 'FOLDER' | 'PICK';

/**
 * 가젯 내용 설정(실제 화면의 ⚙ → 설정 창) — 서버 JSON 그대로의 평평한 모양이고 종류마다 쓰는 칸만 채운다. 고칠 수 있는 종류·칸·범위·처음 값은 `UtilGadgetConfig`.
 *   · `GROUP_BUY` `count`(1~10) · `sort`(HomeGroupBuySort)
 *   · `POST_LIST` `boardScope`·`boardIds` · `count`(1~30) · `sort`(HomePostListSort) · `periodTabs`(인기일 때 실시간·어제·이번 주 탭)
 *   · `GALLERY` `boardScope`·`boardIds` · `count`(1~24)
 *   · `BOARD_CARDS` `boardScope`·`boardIds`(카드로 보일 게시판) · `perBoard`(카드마다 제목 수 1~10)
 * 다른 종류는 null.
 */
export interface HomeGadgetConfig {
    count?: number;
    sort?: HomeGroupBuySort | HomePostListSort;
    boardScope?: HomeBoardScope;
    boardIds?: string[];
    periodTabs?: boolean;
    perBoard?: number;
}

/** 배치 편집기가 보는 가젯 한 개(내용 설정은 고칠 수 있는 종류만 — 공구 · 글 목록 · 갤러리 · 게시판 카드) */
export interface HomeLayoutGadget {
    id: string;
    /** 종류 — 모르는 키도 받는다(편집기는 기본 아이콘으로 그린다) */
    type: string;
    /** 가젯 제목(운영자가 정한 것 — 이 화면에서는 고치지 않는다) */
    title: string;
    /** 데스크톱(lg 이상)에서 보이는가 */
    desktop: boolean;
    /** 모바일에서 보이는가 */
    mobile: boolean;
    /** 내용 설정 — 고칠 수 있는 종류만(`UtilGadgetConfig.settable`, 서버가 저장 값을 준다). 다른 종류는 null. 저장 때 보내면 그 값으로 바꾸고 null 이면 그대로 */
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

/** `PUT /api/admin/home-layouts/{theme}` 본문 — 가젯은 넣고 뺀 결과 전체(빠진 기존 가젯은 지운다). 제목·노출은 서버 값을 쓰고 새 가젯만 `type`·`title` 로 만든다 */
export type HomeLayoutSaveRequest = HomeLayoutBody;
