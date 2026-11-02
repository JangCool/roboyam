// MOCK: 목업 API 들(각 업무의 `api/mock/*-mock.ts`)이 함께 쓰는 작은 도구 — 개발 전용 스위치 읽기와 지연. (user-web `composables/common/mock/mock-util.ts` 와 같은 모양)
// 서버가 붙으면 각 업무의 목업 폴더(`composables/<업무>/api/mock/`)와 함께 지운다(쓰는 목업이 하나도 없을 때 이 파일도).
// 지금 쓰는 목업: 광고·사업자 핫딜 일시 정지·중단(`bizapplication/api/mock/pause-mock.ts` · `ad/api/mock/pause-mock.ts` · `hotdeal/api/mock/pause-mock.ts`)
//   — 실제 API 위에 `?mock=pause-…` 일 때만 끼운다.

import { ApiError } from '~/composables/common/useApiClient';

/** 현재 주소의 `?mock=` 스위치들 — 쉼표로 여러 개(`?mock=pause-paused,pause-stopped`)이거나 `mock` 을 여러 번 써도 된다. 요청마다 읽는다 */
export function mockFlags(): Set<string> {
    const out = new Set<string>();
    if (typeof window === 'undefined') return out;
    for (const value of new URLSearchParams(window.location.search).getAll('mock')) {
        for (const name of value.split(',')) if (name.trim()) out.add(name.trim());
    }
    return out;
}

/** 다른 화면으로 가는 링크에 지금 `?mock=` 를 그대로 싣는다(목업 건끼리 오가게) — 스위치가 없으면 빈 객체 */
export function mockQuery(): Record<string, string> {
    const on = [...mockFlags()];
    return on.length ? { mock: on.join(',') } : {};
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
    conflict: (code: string, message: string) => new ApiError({ status: 409, code, message }),
};
