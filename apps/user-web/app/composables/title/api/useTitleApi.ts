// MOCK: 서버가 생기기 전까지 `TITLE_MOCK` 분기로 목업을 돌려준다 — 서버가 붙으면 이 import 와 각 메서드의 `// MOCK:` 줄을 지운다
import TitleMock, { TITLE_MOCK } from '~/composables/title/api/mock/title-mock';
import type {
    BoardTitleApplication,
    HallOfFame,
    MyTitles,
    RepresentativeTitleRequest,
    TitleApplicationStatus,
    TitleCatalogItem,
    TitleReviewRequest,
} from '~/composables/title/api/useTitleApi.types';

/**
 * 회원 칭호 API(공개 사이트) — 서버 주소 문자열(`/api/titles`·`/api/hall-of-fame`·`/api/me/titles…`·`/api/boards/{slug}/staff/title-applications…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/member-title-contract.md` 「구현 결정」 7(api 서버).
 *
 *  - `GET  /api/titles` → `TitleCatalogItem[]` — 칭호 도감(**비회원도**)
 *  - `GET  /api/hall-of-fame` → `HallOfFame` — 명예의 전당(**비회원도**)
 *  - `GET  /api/me/titles` → `MyTitles` — 로그인 필요
 *  - `PUT  /api/me/titles/representative` `{ key | null, hidden }` → `MyTitles`(바뀐 내 칭호)
 *  - `POST /api/me/titles/{key}/applications` → 201(본문은 쓰지 않는다 — 화면은 `mine` 을 다시 받는다)
 *  - `POST /api/me/titles/seen` → 204 — 축하 창을 닫으면
 *  - `GET  /api/boards/{slug}/staff/title-applications?status=` → `BoardTitleApplication[]` — 그 게시판 매니저만, 연결된 칭호만
 *  - `POST /api/boards/{slug}/staff/title-applications/{id}/approve|reject` `{ reason }` → 204 — 자기 신청 불가
 *
 * ⚠ MOCK — 서버가 아직 없어 `TITLE_MOCK` 이면 `mock/title-mock.ts` 가 답한다(실제 호출 줄은 그 아래에 그대로 있다).
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
        catalog: (signal?: AbortSignal): Promise<TitleCatalogItem[]> => {
            if (TITLE_MOCK) return TitleMock.catalog(); // MOCK:
            return client.get<TitleCatalogItem[]>(PATH.catalog, { signal });
        },

        /** 명예의 전당 — 명예 작가 · 역대 올해의 작가 · 역대 이달의 작가 */
        hallOfFame: (signal?: AbortSignal): Promise<HallOfFame> => {
            if (TITLE_MOCK) return TitleMock.hallOfFame(); // MOCK:
            return client.get<HallOfFame>(PATH.hallOfFame, { signal });
        },

        /** 내 칭호 — 대표·가진 것·진행·신청형·신청 상태·아직 안 본 것 */
        mine: (signal?: AbortSignal): Promise<MyTitles> => {
            if (TITLE_MOCK) return TitleMock.mine(); // MOCK:
            return client.get<MyTitles>(PATH.mine, { signal });
        },

        /** 대표 칭호 고르기 — `key: null` = 기본, `hidden: true` = 안 보이기 */
        setRepresentative: (body: RepresentativeTitleRequest): Promise<MyTitles> => {
            if (TITLE_MOCK) return TitleMock.setRepresentative(body); // MOCK:
            return client.put<MyTitles>(PATH.representative, body);
        },

        /** 신청형 칭호 신청 */
        apply: async (key: string): Promise<void> => {
            if (TITLE_MOCK) return TitleMock.apply(key); // MOCK:
            await client.post<unknown>(PATH.apply(key));
        },

        /** 축하 창을 봤다 — `unseen` 을 비운다 */
        markSeen: (): Promise<void> => {
            if (TITLE_MOCK) return TitleMock.markSeen(); // MOCK:
            return client.post<void>(PATH.seen);
        },

        /** 게시판 스텝 「칭호 신청」 목록(그 게시판 매니저) */
        staffApplications: (slug: string, status: TitleApplicationStatus | null, signal?: AbortSignal): Promise<BoardTitleApplication[]> => {
            if (TITLE_MOCK) return TitleMock.staffApplications(); // MOCK:
            return client.get<BoardTitleApplication[]>(PATH.staffApplications(slug), { query: { status: status ?? undefined }, signal });
        },

        /** 게시판 스텝 승인 */
        staffApprove: (slug: string, id: string, body: TitleReviewRequest): Promise<void> => {
            if (TITLE_MOCK) return TitleMock.staffReview(id, true, body.reason); // MOCK:
            return client.post<void>(PATH.staffApprove(slug, id), body);
        },

        /** 게시판 스텝 거절 — 사유 필수 */
        staffReject: (slug: string, id: string, body: TitleReviewRequest): Promise<void> => {
            if (TITLE_MOCK) return TitleMock.staffReview(id, false, body.reason); // MOCK:
            return client.post<void>(PATH.staffReject(slug, id), body);
        },
    };
}
