import type { HotdealBoardOption } from '~/composables/hotdeal/api/useHotdealApi.types';

/**
 * 핫딜 게시판(DEAL) 선택지 — 핫딜 글 목록의 게시판 고르기와 가젯 `HOT_DEALS` ⚙ 설정의 게시판 고르기가 같이 쓴다(한 번 받으면 다시 묻지 않는다).
 * 실패해도 화면은 게시판 조건 없이 쓸 수 있어 오류 문구만 들고 있다.
 */
export function useHotdealBoards() {
    const boards = useState<HotdealBoardOption[]>('hotdeal-boards', () => []);
    const loaded = useState<boolean>('hotdeal-boards-loaded', () => false);
    const loading = ref(false);
    const error = ref('');

    const api = useHotdealApi();
    const toApiError = useApiErrorOf();

    async function load(force = false): Promise<void> {
        if ((loaded.value && !force) || loading.value) return;
        loading.value = true;
        error.value = '';
        try {
            boards.value = (await api.boards()).boards;
            loaded.value = true;
        } catch (e) {
            error.value = toApiError(e).message;
        } finally {
            loading.value = false;
        }
    }

    return { boards, loading, error, load };
}
