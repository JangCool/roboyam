import type { BoardView } from '~/composables/board/api/useBoardApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';

/**
 * 전체 게시판 목록 — 게시판 화면·글쓰기·메뉴(색 점 판정·기본 메뉴)가 함께 쓰는 **공유 상태**다 (frontend-guide 3장). 메뉴의 구성은 `useMenu` 가 따로 받는다.
 * 서버에서 한 번만 받고(`useBoardApi().boards`) 화면 전체가 같은 목록을 본다. 로그인 없이 받는다.
 *
 * `null` 은 "아직 안 물어봄"이고 빈 배열은 "게시판이 없음"이다 — 가르지 않으면 화면이 뜨자마자 "없어요"를 잠깐 보인다.
 * 게시판·말머리·그룹은 코드에 박지 않고 여기서 받은 것만 그린다(CLAUDE.md 2).
 *
 * **일반 게시판만 온다**(GRP-006, G-5 contract) — `GET /api/boards` 는 소모임 게시판을 싣지 않는다(`bySlug` 로도 못 찾는다 — `/boards/{소모임 게시판 주소}` 는 없는 게시판과 같다).
 * 소모임 글쓰기의 말머리는 이 목록이 아니라 소모임 상세의 `board` 에서 온다(`useGroupWrite`).
 */

/** 진행 중인 요청 — 여러 곳이 동시에 물어도 한 번만 보낸다 */
let inflight: Promise<void> | null = null;

export function useBoards() {
    const api = useBoardApi();
    const t = useTranslate();

    const boards = useState<BoardView[] | null>('boards', () => null);
    /** 마지막으로 받지 못했을 때의 문구. 받으면 빈 문자열 */
    const errorMessage = useState<string>('boards-error', () => '');
    const loading = useState<boolean>('boards-loading', () => false);

    function bySlug(slug: string | null | undefined): BoardView | undefined {
        return slug ? boards.value?.find((b) => b.slug === slug) : undefined;
    }

    /** 서버에서 새로 받는다. 실패하면 `errorMessage` 에 문구를 두고 이미 받은 목록은 지우지 않는다 */
    function reload(): Promise<void> {
        if (inflight) return inflight;
        loading.value = true;
        inflight = api
            .boards()
            .then((list) => {
                boards.value = list;
                errorMessage.value = '';
            })
            .catch((e: unknown) => {
                errorMessage.value = e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
            })
            .finally(() => {
                loading.value = false;
                inflight = null;
            });
        return inflight;
    }

    /** 이미 받았으면 다시 묻지 않는다. 화면마다 부르면 이동할 때마다 왕복이 생긴다 */
    async function ensureLoaded(): Promise<void> {
        if (boards.value === null) await reload();
    }

    return { boards, loading, errorMessage, bySlug, reload, ensureLoaded };
}
