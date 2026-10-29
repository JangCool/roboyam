/**
 * 관리자 가젯 페이지 API(`/api/admin/gadget-pages/**`)가 주고받는 모양 — 서버 `GadgetPageController`(docs/features/gadget-page-contract.md 「API」, 2026-10-29).
 * 가젯 페이지 = 홈처럼 가젯 격자로 꾸미는 공개 페이지(`/p/{key}`, 사용자 결정 2026-10-29 — 「커뮤니티」 전용 화면 대신 일반화). 첫 페이지는 「커뮤니티」, 다음은 「주제」.
 *   · 페이지 정보(이름·설명·영문 주소·기준 폴더·글쓰기 단추)는 테마와 상관없이 하나.
 *   · 배치는 홈처럼 **테마마다 따로** — 모양은 홈 배치와 같다(`HomeLayoutBody`). 저장한 적 없는 테마는 페이지의 처음 배치(만들 때 기준 폴더의 게시판으로 채운 것)를 보인다.
 *   · 기준 폴더 = 공개 메뉴(main)의 폴더 하나 — 가젯 설정의 「이 페이지가 연결된 폴더의 게시판 전체」(`boardScope: 'FOLDER'`)와 처음 배치가 이 폴더의 게시판을 쓴다.
 *     폴더를 누르면 이 페이지로 가게 하는 것은 메뉴 관리(폴더의 「누르면 가는 곳」 › 사이트 화면 › 가젯 페이지, 연결 유형 `GADGET_PAGE`)가 따로 한다.
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */
import type { HomeLayoutBody } from '~/composables/home/api/useHomeLayoutApi.types';

/** 기준 폴더의 게시판 한 개(서버가 메뉴 트리에서 풀어 준다 — 폴더 안 하위 폴더의 게시판까지, 메뉴 차례) */
export interface GadgetPageBoard {
    slug: string;
    name: string;
    nameEn: string | null;
    /** 게시판 목록 모양 */
    layout: 'LIST' | 'CARD' | 'GALLERY';
    /** 목록·갤러리 보기 전환 — 처음 배치의 갤러리 가젯은 목록 모양이 `GALLERY` 이거나 이것이 켜진 첫 게시판을 고른다 */
    viewToggle: boolean;
    /** 켜진 게시판인가 — 꺼진 게시판은 처음 배치·공개 데이터에서 빠진다 */
    active: boolean;
}

/** 기준 폴더로 고를 수 있는 폴더 한 개(`GET /api/admin/gadget-pages/folders`) — 공개 메뉴 main 의 폴더(깊이 무관, 트리 차례) */
export interface GadgetPageFolder {
    id: string;
    name: string;
    nameEn: string | null;
    /** 위 폴더가 있으면 「위 › 이름」 */
    label: string;
    /** 폴더 아래(하위 폴더까지) 게시판 항목의 게시판(메뉴 차례, 꺼진 것 포함) */
    boards: GadgetPageBoard[];
}

/** `GET /api/admin/gadget-pages/folders` 응답 */
export interface GadgetPageFolderListResponse {
    folders: GadgetPageFolder[];
}

/** 가젯 페이지 한 개 */
export interface GadgetPage {
    id: string;
    /** 영문 주소 — 공개 주소 `/p/{key}`. 소문자로 시작하는 소문자·숫자·`-` 2~30자, 겹치지 않는다 */
    key: string;
    /** 이름(1~40자) — 공개 페이지 머리의 제목 · 메뉴 연결 목록 */
    name: string;
    /** 영문 이름(선택) — 영어 화면 */
    nameEn: string | null;
    /** 설명(0~200자) — 공개 페이지 머리의 둘째 줄 */
    description: string;
    /** 기준 폴더 — 공개 메뉴(main)의 폴더 ID. 없으면 null(대상 게시판 「폴더 게시판 전체」는 전체 게시판과 같다) */
    folderId: string | null;
    /** 기준 폴더 이름(서버가 붙여 준다 — 폴더가 지워졌으면 null) */
    folderName: string | null;
    /** 기준 폴더의 게시판(서버가 붙여 준다) */
    folderBoards: GadgetPageBoard[];
    /** 머리에 글쓰기 단추를 두는가 — 누르면 글쓰기(기준 폴더의 첫 게시판을 골라 둔 채) */
    writeButton: boolean;
    /** 이 페이지를 「누르면 가는 곳」으로 쓰는 메뉴 노드 수(서버가 센다 — 지울 때 안내) */
    menuLinks: number;
    updatedAt: string;
    updatedBy: string | null;
}

/** `GET /api/admin/gadget-pages` 응답 */
export interface GadgetPageListResponse {
    pages: GadgetPage[];
}

/** `POST /api/admin/gadget-pages` 본문 — 만들 때 서버가 기준 폴더의 켜진 게시판으로 처음 배치를 만든다(`UtilGadgetPage.initialLayout` 과 같은 규칙) */
export interface GadgetPageCreateRequest {
    key: string;
    name: string;
    nameEn: string | null;
    description: string;
    folderId: string | null;
    writeButton: boolean;
}

/** `PUT /api/admin/gadget-pages/{id}` 본문 — 칸 전부. 기준 폴더를 바꿔도 이미 저장한 배치는 그대로다(「폴더 게시판 전체」 가젯만 새 폴더를 따른다) */
export type GadgetPageUpdateRequest = GadgetPageCreateRequest;

/** `GET /api/admin/gadget-pages/{id}/layouts/{theme}` · `PUT` 응답 — 홈 배치와 같은 모양 */
export interface GadgetPageLayoutView extends HomeLayoutBody {
    pageId: string;
    theme: string;
    /** 이 테마에서 마지막 저장 시각 — 저장한 적 없으면(처음 배치) null */
    updatedAt: string | null;
    updatedBy: string | null;
}
