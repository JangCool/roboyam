import type {
    MenuAudience,
    MenuDivider,
    MenuBoardOption,
    MenuFolderDto,
    MenuGadgetPageOption,
    MenuGradeOption,
    MenuItemDto,
    MenuKnownTargetType,
    MenuLinkClick,
    MenuNodeDto,
    MenuPlacement,
} from '~/composables/menu/api/useMenuApi.types';
import type { BoardColor } from '~/composables/board/api/useBoardApi.types';
import UtilBoard from '~/utils/util-board';
import UtilColor from '~/utils/util-color';
import type { MenuMoveAction } from '~/utils/util-menu-tree';
import UtilMenuTree from '~/utils/util-menu-tree';

/**
 * 공개 사이트 메뉴(메뉴 그룹별 트리)의 규칙 — 한도 · 연결 유형 표 · 앱 화면 레지스트리 · 검증 · 상태 · 미연결 게시판 · 미리보기 거르기. 순수 함수만 둔다.
 * 서버(`server/shared` 의 `MenuTreeValidator`·`MenuTargetRegistry`·`MenuLimits`)가 **같은 표·같은 규칙**을 가진다 — 화면 검사는 미리 알려 주는 용도이고 판정은 서버다(`forms.md`).
 */

// ── 화면 편집 모양 ─

/** 폴더 접기 — 안 함 / 펼친 채 시작 / 접은 채 시작 (서버 칸 `collapsible`·`collapsed` 두 개를 하나로) */
export type MenuCollapseMode = 'NONE' | 'OPEN' | 'CLOSED';

/** 항목 상태 — 저장은 되지만 방문자에게 안 보이는 이유. `OK` 는 문제 없음 */
export type MenuItemStatus = 'OK' | 'BOARD_INACTIVE' | 'TARGET_MISSING' | 'SALES_OFF' | 'UNKNOWN_TYPE';

interface MenuEditCommon {
    /** 화면 열쇠(`UtilMenuTree.newKey`) — 저장하지 않는다 */
    key: string;
    id: string | null;
    label: string;
    nameEn: string;
    visible: boolean;
    audience: MenuAudience;
    minGrade: string | null;
    /** 구분선 — 최상위에서만 뜻이 있다(하위로 옮기면 저장 때 `NONE` 으로 보낸다) */
    divider: MenuDivider;
    /**
     * 글자 색 — null 기본 · 12색 이름(`UtilMenu.TEXT_COLORS`) · `#RRGGBB`(직접 고른 색). 직접 입력 중에는 틀린 글자도 들고 있다가 검사(`TEXT_COLOR_FORMAT`)로 막는다.
     * 이름 앞 점(●)의 게시판 표시색과 따로다(menu-contract 「메뉴 항목 글자 색·강조」). 폴더·항목 모두 창에서 고친다(`MenuTextStyleField` — 폴더 창·항목 창).
     */
    textColor: string | null;
    /** 이름을 굵게 */
    bold: boolean;
    /** 이름 뒤 배지 글자(빈 글자 = 없음, 1~`LIMITS.badgeMax`자) — 배지 색 = 글자 색 */
    badge: string;
}

/** 연결 대상 — 항목의 대상, 폴더 이름을 누르면 가는 곳(`MenuEditFolder.link`)이 같은 모양이다 */
export interface MenuEditLink {
    targetType: string;
    targetId: string | null;
    targetKey: string | null;
    /** `PATH` 의 주소 */
    path: string;
    /** `URL` 의 주소 */
    href: string;
    /** `PATH`·`URL` 만 */
    newTab: boolean;
}

export interface MenuEditFolder extends MenuEditCommon {
    kind: 'FOLDER';
    collapse: MenuCollapseMode;
    autoAdd: boolean;
    /** 폴더 이름을 누르면 가는 곳(선택) — null 이면 이름은 펼치기·접기만 한다. 유형은 게시판·사이트 화면·외부 링크 */
    link: MenuEditLink | null;
    /** 연결이 있을 때 상단 탭을 누르면 — 이동하고 하위 메뉴도 열기(기본) · 이동만. 연결이 없으면 쓰지 않는다 */
    linkClick: MenuLinkClick;
    children: MenuEditNode[];
}

export interface MenuEditItem extends MenuEditCommon, MenuEditLink {
    kind: 'ITEM';
    mobileTab: boolean;
    /** 설명 한 줄 — 최상위에서만 뜻이 있다(하위로 옮기면 저장 때 null 로 보낸다). 빈 글자 = 공개 화면 기본 설명 */
    description: string;
    /** 화면이 모르는 유형의 설정 — 받은 그대로 돌려보낸다 */
    rawSettings: Record<string, unknown> | null;
    /**
     * 바로가기 — 다른 묶음(폴더)에 진짜 자리가 있는 게시판을 이 묶음에도 보이게 둔 항목(「다른 묶음에도 보이기」, `BOARD` 만). 연결 게시판은 바꿀 수 없고 지우면 바로가기만 지워진다.
     * 한 그룹 안 한 번 규칙은 진짜 자리(바로가기 아닌 항목)에만 걸린다. 폴더 안에만 둔다(최상위 불가). 모바일 탭은 쓰지 않는다. 서버 칸 `menu_nodes.shortcut`(shared V167).
     */
    shortcut: boolean;
}

export type MenuEditNode = MenuEditFolder | MenuEditItem;

/** 상태·이름·미리보기 계산에 쓰는 값 */
export interface MenuContext {
    placement: MenuPlacement;
    boards: MenuBoardOption[];
    grades: MenuGradeOption[];
    salesEnabled: boolean;
    /** 가젯 페이지(연결 유형 `GADGET_PAGE`) — 없으면 빈 목록 */
    pages: MenuGadgetPageOption[];
}

// ── 연결 유형 · 앱 화면 레지스트리 ─

/** 대상 고르기 모양 — 게시판 목록 · 앱 화면 목록 · 주소 입력 · 외부 링크 입력 */
export type MenuPicker = 'BOARD_SELECT' | 'BUILTIN_SELECT' | 'PATH_INPUT' | 'URL_INPUT';

/**
 * 화면이 보여 주는 연결 종류 — 게시판 · 사이트 화면(목록에서 고르는 `BUILTIN`·`GADGET_PAGE` + 주소를 적는 `PATH`) · 외부 링크.
 * 「앱 화면」과 「페이지」를 화면에서만 「사이트 화면」 하나로 합쳤다(2026-10-29). 가젯 페이지(`GADGET_PAGE`, 2026-10-29)도 사이트 화면 목록의 맨 위 묶음으로 고른다.
 */
export type MenuTargetKind = 'BOARD' | 'SITE' | 'URL';

export interface MenuTargetKindDef {
    kind: MenuTargetKind;
    labelKey: string;
    descKey: string;
    icon: string;
    /** 이 종류를 처음 고를 때의 서버 유형 */
    initialType: MenuKnownTargetType;
}

export interface MenuTargetTypeDef {
    type: MenuKnownTargetType;
    kind: MenuTargetKind;
    labelKey: string;
    descKey: string;
    icon: string;
    picker: MenuPicker;
}

export interface MenuSoonTypeDef {
    type: string;
    labelKey: string;
    descKey: string;
    icon: string;
}

export type MenuBuiltinCategory = 'GENERAL' | 'GROUP_BUY' | 'GROUPS' | 'MYPAGE';

export interface MenuBuiltinDef {
    key: string;
    category: MenuBuiltinCategory;
    /** 기본 이름의 i18n 키 */
    labelKey: string;
    path: string;
    /** 그 아래 주소도 이 항목이 밝다 */
    matchPrefix: boolean;
    /** 로그인한 사람에게만 보이게 할 수 있다(보이는 대상이 MEMBER·GRADE_MIN·STAFF 여야 한다) */
    loginRequired: boolean;
    /** 공동구매 판매 스위치가 꺼지면 숨는다 */
    salesGated: boolean;
}

/** 보는 사람 — 미리보기 */
export type MenuViewer = { kind: 'GUEST' } | { kind: 'MEMBER'; level: number } | { kind: 'STAFF' };

/** 미리보기 결과 노드(저장 전 트리를 보는 사람 기준으로 거른 것) */
export interface MenuPreviewNode {
    node: MenuEditNode;
    children: MenuPreviewNode[];
    /** 폴더 이름이 링크로 보이는가(`UtilMenu.folderLinkShown`) — 항목은 없음 */
    linked?: boolean;
}

/** 메뉴에 없는 게시판 — 항목이 없다 · 숨김 안에만 있다 */
export interface MenuUnlinkedBoard {
    board: MenuBoardOption;
    reason: 'NO_ITEM' | 'HIDDEN';
}

/** 게시판 하나의 메뉴 자리 — 진짜 자리(바로가기 아닌 항목)와 바로가기들. 자리는 들어 있는 폴더의 열쇠(최상위면 null) */
export interface MenuBoardPlaces {
    origin: { itemKey: string; folderKey: string | null } | null;
    shortcuts: { itemKey: string; folderKey: string | null }[];
}

