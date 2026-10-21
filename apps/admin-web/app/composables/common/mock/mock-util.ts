// MOCK: 목업 API 들(각 업무의 `api/mock/*-mock.ts`)이 함께 쓰는 작은 도구 — 개발 전용 스위치 읽기와 지연.
// 서버가 붙으면 각 업무의 목업 폴더(`composables/<업무>/api/mock/`)와 함께 지운다.

import { ApiError } from '~/composables/common/useApiClient';

/** 현재 주소의 `?mock=` 스위치들 — 쉼표로 여러 개(`?mock=site-error,profile-error`)이거나 `mock` 을 여러 번 써도 된다. 요청마다 읽는다(라우터가 쿼리를 떼도 유지된다) */
export function mockFlags(): Set<string> {
    const out = new Set<string>();
    if (typeof window === 'undefined') return out;
    for (const value of new URLSearchParams(window.location.search).getAll('mock')) {
        for (const name of value.split(',')) if (name.trim()) out.add(name.trim());
    }
    return out;
}

export function mockAborted(): ApiError {
    return new ApiError({ status: 0, code: 'ABORTED', message: '요청을 취소했어요.' });
}

/** 요청마다 300~800ms 기다린다. 화면이 끊은(abort) 요청은 기다리다 `ABORTED` 로 끝난다 */
export function mockDelay(signal?: AbortSignal): Promise<void> {
    const ms = 300 + Math.random() * 500;
    return new Promise((resolve, reject) => {
        if (signal?.aborted) return reject(mockAborted());
        const timer = setTimeout(() => {
            signal?.removeEventListener('abort', onAbort);
            resolve();
        }, ms);
        const onAbort = () => {
            clearTimeout(timer);
            reject(mockAborted());
        };
        signal?.addEventListener('abort', onAbort, { once: true });
    });
}

/** 서버 오류 모양(원본 `ApiError`) 중 목업들이 같이 쓰는 것 */
export const MOCK_ERRORS = {
    network: () => new ApiError({ status: 0, code: 'NETWORK', message: '서버에 연결하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요.' }),
    internal: () => new ApiError({ status: 500, code: 'INTERNAL', message: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.' }),
    unauthenticated: () => new ApiError({ status: 401, code: 'UNAUTHENTICATED', message: '로그인이 필요해요.' }),
};
