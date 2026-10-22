/**
 * 메뉴 관리 API 가 주고받는 모양 — 공개 사이트 메뉴(메뉴 그룹 · 그룹별 트리)와 관리자 사이트 메뉴. 후보 계약은 `useMenuApi.ts` 머리말.
 * ⚠ ID 는 모두 문자열이다(Snowflake 64비트). 저장 전 새 노드는 `id: null`.
 *
 * 공개 사이트가 받는 `GET /api/menu?set=main` 응답(user-web `useMenuApi.types.ts` 의 `MenuResponse`)은 이 트리를 **서버가 보는 사람 기준으로 걸러** 만든다:
 * 폴더(`FOLDER`)는 거기서 `kind: 'GROUP'` 이고 `collapsible`·`collapsed` 를 그대로 싣는다. 항목은 `type`(=`targetType`)·`key`(=`targetKey`)·`label`(비면 대상 이름)·`path`·`matchPrefix`·`newTab`·`mobileTab`·`board`·`extra` 로 바뀐다.
 */
import type { AdminMenuMode } from '~/utils/util-admin-menu';

// ── 공개 사이트 메뉴: 메뉴 그룹 ─

/** 메뉴 그룹의 위치·용도 — 좌측 사이드(기본 내비게이션) · 마이페이지 사이드 · 공동구매 영역 사이드 · 사용자 정의(화면 코드가 key 로 불러 씀) */
export type MenuPlacement = 'SIDE' | 'MYPAGE' | 'GROUP_BUY' | 'CUSTOM';

/** 메뉴 그룹(메뉴 세트) 하나 — 각 그룹이 독립된 트리를 가진다 */
export interface MenuSet {
    /** 코드 — `^[a-z][a-z0-9-]*$` 2~30자, 만든 뒤 바꿀 수 없다(유일) */
    key: string;
    /** 이름 1~20자 */
    name: string;
    nameEn: string | null;
    placement: MenuPlacement;
    enabled: boolean;
    /** 설명(선택, 100자) */
    description: string | null;
    /** 기본 그룹 — 지우거나 key 를 바꿀 수 없다 */
    system: boolean;
    /** 트리의 노드 수(하위 포함) */
    nodeCount: number;
    updatedAt: string | null;
}

/** `POST /api/admin/menu-sets` 요청 */
export interface MenuSetCreateRequest {
    key: string;
    name: string;
    nameEn: string | null;
    placement: MenuPlacement;
    enabled: boolean;
    description: string | null;
}

/** `PUT /api/admin/menu-sets/{key}` 요청 — key 는 주소에 있고 바꿀 수 없다 */
export type MenuSetUpdateRequest = Omit<MenuSetCreateRequest, 'key'>;

// ── 공개 사이트 메뉴: 트리 ─

/** 보이는 대상 — 모두 · 비로그인만 · 로그인 · 등급 이상(`minGrade`) · 게시판 매니저·스텝(게시판 매니저·스텝을 하나라도 맡은 회원) */
export type MenuAudience = 'ALL' | 'GUEST' | 'MEMBER' | 'GRADE_MIN' | 'STAFF';

/** 화면이 아는 연결 유형. 응답에 다른 값이 오면 화면은 「알 수 없는 유형」으로 보이고 그대로 돌려보낸다 */
export type MenuKnownTargetType = 'BOARD' | 'BUILTIN' | 'PATH' | 'URL';

interface MenuNodeBase {
    /** 저장 전 새 노드는 null */
    id: string | null;
    /** 이름 — 항목은 비우면(`null`·빈 글자) 대상 이름을 따른다 */
    label: string | null;
    nameEn: string | null;
    visible: boolean;
    audience: MenuAudience;
    /** `GRADE_MIN` 일 때의 등급 키. 그 밖에는 null */
    minGrade: string | null;
}

/** 폴더 — 이름과 하위 노드뿐(주소 없음). 깊이 3 까지 */
export interface MenuFolderDto extends MenuNodeBase {
    kind: 'FOLDER';
    /** 접을 수 있는가 */
    collapsible: boolean;
    /** 처음에 접힌 채인가(`collapsible` 일 때만 뜻이 있다) */
    collapsed: boolean;
    /** 새 게시판 자동 추가 폴더 — `SIDE` 그룹에서만, 한 그룹 안에서 한 폴더만 */
    autoAdd: boolean;
    children: MenuNodeDto[];
}