/** 바로가기를 둘 수 있는 묶음(폴더) 하나 — 막혔으면 이유 */
export interface MenuShortcutSpot {
    folder: MenuEditFolder;
    depth: number;
    /** `ORIGIN` 원래 자리 묶음 · `EXISTS` 이미 바로가기가 있음 · `DEPTH` 단계 한도 · null 둘 수 있음 */
    blocked: 'ORIGIN' | 'EXISTS' | 'DEPTH' | null;
}

/** 이미 연결된 대상 — 대상 고르기에서 비활성 + 「○○ 폴더에 있음」 */
export interface MenuLinkedTarget {
    itemKey: string;
    /** 들어 있는 폴더 이름(최상위면 null) */
    folderLabel: string | null;
}

// ── 검증 ─

/** 줄 하나의 문제 칸 */
export type MenuProblemField = 'label' | 'nameEn' | 'target' | 'path' | 'href' | 'minGrade' | 'audience' | 'mobileTab' | 'textColor' | 'badge';

export type MenuProblemCode =
    | 'LABEL_REQUIRED'
    | 'LABEL_LENGTH'
    | 'LABEL_CHARS'
    | 'NAME_EN_LENGTH'
    | 'TARGET_REQUIRED'
    | 'TARGET_DUPLICATE'
    | 'LOGIN_AUDIENCE'
    | 'GRADE_REQUIRED'
    | 'PATH_REQUIRED'
    | 'PATH_START'
    | 'PATH_LENGTH'
    | 'PATH_RESERVED'
    | 'PATH_CHARS'
    | 'PATH_DOTS'
    | 'PATH_DOUBLE_SLASH'
    | 'HREF_REQUIRED'
    | 'HREF_LENGTH'
    | 'HREF_HTTP'
    | 'HREF_SCHEME'
    | 'HREF_PROTOCOL_RELATIVE'
    | 'HREF_CHARS'
    | 'HREF_USERINFO'
    | 'MOBILE_TAB_SIDE'
    | 'SHORTCUT_ORIGIN_MISSING'
    | 'SHORTCUT_TOP_LEVEL'
    | 'SHORTCUT_SAME_FOLDER'
    | 'TEXT_COLOR_FORMAT'
    | 'BADGE_LENGTH'
    | 'BADGE_CHARS';

/** 트리 전체의 문제 */
export type MenuTreeProblem = 'NODES_LIMIT' | 'TABS_LIMIT' | 'AUTO_ADD_MULTI' | 'FOLDER_DEPTH' | 'ITEM_DEPTH';

export interface MenuProblems {
    rows: Record<string, Partial<Record<MenuProblemField, MenuProblemCode>>>;
    tree: MenuTreeProblem[];
}

export default class UtilMenu {
    /** 한도 — 한 곳(서버 `MenuLimits` 와 같은 값) */
    static readonly LIMITS = {
        folderDepth: 3,
        itemDepth: 4,
        nodes: 80,
        mobileTabs: 10,
        labelMax: 20,
        nameEnMax: 40,
        pathMax: 500,
        hrefMax: 2000,
        /** 항목 설명(MOCK — 서버 한도가 정해지면 맞춘다) */
        descriptionMax: 80,
        /** 배지 글자(코드포인트) — 서버 `menu_nodes.badge` */
        badgeMax: 4,
    } as const;

    /** 메뉴 그룹 칸 한도 */
    static readonly SET_LIMITS = { keyMin: 2, keyMax: 30, nameMax: 20, nameEnMax: 40, descMax: 100 } as const;

    static readonly SET_KEY_PATTERN = /^[a-z][a-z0-9-]*$/;

    static readonly PLACEMENTS: readonly MenuPlacement[] = ['SIDE', 'MYPAGE', 'GROUP_BUY', 'CUSTOM'];

    static readonly AUDIENCES: readonly MenuAudience[] = ['ALL', 'GUEST', 'MEMBER', 'GRADE_MIN', 'STAFF'];

    static readonly COLLAPSE_MODES: readonly MenuCollapseMode[] = ['NONE', 'OPEN', 'CLOSED'];

    /** 구분선 고르기 차례 — 없음 · 왼쪽 · 오른쪽 */
    static readonly DIVIDERS: readonly MenuDivider[] = ['NONE', 'BEFORE', 'AFTER'];

    // ── 글자 색 · 강조(menu-contract 「메뉴 항목 글자 색·강조」) ─

    /** 글자 색 12색 — 게시판 표시색과 같은 목록·순서(`UtilBoard.COLORS`, 서버 `BoardFields.COLORS`) */
    static readonly TEXT_COLORS: readonly BoardColor[] = UtilBoard.COLORS;

    /**
     * 글자 색 값을 읽는다 — 12색 이름(대문자)은 그대로, `#rrggbb` 는 대문자(서버가 정리하는 모양과 같다), 그 밖(빈 값·틀린 글자)은 null.
     * @param {unknown} value - 서버 값 또는 입력 중인 글자.
     * @returns {string | null} 정리한 값 또는 null(기본).
     */
    static normalizeTextColor(value: unknown): string | null {
        return UtilBoard.normalizeColor(value); // 게시판 표시색(shared V188)과 같은 규칙
    }

    /**
     * 배지 글자의 문제 — 서버와 같은 규칙: 앞뒤 공백을 걷고 4코드포인트까지, 제어 문자·꺾쇠 괄호 불가. 빈 글자는 배지 없음.
     * @param {string} value - 배지 글자.
     * @returns {MenuProblemCode | null} 문제 또는 null.
     */
    static badgeProblem(value: string): MenuProblemCode | null {
        const text = value.trim();
        if (!text) return null;
        if (/[<>\p{Cc}]/u.test(text)) return 'BADGE_CHARS';
        return Array.from(text).length > UtilMenu.LIMITS.badgeMax ? 'BADGE_LENGTH' : null;
    }

    /**
     * 글자 색의 CSS 값(라이트·다크) — 12색은 게시판 표시색 견본과 같은 팔레트(`UtilBoard.colorVar`), 직접 고른 색은 두 바탕에서 읽히게 명도만 보정(`UtilColor.cssPair`, 공개 화면과 같은 식).
     * @param {string | null} value - 글자 색.
     * @returns {string | null} CSS 색 값. 기본·틀린 값이면 null.
     */
    static textColorCss(value: string | null): string | null {
        const color = UtilMenu.normalizeTextColor(value);
        if (color === null) return null;
        return UtilColor.isHex(color) ? UtilColor.cssPair(color) : UtilBoard.colorVar(color as BoardColor);
    }

    /**
     * 이름에 칠할 글자 색 스타일 — 기본이면 undefined.
     * @param {Pick<MenuEditCommon, 'textColor'>} node - 노드.
     * @returns {Record<string, string> | undefined} 스타일.
     */
    static textStyle(node: Pick<MenuEditCommon, 'textColor'>): Record<string, string> | undefined {
        const css = UtilMenu.textColorCss(node.textColor);
        return css ? { color: css } : undefined;
    }

    /**
     * 이름 뒤 배지의 색 — 배지 색 = 글자 색(바탕은 그 색 14% + 카드 바탕), 글자 색이 기본이면 주 색.
     * @param {Pick<MenuEditCommon, 'textColor'>} node - 노드.
     * @returns {Record<string, string>} 스타일.
     */
    static badgeStyle(node: Pick<MenuEditCommon, 'textColor'>): Record<string, string> {
        const css = UtilMenu.textColorCss(node.textColor) ?? 'var(--p-primary-color)';
        return { '--menu-badge-c': css, color: 'var(--menu-badge-c)', background: 'color-mix(in srgb, var(--menu-badge-c) 14%, var(--p-content-background))' };
    }

    /** 연결 있는 폴더의 탭을 누르면 — 고르기 차례(첫째가 기본) */
    static readonly LINK_CLICKS: readonly MenuLinkClick[] = ['NAVIGATE_OPEN', 'NAVIGATE'];

    /**
     * 연결 유형 표 — 서버 `GET /api/admin/menu/target-types` 응답을 흉내 낸다(`type → { 이름, 대상 고르기 모양 }`).
     * 새 유형은 서버 표와 함께 여기에 한 줄 더한다. 이 표에 없는 유형이 응답에 오면 「알 수 없는 유형」으로 보인다.
     */
    static readonly TARGET_TYPES: readonly MenuTargetTypeDef[] = [
        { type: 'BOARD', kind: 'BOARD', labelKey: 'menuConfig.type.BOARD', descKey: 'menuConfig.typeDesc.BOARD', icon: 'pi pi-th-large', picker: 'BOARD_SELECT' },
        { type: 'BUILTIN', kind: 'SITE', labelKey: 'menuConfig.type.SITE', descKey: 'menuConfig.typeDesc.SITE', icon: 'pi pi-desktop', picker: 'BUILTIN_SELECT' },
        { type: 'PATH', kind: 'SITE', labelKey: 'menuConfig.type.SITE', descKey: 'menuConfig.typeDesc.SITE', icon: 'pi pi-desktop', picker: 'PATH_INPUT' },
        { type: 'GADGET_PAGE', kind: 'SITE', labelKey: 'menuConfig.type.SITE', descKey: 'menuConfig.typeDesc.SITE', icon: 'pi pi-desktop', picker: 'BUILTIN_SELECT' },
        { type: 'URL', kind: 'URL', labelKey: 'menuConfig.type.URL', descKey: 'menuConfig.typeDesc.URL', icon: 'pi pi-external-link', picker: 'URL_INPUT' },
    ];

