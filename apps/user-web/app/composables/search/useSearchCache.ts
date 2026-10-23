import type { SearchResponse } from '~/composables/search/api/useSearchApi.types';

/**
 * 방금 본 검색 결과 — 뒤로 가기로 돌아오면 스켈레톤 없이 바로 보여 주고 뒤에서 새로 받는다(검색 화면만 쓴다).
 * 페이지를 새로 열면 비어 있다(모듈 메모리). 최근 것 20개까지.
 */
const MAX_ENTRIES = 20;
const cache = new Map<string, SearchResponse>();

export function useSearchCache() {
    return {
        get: (key: string): SearchResponse | undefined => cache.get(key),
        set: (key: string, value: SearchResponse): void => {
            cache.delete(key); // 가장 최근에 쓴 것이 뒤로 가게
            cache.set(key, value);
            if (cache.size > MAX_ENTRIES) cache.delete(cache.keys().next().value as string);
        },
    };
}
