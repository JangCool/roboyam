// MOCK: 목업 API 들(각 업무의 `api/mock/*-mock.ts`)이 함께 쓰는 작은 도구 — 개발 전용 스위치 읽기와 지연.
// 서버가 붙으면 각 업무의 목업 폴더(`composables/<업무>/api/mock/`)와 함께 지운다(목업이 하나도 남지 않으면 이 파일도).
// 앞서 쓰던 같은 이름의 도구(서버 연결 때 지웠다)를 메일 템플릿 목업을 위해 되살렸다.

import { ApiError } from '~/composables/common/useApiClient';

/** 현재 주소의 `?mock=` 스위치들 — 쉼표로 여러 개(`?mock=a,b`)이거나 `mock` 을 여러 번 써도 된다. 요청마다 읽는다(라우터가 쿼리를 떼도 유지된다) */
export function mockFlags(): Set<string> {
    const out = new Set<string>();
    if (typeof window === 'undefined') return out;
    for (const value of new URLSearchParams(window.location.search).getAll('mock')) {
        for (const name of value.split(',')) if (name.trim()) out.add(name.trim());
    }
    return out;
}

/** 요청마다 200~500ms 기다린다 */
export function mockDelay(): Promise<void> {
    const ms = 200 + Math.random() * 300;
    return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 서버 오류 모양(원본 `ApiError`) 중 목업들이 같이 쓰는 것 */
export const MOCK_ERRORS = {
    internal: () => new ApiError({ status: 500, code: 'INTERNAL', message: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.' }),
    denied: () => new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' }),
};
