import type { BoardLayoutResponse } from '~/composables/board/api/useBoardLayoutApi.types';
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';

/**
 * 공개 게시판 레이아웃 API — 서버 주소 문자열(`/api/boards/{slug}/layout`)이 있는 **유일한 곳**이다. 상태는 없다. 모양·가정은 `useBoardLayoutApi.types.ts`.
 * 계약 `docs/features/board-layout-contract.md` 「API」(서버 `BoardLayoutController`): `GET /api/boards/{slug}/layout` (공개, 로그인 없이 · `no-store` — 글 줄에 보는 사람 몫이 섞인다) ·
 *   `?theme=`(관리자 미리보기만 — 홈 가젯 API 와 같은 규칙) → `BoardLayoutResponse` · 없는 게시판 `404 BOARD_NOT_FOUND` · 꺼진 게시판 `403 BOARD_INACTIVE`
 *   (화면은 레이아웃 없이 본문만 — 게시판 화면이 그 오류를 그린다).
 * 관리자 미리보기(`preview`)면 홈 가젯 응답도 같이 받아 `extra` 로 준다 — 관리자가 새로 넣은 가젯을 그릴 바탕(`UtilAdminPreview.applyHome`).
 */
const PATH = {
    layout: (slug: string) => `/api/boards/${encodeURIComponent(slug)}/layout`,
};

export function useBoardLayoutApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    const home = useHomeGadgetApi();

    return {
        /** 게시판 하나의 레이아웃(지금 사이트 테마 — 미리보기면 `theme`) + 미리보기면 홈 가젯(바탕) */
        layout: async (slug: string, options: { signal?: AbortSignal; preview?: boolean; theme?: string } = {}): Promise<{ res: BoardLayoutResponse; extra: HomeGadget[] }> => {
            const theme = options.preview ? options.theme : undefined;
            const [res, extra] = await Promise.all([
                client.get<BoardLayoutResponse>(PATH.layout(slug), { query: theme ? { theme } : undefined, signal: options.signal }),
                options.preview
                    ? home
                          .gadgets(options.signal, theme)
                          .then((r) => r.gadgets)
                          .catch(() => [] as HomeGadget[])
                    : Promise.resolve([] as HomeGadget[]),
            ]);
            return { res, extra };
        },
    };
}
