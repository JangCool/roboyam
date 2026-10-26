/**
 * 소모임 분류 API 가 주고받는 모양 — community `docs/api-groups.md` 6-3(`AdminCategory`)과 같은 이름이다.
 * 분류(이름·순서) 1 : 소모임 게시판(게시판 종류 `GROUP`) N : 소모임. 소모임의 분류는 그 게시판이 할당된 분류로 정해지는 파생값이다.
 * 서버 연결됨(`server/admin`) — 소모임 서버가 아직 없어 `groupCount` 는 늘 0 이다.
 */

/** 분류에 할당한 게시판 한 줄 — 꺼진 게시판도 `active: false` 로 실린다 */
export interface GroupCategoryBoard {
    boardId: string;
    slug: string;
    name: string;
    active: boolean;
    /** 투자 고지 게시판 */
    investmentNotice: boolean;
    /** 이 게시판을 쓰는 소모임 수(열림·비활성·대기) */
    groupCount: number;
}

/** 분류 하나(`AdminCategory`) */
export interface GroupCategoryItem {
    id: string;
    name: string;
    sortOrder: number;
    boardCount: number;
    /** 이 분류 게시판들을 쓰는 소모임 수(삭제·반려 제외) */
    groupCount: number;
    /** 할당한 게시판 중 하나라도 투자 고지 */
    investmentNotice: boolean;
    /** 할당한 순서 */
    boards: GroupCategoryBoard[];
    /** 서버가 이 분류를 마지막으로 고친 시각 — 저장의 「다른 관리자가 먼저 고쳤어요」 비교 기준 */
    updatedAt: string;
}

/** 할당할 수 있는 게시판 — 켜져 있는 소모임 게시판. 다른 분류에 있으면 `categoryId`·`categoryName`(화면이 「옮기기」로 보인다) */
export interface GroupCategoryCandidate {
    boardId: string;
    slug: string;
    name: string;
    categoryId: string | null;
    categoryName: string | null;
    investmentNotice: boolean;
    groupCount: number;
}

/** `GET /api/admin/group-categories` — `PUT {id}`·`PUT order` 도 같은 본문 전체를 돌려준다 */
export interface GroupCategoriesResponse {
    /** sortOrder 순 */
    categories: GroupCategoryItem[];
    available: GroupCategoryCandidate[];
}

/** `POST /api/admin/group-categories` — 이름만, 맨 끝에 게시판 0개로 생긴다 */
export interface GroupCategoryCreateRequest {
    name: string;
}

/** `PUT /api/admin/group-categories/{id}` — 이름 + 게시판 전체 교체(보낸 순서가 할당 순서) */
export interface GroupCategorySaveRequest {
    name: string;
    boardIds: string[];
    /** 불러왔을 때의 `updatedAt` — 서버가 더 최근에 바뀌었으면 `409 GROUP_CATEGORY_STALE` */
    updatedAt?: string;
}

/** `PUT /api/admin/group-categories/order` — 모든 분류를 한 번씩 */
export interface GroupCategoryOrderRequest {
    ids: string[];
}
