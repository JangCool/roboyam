/**
 * 고르기 창 「이모지」 탭의 최근 사용 — 이 기기(localStorage)에만 남긴다(서버에 보내지 않는다). 저장소를 못 쓰면(사생활 창 등) 이 화면 동안만 기억한다.
 */
const STORAGE_KEY = 'irobit.emoji.recent';
const MAX = 24;

export function useRecentEmoji() {
    const recent = useState<string[] | undefined>('emoji-recent', () => undefined);

    function read(): string[] {
        try {
            const raw = window.localStorage.getItem(STORAGE_KEY);
            const parsed: unknown = raw ? JSON.parse(raw) : [];
            return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string').slice(0, MAX) : [];
        } catch {
            return [];
        }
    }

    /** 처음 볼 때 한 번 읽는다 */
    function ensureLoaded(): void {
        if (recent.value === undefined) recent.value = typeof window === 'undefined' ? [] : read();
    }

    /** 쓴 이모지를 맨 앞으로 */
    function push(emoji: string): void {
        ensureLoaded();
        const next = [emoji, ...(recent.value ?? []).filter((e) => e !== emoji)].slice(0, MAX);
        recent.value = next;
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        } catch {
            // 저장소를 못 쓴다 — 메모리에만 남는다
        }
    }

    return { recent, ensureLoaded, push };
}
