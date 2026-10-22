/**
 * 메뉴 트리 편집의 순수 함수 — 공개 사이트 메뉴(`util-menu.ts`)와 관리자 사이트 메뉴(`util-admin-menu-config.ts`)가 함께 쓴다.
 * 노드는 화면 열쇠(`key`, 저장 전 새 노드도 갖는 화면 전용 값)와 (담을 수 있는 노드면) `children` 배열만 있으면 된다. 담을 수 없는 노드(항목·화면)는 `children` 이 없다.
 * 모든 함수는 받은 트리를 고치지 않고 새 트리를 돌려준다. 어떤 자리에 놓을 수 있는지(단계·부모 종류)는 각 메뉴의 규칙이 정한다 — 여기는 옮기는 계산만 한다.
 */

/** 트리 노드의 최소 모양 */
export interface MenuTreeNodeLike {
    key: string;
    children?: MenuTreeNodeLike[];
}

/** 끌어 놓을 자리 — 그 줄의 앞 · 뒤 · (담을 수 있는 노드면) 안 맨 끝 */
export type MenuDropWhere = 'BEFORE' | 'AFTER' | 'INSIDE';

export interface MenuDropTarget {
    key: string;
    where: MenuDropWhere;
}

/** 키보드·줄 메뉴로 옮기기 — 위 · 아래 · 한 단계 밖(부모 바로 뒤) · 바로 위 폴더 안(맨 끝) */
export type MenuMoveOp = 'UP' | 'DOWN' | 'OUT' | 'IN';

/** 옮기기 한 번 — 키보드·줄 메뉴(`op`) 또는 끌어 놓기(`drop`) */
export type MenuMoveAction = { op: MenuMoveOp } | { drop: MenuDropTarget };

/** 트리에서 찾은 노드와 그 자리 */
export interface MenuTreeHit<T> {
    node: T;
    parent: T | null;
    siblings: T[];
    index: number;
    /** 최상위가 1 */
    depth: number;
}

/** 화면에 한 줄씩 늘어놓은 노드 */
export interface MenuTreeFlatRow<T> {
    node: T;
    depth: number;
    parentKey: string | null;
    /** 자기나 조상 중 하나라도 `hidden(node)` 이 참이다(숨김 폴더 안) */
    hiddenByAncestor: boolean;
}

/** 놓을 자리 — 부모(null 이면 최상위)와 그 안의 차례(옮길 노드를 뺀 뒤의 차례) */
interface MenuTreeSpot {
    parentKey: string | null;
    index: number;
}

let keySeq = 0;

export default class UtilMenuTree {
    /**
     * 화면 열쇠를 새로 만든다 — 서버 ID 가 없는 새 노드와 받은 노드 모두 이것으로 줄을 구분한다.
     * @returns {string} 화면 안에서 유일한 열쇠.
     */
    static newKey(): string {
        keySeq += 1;
        return `n${Date.now().toString(36)}${keySeq.toString(36)}`;
    }

    /**
     * 깊은 복사 — 노드는 평범한 데이터(문자열·숫자·불리언·배열·객체)만 담는다.
     * @param {T} value - 복사할 값.
     * @returns {T} 새 값.
     */
    static clone<T>(value: T): T {
        return JSON.parse(JSON.stringify(value)) as T;
    }

    /**
     * 열쇠로 노드를 찾는다.
     * @param {T[]} nodes - 트리.
     * @param {string} key - 찾을 열쇠.
     * @returns {MenuTreeHit<T> | null} 노드와 자리. 없으면 null.
     */
    static find<T extends MenuTreeNodeLike>(nodes: T[], key: string): MenuTreeHit<T> | null {
        const walk = (list: T[], parent: T | null, depth: number): MenuTreeHit<T> | null => {
            for (let i = 0; i < list.length; i++) {
                const node = list[i]!;
                if (node.key === key) return { node, parent, siblings: list, index: i, depth };
                if (node.children) {
                    const hit = walk(node.children as T[], node, depth + 1);
                    if (hit) return hit;
                }
            }
            return null;
        };
        return walk(nodes, null, 1);
    }

