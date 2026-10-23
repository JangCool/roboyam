import type { AdminMenuConfigKind, AdminMenuConfigNodeDto, AdminMenuTargetType } from '~/composables/menu/api/useMenuApi.types';
import type { AdminMenuItem, AdminMenuMode } from '~/utils/util-admin-menu';
import UtilAdminMenu from '~/utils/util-admin-menu';
import type { MenuMoveAction } from '~/utils/util-menu-tree';
import UtilMenuTree from '~/utils/util-menu-tree';
import type { MenuProblemCode } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';

/**
 * 관리자 사이트 메뉴 구성(메뉴 관리 › 관리자 메뉴)의 규칙 — 화면 레지스트리 · 시드 · 구조 규칙 · 검증 · 미연결 화면 · 미리보기 거르기. 순수 함수만 둔다.
 * 구조는 지금 관리자 메뉴(`util-admin-menu.ts`)와 같다: 묶음(`SECTION`, 최상위만) → 화면 항목(`SCREEN`) 또는 부모 항목(`FOLDER`, 최상위나 묶음 안) → 하위 화면 항목(부모 안, 아이콘 없음).
 * 권한은 편집하지 않는다 — 화면 항목의 권한은 화면 레지스트리(메뉴 정의의 `permission`)가 정하고, 외부 링크만 선택적으로 권한 하나를 고른다.
 * 서버(`AdminMenuService`·`AdminScreenRegistry`)가 같은 구조 규칙으로 판정하고 구성을 저장한다. ⚠ 셸(사이드바)은 아직 저장한 구성이 아니라 `UtilAdminMenu.ITEMS` 를 읽는다(menu-contract 미확정 1).
 */

/** 화면 편집 노드 */
export interface AdminMenuEditNode {
    /** 화면 열쇠 — 저장하지 않는다 */
    key: string;
    id: string | null;
    kind: AdminMenuConfigKind;
    labelKey: string | null;
    label: string;
    labelEn: string;
    icon: string;
    visible: boolean;
    targetType: AdminMenuTargetType | null;
    targetKey: string | null;
    href: string;
    newTab: boolean;
    permission: string | null;
    /** 묶음·부모 항목만 */
    children?: AdminMenuEditNode[];
}

export type AdminMenuEditModes = Record<AdminMenuMode, AdminMenuEditNode[]>;

/** 관리자 화면 하나 — `UtilAdminMenu.ITEMS` 의 잎 항목에서 만든다 */
export interface AdminScreenDef {
    key: string;
    labelKey: string;
    to: string;
    permission: string | null;
    /** 최고 관리자(루트)만 보는 화면(메뉴 정의의 `superOnly`) — 권한 칸 대신 `UtilAdminMenu.SUPER_ONLY` 로 다룬다 */
    superOnly: boolean;
    /** 메뉴 정의의 아이콘(하위 항목은 없다) */
    icon: string | null;
    /** 메뉴 정의에서 속한 모드 */
    mode: AdminMenuMode;
    /** 메뉴 정의에서 속한 묶음의 이름 키(낱개 항목이면 null) */
    sectionLabelKey: string | null;
}

export type AdminMenuProblemField = 'label' | 'labelEn' | 'icon' | 'target' | 'href';
export type AdminMenuProblemCode = MenuProblemCode | 'ICON_FORMAT';

export interface AdminMenuProblems {
    rows: Record<string, Partial<Record<AdminMenuProblemField, AdminMenuProblemCode>>>;
}

/** 옮기기가 막힌 이유 */
export type AdminMenuMoveProblem = 'NOWHERE' | 'SECTION_TOP' | 'FOLDER_PLACE';

export interface AdminMenuPreviewNode {
    node: AdminMenuEditNode;
    children: AdminMenuPreviewNode[];
}

export default class UtilAdminMenuConfig {
    static readonly MODES: readonly AdminMenuMode[] = ['normal', 'settings'];

    static readonly LABEL_MAX = 20;

    static readonly ICON_PATTERN = /^pi pi-[a-z0-9-]+$/;

