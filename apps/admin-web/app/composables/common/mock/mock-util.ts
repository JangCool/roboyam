// MOCK: 목업 API 들(각 업무의 `api/mock/*-mock.ts`)이 함께 쓰는 작은 도구 — 개발 전용 스위치 읽기와 지연. (user-web `composables/common/mock/mock-util.ts` 와 같은 모양)
// 서버가 붙으면 각 업무의 목업 폴더(`composables/<업무>/api/mock/`)와 함께 지운다(쓰는 목업이 하나도 없을 때 이 파일도).
// 지금 쓰는 목업: 광고 일정표(`ad/api/mock`). 사업자 심사·사업자 신청 건 목업은 서버 연결(V111·V112) 때 지웠고, 공구·커머스 설정 목업은 커머스 2b-4 에서 실제 API 로 바꿨다.

import { ApiError } from '~/composables/common/useApiClient';

/** 현재 주소의 `?mock=` 스위치들 — 쉼표로 여러 개(`?mock=gb-list-fail,orders-empty`)이거나 `mock` 을 여러 번 써도 된다. 요청마다 읽는다 */
export function mockFlags(): Set<string> {
    const out = new Set<string>();
    if (typeof window === 'undefined') return out;
    for (const value of new URLSearchParams(window.location.search).getAll('mock')) {
        for (const name of value.split(',')) if (name.trim()) out.add(name.trim());
    }
    return out;
}

/** 요청마다 300~700ms 기다린다 */
export function mockDelay(): Promise<void> {
    const ms = 300 + Math.random() * 400;
    return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 목업 응답을 깊은 사본으로 — 화면이 받은 값을 고쳐도 메모리 목업이 따라 바뀌지 않게 */
export function mockClone<T>(value: T): T {
    return JSON.parse(JSON.stringify(value)) as T;
}

/** 서버 오류 모양 중 목업들이 같이 쓰는 것(문구는 서버 몫이라 번역 키로 옮기지 않는다 — `i18n.md` 5절) */
export const MOCK_ERRORS = {
    internal: () => new ApiError({ status: 500, code: 'INTERNAL', message: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.' }),
    validation: (fields: Record<string, string>) => new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 값을 확인해 주세요.', fields }),
    forbidden: () => new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' }),
    notFound: (code: string, message: string) => new ApiError({ status: 404, code, message }),
    conflict: (code: string, message: string, extras?: Record<string, unknown>) => new ApiError({ status: 409, code, message, extras }),
};
