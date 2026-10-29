import type { BoardLayoutBody, BoardLayoutSaveRequest, BoardLayoutView } from '~/composables/board/api/useBoardLayoutApi.types';

/**
 * 관리자 게시판 레이아웃 API — 서버 주소 문자열(`/api/admin/board-layouts`)이 있는 **유일한 곳**이다. 상태는 없다. 모양·가정은 `useBoardLayoutApi.types.ts`.
 * 계약: `docs/features/board-layout-contract.md` 「API」(서버 `BoardLayoutController` — 홈 배치 API 와 같은 모양):
 *   - `GET /api/admin/board-layouts/{theme}` → 200 `BoardLayoutView` (`site.read`) — 저장한 배치, 없으면 기본 배치(본문만, `updatedAt: null`) · 404 모르는 테마
 *   - `GET /api/admin/board-layouts/{theme}/defaults` → 200 `BoardLayoutBody` (`site.read`) — 기본 배치(저장하지 않는다)
 *   - `PUT /api/admin/board-layouts/{theme}` `BoardLayoutSaveRequest` → 200 `BoardLayoutView` (`site.manage`) · 400 홈 저장과 같은 칸 +
 *       `fields.layout.items`(본문 없음·둘 이상) · `fields.layout.items[i]`(본문 최소 크기) · `fields.screens.list|post` · `fields.gadgets[i].config.boardScope`(`FOLDER` 는 400)
 */
const PATH = {
    layout: (theme: string) => `/api/admin/board-layouts/${encodeURIComponent(theme)}`,
    defaults: (theme: string) => `/api/admin/board-layouts/${encodeURIComponent(theme)}/defaults`,
};

export function useBoardLayoutApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 테마 하나의 게시판 레이아웃 */
        get: (theme: string): Promise<BoardLayoutView> => client.get<BoardLayoutView>(PATH.layout(theme)),

        /** 기본 배치(본문만 — 저장하지 않는다) */
        defaults: (theme: string): Promise<BoardLayoutBody> => client.get<BoardLayoutBody>(PATH.defaults(theme)),

        /** 저장 */
        save: (theme: string, body: BoardLayoutSaveRequest): Promise<BoardLayoutView> => client.put<BoardLayoutView>(PATH.layout(theme), body),
    };
}
