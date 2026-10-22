/**
 * 보기 전환(게시판형 ↔ 갤러리형) 선택을 이 브라우저에 게시판별로 기억한다 — `localStorage` `roboyam:board-view:<slug>`.
 * 읽는 사람 개인의 편의라 서버에 보내지 않는다. 저장소가 막혀 있으면(사생활 보호 창 등) 이번 방문 동안만 기억한다.
 * 화면 상태(`useState`)에도 두어 같은 게시판으로 돌아와도 바로 같은 모양이다.
 */
export type BoardViewMode = 'LIST' | 'GALLERY';

const KEY = (slug: string) => `roboyam:board-view:${slug}`;

export function useBoardViewMode() {
    const chosenMap = useState<Record<string, BoardViewMode>>('board-view-mode', () => ({}));

    function chosen(slug: string): BoardViewMode | null {
        const inMemory = chosenMap.value[slug];
        if (inMemory) return inMemory;
        try {
            const stored = window.localStorage.getItem(KEY(slug));
            return stored === 'LIST' || stored === 'GALLERY' ? stored : null;
        } catch {
            return null;
        }
    }

    function choose(slug: string, mode: BoardViewMode): void {
        chosenMap.value = { ...chosenMap.value, [slug]: mode };
        try {
            window.localStorage.setItem(KEY(slug), mode);
        } catch {
            // 저장소가 막혔으면 이번 방문만 기억한다
        }
    }

    return { chosen, choose };
}
