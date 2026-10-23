/**
 * 최근 방문한 게시판 — **이 기기(브라우저)에만** 5개까지 남긴다. 게시판 목록 화면(`/boards/{slug}`)이 열릴 때 하나 더하고, 게시판 모음(`/boards`)이 읽는다.
 * 저장이 막혀 있어도 이번 방문 동안은 메모리(`useState`)에 남는다.
 */
const KEY = 'roboyam:recent-boards';
const MAX = 5;

function read(): string[] {
    try {
        const raw = window.localStorage.getItem(KEY);
        const parsed: unknown = raw ? JSON.parse(raw) : [];
        return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string').slice(0, MAX) : [];
    } catch {
        return [];
    }
}

export function useRecentBoards() {
    const slugs = useState<string[]>('recent-boards', () => []);

    /** 저장해 둔 것을 읽는다 — 화면이 뜬 뒤(`onMounted`) 부른다 */
    function load(): void {
        if (typeof window !== 'undefined') slugs.value = read();
    }

    /** 게시판 하나를 맨 앞에 더한다 — 저장된 것을 먼저 읽어 이전 방문을 지우지 않는다 */
    function record(slug: string): void {
        if (!slug) return;
        const saved = typeof window === 'undefined' ? slugs.value : read();
        slugs.value = [slug, ...saved.filter((s) => s !== slug)].slice(0, MAX);
        try {
            window.localStorage.setItem(KEY, JSON.stringify(slugs.value));
        } catch {
            /* 저장이 막혀 있어도 이번 방문 동안은 메모리에 남는다 */
        }
    }

    return { slugs, load, record };
}
