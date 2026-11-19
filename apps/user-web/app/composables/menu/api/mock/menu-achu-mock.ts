// MOCK: 메뉴에 「아추!」 항목(BUILTIN `ACHU` → `/achu`, 이름 모양 = 로고 글꼴 그림)을 끼워 넣는다 — `docs/features/achu-page-contract.md`.
// 서버(메뉴 항목 `nameArt`·BUILTIN `ACHU`)가 붙으면 이 파일과 `useMenu` 의 `// MOCK:` 줄을 지운다.
// `?mock=achu-text` 면 그림 없이 보통 글자로(그림 끈 항목 미리보기), `?mock=achu-off` 면 끼워 넣지 않는다.

import type { MenuItemNode, MenuNode } from '~/composables/menu/api/useMenuApi.types';
import { mockFlags } from '~/composables/common/mock/mock-util';
import UtilAchu from '~/utils/util-achu';

const MOCK_ID = 'mock-achu';

function achuItem(art: boolean): MenuItemNode {
    return {
        id: MOCK_ID,
        kind: 'ITEM',
        type: 'BUILTIN',
        key: 'ACHU',
        label: '아추!',
        nameEn: 'Achu!',
        path: '/achu',
        matchPrefix: false,
        newTab: false,
        mobileTab: true,
        shortcut: false,
        board: null,
        extra: null,
        divider: null,
        description: '아이로빗 추천 — 운영자가 고른 글',
        textColor: null,
        bold: false,
        badge: null,
        nameArt: art ? UtilAchu.TITLE_ART : null,
    };
}

/** 인기글(BUILTIN `POPULAR`) 바로 뒤에 넣는다 — 인기글이 없으면 최상위 끝. 이미 `ACHU` 가 있으면(서버가 붙은 뒤) 그대로 */
function insert(nodes: MenuNode[], item: MenuItemNode): boolean {
    const at = nodes.findIndex((n) => n.kind === 'ITEM' && n.key === 'POPULAR');
    if (at >= 0) {
        nodes.splice(at + 1, 0, item);
        return true;
    }
    for (const n of nodes) if (n.kind === 'GROUP' && insert(n.children, item)) return true;
    return false;
}

function hasAchu(nodes: readonly MenuNode[]): boolean {
    return nodes.some((n) => (n.kind === 'ITEM' ? n.key === 'ACHU' : hasAchu(n.children)));
}

/** 받은 메뉴 트리 + 모바일 탭 차례에 「아추!」를 끼운 사본 */
export function withAchuMock(nodes: MenuNode[], mobileTabs: string[]): { nodes: MenuNode[]; mobileTabs: string[] } {
    const flags = mockFlags();
    if (flags.has('achu-off') || hasAchu(nodes)) return { nodes, mobileTabs };
    const copy = nodes.map((n) => (n.kind === 'GROUP' ? { ...n, children: [...n.children] } : n));
    const item = achuItem(!flags.has('achu-text'));
    if (!insert(copy, item)) copy.push(item);
    // 모바일 상단 탭 — 인기글 탭 뒤(없으면 끝)
    const popular = findPopularId(nodes);
    const tabs = [...mobileTabs];
    const tabAt = popular ? tabs.indexOf(popular) : -1;
    tabs.splice(tabAt >= 0 ? tabAt + 1 : tabs.length, 0, MOCK_ID);
    return { nodes: copy, mobileTabs: tabs };
}

function findPopularId(nodes: readonly MenuNode[]): string | null {
    for (const n of nodes) {
        if (n.kind === 'ITEM' && n.key === 'POPULAR') return n.id;
        if (n.kind === 'GROUP') {
            const found = findPopularId(n.children);
            if (found) return found;
        }
    }
    return null;
}
