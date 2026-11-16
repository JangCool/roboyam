// MOCK: 게시판 포인트 등급(point-board-tier-contract) — 서버 `pointMode` 는 아직 `ON`·`OFF` 만 안다.
// 받을 때 `ON` 을 등급으로 읽고(이 탭에서 고른 등급이 있으면 그것, 없으면 일반), 보낼 때 등급을 `ON` 으로 바꿔 보낸다(새로 고치면 고른 등급은 일반으로 돌아간다).
// 서버가 `OFF|NORMAL|INFO|CORE` 를 받게 되면 이 파일과 `useBoardApi.ts` 의 `// MOCK:` 줄을 지운다.

import type { BoardDefaults, BoardDefaultsResponse, BoardDefaultsSaveRequest, BoardDetail, BoardPointMode, BoardSaveRequest } from '~/composables/board/api/useBoardApi.types';

/** 기본 설정의 열쇠 — 게시판 열쇠는 게시판 ID */
const DEFAULTS_KEY = '__defaults__';

/** 이 탭에서 저장한 등급(열쇠 → 등급) */
const remembered = new Map<string, BoardPointMode>();

/** 서버 값 → 화면 값. `ON` 은 저장해 둔 등급 또는 일반. `null`(기본값 따름)은 그대로 */
function read(value: string | null, key: string): BoardPointMode | null {
    if (value === null) return null;
    if (value === 'ON') return remembered.get(key) ?? 'NORMAL';
    return value as BoardPointMode;
}

/** 화면 값 → 서버 값(서버는 `ON`·`OFF`·`null` 만 안다). 등급은 기억해 두고 `ON` 으로 보낸다 */
function send(value: BoardPointMode | null, key: string): BoardPointMode | null {
    if (value === null || value === 'OFF') {
        remembered.delete(key);
        return value;
    }
    remembered.set(key, value);
    return 'ON' as BoardPointMode;
}

function readDefaults(defaults: BoardDefaults): BoardDefaults {
    return { ...defaults, pointMode: read(defaults.pointMode, DEFAULTS_KEY) ?? 'NORMAL' };
}

export function readDefaultsResponse(res: BoardDefaultsResponse): BoardDefaultsResponse {
    return { ...res, defaults: readDefaults(res.defaults) };
}

export function sendDefaults(body: BoardDefaultsSaveRequest): BoardDefaultsSaveRequest {
    return { ...body, pointMode: send(body.pointMode, DEFAULTS_KEY) ?? 'NORMAL' };
}

export function readDetail(detail: BoardDetail): BoardDetail {
    return { ...detail, policy: { ...detail.policy, pointMode: read(detail.policy.pointMode, detail.id) }, defaults: readDefaults(detail.defaults) };
}

export function sendDetail(id: string, body: BoardSaveRequest): BoardSaveRequest {
    return { ...body, policy: { ...body.policy, pointMode: send(body.policy.pointMode, id) } };
}
