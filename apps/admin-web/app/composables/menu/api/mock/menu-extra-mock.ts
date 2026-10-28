// MOCK: 공개 사이트 메뉴 노드의 서버에 아직 없는 칸 목업 — 구분선(`divider`, 모든 노드)·항목 설명(`description`, 항목만). 서버 `GET/PUT /api/admin/menu-sets/{key}/tree` 에 아직 칸이 없다(사용자 결정 2026-10-28).
//   `useMenuApi.ts` 가 실제 응답의 노드에 저장해 둔 값(노드 ID → 칸 값)을 덧붙이고, `PUT` 요청에서는 이 칸들을 빼고 보낸다(모르는 칸으로 400 이 나지 않게).
//   새 노드(`id: null`)는 저장 응답의 같은 자리 노드 ID 로 이어 붙인다(트리 전체 교체라 응답이 요청과 같은 차례·모양이다).
// 메모리에만 있어 새로고침하면 모두 「없음」으로 돌아가고, 공개 사이트에는 전해지지 않는다. 서버가 칸을 주면 이 파일을 지운다.

import type { MenuDivider, MenuNodeDto, MenuTreeResponse } from '~/composables/menu/api/useMenuApi.types';

/** 노드 ID → 구분선(「없음」은 두지 않는다) */
const savedDivider = new Map<string, MenuDivider>();
/** 항목 노드 ID → 설명(빈 값은 두지 않는다) */
const savedDescription = new Map<string, string>();

function attach(nodes: MenuNodeDto[]): MenuNodeDto[] {
    return nodes.map((node) => {
        const divider = (node.id !== null && savedDivider.get(node.id)) || 'NONE';
        if (node.kind === 'FOLDER') return { ...node, divider, children: attach(node.children) };
        const description = (node.id !== null && savedDescription.get(node.id)) || null;
        return { ...node, divider, description };
    });
}

function strip(nodes: MenuNodeDto[]): MenuNodeDto[] {
    return nodes.map((node) => {
        if (node.kind === 'FOLDER') {
            const { divider: _divider, ...rest } = node;
            return { ...rest, children: strip(rest.children) };
        }
        const { divider: _divider, description: _description, ...rest } = node;
        return rest;
    }) as MenuNodeDto[];
}

function remember(sent: MenuNodeDto[], got: MenuNodeDto[]): void {
    sent.forEach((node, i) => {
        const back = got[i];
        if (!back?.id) return;
        if (node.divider && node.divider !== 'NONE') savedDivider.set(back.id, node.divider);
        else savedDivider.delete(back.id);
        if (node.kind === 'ITEM' && node.description) savedDescription.set(back.id, node.description);
        else savedDescription.delete(back.id);
        if (node.kind === 'FOLDER' && back.kind === 'FOLDER') remember(node.children, back.children);
    });
}

/**
 * 서버 트리 응답에 저장해 둔 구분선·설명을 덧붙인다.
 * @param {MenuTreeResponse} res - 서버 응답(두 칸 없음).
 * @returns {MenuTreeResponse} 노드마다 `divider`(항목은 `description` 도)가 있는 응답.
 */
export function mockAttachExtras(res: MenuTreeResponse): MenuTreeResponse {
    return { ...res, nodes: attach(res.nodes) };
}

/**
 * 저장 요청 트리에서 구분선·설명 칸을 뺀다(서버에 보낼 모양).
 * @param {MenuNodeDto[]} nodes - 화면이 만든 요청 트리.
 * @returns {MenuNodeDto[]} `divider`·`description` 이 없는 트리.
 */
export function mockStripExtras(nodes: MenuNodeDto[]): MenuNodeDto[] {
    return strip(nodes);
}

/**
 * 저장 응답을 받은 뒤 보낸 구분선·설명을 응답 노드 ID 에 기억하고, 응답에 덧붙여 돌려준다.
 * @param {MenuNodeDto[]} sent - 화면이 만든 요청 트리(두 칸 포함).
 * @param {MenuTreeResponse} res - 서버 저장 응답.
 * @returns {MenuTreeResponse} 두 칸을 덧붙인 응답.
 */
export function mockSaveExtras(sent: MenuNodeDto[], res: MenuTreeResponse): MenuTreeResponse {
    remember(sent, res.nodes);
    return mockAttachExtras(res);
}
