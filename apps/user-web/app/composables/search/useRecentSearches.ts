import UtilSearchQuery from '~/utils/util-search-query';

/**
 * 최근 검색어 — **이 기기(브라우저)에만** 남긴다(서버에 보내지 않는다). 검색 화면이 실제로 검색해 결과를 받았을 때 하나 더한다.
 * 저장이 막혀 있어도(사생활 보호 창 등) 이번 방문 동안은 메모리(`useState`)에 남는다.
 */
const KEY = 'roboyam:recent-searches';

function read(): string[] | null {
    try {
        const raw = window.localStorage.getItem(KEY);
        return UtilSearchQuery.sanitizeRecent(raw ? (JSON.parse(raw) as unknown) : []);
    } catch {
        return null;
    }
}

function write(list: readonly string[]): void {
    try {
        if (list.length) window.localStorage.setItem(KEY, JSON.stringify(list));
        else window.localStorage.removeItem(KEY);
    } catch {
        /* 저장이 막혀 있어도 이번 방문 동안은 메모리에 남는다 */
    }
}

export function useRecentSearches() {
    const terms = useState<string[]>('recent-searches', () => []);

    /** 저장해 둔 것을 읽는다 — 화면이 뜬 뒤(`onMounted`) 부른다 */
    function load(): void {
        if (typeof window === 'undefined') return;
        const saved = read();
        if (saved) terms.value = saved;
    }

    /** 다른 탭에서 바뀐 것까지 읽은 지금 목록 */
    function current(): string[] {
        return (typeof window === 'undefined' ? null : read()) ?? terms.value;
    }

    function record(q: string): void {
        terms.value = UtilSearchQuery.recordRecent(current(), q);
        write(terms.value);
    }

    function remove(q: string): void {
        terms.value = current().filter((v) => v !== q);
        write(terms.value);
    }

    function clear(): void {
        terms.value = [];
        write([]);
    }

    return { terms, load, record, remove, clear };
}