    /** 추가 시트·폴더 연결에서 고르는 종류 — 사이트 화면은 목록(`BUILTIN`)에서 시작하고 「주소 직접 입력」으로 `PATH` 가 된다 */
    static readonly TARGET_KINDS: readonly MenuTargetKindDef[] = [
        { kind: 'BOARD', labelKey: 'menuConfig.type.BOARD', descKey: 'menuConfig.typeDesc.BOARD', icon: 'pi pi-th-large', initialType: 'BOARD' },
        { kind: 'SITE', labelKey: 'menuConfig.type.SITE', descKey: 'menuConfig.typeDesc.SITE', icon: 'pi pi-desktop', initialType: 'BUILTIN' },
        { kind: 'URL', labelKey: 'menuConfig.type.URL', descKey: 'menuConfig.typeDesc.URL', icon: 'pi pi-external-link', initialType: 'URL' },
    ];

    /** 준비 중 유형 — 대상 표가 생겨야 고를 수 있다(추가 시트에 흐린 「준비 중」 줄로만 보인다) */
    static readonly SOON_TYPES: readonly MenuSoonTypeDef[] = [
        { type: 'PAGE', labelKey: 'menuConfig.type.PAGE', descKey: 'menuConfig.typeDesc.PAGE', icon: 'pi pi-book' },
    ];

    /** 앱 화면 카테고리 차례 */
    static readonly BUILTIN_CATEGORIES: readonly MenuBuiltinCategory[] = ['GENERAL', 'GROUP_BUY', 'GROUPS', 'MYPAGE'];

    /**
     * 앱 화면 레지스트리(`BUILTIN` 의 `targetKey`) — 공개 사이트(user-web)의 코드 화면. **서버가 같은 표를 갖는다**(후보 `MenuTargetRegistry`): 키 · 기본 이름 · 주소 · matchPrefix · 로그인 필요.
     * 화면이 새로 생기면 서버 표·user-web 화면과 함께 한 줄 더한다.
     */
    static readonly BUILTINS: readonly MenuBuiltinDef[] = [
        { key: 'HOME', category: 'GENERAL', labelKey: 'menuConfig.builtin.HOME', path: '/', matchPrefix: false, loginRequired: false, salesGated: false },
        { key: 'BOARDS_HUB', category: 'GENERAL', labelKey: 'menuConfig.builtin.BOARDS_HUB', path: '/boards', matchPrefix: false, loginRequired: false, salesGated: false },
        { key: 'POPULAR', category: 'GENERAL', labelKey: 'menuConfig.builtin.POPULAR', path: '/popular', matchPrefix: true, loginRequired: false, salesGated: false },
        { key: 'LATEST', category: 'GENERAL', labelKey: 'menuConfig.builtin.LATEST', path: '/latest', matchPrefix: true, loginRequired: false, salesGated: false },
        { key: 'SEARCH', category: 'GENERAL', labelKey: 'menuConfig.builtin.SEARCH', path: '/search', matchPrefix: true, loginRequired: false, salesGated: false },
        { key: 'GROUP_BUYS', category: 'GROUP_BUY', labelKey: 'menuConfig.builtin.GROUP_BUYS', path: '/group-buys', matchPrefix: true, loginRequired: false, salesGated: true },
        { key: 'GROUP_BUY_ORDERS', category: 'GROUP_BUY', labelKey: 'menuConfig.builtin.GROUP_BUY_ORDERS', path: '/me/orders', matchPrefix: true, loginRequired: true, salesGated: true },
        { key: 'GROUPS_ALL', category: 'GROUPS', labelKey: 'menuConfig.builtin.GROUPS_ALL', path: '/groups', matchPrefix: true, loginRequired: false, salesGated: false },
        { key: 'GROUPS_MINE', category: 'GROUPS', labelKey: 'menuConfig.builtin.GROUPS_MINE', path: '/groups/me', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'GROUPS_NEW', category: 'GROUPS', labelKey: 'menuConfig.builtin.GROUPS_NEW', path: '/groups/new', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'MY_HOME', category: 'MYPAGE', labelKey: 'menuConfig.builtin.MY_HOME', path: '/me', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'MY_GRADE', category: 'MYPAGE', labelKey: 'menuConfig.builtin.MY_GRADE', path: '/me/grade', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'MY_POINTS', category: 'MYPAGE', labelKey: 'menuConfig.builtin.MY_POINTS', path: '/me/points', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'MY_SHOP', category: 'MYPAGE', labelKey: 'menuConfig.builtin.MY_SHOP', path: '/point-shop', matchPrefix: true, loginRequired: true, salesGated: false },
        { key: 'MY_SCHEDULED', category: 'MYPAGE', labelKey: 'menuConfig.builtin.MY_SCHEDULED', path: '/scheduled', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'MESSAGES', category: 'MYPAGE', labelKey: 'menuConfig.builtin.MESSAGES', path: '/messages', matchPrefix: true, loginRequired: true, salesGated: false },
        { key: 'NOTIFICATIONS', category: 'MYPAGE', labelKey: 'menuConfig.builtin.NOTIFICATIONS', path: '/notifications', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'NOTIFICATION_SETTINGS', category: 'MYPAGE', labelKey: 'menuConfig.builtin.NOTIFICATION_SETTINGS', path: '/settings/notifications', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'SETTINGS_PROFILE', category: 'MYPAGE', labelKey: 'menuConfig.builtin.SETTINGS_PROFILE', path: '/settings/profile', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'SETTINGS_ACCOUNT', category: 'MYPAGE', labelKey: 'menuConfig.builtin.SETTINGS_ACCOUNT', path: '/settings/account', matchPrefix: true, loginRequired: true, salesGated: false },
        { key: 'SETTINGS_VIEW', category: 'MYPAGE', labelKey: 'menuConfig.builtin.SETTINGS_VIEW', path: '/settings/view', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'SETTINGS_DISPLAY', category: 'MYPAGE', labelKey: 'menuConfig.builtin.SETTINGS_DISPLAY', path: '/settings/display', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'SETTINGS_MESSAGES', category: 'MYPAGE', labelKey: 'menuConfig.builtin.SETTINGS_MESSAGES', path: '/settings/messages', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'SETTINGS_NICKNAME_IMAGE', category: 'MYPAGE', labelKey: 'menuConfig.builtin.SETTINGS_NICKNAME_IMAGE', path: '/settings/nickname-image', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'SETTINGS_BLOCKS', category: 'MYPAGE', labelKey: 'menuConfig.builtin.SETTINGS_BLOCKS', path: '/settings/blocks', matchPrefix: false, loginRequired: true, salesGated: false },
        { key: 'SETTINGS_MEMOS', category: 'MYPAGE', labelKey: 'menuConfig.builtin.SETTINGS_MEMOS', path: '/settings/memos', matchPrefix: false, loginRequired: true, salesGated: false },
    ];

    /**
     * 연결 유형 정의 — 화면이 아는 유형만.
     * @param {string} type - 연결 유형.
     * @returns {MenuTargetTypeDef | undefined} 정의. 모르는 유형이면 undefined.
     */
    static typeOf(type: string): MenuTargetTypeDef | undefined {
        return UtilMenu.TARGET_TYPES.find((def) => def.type === type);
    }

    /**
     * 화면이 보여 주는 연결 종류 — `BUILTIN`·`PATH` 는 「사이트 화면」.
     * @param {string} type - 연결 유형.
     * @returns {MenuTargetKind | undefined} 종류. 모르는 유형이면 undefined.
     */
    static kindOf(type: string): MenuTargetKind | undefined {
        return UtilMenu.typeOf(type)?.kind;
    }

    /**
     * 주소가 같은 앱 화면 — 「주소 직접 입력」에서 「목록에서 고르기」로 돌아갈 때 그 화면을 고른 채로 둔다.
     * @param {string} path - 주소.
     * @returns {MenuBuiltinDef | undefined} 정의. 없으면 undefined.
     */
    static builtinByPath(path: string): MenuBuiltinDef | undefined {
        const value = path.trim();
        return value ? UtilMenu.BUILTINS.find((def) => def.path === value) : undefined;
    }

    /**
     * 앱 화면 정의.
     * @param {string | null} key - 앱 화면 키.
     * @returns {MenuBuiltinDef | undefined} 정의. 레지스트리에 없으면 undefined.
     */
    static builtinOf(key: string | null): MenuBuiltinDef | undefined {
        return key ? UtilMenu.BUILTINS.find((def) => def.key === key) : undefined;
    }

    /**
     * 앱 화면 카테고리 차례 — 그룹의 위치에 맞는 카테고리(마이페이지 → 마이페이지, 공동구매 → 공동구매)를 맨 위로.
     * @param {MenuPlacement} placement - 그룹의 위치.
     * @returns {MenuBuiltinCategory[]} 차례.
     */
    static builtinCategoryOrder(placement: MenuPlacement): MenuBuiltinCategory[] {
        const first: MenuBuiltinCategory | null = placement === 'MYPAGE' ? 'MYPAGE' : placement === 'GROUP_BUY' ? 'GROUP_BUY' : null;
        return first ? [first, ...UtilMenu.BUILTIN_CATEGORIES.filter((c) => c !== first)] : [...UtilMenu.BUILTIN_CATEGORIES];
    }

