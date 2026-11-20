// MOCK: 본문·댓글 짤 허용(`postJjalAllowed`·`commentJjalAllowed`, my-jjal-contract 「게시판별 사용」) — 서버가 아직 칸을 주지 않는다.
// 받은 응답에 칸이 없으면 메모리 값(기본값 켬 · 게시판은 「기본값 따름」)으로 채우고, 저장하면 메모리에 기억한다(새로 고치면 처음 상태).
// 서버가 칸을 주면 이 파일과 `useBoardApi` 의 MOCK 줄을 지운다.

import type { BoardDefaults, BoardDefaultsResponse, BoardDefaultsSaveRequest, BoardDetail, BoardSaveRequest } from '~/composables/board/api/useBoardApi.types';

/** 목업이 채우는 칸 */
const FIELDS = ['postJjalAllowed', 'commentJjalAllowed'] as const;
type JjalField = (typeof FIELDS)[number];

const defaultValues: Record<JjalField, boolean> = { postJjalAllowed: true, commentJjalAllowed: true };
/** 게시판 ID → 칸 → 게시판 값(null = 기본값 따름) */
const boardValues = new Map<string, Partial<Record<JjalField, boolean | null>>>();

function fillDefaults(defaults: BoardDefaults): BoardDefaults {
    const out = { ...defaults };
    for (const field of FIELDS) out[field] = typeof defaults[field] === 'boolean' ? defaults[field] : defaultValues[field];
    return out;
}

export function mockFillJjalDefaults(response: BoardDefaultsResponse): BoardDefaultsResponse {
    return { ...response, defaults: fillDefaults(response.defaults) };
}

export function mockRememberJjalDefaults(body: BoardDefaultsSaveRequest): void {
    for (const field of FIELDS) defaultValues[field] = body[field];
}

export function mockFillJjalBoard(detail: BoardDetail): BoardDetail {
    const remembered = boardValues.get(detail.id) ?? {};
    const policy = { ...detail.policy };
    for (const field of FIELDS) {
        const server = detail.policy[field];
        policy[field] = typeof server === 'boolean' ? server : (remembered[field] ?? null);
    }
    return { ...detail, defaults: fillDefaults(detail.defaults), policy };
}

export function mockRememberJjalBoard(id: string, body: BoardSaveRequest): void {
    boardValues.set(id, { postJjalAllowed: body.policy.postJjalAllowed ?? null, commentJjalAllowed: body.policy.commentJjalAllowed ?? null });
}