/** 항목 — 연결 대상이 있는 끝 노드. 깊이 4 까지 */
export interface MenuItemDto extends MenuNodeBase {
    kind: 'ITEM';
    /** 연결 유형(`MenuKnownTargetType` 또는 화면이 모르는 새 유형) */
    targetType: string;
    /** `BOARD` 의 게시판 ID */
    targetId: string | null;
    /** `BUILTIN` 의 앱 화면 키(`UtilMenu.BUILTINS`) */
    targetKey: string | null;
    /** 유형별 설정 — `PATH`: `{ path, newTab }` · `URL`: `{ href, newTab }` · 그 밖에는 null(모르는 유형은 받은 그대로) */
    settings: Record<string, unknown> | null;
    /** 모바일 상단 탭 — `SIDE` 그룹에서만, 그룹당 10개까지 */
    mobileTab: boolean;
}

export type MenuNodeDto = MenuFolderDto | MenuItemDto;

/** `GET·PUT /api/admin/menu-sets/{key}/tree` 응답 */
export interface MenuTreeResponse {
    set: MenuSet;
    nodes: MenuNodeDto[];
    /** 저장할 때 그대로 돌려보낸다 — 그사이 다른 사람이 저장했으면 409 `MENU_VERSION_CONFLICT` */
    version: string;
    /** 이 운영자가 저장할 수 있는가(`layout.settings`) — false 면 화면은 읽기 전용 */
    editable: boolean;
}

/** `PUT /api/admin/menu-sets/{key}/tree` 요청 — 트리 전체 교체 */
export interface MenuTreeSaveRequest {
    version: string;
    nodes: MenuNodeDto[];
}

/** 고를 수 있는 게시판 */
export interface MenuBoardOption {
    id: string;
    slug: string;
    name: string;
    nameEn: string | null;
    /** 꺼진 게시판 — 메뉴에 넣을 수는 있지만 방문자에게 안 보인다 */
    active: boolean;
}

/** 보이는 대상의 등급(서열 순서) */
export interface MenuGradeOption {
    gradeKey: string;
    label: string;
    /** 1 이 기본 등급 */
    level: number;
}

/** `GET /api/admin/menu/target-options?set=` 응답 — 대상 고르기·상태 계산·미리보기에 쓰는 값 한 번에 */
export interface MenuTargetOptions {
    boards: MenuBoardOption[];
    grades: MenuGradeOption[];
    /** 공동구매 판매 스위치 — 꺼지면 `GROUP_BUYS` 계열 앱 화면 항목은 방문자에게 안 보인다 */
    salesEnabled: boolean;
}

// ── 관리자 사이트 메뉴 ─

/** 묶음(구획 제목, 주소 없음) · 부모 항목(하위 화면을 펼침, 주소 없음) · 화면 항목(관리자 화면 또는 외부 링크) */
export type AdminMenuConfigKind = 'SECTION' | 'FOLDER' | 'SCREEN';

/** 관리자 메뉴 항목의 연결 유형 */
export type AdminMenuTargetType = 'ADMIN_SCREEN' | 'URL';

export interface AdminMenuConfigNodeDto {
    id: string | null;
    kind: AdminMenuConfigKind;
    /** 기본 이름의 i18n 키 — 지금 메뉴 정의에서 옮긴 묶음·부모 항목에만 있다(운영자가 만든 것은 null, 이름이 필수) */
    labelKey: string | null;
    /** 한국어 이름 덮어쓰기 — 비우면 기본 이름(화면 항목은 화면의 기본 이름) */
    label: string | null;
    labelEn: string | null;
    /** primeicons 클래스 — 부모 항목 아래의 하위 화면은 없다 */
    icon: string | null;
    visible: boolean;
    /** `SCREEN` 만 */
    targetType: AdminMenuTargetType | null;
    /** `ADMIN_SCREEN` 의 화면 키(`UtilAdminMenu.ITEMS` 의 잎 항목 key) */
    targetKey: string | null;
    /** `URL` 만 — `{ href, newTab }` */
    settings: { href: string; newTab: boolean } | null;
    /** `URL` 만 — 고르면 그 권한이 있는 운영자에게만 보인다. 화면 항목의 권한은 화면이 정해 여기 오지 않는다 */
    permission: string | null;
    /** `SECTION`·`FOLDER` 만(없으면 null) */
    children: AdminMenuConfigNodeDto[] | null;
}

/** `GET·PUT /api/admin/admin-menu` 응답 — 두 모드를 한 번에 */
export interface AdminMenuConfigResponse {
    modes: Record<AdminMenuMode, AdminMenuConfigNodeDto[]>;
    version: string;
    /** 이 운영자가 저장할 수 있는가(`admin-menu.manage`) */
    editable: boolean;
}

/** `PUT /api/admin/admin-menu` 요청 — 두 모드 전체 교체 */
export interface AdminMenuConfigSaveRequest {
    version: string;
    modes: Record<AdminMenuMode, AdminMenuConfigNodeDto[]>;
}