    /** 아이콘 고르기의 자주 쓰는 아이콘(직접 입력도 된다) */
    static readonly COMMON_ICONS: readonly string[] = [
        'pi pi-home',
        'pi pi-users',
        'pi pi-user',
        'pi pi-list',
        'pi pi-th-large',
        'pi pi-bars',
        'pi pi-cog',
        'pi pi-sliders-h',
        'pi pi-bell',
        'pi pi-send',
        'pi pi-wallet',
        'pi pi-chart-bar',
        'pi pi-shield',
        'pi pi-history',
        'pi pi-book',
        'pi pi-globe',
        'pi pi-desktop',
        'pi pi-external-link',
        'pi pi-star',
        'pi pi-folder',
    ];

    /**
     * 관리자 화면 레지스트리 — `UtilAdminMenu.ITEMS` 의 모든 잎 화면(부모 항목은 빠지고 그 하위가 들어간다). 메뉴 정의에 한 줄 더하면 여기에도 생긴다(서버 짝 `AdminScreenRegistry` 에도 같은 한 줄 — 저장한 적 없을 때의 기본 구성도 서버가 그 레지스트리로 만든다).
     * @returns {AdminScreenDef[]} 메뉴 정의 차례.
     */
    static screens(): AdminScreenDef[] {
        const out: AdminScreenDef[] = [];
        const add = (items: readonly AdminMenuItem[], mode: AdminMenuMode, sectionLabelKey: string | null) => {
            for (const leaf of UtilAdminMenu.leaves(items)) {
                out.push({ key: leaf.key, labelKey: leaf.labelKey, to: leaf.to, permission: leaf.permission ?? null, superOnly: leaf.superOnly === true, icon: leaf.icon ?? null, mode, sectionLabelKey });
            }
        };
        for (const node of UtilAdminMenu.ITEMS) {
            const mode = UtilAdminMenu.modeOf(node);
            if (UtilAdminMenu.isGroup(node)) add(node.items, mode, node.labelKey);
            else add([node], mode, null);
        }
        return out;
    }

    /**
     * 화면 하나.
     * @param {string | null} key - 화면 키.
     * @returns {AdminScreenDef | undefined} 정의. 없으면 undefined.
     */
    static screenOf(key: string | null): AdminScreenDef | undefined {
        return key ? UtilAdminMenuConfig.screens().find((s) => s.key === key) : undefined;
    }

    // ── 받기 · 보내기 ─

    static toEdit(nodes: AdminMenuConfigNodeDto[]): AdminMenuEditNode[] {
        return nodes.map((node) => {
            const out: AdminMenuEditNode = {
                key: UtilMenuTree.newKey(),
                id: node.id,
                kind: node.kind,
                labelKey: node.labelKey,
                label: node.label ?? '',
                labelEn: node.labelEn ?? '',
                icon: node.icon ?? '',
                visible: node.visible,
                targetType: node.targetType,
                targetKey: node.targetKey,
                href: node.settings?.href ?? '',
                newTab: node.settings?.newTab ?? true,
                permission: node.permission,
            };
            if (node.kind !== 'SCREEN') out.children = UtilAdminMenuConfig.toEdit(node.children ?? []);
            return out;
        });
    }

    /**
     * 화면 편집 트리 → 서버 트리. 부모 항목 안의 하위 화면은 아이콘을 비워 보낸다.
     * @param {AdminMenuEditNode[]} nodes - 편집 트리.
     * @param {boolean} [underFolder] - 부모 항목 안인가.
     * @returns {AdminMenuConfigNodeDto[]} 서버 트리.
     */
    static toDto(nodes: AdminMenuEditNode[], underFolder = false): AdminMenuConfigNodeDto[] {
        return nodes.map((node) => ({
            id: node.id,
            kind: node.kind,
            labelKey: node.labelKey,
            label: node.label.trim() || null,
            labelEn: node.labelEn.trim() || null,
            icon: underFolder ? null : node.icon.trim() || null,
            visible: node.visible,
            targetType: node.kind === 'SCREEN' ? node.targetType : null,
            targetKey: node.kind === 'SCREEN' && node.targetType === 'ADMIN_SCREEN' ? node.targetKey : null,
            settings: node.kind === 'SCREEN' && node.targetType === 'URL' ? { href: node.href.trim(), newTab: node.newTab } : null,
            permission: node.kind === 'SCREEN' && node.targetType === 'URL' ? node.permission : null,
            children: node.children ? UtilAdminMenuConfig.toDto(node.children, node.kind === 'FOLDER') : null,
        }));
    }