    /**
     * 모바일 상단 탭·자동 추가 폴더·미연결 게시판 경고를 쓰는 그룹인가(좌측 사이드 = 기본 내비게이션).
     * @param {MenuPlacement} placement - 그룹의 위치.
     * @returns {boolean} `SIDE` 면 true.
     */
    static isSide(placement: MenuPlacement): boolean {
        return placement === 'SIDE';
    }

    // ── 받기 · 보내기 ─

    /**
     * 서버 트리 → 화면 편집 트리(화면 열쇠를 붙인다).
     * @param {MenuNodeDto[]} nodes - 서버 트리.
     * @returns {MenuEditNode[]} 편집 트리.
     */
    static toEdit(nodes: MenuNodeDto[]): MenuEditNode[] {
        return nodes.map((node): MenuEditNode => {
            const common = {
                key: UtilMenuTree.newKey(),
                id: node.id,
                label: node.label ?? '',
                nameEn: node.nameEn ?? '',
                visible: node.visible,
                audience: node.audience,
                minGrade: node.minGrade,
                divider: node.divider ?? 'NONE',
                textColor: UtilMenu.normalizeTextColor(node.textColor),
                bold: node.bold === true,
                badge: node.badge ?? '',
            };
            if (node.kind === 'FOLDER') {
                // 이 화면이 모르는 유형의 폴더 연결은 연결 없음으로 읽는다(폴더 연결은 화면이 아는 유형만 저장된다)
                const link = node.targetType && UtilMenu.typeOf(node.targetType) ? UtilMenu.linkOf(node.targetType, node.targetId ?? null, node.targetKey ?? null, node.settings ?? null) : null;
                return { ...common, kind: 'FOLDER', collapse: !node.collapsible ? 'NONE' : node.collapsed ? 'CLOSED' : 'OPEN', autoAdd: node.autoAdd, link, linkClick: node.linkClick ?? 'NAVIGATE_OPEN', children: UtilMenu.toEdit(node.children) };
            }
            const known = UtilMenu.typeOf(node.targetType);
            return {
                ...common,
                kind: 'ITEM',
                ...UtilMenu.linkOf(node.targetType, node.targetId, node.targetKey, node.settings),
                mobileTab: node.mobileTab,
                description: node.description ?? '',
                rawSettings: known ? null : node.settings,
                shortcut: node.targetType === 'BOARD' && node.shortcut === true,
            };
        });
    }

    /**
     * 서버 칸(유형·ID·키·설정) → 화면 연결 대상.
     * @param {string} targetType - 연결 유형.
     * @param {string | null} targetId - `BOARD` 의 게시판 ID.
     * @param {string | null} targetKey - `BUILTIN` 의 앱 화면 키.
     * @param {Record<string, unknown> | null} raw - 유형별 설정.
     * @returns {MenuEditLink} 연결 대상.
     */
    static linkOf(targetType: string, targetId: string | null, targetKey: string | null, raw: Record<string, unknown> | null): MenuEditLink {
        const settings = raw ?? {};
        return {
            targetType,
            targetId,
            targetKey,
            path: targetType === 'PATH' && typeof settings.path === 'string' ? settings.path : '',
            href: targetType === 'URL' && typeof settings.href === 'string' ? settings.href : '',
            newTab: settings.newTab === true,
        };
    }

    /**
     * 화면 연결 대상 → 서버 칸(유형에 맞는 칸만 채운다). 이 화면이 아는 유형만 넘긴다.
     * @param {MenuEditLink} link - 연결 대상.
     * @returns {{ targetType: string; targetId: string | null; targetKey: string | null; settings: Record<string, unknown> | null }} 서버 칸.
     */
    static linkDto(link: MenuEditLink): { targetType: string; targetId: string | null; targetKey: string | null; settings: Record<string, unknown> | null } {
        let settings: Record<string, unknown> | null = null;
        if (link.targetType === 'PATH') settings = { path: link.path.trim(), newTab: link.newTab };
        else if (link.targetType === 'URL') settings = { href: link.href.trim(), newTab: link.newTab };
        const byId = link.targetType === 'BOARD' || link.targetType === 'GADGET_PAGE';
        return { targetType: link.targetType, targetId: byId ? link.targetId : null, targetKey: link.targetType === 'BUILTIN' ? link.targetKey : null, settings };
    }

    /**
     * 화면 편집 트리 → 서버 요청 트리(화면 열쇠를 뺀다. 이름은 다듬어 보내고 빈 이름은 null).
     * @param {MenuEditNode[]} nodes - 편집 트리.
     * @param {MenuPlacement} placement - 그룹의 위치(SIDE 가 아니면 모바일 탭·자동 추가·구분선을 끄고 보낸다).
     * @param {boolean} top - 최상위 줄인가(구분선은 최상위만 — 안쪽 줄은 `NONE`).
     * @returns {MenuNodeDto[]} 서버 트리.
     */
    static toDto(nodes: MenuEditNode[], placement: MenuPlacement, top: boolean = true): MenuNodeDto[] {
        const side = UtilMenu.isSide(placement);
        return nodes.map((node): MenuNodeDto => {
            const common = {
                id: node.id,
                label: node.label.trim() || null,
                nameEn: node.nameEn.trim() || null,
                visible: node.visible,
                audience: node.audience,
                minGrade: node.audience === 'GRADE_MIN' ? node.minGrade : null,
                divider: side && top ? node.divider : 'NONE',
                textColor: UtilMenu.normalizeTextColor(node.textColor),
                bold: node.bold,
                badge: node.badge.trim() || null,
            };
            if (node.kind === 'FOLDER') {
                // 폴더 연결은 화면이 아는 유형만 만든다(`newLink`)
                const link = node.link ? { ...UtilMenu.linkDto(node.link), targetType: node.link.targetType as MenuKnownTargetType } : { targetType: null, targetId: null, targetKey: null, settings: null };
                const folder: MenuFolderDto = {
                    ...common,
                    kind: 'FOLDER',
                    collapsible: node.collapse !== 'NONE',
                    collapsed: node.collapse === 'CLOSED',
                    autoAdd: side && node.autoAdd,
                    ...link,
                    linkClick: node.link ? node.linkClick : null,
                    children: UtilMenu.toDto(node.children, placement, false),
                };
                return folder;
            }
            // 모르는 유형은 받은 그대로 돌려보낸다
            const target = UtilMenu.typeOf(node.targetType) ? UtilMenu.linkDto(node) : { targetType: node.targetType, targetId: node.targetId, targetKey: node.targetKey, settings: node.rawSettings };
            const item: MenuItemDto = {
                ...common,
                kind: 'ITEM',
                ...target,
                mobileTab: side && node.mobileTab && !node.shortcut,
                description: (side && top && node.description.trim()) || null,
                // 바로가기 표시는 바로가기에만 싣는다(일반 항목의 요청 모양은 그대로)
                ...(node.shortcut ? { shortcut: true } : {}),
            };
            return item;
        });
    }

    /**
     * 새 폴더.
     * @returns {MenuEditFolder} 이름이 빈 폴더(보임 · 모두 · 접기 안 함).
     */
    static newFolder(): MenuEditFolder {
        return { key: UtilMenuTree.newKey(), id: null, kind: 'FOLDER', label: '', nameEn: '', visible: true, audience: 'ALL', minGrade: null, divider: 'NONE', textColor: null, bold: false, badge: '', collapse: 'NONE', autoAdd: false, link: null, linkClick: 'NAVIGATE_OPEN', children: [] };
    }

    /**
     * 새 항목 — 유형에 맞는 기본값(외부 링크는 새 탭 켬, 로그인 필요 앱 화면은 보이는 대상을 로그인으로).
     * @param {MenuKnownTargetType} type - 연결 유형.
     * @returns {MenuEditItem} 대상이 빈 항목.
     */
    static newItem(type: MenuKnownTargetType): MenuEditItem {
        return {
            key: UtilMenuTree.newKey(),
            id: null,
            kind: 'ITEM',
            label: '',
            nameEn: '',
            visible: true,
            audience: 'ALL',
            minGrade: null,
            divider: 'NONE',
            ...UtilMenu.newLink(type),
            mobileTab: false,
            description: '',
            rawSettings: null,
            shortcut: false,
            textColor: null,
            bold: false,
            badge: '',
        };
    }

    /**
     * 새 연결 대상 — 대상이 빈 채(외부 링크는 `https://` + 새 탭).
     * @param {MenuKnownTargetType} type - 연결 유형.
     * @returns {MenuEditLink} 연결 대상.
     */
    static newLink(type: MenuKnownTargetType): MenuEditLink {
        return { targetType: type, targetId: null, targetKey: null, path: '', href: type === 'URL' ? 'https://' : '', newTab: type === 'URL' };
    }