    /**
     * 트리를 위에서 아래 차례의 줄로 편다.
     * @param {T[]} nodes - 트리.
     * @param {(node: T) => boolean} [hidden] - 이 노드가 숨김인가(그 아래는 모두 `hiddenByAncestor`).
     * @returns {MenuTreeFlatRow<T>[]} 줄.
     */
    static flatten<T extends MenuTreeNodeLike>(nodes: T[], hidden: (node: T) => boolean = () => false): MenuTreeFlatRow<T>[] {
        const out: MenuTreeFlatRow<T>[] = [];
        const walk = (list: T[], depth: number, parentKey: string | null, parentHidden: boolean) => {
            for (const node of list) {
                out.push({ node, depth, parentKey, hiddenByAncestor: parentHidden });
                if (node.children) walk(node.children as T[], depth + 1, node.key, parentHidden || hidden(node));
            }
        };
        walk(nodes, 1, null, false);
        return out;
    }

    /**
     * 모든 노드(하위 포함)를 차례대로.
     * @param {T[]} nodes - 트리.
     * @returns {T[]} 노드.
     */
    static all<T extends MenuTreeNodeLike>(nodes: T[]): T[] {
        return UtilMenuTree.flatten(nodes).map((row) => row.node);
    }

    /**
     * 노드 수(하위 포함).
     * @param {T[]} nodes - 트리.
     * @returns {number} 수.
     */
    static count<T extends MenuTreeNodeLike>(nodes: T[]): number {
        return UtilMenuTree.all(nodes).length;
    }

    /**
     * 이 노드 아래(자기 제외)의 노드 수.
     * @param {T} node - 노드.
     * @returns {number} 하위 수.
     */
    static descendantCount<T extends MenuTreeNodeLike>(node: T): number {
        return node.children ? UtilMenuTree.count(node.children as T[]) : 0;
    }

    /**
     * 옮긴 결과 트리 — 놓을 수 없는 자리(자기 자신·자기 하위 안·끝을 넘음)면 null. 단계·부모 종류 규칙은 보지 않는다(부르는 쪽이 결과로 검사).
     * @param {T[]} nodes - 트리.
     * @param {string} key - 옮길 노드.
     * @param {MenuMoveAction} action - 옮기기.
     * @returns {T[] | null} 새 트리.
     */
    static move<T extends MenuTreeNodeLike>(nodes: T[], key: string, action: MenuMoveAction): T[] | null {
        const hit = UtilMenuTree.find(nodes, key);
        if (!hit) return null;
        let spot: MenuTreeSpot | null = null;
        const parentKey = hit.parent?.key ?? null;
        if ('op' in action) {
            switch (action.op) {
                case 'UP':
                    spot = hit.index > 0 ? { parentKey, index: hit.index - 1 } : null;
                    break;
                case 'DOWN':
                    spot = hit.index < hit.siblings.length - 1 ? { parentKey, index: hit.index + 1 } : null;
                    break;
                case 'OUT': {
                    if (!hit.parent) break;
                    const up = UtilMenuTree.find(nodes, hit.parent.key);
                    if (up) spot = { parentKey: up.parent?.key ?? null, index: up.index + 1 };
                    break;
                }
                case 'IN': {
                    const prev = hit.index > 0 ? hit.siblings[hit.index - 1] : undefined;
                    if (prev?.children) spot = { parentKey: prev.key, index: prev.children.length };
                    break;
                }
            }
            if (!spot) return null;
        } else {
            const target = action.drop;
            if (target.key === key) return null;
            // 자기 하위 안에는 놓을 수 없다
            if (UtilMenuTree.find(hit.node.children ? (hit.node.children as T[]) : [], target.key)) return null;
        }

        const copy = UtilMenuTree.clone(nodes);
        const own = UtilMenuTree.find(copy, key)!;
        const [moved] = own.siblings.splice(own.index, 1);
        if (!moved) return null;

        if ('drop' in action) {
            const target = UtilMenuTree.find(copy, action.drop.key);
            if (!target) return null;
            if (action.drop.where === 'INSIDE') {
                if (!target.node.children) return null;
                spot = { parentKey: target.node.key, index: target.node.children.length };
            } else {
                spot = { parentKey: target.parent?.key ?? null, index: target.index + (action.drop.where === 'AFTER' ? 1 : 0) };
            }
        }
        if (!spot) return null;
        const list = spot.parentKey === null ? copy : (UtilMenuTree.find(copy, spot.parentKey)?.node.children as T[] | undefined);
        if (!list) return null;
        list.splice(Math.min(spot.index, list.length), 0, moved);
        return copy;
    }

