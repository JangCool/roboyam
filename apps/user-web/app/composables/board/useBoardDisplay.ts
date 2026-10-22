import type { BoardDisplaySettings } from '~/composables/board/api/useBoardApi.types';

/**
 * 게시판 공개 표시 기본값(날짜 표시 방식·새 글 기준) — 게시판 목록·글 보기가 같이 쓴다.
 * 따로 받지 않고 공개 사이트 정보(`GET /api/site/info` 의 `boardDisplay`, 앱 시작 때 한 번 받는다 — `useSiteInfo`)를 읽는다.
 * 받기 전·못 받았을 때는 표준형·24시간으로 그린다(화면이 비지 않게).
 */
const FALLBACK: BoardDisplaySettings = { dateDisplay: 'STANDARD', newPostHours: 24 };

export function useBoardDisplay() {
    const state = useSiteInfoState();
    const { ensureLoaded: ensureSiteInfo } = useSiteInfo();
    const display = computed<BoardDisplaySettings>(() => state.value?.boardDisplay ?? FALLBACK);

    /** 사이트 정보를 아직 안 받았으면 받는다(이미 받았으면 묻지 않는다) */
    async function ensureLoaded(): Promise<void> {
        await ensureSiteInfo();
    }

    return { display, ensureLoaded };
}