    /**
     * 연결 유형을 바꾼다 — 대상 칸만 비우고 나머지(이름·보이는 대상·탭·구분선·설명·ID)는 그대로 둔다. 이미 만든 항목의 유형 바꾸기에 쓴다.
     * @param {T} node - 항목 또는 연결 대상.
     * @param {MenuKnownTargetType} type - 새 유형.
     * @returns {T} 유형을 바꾼 복사본(같은 유형이면 그대로 복사).
     */
    static retarget<T extends MenuEditLink>(node: T, type: MenuKnownTargetType): T {
        if (node.targetType === type) return { ...node };
        return { ...node, ...UtilMenu.newLink(type), ...('rawSettings' in node ? { rawSettings: null } : {}) };
    }

    // ── 대상 · 상태 · 이름 ─

    /**
     * 사이트 안 주소가 판매 스위치가 필요한 앱 화면을 가리키나 — 서버 `MenuBuiltin.salesGatedPath` 와 같은 규칙(물음표·샵 뒤는 보지 않는다).
     * @param {string} path - 주소.
     * @returns {boolean} 공동구매 목록·주문 내역(과 그 아래)이면 true.
     */
    static salesGatedPath(path: string): boolean {
        let p = path.trim().split(/[?#]/)[0] ?? '';
        if (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1);
        return UtilMenu.BUILTINS.some((b) => b.salesGated && (p === b.path || (b.matchPrefix && p.startsWith(`${b.path}/`))));
    }

    /**
     * 항목 상태 — 방문자에게 안 보이는 이유(저장은 된다).
     * @param {MenuEditLink} item - 항목(또는 폴더의 연결 대상).
     * @param {MenuContext} ctx - 게시판 목록·판매 스위치.
     * @returns {MenuItemStatus} 상태.
     */
    static itemStatus(item: MenuEditLink, ctx: MenuContext): MenuItemStatus {
        if (!UtilMenu.typeOf(item.targetType)) return 'UNKNOWN_TYPE';
        if (item.targetType === 'BOARD') {
            const board = ctx.boards.find((b) => b.id === item.targetId);
            if (!board) return 'TARGET_MISSING';
            return board.active ? 'OK' : 'BOARD_INACTIVE';
        }
        if (item.targetType === 'BUILTIN') {
            const def = UtilMenu.builtinOf(item.targetKey);
            if (!def) return 'TARGET_MISSING';
            if (def.salesGated && !ctx.salesEnabled) return 'SALES_OFF';
        }
        // 주소 직접 입력이 판매 스위치가 필요한 앱 화면(공동구매·주문 내역과 그 아래)을 가리키면 같은 판정(서버 `MenuBuiltin.salesGatedPath`)
        if (item.targetType === 'PATH' && !ctx.salesEnabled && UtilMenu.salesGatedPath(item.path)) return 'SALES_OFF';
        if (item.targetType === 'GADGET_PAGE' && !UtilMenu.gadgetPageOf(item.targetId, ctx)) return 'TARGET_MISSING';
        return 'OK';
    }

    /**
     * 항목이 이름을 비웠을 때 따라가는 대상 이름 — 게시판 이름(+영문명) 또는 앱 화면 기본 이름의 키. 주소·외부 링크·모르는 유형은 따라갈 이름이 없다.
     * @param {MenuEditLink} item - 항목(또는 폴더의 연결 대상).
     * @param {MenuContext} ctx - 게시판 목록.
     * @returns {{ name: string; nameEn: string | null } | { labelKey: string } | null} 대상 이름.
     */
    static targetName(item: MenuEditLink, ctx: MenuContext): { name: string; nameEn: string | null } | { labelKey: string } | null {
        if (item.targetType === 'BOARD') {
            const board = ctx.boards.find((b) => b.id === item.targetId);
            return board ? { name: board.name, nameEn: board.nameEn } : null;
        }
        if (item.targetType === 'BUILTIN') {
            const def = UtilMenu.builtinOf(item.targetKey);
            return def ? { labelKey: def.labelKey } : null;
        }
        if (item.targetType === 'GADGET_PAGE') {
            const page = UtilMenu.gadgetPageOf(item.targetId, ctx);
            return page ? { name: page.name, nameEn: page.nameEn } : null;
        }
        return null;
    }

    /**
     * 가젯 페이지 하나.
     * @param {string | null} id - 가젯 페이지 ID(`targetId`).
     * @param {MenuContext} ctx - 가젯 페이지 목록.
     * @returns {MenuGadgetPageOption | undefined} 페이지. 없으면 undefined.
     */
    static gadgetPageOf(id: string | null, ctx: MenuContext): MenuGadgetPageOption | undefined {
        return id ? ctx.pages.find((p) => p.id === id) : undefined;
    }

    /**
     * 줄에 보일 대상 — 게시판 주소(`/boards/{slug}`, slug 작은 글자) · 앱 화면 주소 · 페이지 주소 · 외부 링크 도메인.
     * @param {MenuEditLink} item - 항목(또는 폴더의 연결 대상).
     * @param {MenuContext} ctx - 게시판 목록.
     * @returns {string} 대상 글자(모르면 빈 글자).
     */
    static targetText(item: MenuEditLink, ctx: MenuContext): string {
        switch (item.targetType) {
            case 'BOARD': {
                const board = ctx.boards.find((b) => b.id === item.targetId);
                return board ? `/boards/${board.slug.toLowerCase()}` : '';
            }
            case 'BUILTIN':
                return UtilMenu.builtinOf(item.targetKey)?.path ?? item.targetKey ?? '';
            case 'PATH':
                return item.path;
            case 'GADGET_PAGE': {
                const page = UtilMenu.gadgetPageOf(item.targetId, ctx);
                return page ? (page.path ?? `/p/${page.key}`) : (item.targetId ?? '');
            }
            case 'URL':
                return UtilMenu.hostOf(item.href);
            default:
                return item.targetKey ?? item.targetId ?? '';
        }
    }

    /**
     * 외부 링크의 도메인 — 사이트 안 주소(`/…`)면 그대로.
     * @param {string} href - 주소.
     * @returns {string} 도메인 또는 주소.
     */
    static hostOf(href: string): string {
        const value = href.trim();
        if (value.startsWith('/')) return value;
        try {
            return new URL(value).host;
        } catch {
            return value;
        }
    }

    /**
     * 외부(다른 도메인)로 가는 링크인가.
     * @param {MenuEditLink} item - 항목(또는 폴더의 연결 대상).
     * @returns {boolean} `URL` 이고 `https://` 주소면 true.
     */
    static isExternal(item: MenuEditLink): boolean {
        return item.targetType === 'URL' && item.href.trim().startsWith('https://');
    }

    /**
     * 이 그룹에 이미 연결된 게시판·앱 화면 — `BOARD:<id>` · `BUILTIN:<key>` → 항목과 들어 있는 폴더 이름.
     * 폴더 연결(`link`)은 세지 않는다 — 폴더 이름 바로가기라 하위 항목과 같은 대상(예: 「소모임」 폴더와 그 안의 「전체 소모임」)을 가리켜도 된다.
     * @param {MenuEditNode[]} nodes - 트리.
     * @returns {Map<string, MenuLinkedTarget>} 연결.
     */
    static linkedTargets(nodes: MenuEditNode[]): Map<string, MenuLinkedTarget> {
        const out = new Map<string, MenuLinkedTarget>();
        const walk = (list: MenuEditNode[], folder: MenuEditFolder | null) => {
            for (const node of list) {
                if (node.kind === 'FOLDER') {
                    walk(node.children, node);
                    continue;
                }
                // 바로가기는 진짜 자리가 아니다 — 대상 고르기의 「○○ 폴더에 있음」은 진짜 자리만 알린다
                if (node.shortcut) continue;
                const id = UtilMenu.linkId(node);
                if (id && !out.has(id)) out.set(id, { itemKey: node.key, folderLabel: folder ? folder.label || null : null });
            }
        };
        walk(nodes, null);
        return out;
    }

    /**
     * 한 그룹 안에서 한 번만 연결할 수 있는 대상의 이름표 — 게시판·앱 화면·가젯 페이지.
     * @param {MenuEditLink} item - 항목(또는 폴더의 연결 대상).
     * @returns {string | null} `BOARD:<id>` · `BUILTIN:<key>` · `GADGET_PAGE:<key>`. 그 밖에는 null.
     */
    static linkId(item: MenuEditLink): string | null {
        if (item.targetType === 'BOARD' && item.targetId) return `BOARD:${item.targetId}`;
        if (item.targetType === 'BUILTIN' && item.targetKey) return `BUILTIN:${item.targetKey}`;
        if (item.targetType === 'GADGET_PAGE' && item.targetId) return `GADGET_PAGE:${item.targetId}`;
        return null;
    }

    // ── 검증 ─

    /**
     * 이름 문제 — 1~20자, `<`·`>`·줄바꿈 금지.
     * @param {string} value - 이름.
     * @param {boolean} required - 비우면 안 되는가.
     * @param {number} [max] - 최대 글자 수.
     * @returns {MenuProblemCode | null} 문제.
     */
    static labelProblem(value: string, required: boolean, max: number = UtilMenu.LIMITS.labelMax): MenuProblemCode | null {
        const text = value.trim();
        if (!text) return required ? 'LABEL_REQUIRED' : null;
        if (/[<>\r\n]/.test(value)) return 'LABEL_CHARS';
        if ([...text].length > max) return 'LABEL_LENGTH';
        return null;
    }

    /**
     * 영문명 문제 — 선택, 40자 이하, `<`·`>`·줄바꿈 금지.
     * @param {string} value - 영문명.
     * @returns {MenuProblemCode | null} 문제.
     */
    static nameEnProblem(value: string): MenuProblemCode | null {
        const text = value.trim();
        if (!text) return null;
        if (/[<>\r\n]/.test(value)) return 'LABEL_CHARS';
        if (text.length > UtilMenu.LIMITS.nameEnMax) return 'NAME_EN_LENGTH';
        return null;
    }

    /**
     * 사이트 안 주소 문제 — `/` 로 시작, 500자 이하, `//`·스킴·`/api`·`/admin` 구간·공백·따옴표·꺾쇠·역슬래시·`.`/`..` 구간 거절.
     * @param {string} value - 주소.
     * @returns {MenuProblemCode | null} 문제.
     */
    static pathProblem(value: string): MenuProblemCode | null {
        const path = value.trim();
        if (!path) return 'PATH_REQUIRED';
        if (path.length > UtilMenu.LIMITS.pathMax) return 'PATH_LENGTH';
        if (/[\s"'<>\\`]/.test(path)) return 'PATH_CHARS';
        if (!path.startsWith('/')) return 'PATH_START';
        if (path.startsWith('//') || path.includes('//')) return 'PATH_DOUBLE_SLASH';
        const segments = path.split(/[?#]/)[0]!.split('/').filter(Boolean);
        if (segments.some((s) => s === '.' || s === '..')) return 'PATH_DOTS';
        const first = segments[0]?.toLowerCase();
        if (first === 'api' || first === 'admin') return 'PATH_RESERVED';
        if (/^[a-z][a-z0-9+.-]*:/i.test(segments[0] ?? '')) return 'PATH_START';
        return null;
    }

    /**
     * 외부 링크 주소 문제 — `https://` 절대 주소 또는 `/…`(사이트 안 주소 규칙), 2000자 이하, `http://`·`javascript:`·`data:`·`//`·공백·계정정보(`user@host`) 거절.
     * @param {string} value - 주소.
     * @returns {MenuProblemCode | null} 문제.
     */
    static hrefProblem(value: string): MenuProblemCode | null {
        const href = value.trim();
        if (!href || href === 'https://') return 'HREF_REQUIRED';
        if (href.length > UtilMenu.LIMITS.hrefMax) return 'HREF_LENGTH';
        if (/\s/.test(value.replace(/^\s+|\s+$/g, ''))) return 'HREF_CHARS';
        if (href.startsWith('//')) return 'HREF_PROTOCOL_RELATIVE';
        if (href.startsWith('/')) {
            const problem = UtilMenu.pathProblem(href);
            return problem === 'PATH_DOUBLE_SLASH' ? 'HREF_PROTOCOL_RELATIVE' : problem ? 'HREF_SCHEME' : null;
        }
        if (/^http:\/\//i.test(href)) return 'HREF_HTTP';
        if (!/^https:\/\//i.test(href)) return 'HREF_SCHEME';
        if (/[<>"'\\`]/.test(href)) return 'HREF_CHARS';
        const authority = href.slice('https://'.length).split(/[/?#]/)[0] ?? '';
        if (authority.includes('@')) return 'HREF_USERINFO';
        if (!authority || !/^[a-z0-9.-]+(:\d{1,5})?$/i.test(authority)) return 'HREF_SCHEME';
        return null;
    }

    /**
     * 로그인이 필요한 앱 화면에 맞는 보이는 대상인가.
     * @param {MenuAudience} audience - 보이는 대상.
     * @returns {boolean} 로그인한 사람에게만 보이면 true.
     */
    static isLoginAudience(audience: MenuAudience): boolean {
        return audience === 'MEMBER' || audience === 'GRADE_MIN' || audience === 'STAFF';
    }

    /**
     * 항목 하나의 문제(트리 전체를 봐야 하는 중복은 `validate`).
     * @param {MenuEditItem} item - 항목.
     * @param {MenuPlacement} placement - 그룹의 위치.
     * @returns {Partial<Record<MenuProblemField, MenuProblemCode>>} 칸별 문제.
     */
    static itemProblems(item: MenuEditItem, placement: MenuPlacement): Partial<Record<MenuProblemField, MenuProblemCode>> {
        const out: Partial<Record<MenuProblemField, MenuProblemCode>> = {};
        const known = UtilMenu.typeOf(item.targetType);
        if (!known) return out;
        const needsLabel = item.targetType === 'PATH' || item.targetType === 'URL';
        const label = UtilMenu.labelProblem(item.label, needsLabel);
        if (label) out.label = label;
        const nameEn = UtilMenu.nameEnProblem(item.nameEn);
        if (nameEn) out.nameEn = nameEn;
        if (item.audience === 'GRADE_MIN' && !item.minGrade) out.minGrade = 'GRADE_REQUIRED';
        Object.assign(out, UtilMenu.linkProblems(item));
        if (item.targetType === 'BUILTIN' && UtilMenu.builtinOf(item.targetKey)?.loginRequired && !UtilMenu.isLoginAudience(item.audience)) out.audience = 'LOGIN_AUDIENCE';
        if (item.mobileTab && !UtilMenu.isSide(placement)) out.mobileTab = 'MOBILE_TAB_SIDE';
        if (item.textColor !== null && UtilMenu.normalizeTextColor(item.textColor) === null) out.textColor = 'TEXT_COLOR_FORMAT';
        const badge = UtilMenu.badgeProblem(item.badge);
        if (badge) out.badge = badge;
        return out;
    }

    /**
     * 연결 대상의 문제 — 대상 고르기 · 주소 · 링크 주소. 항목과 폴더 연결이 같은 규칙이다.
     * @param {MenuEditLink} link - 연결 대상.
     * @returns {Partial<Record<MenuProblemField, MenuProblemCode>>} 칸별 문제(`target`·`path`·`href`).
     */
    static linkProblems(link: MenuEditLink): Partial<Record<MenuProblemField, MenuProblemCode>> {
        const out: Partial<Record<MenuProblemField, MenuProblemCode>> = {};
        if (link.targetType === 'BOARD' && !link.targetId) out.target = 'TARGET_REQUIRED';
        if (link.targetType === 'BUILTIN' && !link.targetKey) out.target = 'TARGET_REQUIRED';
        if (link.targetType === 'GADGET_PAGE' && !link.targetId) out.target = 'TARGET_REQUIRED';
        if (link.targetType === 'PATH') {
            const path = UtilMenu.pathProblem(link.path);
            if (path) out.path = path;
        }
        if (link.targetType === 'URL') {
            const href = UtilMenu.hrefProblem(link.href);
            if (href) out.href = href;
        }
        return out;
    }

    /**
     * 폴더 하나의 문제.
     * @param {MenuEditFolder} folder - 폴더.
     * @returns {Partial<Record<MenuProblemField, MenuProblemCode>>} 칸별 문제.
     */
    static folderProblems(folder: MenuEditFolder): Partial<Record<MenuProblemField, MenuProblemCode>> {
        const out: Partial<Record<MenuProblemField, MenuProblemCode>> = {};
        const label = UtilMenu.labelProblem(folder.label, true);
        if (label) out.label = label;
        const nameEn = UtilMenu.nameEnProblem(folder.nameEn);
        if (nameEn) out.nameEn = nameEn;
        if (folder.audience === 'GRADE_MIN' && !folder.minGrade) out.minGrade = 'GRADE_REQUIRED';
        if (folder.link) Object.assign(out, UtilMenu.linkProblems(folder.link));
        if (folder.textColor !== null && UtilMenu.normalizeTextColor(folder.textColor) === null) out.textColor = 'TEXT_COLOR_FORMAT';
        const badge = UtilMenu.badgeProblem(folder.badge);
        if (badge) out.badge = badge;
        return out;
    }

    /**
     * 트리 전체 검증 — 줄별 문제 + 한도(노드 수·모바일 탭 수·자동 추가 폴더 하나·단계). 서버 `MenuTreeValidator` 와 같은 규칙.
     * 한 그룹 안 한 번(`TARGET_DUPLICATE`)은 진짜 자리끼리만 센다. 바로가기는 진짜 자리가 있어야 하고(`SHORTCUT_ORIGIN_MISSING` — 서버 `menu.shortcut.originMissing`), 폴더 안에만 두고(`SHORTCUT_TOP_LEVEL` — `menu.shortcut.topLevel`),
     * 원래 자리 묶음·같은 게시판 바로가기가 이미 있는 묶음에 둘 수 없다(`SHORTCUT_SAME_FOLDER` — `menu.shortcut.sameFolder`). 서버 `MenuTreeValidator` 와 같은 차례.
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {MenuPlacement} placement - 그룹의 위치.
     * @returns {MenuProblems} 문제(없으면 빈 것).
     */
    static validate(nodes: MenuEditNode[], placement: MenuPlacement): MenuProblems {
        const rows: MenuProblems['rows'] = {};
        const tree: MenuTreeProblem[] = [];
        const seen = new Set<string>();
        let tabs = 0;
        let autoAdd = 0;
        const places = UtilMenu.boardPlaces(nodes);
        for (const { node, parentKey } of UtilMenuTree.flatten(nodes)) {
            const problems = node.kind === 'FOLDER' ? UtilMenu.folderProblems(node) : UtilMenu.itemProblems(node, placement);
            if (node.kind === 'ITEM' && node.shortcut) {
                const place = node.targetId ? places.get(node.targetId) : undefined;
                if (!place?.origin) problems.target = 'SHORTCUT_ORIGIN_MISSING';
                else if (parentKey === null) problems.target = 'SHORTCUT_TOP_LEVEL';
                else if (place.origin.folderKey === parentKey || UtilMenu.firstShortcutIn(place, parentKey) !== node.key) problems.target = 'SHORTCUT_SAME_FOLDER';
            } else if (node.kind === 'ITEM') {
                const id = UtilMenu.linkId(node);
                if (id && seen.has(id)) problems.target = 'TARGET_DUPLICATE';
                if (id) seen.add(id);
                if (node.mobileTab) tabs += 1;
            } else if (node.autoAdd) {
                autoAdd += 1;
            }
            if (Object.keys(problems).length) rows[node.key] = problems;
        }
        if (UtilMenuTree.count(nodes) > UtilMenu.LIMITS.nodes) tree.push('NODES_LIMIT');
        if (tabs > UtilMenu.LIMITS.mobileTabs) tree.push('TABS_LIMIT');
        if (autoAdd > 1) tree.push('AUTO_ADD_MULTI');
        const depth = UtilMenu.depthProblem(nodes);
        if (depth) tree.push(depth);
        return { rows, tree };
    }

    /**
     * 단계 문제 — 폴더 깊이 ≤ 3, 항목 깊이 ≤ 4.
     * @param {MenuEditNode[]} nodes - 트리.
     * @returns {'FOLDER_DEPTH' | 'ITEM_DEPTH' | null} 문제.
     */
    static depthProblem(nodes: MenuEditNode[]): 'FOLDER_DEPTH' | 'ITEM_DEPTH' | null {
        for (const row of UtilMenuTree.flatten(nodes)) {
            if (row.node.kind === 'FOLDER' && row.depth > UtilMenu.LIMITS.folderDepth) return 'FOLDER_DEPTH';
            if (row.node.kind === 'ITEM' && row.depth > UtilMenu.LIMITS.itemDepth) return 'ITEM_DEPTH';
        }
        return null;
    }

    /**
     * 옮기기를 해 보고 결과 트리 또는 막힌 이유를 준다.
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {string} key - 옮길 노드.
     * @param {MenuMoveAction} action - 옮기기.
     * 바로가기는 최상위(폴더 밖)로 옮길 수 없다(`SHORTCUT_TOP` — 서버 `menu.shortcut.topLevel` 을 미리 막는다).
     * @returns {{ tree: MenuEditNode[] | null; problem: 'NOWHERE' | 'FOLDER_DEPTH' | 'ITEM_DEPTH' | 'SHORTCUT_TOP' | null }} 결과.
     */
    static tryMove(nodes: MenuEditNode[], key: string, action: MenuMoveAction): { tree: MenuEditNode[] | null; problem: 'NOWHERE' | 'FOLDER_DEPTH' | 'ITEM_DEPTH' | 'SHORTCUT_TOP' | null } {
        const tree = UtilMenuTree.move(nodes, key, action);
        if (!tree) return { tree: null, problem: 'NOWHERE' };
        const moved = tree.find((node) => node.key === key);
        if (moved?.kind === 'ITEM' && moved.shortcut) return { tree: null, problem: 'SHORTCUT_TOP' };
        const depth = UtilMenu.depthProblem(tree);
        return depth ? { tree: null, problem: depth } : { tree, problem: null };
    }

    /**
     * 이 부모 안에 새 노드를 넣을 수 있는가(단계).
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {string | null} parentKey - 부모 폴더(null 이면 최상위).
     * @param {'FOLDER' | 'ITEM'} kind - 넣을 노드.
     * @returns {boolean} 넣을 수 있으면 true.
     */
    static canAddInside(nodes: MenuEditNode[], parentKey: string | null, kind: 'FOLDER' | 'ITEM'): boolean {
        const depth = parentKey === null ? 1 : (UtilMenuTree.find(nodes, parentKey)?.depth ?? 0) + 1;
        return kind === 'FOLDER' ? depth <= UtilMenu.LIMITS.folderDepth : depth <= UtilMenu.LIMITS.itemDepth;
    }

    /**
     * 폴더를 넣을 수 있는 자리(최상위 + 깊이 한도 안의 폴더).
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {'FOLDER' | 'ITEM'} kind - 넣을 노드.
     * @returns {MenuEditFolder[]} 부모가 될 수 있는 폴더(위에서 아래 차례).
     */
    static parentCandidates(nodes: MenuEditNode[], kind: 'FOLDER' | 'ITEM'): { folder: MenuEditFolder; depth: number }[] {
        return UtilMenuTree.flatten(nodes)
            .filter((row) => row.node.kind === 'FOLDER' && UtilMenu.canAddInside(nodes, row.node.key, kind))
            .map((row) => ({ folder: row.node as MenuEditFolder, depth: row.depth }));
    }

    /**
     * 모바일 탭 수.
     * @param {MenuEditNode[]} nodes - 트리.
     * @returns {number} 수.
     */
    static mobileTabCount(nodes: MenuEditNode[]): number {
        return UtilMenuTree.all(nodes).filter((node) => node.kind === 'ITEM' && node.mobileTab).length;
    }

    /**
     * 자동 추가 폴더를 이 폴더 하나로 — 켜면 다른 폴더는 꺼진다.
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {string} key - 켤 폴더(끄려면 `on` false).
     * @param {boolean} on - 켬/끔.
     * @returns {MenuEditNode[]} 새 트리.
     */
    static setAutoAdd(nodes: MenuEditNode[], key: string, on: boolean): MenuEditNode[] {
        const copy = UtilMenuTree.clone(nodes);
        for (const node of UtilMenuTree.all(copy)) {
            if (node.kind !== 'FOLDER') continue;
            if (node.key === key) node.autoAdd = on;
            else if (on) node.autoAdd = false;
        }
        return copy;
    }

    // ── 바로가기(다른 묶음에도 보이기) ─

    /**
     * 게시판마다 메뉴 자리 — 진짜 자리(바로가기 아닌 첫 항목)와 바로가기들(트리 차례).
     * @param {MenuEditNode[]} nodes - 트리.
     * @returns {Map<string, MenuBoardPlaces>} 게시판 ID → 자리.
     */
    static boardPlaces(nodes: MenuEditNode[]): Map<string, MenuBoardPlaces> {
        const out = new Map<string, MenuBoardPlaces>();
        for (const { node, parentKey } of UtilMenuTree.flatten(nodes)) {
            if (node.kind !== 'ITEM' || node.targetType !== 'BOARD' || !node.targetId) continue;
            const place = out.get(node.targetId) ?? { origin: null, shortcuts: [] };
            if (node.shortcut) place.shortcuts.push({ itemKey: node.key, folderKey: parentKey });
            else if (!place.origin) place.origin = { itemKey: node.key, folderKey: parentKey };
            out.set(node.targetId, place);
        }
        return out;
    }

    /**
     * 이 묶음에 든 이 게시판의 첫 바로가기 — 같은 묶음에 둘 이상이면 뒤의 것만 문제로 표시한다.
     * @param {MenuBoardPlaces} place - 게시판의 자리.
     * @param {string | null} folderKey - 묶음.
     * @returns {string | null} 첫 바로가기의 열쇠.
     */
    static firstShortcutIn(place: MenuBoardPlaces, folderKey: string | null): string | null {
        return place.shortcuts.find((s) => s.folderKey === folderKey)?.itemKey ?? null;
    }

    /**
     * 이 게시판 항목의 바로가기를 둘 묶음 후보 — 모든 폴더(위에서 아래 차례), 원래 자리 묶음·이미 바로가기가 있는 묶음·단계 한도인 묶음은 막힌 이유와 함께.
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {MenuEditItem} origin - 진짜 자리 게시판 항목.
     * @returns {MenuShortcutSpot[]} 후보.
     */
    static shortcutSpots(nodes: MenuEditNode[], origin: MenuEditItem): MenuShortcutSpot[] {
        const rows = UtilMenuTree.flatten(nodes);
        const place = origin.targetId ? UtilMenu.boardPlaces(nodes).get(origin.targetId) : undefined;
        const originFolder = rows.find((row) => row.node.key === origin.key)?.parentKey ?? null;
        return rows
            .filter((row) => row.node.kind === 'FOLDER')
            .map((row) => {
                const key = row.node.key;
                let blocked: MenuShortcutSpot['blocked'] = null;
                if (key === originFolder) blocked = 'ORIGIN';
                else if (place?.shortcuts.some((s) => s.folderKey === key)) blocked = 'EXISTS';
                else if (!UtilMenu.canAddInside(nodes, key, 'ITEM')) blocked = 'DEPTH';
                return { folder: row.node as MenuEditFolder, depth: row.depth, blocked };
            });
    }

    /**
     * 진짜 자리 항목의 바로가기를 묶음 끝에 넣는다 — 같은 게시판·이름·영문명·보이는 대상을 따르고, 보임 · 모바일 탭 끔 · 구분선 없음 · 설명 없음.
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {MenuEditItem} origin - 진짜 자리 게시판 항목.
     * @param {string} folderKey - 넣을 묶음(폴더).
     * @returns {MenuEditNode[]} 새 트리.
     */
    static addShortcut(nodes: MenuEditNode[], origin: MenuEditItem, folderKey: string): MenuEditNode[] {
        const item: MenuEditItem = {
            ...UtilMenu.newItem('BOARD'),
            targetId: origin.targetId,
            label: origin.label,
            nameEn: origin.nameEn,
            audience: origin.audience,
            minGrade: origin.minGrade,
            shortcut: true,
        };
        return UtilMenuTree.append(nodes, folderKey, item);
    }

    /**
     * 이 노드(항목, 또는 폴더와 그 안)에 진짜 자리가 있는 게시판의 바로가기 중 이 노드 밖의 것 — 진짜 자리를 지울 때 함께 지운다.
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {MenuEditNode} node - 지울 노드.
     * @returns {string[]} 함께 지울 바로가기 열쇠.
     */
    static orphanedShortcuts(nodes: MenuEditNode[], node: MenuEditNode): string[] {
        const inside = UtilMenuTree.all([node]);
        const insideKeys = new Set(inside.map((n) => n.key));
        const boards = new Set(inside.flatMap((n) => (n.kind === 'ITEM' && !n.shortcut && n.targetType === 'BOARD' && n.targetId ? [n.targetId] : [])));
        if (!boards.size) return [];
        const places = UtilMenu.boardPlaces(nodes);
        return [...boards].flatMap((id) => {
            const place = places.get(id);
            if (!place?.origin || !insideKeys.has(place.origin.itemKey)) return [];
            return place.shortcuts.map((s) => s.itemKey).filter((key) => !insideKeys.has(key));
        });
    }

    // ── 미연결 게시판 ─

    /**
     * 메뉴에 없는 게시판 — 사용 중 게시판 중 항목이 없거나(`NO_ITEM`) 숨김(자기·조상 `visible: false`) 안에만 있는(`HIDDEN`) 것. `SIDE` 그룹에서만 쓴다.
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {MenuContext} ctx - 게시판 목록.
     * @returns {MenuUnlinkedBoard[]} 게시판 순서대로.
     */
    static unlinkedBoards(nodes: MenuEditNode[], ctx: MenuContext): MenuUnlinkedBoard[] {
        const shown = new Set<string>();
        const any = new Set<string>();
        for (const row of UtilMenuTree.flatten(nodes, (node) => !node.visible)) {
            if (row.node.kind !== 'ITEM' || row.node.targetType !== 'BOARD' || !row.node.targetId) continue;
            any.add(row.node.targetId);
            if (row.node.visible && !row.hiddenByAncestor) shown.add(row.node.targetId);
        }
        return ctx.boards
            .filter((board) => board.active && !shown.has(board.id))
            .map((board) => ({ board, reason: any.has(board.id) ? 'HIDDEN' : 'NO_ITEM' }) as MenuUnlinkedBoard);
    }

    /**
     * 자동 추가 폴더(없으면 최상위) 끝에 게시판 항목을 넣는다.
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {MenuBoardOption} board - 게시판.
     * @returns {MenuEditNode[]} 새 트리.
     */
    static putBoard(nodes: MenuEditNode[], board: MenuBoardOption): MenuEditNode[] {
        const folder = UtilMenuTree.all(nodes).find((node) => node.kind === 'FOLDER' && node.autoAdd);
        const item: MenuEditItem = { ...UtilMenu.newItem('BOARD'), targetId: board.id };
        const parentKey = folder && UtilMenu.canAddInside(nodes, folder.key, 'ITEM') ? folder.key : null;
        return UtilMenuTree.append(nodes, parentKey, item);
    }

    // ── 미리보기 ─

    /**
     * 보이는 대상이 이 사람을 들이는가.
     * @param {MenuAudience} audience - 보이는 대상.
     * @param {string | null} minGrade - `GRADE_MIN` 의 등급 키.
     * @param {MenuViewer} viewer - 보는 사람.
     * @param {MenuGradeOption[]} grades - 등급 목록.
     * @returns {boolean} 보이면 true.
     */
    static audienceAllows(audience: MenuAudience, minGrade: string | null, viewer: MenuViewer, grades: MenuGradeOption[]): boolean {
        switch (audience) {
            case 'ALL':
                return true;
            case 'GUEST':
                return viewer.kind === 'GUEST';
            case 'MEMBER':
                return viewer.kind !== 'GUEST';
            case 'STAFF':
                return viewer.kind === 'STAFF';
            case 'GRADE_MIN': {
                if (viewer.kind === 'STAFF') return true;
                if (viewer.kind === 'GUEST') return false;
                const level = grades.find((g) => g.gradeKey === minGrade)?.level;
                return level !== undefined && viewer.level >= level;
            }
        }
    }

    /**
     * 저장 전 트리를 보는 사람 기준으로 거른다 — 서버가 `GET /api/menu` 를 만들 때와 같은 차례:
     * ① 표시 여부(`visible`) → ② 보이는 대상 → ③ 항목 상태(꺼진 게시판·대상 없음·판매 꺼짐·모르는 유형, 로그인 필요 화면을 비로그인이 봄) → ④ 빈 폴더 제거(아래에서 위로).
     * @param {MenuEditNode[]} nodes - 트리.
     * @param {MenuViewer} viewer - 보는 사람.
     * @param {MenuContext} ctx - 게시판·등급·판매 스위치.
     * @returns {MenuPreviewNode[]} 보이는 트리.
     */
    static preview(nodes: MenuEditNode[], viewer: MenuViewer, ctx: MenuContext): MenuPreviewNode[] {
        const out: MenuPreviewNode[] = [];
        for (const node of nodes) {
            if (!node.visible) continue;
            if (!UtilMenu.audienceAllows(node.audience, node.minGrade, viewer, ctx.grades)) continue;
            if (node.kind === 'FOLDER') {
                const children = UtilMenu.preview(node.children, viewer, ctx);
                if (children.length) out.push({ node, children, linked: UtilMenu.folderLinkShown(node, viewer, ctx) });
                continue;
            }
            if (UtilMenu.itemStatus(node, ctx) !== 'OK') continue;
            if (node.targetType === 'BUILTIN' && viewer.kind === 'GUEST' && UtilMenu.builtinOf(node.targetKey)?.loginRequired) continue;
            out.push({ node, children: [] });
        }
        return out;
    }

    /**
     * 폴더 이름이 이 사람에게 링크로 보이는가 — 연결이 있고, 그 대상이 방문자에게 보이는 상태이고, 로그인 필요 화면을 비로그인이 보는 것이 아닐 때. 아니면 이름만(펼치기·접기) 보인다.
     * 폴더 자체는 연결과 상관없이 하위 항목이 있으면 보인다.
     * @param {MenuEditFolder} folder - 폴더.
     * @param {MenuViewer} viewer - 보는 사람.
     * @param {MenuContext} ctx - 게시판·판매 스위치.
     * @returns {boolean} 링크면 true.
     */
    static folderLinkShown(folder: MenuEditFolder, viewer: MenuViewer, ctx: MenuContext): boolean {
        const link = folder.link;
        if (!link || UtilMenu.itemStatus(link, ctx) !== 'OK') return false;
        return !(link.targetType === 'BUILTIN' && viewer.kind === 'GUEST' && UtilMenu.builtinOf(link.targetKey)?.loginRequired);
    }

    /**
     * 미리보기 결과의 모바일 상단 탭(깊이 우선 차례, `SIDE` 그룹만).
     * @param {MenuPreviewNode[]} nodes - 미리보기 결과.
     * @returns {MenuEditItem[]} 탭 항목.
     */
    static previewTabs(nodes: MenuPreviewNode[]): MenuEditItem[] {
        const out: MenuEditItem[] = [];
        const walk = (list: MenuPreviewNode[]) => {
            for (const entry of list) {
                if (entry.node.kind === 'ITEM' && entry.node.mobileTab) out.push(entry.node);
                walk(entry.children);
            }
        };
        walk(nodes);
        return out.slice(0, UtilMenu.LIMITS.mobileTabs);
    }

    // ── 메뉴 그룹 ─

    /**
     * 메뉴 그룹 코드 문제.
     * @param {string} value - 코드.
     * @param {string[]} taken - 이미 있는 코드.
     * @returns {'KEY_REQUIRED' | 'KEY_LENGTH' | 'KEY_FORMAT' | 'KEY_DUPLICATE' | null} 문제.
     */
    static setKeyProblem(value: string, taken: string[]): 'KEY_REQUIRED' | 'KEY_LENGTH' | 'KEY_FORMAT' | 'KEY_DUPLICATE' | null {
        const key = value.trim();
        if (!key) return 'KEY_REQUIRED';
        if (key.length < UtilMenu.SET_LIMITS.keyMin || key.length > UtilMenu.SET_LIMITS.keyMax) return 'KEY_LENGTH';
        if (!UtilMenu.SET_KEY_PATTERN.test(key)) return 'KEY_FORMAT';
        if (taken.includes(key)) return 'KEY_DUPLICATE';
        return null;
    }
}