    /**
     * 새 노드.
     * @param {AdminMenuConfigKind} kind - 종류.
     * @param {AdminMenuTargetType | null} [targetType] - 화면 항목의 연결 유형.
     * @returns {AdminMenuEditNode} 빈 노드.
     */
    static newNode(kind: AdminMenuConfigKind, targetType: AdminMenuTargetType | null = null): AdminMenuEditNode {
        const node: AdminMenuEditNode = {
            key: UtilMenuTree.newKey(),
            id: null,
            kind,
            labelKey: null,
            label: '',
            labelEn: '',
            icon: kind === 'SCREEN' && targetType === 'URL' ? 'pi pi-external-link' : '',
            visible: true,
            targetType: kind === 'SCREEN' ? targetType : null,
            targetKey: null,
            href: targetType === 'URL' ? 'https://' : '',
            newTab: true,
            permission: null,
        };
        if (kind !== 'SCREEN') node.children = [];
        return node;
    }

    // ── 구조 ─

    /**
     * 구조 문제 — 묶음은 최상위만, 부모 항목은 최상위나 묶음 안만(그래서 부모 안에는 화면 항목만 들어간다).
     * @param {AdminMenuEditNode[]} nodes - 한 모드의 트리.
     * @returns {AdminMenuMoveProblem | null} 문제.
     */
    static structureProblem(nodes: AdminMenuEditNode[]): AdminMenuMoveProblem | null {
        for (const row of UtilMenuTree.flatten(nodes)) {
            if (row.node.kind === 'SECTION' && row.depth > 1) return 'SECTION_TOP';
            if (row.node.kind === 'FOLDER' && row.parentKey !== null) {
                const parent = UtilMenuTree.find(nodes, row.parentKey)?.node;
                if (parent?.kind !== 'SECTION') return 'FOLDER_PLACE';
            }
        }
        return null;
    }

    /**
     * 옮기기를 해 보고 결과 트리 또는 막힌 이유를 준다.
     * @param {AdminMenuEditNode[]} nodes - 한 모드의 트리.
     * @param {string} key - 옮길 노드.
     * @param {MenuMoveAction} action - 옮기기.
     * @returns {{ tree: AdminMenuEditNode[] | null; problem: AdminMenuMoveProblem | null }} 결과.
     */
    static tryMove(nodes: AdminMenuEditNode[], key: string, action: MenuMoveAction): { tree: AdminMenuEditNode[] | null; problem: AdminMenuMoveProblem | null } {
        const tree = UtilMenuTree.move(nodes, key, action);
        if (!tree) return { tree: null, problem: 'NOWHERE' };
        const problem = UtilAdminMenuConfig.structureProblem(tree);
        return problem ? { tree: null, problem } : { tree, problem: null };
    }

    /**
     * 이 부모 안에 이 종류를 넣을 수 있는가.
     * @param {AdminMenuEditNode | null} parent - 부모(null 이면 최상위).
     * @param {AdminMenuConfigKind} kind - 넣을 종류.
     * @returns {boolean} 넣을 수 있으면 true.
     */
    static canContain(parent: AdminMenuEditNode | null, kind: AdminMenuConfigKind): boolean {
        if (!parent) return true;
        if (parent.kind === 'SECTION') return kind !== 'SECTION';
        if (parent.kind === 'FOLDER') return kind === 'SCREEN';
        return false;
    }

