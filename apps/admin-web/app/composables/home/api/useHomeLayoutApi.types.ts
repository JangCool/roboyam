/**
 * 관리자 홈 배치 API(`/api/admin/home-layouts/**`)가 주고받는 모양 — **서버 후보**(아직 서버 없음, 목업 `./mock/home-layout-mock.ts`).
 * 홈 배치는 **테마마다 따로** 둔다(템플릿 = 테마, 사용자 결정 2026-10-28). 배치 모양·고정값·계산은 공유 레이어 `@roboyam/home-layout`(`UtilHomeLayout`) — 공개 홈(user-web)과 같은 값이다.
 * 이번 범위는 **위치·크기·칸 수와 가젯 넣기·빼기**만이다. 가젯 내용 설정(게시판·개수·꾸미기 …)은 다루지 않는다 — 새로 넣은 가젯은 서버가 종류별 기본 설정으로 만든다.
 * ⚠ ID 는 문자열(Snowflake). 화면에서 새로 넣은 가젯은 저장 전까지 `new-<n>`, 테마 기본 배치에서 다시 넣은 가젯은 기본 배치의 ID 를 쓴다(서버가 모르는 ID 면 새로 만든다).
 */
import type { HomeLayout } from '#layers/home-layout/utils/util-home-layout';

/** 가젯 종류 8종 — user-web `GadgetKind` 와 같은 키(로보얌에 없는 핫딜·직접홍보·광고는 없다) */
export type HomeGadgetKind = 'HIGHLIGHT' | 'POST_LIST' | 'BOARD_CARDS' | 'GALLERY' | 'GROUPS' | 'GROUP_BUY' | 'QUICK_LINKS' | 'NOTICE_TEXT';

/** 배치 편집기가 보는 가젯 한 개(내용 설정은 싣지 않는다) */
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

/** `GET /api/admin/home-layouts` 응답 — 배치를 고칠 수 있는 테마(보일 순서) */
export interface HomeLayoutThemesResponse {
    themes: string[];
}

/** `PUT /api/admin/home-layouts/{theme}` 본문 — 가젯은 넣고 뺀 결과 전체(빠진 기존 가젯은 지운다). 제목·노출은 서버 값을 쓰고 새 가젯만 `type`·`title` 로 만든다 */
export type HomeLayoutSaveRequest = HomeLayoutBody;
