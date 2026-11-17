import type {
    BoardTitleApplicationPage,
    BoardTitleApplicationStatusFilter,
    HallOfFame,
    MyTitleApplication,
    MyTitles,
    RepresentativeTitleRequest,
    TitleCatalogItem,
    TitleReviewRequest,
} from '~/composables/title/api/useTitleApi.types';

/**
 * 회원 칭호 API(공개 사이트) — 서버 주소 문자열(`/api/titles`·`/api/hall-of-fame`·`/api/me/titles…`·`/api/boards/{slug}/staff/title-applications…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/member-title-contract.md` 「구현 결정」 7·「서버 구현 결과」. **서버 연결됨**(server/api `TitleController`·`BoardTitleReviewController`).
 *
 *  - `GET  /api/titles` → `TitleCatalogItem[]` — 칭호 도감(**비회원도**)
 *  - `GET  /api/hall-of-fame` → `HallOfFame` — 명예의 전당(**비회원도**)
 *  - `GET  /api/me/titles` → `MyTitles` — 로그인 필요
 *  - `PUT  /api/me/titles/representative` `{ key | null, hidden }` → `MyTitles`(바뀐 내 칭호) · 409 `TITLE_NOT_OWNED`
 *  - `POST /api/me/titles/{key}/applications` → 201 `MyTitleApplication` · 409 `TITLE_NOT_APPLICABLE`·`TITLE_APPLICATION_PENDING`·`TITLE_REAPPLY_TOO_SOON`·`TITLE_CONDITIONS_UNMET` 등
 *  - `POST /api/me/titles/seen` → 204 — 축하 창을 닫으면
 *  - `GET  /api/boards/{slug}/staff/title-applications?status=PENDING|APPROVED|REJECTED|ALL` → `BoardTitleApplicationPage`(`{ items }`, 최신 순 200개, 기본 PENDING) —
 *    그 게시판 매니저만(아니면 403 `TITLE_REVIEW_FORBIDDEN`), 연결된 칭호만
 *  - `POST /api/boards/{slug}/staff/title-applications/{id}/approve|reject` `{ reason }` → 204 — 자기 신청 403 `TITLE_REVIEW_SELF`, 이미 처리됨 409 `TITLE_APPLICATION_CLOSED`
 */
const PATH = {
    catalog: '/api/titles',
    hallOfFame: '/api/hall-of-fame',
    mine: '/api/me/titles',
    representative: '/api/me/titles/representative',
    apply: (key: string) => `/api/me/titles/${encodeURIComponent(key)}/applications`,
    seen: '/api/me/titles/seen',
    staffApplications: (slug: string) => `/api/boards/${encodeURIComponent(slug)}/staff/title-applications`,
    staffApprove: (slug: string, id: string) => `/api/boards/${encodeURIComponent(slug)}/staff/title-applications/${encodeURIComponent(id)}/approve`,
    staffReject: (slug: string, id: string) => `/api/boards/${encodeURIComponent(slug)}/staff/title-applications/${encodeURIComponent(id)}/reject`,
};

export function useTitleApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 칭호 도감 — 칭호별 받는 방법·가진 회원 수·비율 */
        catalog: (signal?: AbortSignal): Promise<TitleCatalogItem[]> => client.get<TitleCatalogItem[]>(PATH.catalog, { signal }),

        /** 명예의 전당 — 명예 작가 · 역대 올해의 작가 · 역대 이달의 작가 */
        hallOfFame: (signal?: AbortSignal): Promise<HallOfFame> => client.get<HallOfFame>(PATH.hallOfFame, { signal }),

        /** 내 칭호 — 대표·가진 것·진행·신청형·신청 상태·아직 안 본 것 */
        mine: (signal?: AbortSignal): Promise<MyTitles> => client.get<MyTitles>(PATH.mine, { signal }),

        /** 대표 칭호 고르기 — `key: null` = 기본, `hidden: true` = 안 보이기 */
        setRepresentative: (body: RepresentativeTitleRequest): Promise<MyTitles> => client.put<MyTitles>(PATH.representative, body),

        /** 신청형 칭호 신청 — 만든 신청 한 줄을 돌려준다(화면은 `mine` 을 다시 받는다) */
        apply: (key: string): Promise<MyTitleApplication> => client.post<MyTitleApplication>(PATH.apply(key)),

        /** 축하 창을 봤다 — `unseen` 을 비운다 */
        markSeen: (): Promise<void> => client.post<void>(PATH.seen),

        /** 게시판 스텝 「칭호 신청」 목록(그 게시판 매니저) */
        staffApplications: (slug: string, status: BoardTitleApplicationStatusFilter, signal?: AbortSignal): Promise<BoardTitleApplicationPage> =>
            client.get<BoardTitleApplicationPage>(PATH.staffApplications(slug), { query: { status }, signal }),

        /** 게시판 스텝 승인 */
        staffApprove: (slug: string, id: string, body: TitleReviewRequest): Promise<void> => client.post<void>(PATH.staffApprove(slug, id), body),

        /** 게시판 스텝 거절 — 사유 필수 */
        staffReject: (slug: string, id: string, body: TitleReviewRequest): Promise<void> => client.post<void>(PATH.staffReject(slug, id), body),
    };
}