    /**
     * 노드를 지운 트리(하위 포함).
     * @param {T[]} nodes - 트리.
     * @param {string} key - 지울 노드.
     * @returns {T[]} 새 트리.
     */
    static remove<T extends MenuTreeNodeLike>(nodes: T[], key: string): T[] {
        const copy = UtilMenuTree.clone(nodes);
        const hit = UtilMenuTree.find(copy, key);
        if (hit) hit.siblings.splice(hit.index, 1);
        return copy;
    }

    /**
     * 노드를 부모(null 이면 최상위)의 맨 끝에 넣은 트리.
     * @param {T[]} nodes - 트리.
     * @param {string | null} parentKey - 부모.
     * @param {T} node - 넣을 노드.
     * @returns {T[]} 새 트리. 부모가 없거나 담을 수 없으면 최상위 끝.
     */
    static append<T extends MenuTreeNodeLike>(nodes: T[], parentKey: string | null, node: T): T[] {
        const copy = UtilMenuTree.clone(nodes);
        const parent = parentKey === null ? null : UtilMenuTree.find(copy, parentKey);
        const list = parent?.node.children ? (parent.node.children as T[]) : copy;
        list.push(UtilMenuTree.clone(node));
        return copy;
    }

    /**
     * 노드 하나를 바꾼 트리 — `patch` 의 칸만 덮는다(하위는 그대로).
     * @param {T[]} nodes - 트리.
     * @param {string} key - 바꿀 노드.
     * @param {Partial<T>} patch - 바꿀 칸.
     * @returns {T[]} 새 트리.
     */
    static patch<T extends MenuTreeNodeLike>(nodes: T[], key: string, patch: Partial<T>): T[] {
        const copy = UtilMenuTree.clone(nodes);
        const hit = UtilMenuTree.find(copy, key);
        if (hit) Object.assign(hit.node, UtilMenuTree.clone(patch));
        return copy;
    }

    /**
     * 노드가 놓인 단계와, 그 아래 가장 깊은 노드까지의 높이(자기 포함 1).
     * @param {T} node - 노드.
     * @returns {number} 높이.
     */
    static height<T extends MenuTreeNodeLike>(node: T): number {
        if (!node.children?.length) return 1;
        return 1 + Math.max(...node.children.map((child) => UtilMenuTree.height(child)));
    }

    /**
     * 서버 요청의 노드 경로(`nodes[1].children[2]`) → 화면 열쇠. 서버 입력 오류(`fields` 의 `nodes[1].children[2].label`)를 줄에 붙일 때 쓴다.
     * @param {T[]} nodes - 요청과 같은 차례의 트리.
     * @param {string} [root] - 경로의 첫 이름(기본 `nodes`).
     * @returns {Map<string, string>} 경로 → 열쇠.
     */
    static pathIndex<T extends MenuTreeNodeLike>(nodes: T[], root = 'nodes'): Map<string, string> {
        const out = new Map<string, string>();
        const walk = (list: T[], prefix: string) => {
            list.forEach((node, i) => {
                const path = `${prefix}[${i}]`;
                out.set(path, node.key);
                if (node.children) walk(node.children as T[], `${path}.children`);
            });
        };
        walk(nodes, root);
        return out;
    }

    /**
     * 서버 입력 오류를 줄별로 나눈다 — 경로가 줄에 닿으면 `rows[열쇠][칸]`, 닿지 않으면 `form` 에 모은다.
     * @param {Record<string, string>} fields - 서버 `fields`.
     * @param {Map<string, string>} index - `pathIndex` 의 결과(여러 트리면 합친 것).
     * @returns {{ rows: Record<string, Record<string, string>>; form: string[] }} 나눈 오류.
     */
    static splitFieldErrors(fields: Record<string, string>, index: Map<string, string>): { rows: Record<string, Record<string, string>>; form: string[] } {
        const rows: Record<string, Record<string, string>> = {};
        const form: string[] = [];
        for (const [name, message] of Object.entries(fields)) {
            // 가장 긴 노드 경로(`…]` 로 끝나는 앞부분)를 찾는다 — `nodes[0].settings.path` 의 칸은 `settings.path`
            let hit: { key: string; field: string } | null = null;
            for (let end = name.lastIndexOf(']'); end > 0; end = name.lastIndexOf(']', end - 1)) {
                const key = index.get(name.slice(0, end + 1));
                if (key) {
                    hit = { key, field: name.slice(end + 2) || 'node' };
                    break;
                }
            }
            if (hit) rows[hit.key] = { ...(rows[hit.key] ?? {}), [hit.field]: message };
            else form.push(message);
        }
        return { rows, form };
    }
}
