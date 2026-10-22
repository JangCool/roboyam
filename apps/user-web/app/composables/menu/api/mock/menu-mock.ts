// MOCK: 메뉴 API(`GET /api/menu`)의 메모리 목업 — `useMenuApi.ts` 가 이 파일의 함수를 부른다. 서버가 붙으면 `menu/api/mock/` 와 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   menu-fail         메뉴(`GET /api/menu`) 요청 실패 → 게시판 목록으로 만든 기본 메뉴 + "다시 시도" 안내
//   menu-empty        메뉴가 비어 있음(`nodes: []`) → "보여 드릴 메뉴가 없어요" 자리

import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { MenuItemNode, MenuNode, MenuResponse } from '~/composables/menu/api/useMenuApi.types';

/** 메뉴에 거는 게시판 — 게시판 목업이 서버로 바뀌어(2026-10-22) 메뉴에 필요한 칸만 여기로 옮겼다. 값은 ROBOYAM 예시 기본 게시판(`install-presets/roboyam.json`)과 같다 */
interface MenuMockBoard {
    id: string;
    slug: string;
    name: string;
    nameEn: string | null;
    color: string | null;
    investmentNotice: boolean;
}

const MOCK_BOARDS: MenuMockBoard[] = [
    { id: 'mock-board-1', slug: 'ai', name: 'AI·테크', nameEn: 'AI & Tech', color: 'BLUE', investmentNotice: false },
    { id: 'mock-board-2', slug: 'robot', name: '로봇', nameEn: 'Robots', color: 'TEAL', investmentNotice: false },
    { id: 'mock-board-3', slug: 'stock', name: '산업·주식', nameEn: 'Industry & Stocks', color: 'AMBER', investmentNotice: true },
    { id: 'mock-board-4', slug: 'game', name: '게임', nameEn: null, color: 'PURPLE', investmentNotice: false },
    { id: 'mock-board-5', slug: 'sports', name: '스포츠', nameEn: null, color: 'GREEN', investmentNotice: false },
    { id: 'mock-board-6', slug: 'free', name: '자유게시판', nameEn: 'Free Board', color: null, investmentNotice: false },
    { id: 'mock-board-7', slug: 'notice', name: '공지', nameEn: null, color: null, investmentNotice: false },
];

function boardItem(b: MenuMockBoard): MenuItemNode {
    return {
        id: `mock-menu-${b.slug}`,
        kind: 'ITEM',
        type: 'BOARD',
        key: null,
        label: b.name,
        nameEn: b.nameEn ?? null,
        path: `/boards/${b.slug}`,
        matchPrefix: true,
        newTab: false,
        mobileTab: ['ai', 'robot', 'stock'].includes(b.slug),
        board: { id: b.id, slug: b.slug, color: b.color ?? null, investmentNotice: b.investmentNotice },
        extra: null,
    };
}

function builtin(id: string, key: string, label: string, path: string, matchPrefix: boolean, mobileTab: boolean, nameEn: string | null = null): MenuItemNode {
    return { id, kind: 'ITEM', type: 'BUILTIN', key, label, nameEn, path, matchPrefix, newTab: false, mobileTab, board: null, extra: null };
}

/** 서버가 준 모양의 메뉴 — 홈·공동구매(고정) → "주제 게시판" 그룹 → "게시판" 그룹 → 소모임(로그인하면 "내 소모임"도) */
export async function mockMenu(loggedIn: boolean, signal?: AbortSignal): Promise<MenuResponse> {
    await mockDelay(signal);
    const on = mockFlags();
    if (on.has('menu-fail')) throw MOCK_ERRORS.internal();
    if (on.has('menu-empty')) return { nodes: [], mobileTabs: [] };

    const topics = MOCK_BOARDS.filter((b) => ['ai', 'robot', 'stock', 'game', 'sports'].includes(b.slug));
    const rest = MOCK_BOARDS.filter((b) => ['free', 'notice'].includes(b.slug));
    const groupItems: MenuItemNode[] = [];
    if (loggedIn) groupItems.push(builtin('mock-menu-groups-mine', 'GROUPS_MINE', '내 소모임', '/groups/me', false, false, 'My groups'));
    groupItems.push(builtin('mock-menu-groups-all', 'GROUPS_ALL', '전체 소모임', '/groups', true, false, 'All groups'));

    const nodes: MenuNode[] = [
        builtin('mock-menu-home', 'HOME', '홈', '/', false, true, 'Home'),
        builtin('mock-menu-group-buys', 'GROUP_BUYS', '공동구매', '/group-buys', true, false, 'Group buys'),
        { id: 'mock-menu-topics', kind: 'GROUP', label: '주제 게시판', nameEn: 'Topic boards', collapsible: true, collapsed: false, children: topics.map(boardItem) },
        { id: 'mock-menu-boards', kind: 'GROUP', label: '게시판', collapsible: false, collapsed: false, children: rest.map(boardItem) },
        { id: 'mock-menu-groups', kind: 'GROUP', label: '소모임', nameEn: 'Groups', collapsible: false, collapsed: false, children: groupItems },
    ];
    return { nodes, mobileTabs: ['mock-menu-home', 'mock-menu-ai', 'mock-menu-robot', 'mock-menu-stock'] };
}