    /**
     * 부모 항목 안의 하위 화면인가(아이콘 없음).
     * @param {AdminMenuEditNode[]} nodes - 한 모드의 트리.
     * @param {string} key - 노드.
     * @returns {boolean} 부모가 `FOLDER` 면 true.
     */
    static isSubItem(nodes: AdminMenuEditNode[], key: string): boolean {
        return UtilMenuTree.find(nodes, key)?.parent?.kind === 'FOLDER';
    }

    // ── 검증 ─

    /**
     * 두 모드 전체 검증 — 이름(묶음·부모·외부 링크는 필수, 화면 항목은 비우면 기본 이름) · 영문명 · 아이콘 형식 · 외부 링크 주소 · 한 화면은 두 모드 통틀어 한 번.
     * @param {AdminMenuEditModes} modes - 두 모드.
     * @returns {AdminMenuProblems} 줄별 문제.
     */
    static validate(modes: AdminMenuEditModes): AdminMenuProblems {
        const rows: AdminMenuProblems['rows'] = {};
        const seen = new Set<string>();
        for (const mode of UtilAdminMenuConfig.MODES) {
            for (const row of UtilMenuTree.flatten(modes[mode])) {
                const node = row.node;
                const out: Partial<Record<AdminMenuProblemField, AdminMenuProblemCode>> = {};
                const required = node.kind === 'SCREEN' ? node.targetType === 'URL' : node.labelKey === null;
                const label = UtilMenu.labelProblem(node.label, required, UtilAdminMenuConfig.LABEL_MAX);
                if (label) out.label = label;
                const labelEn = UtilMenu.nameEnProblem(node.labelEn);
                if (labelEn) out.labelEn = labelEn;
                if (node.icon.trim() && !UtilAdminMenuConfig.ICON_PATTERN.test(node.icon.trim())) out.icon = 'ICON_FORMAT';
                if (node.kind === 'SCREEN' && node.targetType === 'ADMIN_SCREEN') {
                    if (!node.targetKey) out.target = 'TARGET_REQUIRED';
                    else if (seen.has(node.targetKey)) out.target = 'TARGET_DUPLICATE';
                    if (node.targetKey) seen.add(node.targetKey);
                }
                if (node.kind === 'SCREEN' && node.targetType === 'URL') {
                    const href = UtilMenu.hrefProblem(node.href);
                    if (href) out.href = href;
                }
                if (Object.keys(out).length) rows[node.key] = out;
            }
        }
        return { rows };
    }

    /**
     * 메뉴에 이미 있는 화면 키(두 모드 통틀어).
     * @param {AdminMenuEditModes} modes - 두 모드.
     * @returns {Map<string, AdminMenuMode>} 화면 키 → 모드.
     */
    static usedScreens(modes: AdminMenuEditModes): Map<string, AdminMenuMode> {
        const out = new Map<string, AdminMenuMode>();
        for (const mode of UtilAdminMenuConfig.MODES) {
            for (const node of UtilMenuTree.all(modes[mode])) {
                if (node.kind === 'SCREEN' && node.targetType === 'ADMIN_SCREEN' && node.targetKey && !out.has(node.targetKey)) out.set(node.targetKey, mode);
            }
        }
        return out;
    }

    /**
     * 메뉴에 없는 화면 — 레지스트리 화면 중 어느 모드에도 없는 것.
     * @param {AdminMenuEditModes} modes - 두 모드.
     * @returns {AdminScreenDef[]} 레지스트리 차례.
     */
    static unlinkedScreens(modes: AdminMenuEditModes): AdminScreenDef[] {
        const used = UtilAdminMenuConfig.usedScreens(modes);
        return UtilAdminMenuConfig.screens().filter((screen) => !used.has(screen.key));
    }

