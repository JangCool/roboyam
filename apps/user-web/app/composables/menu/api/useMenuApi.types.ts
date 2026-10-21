/**
 * 메뉴 설정 API 가 주고받는 모양 — `GET /api/menu` (docs/api-menu.md 5절).
 * ⚠ ID 는 모두 문자열이다(Snowflake 64비트). 숫자로 바꾸지 않는다.
 *
 * 서버가 보는 사람 기준으로 이미 걸러서(보이는 대상·꺼진 게시판·판매 스위치 꺼진 공동구매 …) 정렬까지 끝내 준다 —
 * 화면은 다시 거르거나 정렬하지 않는다. 모르는 칸(새 연결 유형의 `extra`)은 무시한다.
 */

/** 게시판 항목이 실어 오는 게시판 요약 — 메뉴 점 색(`color` 가 있을 때만)·투자 고지 표시·"지금 보는 곳" 판정(`slug`)에 쓴다 */
export interface MenuBoardRef {
    id: string;
    /** 게시판 코드 — `useCurrentBoard().slug` 와 맞춰 글 상세·글쓰기 화면에서도 이 항목을 밝힌다 */
    slug: string;
    /** 운영자가 고른 표시색(12색 프리셋 이름) 또는 null(색 없음 — 점을 그리지 않는다) */
    color: string | null;
    /** 투자 고지가 붙는 게시판인가 — 메뉴 이름·숨김·위치와 무관하게 게시판 설정이 정한다 */
    investmentNotice: boolean;
}

/** 연결 대상이 있는 끝 노드(게시판·앱 화면·외부 링크) */
export interface MenuItemNode {
    id: string;
    kind: 'ITEM';
    /** 연결 유형 — `BOARD` · `BUILTIN` · `URL`(외부 링크) (뒤에 더 생겨도 화면은 `path`·`label`·`newTab`·`extra` 만 그린다) */
    type: string;
    /** `BUILTIN` 의 앱 화면 키(`HOME`·`GROUP_BUYS`·`GROUPS_MINE`·`GROUPS_ALL`), 그 밖에는 null */
    key: string | null;
    /** 이미 용어·메뉴 구성이 반영된 글자 — 화면이 용어 표로 다시 만들지 않는다 */
    label: string;
    /** 선택 영문명 — 언어가 `en` 이고 비어 있지 않으면 `label` 대신 보인다(`UtilI18n.localizedName`). 서버가 아직 안 주거나 비었으면 null/없음 */
    nameEn?: string | null;
    path: string;
    /** true 면 그 아래 주소(`path/…`)도 이 항목이 밝다 */
    matchPrefix: boolean;
    newTab: boolean;
    /** 모바일 상단 탭에 올릴 항목인가(탭 순서는 `MenuResponse.mobileTabs` 가 정한다) */
    mobileTab: boolean;
    /** `BOARD` 항목에만 있다 */
    board?: MenuBoardRef | null;
    /** 유형이 더 실어 보낸 칸 — 있는 키만 그린다(모르는 키는 무시). `external: true` 면 작은 외부 링크 아이콘(`URL` 의 `https://` 주소). 없으면 null */
    extra?: Record<string, unknown> | null;
}

/** 폴더형 노드 — 이름과 아래 노드들뿐이다(주소 없음) */
export interface MenuGroupNode {
    id: string;
    kind: 'GROUP';
    label: string;
    /** 선택 영문명 — `label` 의 영문명(없으면 null/없음) */
    nameEn?: string | null;
    /** 세로 메뉴·햄버거에서 제목을 눌러 접을 수 있는가(상단 가로 메뉴는 무시한다) */
    collapsible: boolean;
    /** 브라우저가 기억한 접힘이 없을 때의 처음 상태 */
    collapsed: boolean;
    children: MenuNode[];
}

export type MenuNode = MenuGroupNode | MenuItemNode;

/** `GET /api/menu` 응답 */
export interface MenuResponse {
    nodes: MenuNode[];
    /** 모바일 상단 탭에 올릴 항목의 ID — 트리 깊이 우선 순서로 서버가 만든 배열이다(화면이 트리를 훑어 다시 계산하지 않는다) */
    mobileTabs: string[];
    /** 바뀌었는지 비교용 불투명 문자열 — 화면은 쓰지 않아도 된다 */
    version?: string;
}