    /**
     * 화면을 메뉴에 넣는다 — 메뉴 정의에서 속했던 모드의, 같은 이름 키의 묶음이 있으면 그 끝, 없으면 그 모드 최상위 끝.
     * @param {AdminMenuEditModes} modes - 두 모드.
     * @param {AdminScreenDef} screen - 화면.
     * @returns {AdminMenuEditModes} 새 구성.
     */
    static putScreen(modes: AdminMenuEditModes, screen: AdminScreenDef): AdminMenuEditModes {
        const node: AdminMenuEditNode = { ...UtilAdminMenuConfig.newNode('SCREEN', 'ADMIN_SCREEN'), targetKey: screen.key, icon: screen.icon ?? '' };
        const tree = modes[screen.mode];
        const section = screen.sectionLabelKey ? tree.find((n) => n.kind === 'SECTION' && n.labelKey === screen.sectionLabelKey) : undefined;
        return { ...modes, [screen.mode]: UtilMenuTree.append(tree, section?.key ?? null, node) };
    }

    /**
     * 최상위 노드를 다른 모드의 최상위 끝으로 옮긴다.
     * @param {AdminMenuEditModes} modes - 두 모드.
     * @param {AdminMenuMode} from - 지금 모드.
     * @param {string} key - 최상위 노드.
     * @returns {AdminMenuEditModes} 새 구성.
     */
    static switchMode(modes: AdminMenuEditModes, from: AdminMenuMode, key: string): AdminMenuEditModes {
        const to: AdminMenuMode = from === 'normal' ? 'settings' : 'normal';
        const hit = UtilMenuTree.find(modes[from], key);
        if (!hit || hit.parent) return modes;
        return { ...modes, [from]: UtilMenuTree.remove(modes[from], key), [to]: UtilMenuTree.append(modes[to], null, hit.node) } as AdminMenuEditModes;
    }

    // ── 표시 · 미리보기 ─

    /**
     * 노드가 걸린 권한 — 관리자 화면은 레지스트리의 권한(최고 관리자 전용 화면은 예약 코드 `UtilAdminMenu.SUPER_ONLY`), 외부 링크는 고른 권한. 묶음·부모는 없음.
     * @param {AdminMenuEditNode} node - 노드.
     * @returns {string | null} 권한 코드.
     */
    static permissionOf(node: AdminMenuEditNode): string | null {
        if (node.kind !== 'SCREEN') return null;
        if (node.targetType === 'URL') return node.permission;
        const screen = UtilAdminMenuConfig.screenOf(node.targetKey);
        if (screen?.superOnly) return UtilAdminMenu.SUPER_ONLY;
        return screen?.permission ?? null;
    }

    /**
     * 기본 이름의 i18n 키 — 묶음·부모는 `labelKey`, 관리자 화면은 화면의 이름. 외부 링크·운영자가 만든 묶음은 없다.
     * @param {AdminMenuEditNode} node - 노드.
     * @returns {string | null} 키.
     */
    static defaultLabelKey(node: AdminMenuEditNode): string | null {
        if (node.kind === 'SCREEN') return node.targetType === 'ADMIN_SCREEN' ? (UtilAdminMenuConfig.screenOf(node.targetKey)?.labelKey ?? null) : null;
        return node.labelKey;
    }

    /**
     * 이 권한을 가진 운영자가 보는 메뉴 — 숨김 · 권한 없음을 빼고, 비게 된 묶음·부모 항목을 뺀다(`UtilAdminMenu.visible` 과 같은 규칙).
     * @param {AdminMenuEditNode[]} nodes - 한 모드의 트리.
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @returns {AdminMenuPreviewNode[]} 보이는 트리.
     */
    static preview(nodes: AdminMenuEditNode[], can: (code: string) => boolean): AdminMenuPreviewNode[] {
        const out: AdminMenuPreviewNode[] = [];
        for (const node of nodes) {
            if (!node.visible) continue;
            if (node.kind === 'SCREEN') {
                if (node.targetType === 'ADMIN_SCREEN' && !UtilAdminMenuConfig.screenOf(node.targetKey)) continue;
                const permission = UtilAdminMenuConfig.permissionOf(node);
                if (permission && !can(permission)) continue;
                out.push({ node, children: [] });
                continue;
            }
            const children = UtilAdminMenuConfig.preview(node.children ?? [], can);
            if (children.length) out.push({ node, children });
        }
        return out;
    }
}
