// MOCK: 서버를 붙이기 전까지 `TITLE_ADMIN_MOCK` 분기로 목업을 돌려준다 — 서버가 붙으면 이 import 와 각 메서드의 `// MOCK:` 줄을 지운다
import TitleAdminMock, { TITLE_ADMIN_MOCK } from '~/composables/title/api/mock/title-admin-mock';
import type {
    AdminTitle,
    AdminTitleApplication,
    AdminTitleRequest,
    MonthlyCandidateRequest,
    MonthlyCandidates,
    TitleApplicationStatus,
    TitleGrantPage,
    TitleGrantQuery,
    TitleGrantRequest,
    TitleGrantRow,
    TitleReasonRequest,
    TitleSettings,
    TitleSettingsRequest,
} from '~/composables/title/api/useTitleApi.types';

/**
 * 회원 칭호 관리 API(관리자) — 서버 주소 문자열(`/api/admin/titles…`·`/api/admin/members/{id}/titles`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/member-title-contract.md` 「구현 결정」 3·7 + admin 서버 `AdminTitleController`(2026-11-17 대조). 권한은 서버가 판정한다.
 *
 *  - `GET  /api/admin/titles` → `AdminTitle[]`(`title.read`) · `POST /api/admin/titles`(만들기 — 키는 서버) · `PUT /api/admin/titles/{key}`(고치기) `AdminTitleRequest` → 저장한 줄(`title.manage`)
 *    · 이름 6자 넘음·등급 이름과 겹침 `400 VALIDATION fields.name` · 칸 경로 키(`conditions[0].value`·`tiers`) · 기본 칭호 받는 방법 바꾸기 `409 TITLE_BUILTIN_LOCKED`
 *  - `GET/PUT /api/admin/titles/settings` `{ monthlyMax, honoraryCount, reapplyDays }` → `TitleSettings`(저장 응답의 `honoraryGranted` = 이번에 소급해서 준 명예 작가 수)
 *  - `POST   /api/admin/titles/{key}/grants` `{ memberId, period?, tier?, reason }` → 받은 줄 · `DELETE /api/admin/titles/{key}/grants/{grantId}` `{ reason }` → 204(`title.manage`)
 *  - `GET    /api/admin/titles/grants?key&memberId&page&size` → `TitleGrantPage`(주고 뺀 기록) · `GET /api/admin/members/{id}/titles` → `TitleGrantRow[]`(회원 상세)
 *  - `GET    /api/admin/titles/applications?status&key` → `AdminTitleApplication[]` · `POST .../applications/{id}/approve|reject` `{ reason }` → 204(`title.review`)
 *  - `GET    /api/admin/titles/monthly-candidates?month=YYYY-MM` → `MonthlyCandidates` · `POST/DELETE /api/admin/titles/monthly-candidates` `{ postId, month? }` → 204(`title.manage`)
 *
 * ⚠ MOCK — 서버를 붙이기 전까지 `TITLE_ADMIN_MOCK` 이면 `mock/title-admin-mock.ts` 가 답한다(실제 호출 줄은 그 아래에 그대로 있다).
 */
const ROOT = '/api/admin/titles';
const PATH = {
    root: ROOT,
    one: (key: string) => `${ROOT}/${encodeURIComponent(key)}`,
    settings: `${ROOT}/settings`,
    grants: (key: string) => `${ROOT}/${encodeURIComponent(key)}/grants`,
    grant: (key: string, grantId: string) => `${ROOT}/${encodeURIComponent(key)}/grants/${encodeURIComponent(grantId)}`,
    grantLog: `${ROOT}/grants`,
    memberTitles: (memberId: string) => `/api/admin/members/${encodeURIComponent(memberId)}/titles`,
    applications: `${ROOT}/applications`,
    approve: (id: string) => `${ROOT}/applications/${encodeURIComponent(id)}/approve`,
    reject: (id: string) => `${ROOT}/applications/${encodeURIComponent(id)}/reject`,
    monthly: `${ROOT}/monthly-candidates`,
};

export function useTitleApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 칭호 목록(차례대로) */
        list: (): Promise<AdminTitle[]> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.list(); // MOCK:
            return client.get<AdminTitle[]>(PATH.root);
        },

        /** 칭호 만들기 — 키는 서버가 정한다 */
        create: (body: AdminTitleRequest): Promise<AdminTitle> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.save(body, null); // MOCK:
            return client.post<AdminTitle>(PATH.root, body);
        },

        /** 칭호 고치기 — `retroactive` 면 저장 뒤 그 칭호만 소급 지급(자동 칭호) */
        update: (key: string, body: AdminTitleRequest): Promise<AdminTitle> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.save(body, key); // MOCK:
            return client.put<AdminTitle>(PATH.one(key), body);
        },

        /** 작가 칭호 설정 */
        settings: (): Promise<TitleSettings> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.settings(); // MOCK:
            return client.get<TitleSettings>(PATH.settings);
        },

        /** 작가 칭호 설정 저장 — 기준을 내리면 서버가 새 기준을 채운 회원에게 바로 소급해서 준다(올려도 이미 받은 명예 작가는 그대로) */
        saveSettings: (body: TitleSettingsRequest): Promise<TitleSettings> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.saveSettings(body); // MOCK:
            return client.put<TitleSettings>(PATH.settings, body);
        },

        /** 수여 — 이달의 작가는 `period: YYYY-MM`, 올해의 작가는 `YYYY`. 이달의 작가로 명예 작가가 따라오면 응답의 `honorary` */
        grant: (key: string, body: TitleGrantRequest): Promise<TitleGrantRow> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.grant(key, body); // MOCK:
            return client.post<TitleGrantRow>(PATH.grants(key), body);
        },

        /** 회수(사유 필수) — 줄은 기록으로 남는다 */
        revoke: (key: string, grantId: string, body: TitleReasonRequest): Promise<void> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.revoke(grantId, body.reason); // MOCK:
            return client.del<void>(PATH.grant(key, grantId), undefined, body);
        },

        /** 주고 뺀 기록 한 쪽 */
        grants: (query: TitleGrantQuery): Promise<TitleGrantPage> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.grants(query); // MOCK:
            return client.get<TitleGrantPage>(PATH.grantLog, { query: { key: query.key || undefined, memberId: query.memberId || undefined, page: query.page, size: query.size } });
        },

        /** 회원 한 명의 칭호(받은 것 + 회수한 것) — 회원 상세 「칭호」 구획 */
        memberTitles: (memberId: string): Promise<TitleGrantRow[]> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.memberTitles(memberId); // MOCK:
            return client.get<TitleGrantRow[]>(PATH.memberTitles(memberId));
        },

        /** 칭호 신청 목록(확인할 일) */
        applications: (status: TitleApplicationStatus | null, key: string | null): Promise<AdminTitleApplication[]> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.applications(); // MOCK:
            return client.get<AdminTitleApplication[]>(PATH.applications, { query: { status: status ?? undefined, key: key ?? undefined } });
        },

        /** 신청 승인 */
        approve: (id: string, body: TitleReasonRequest): Promise<void> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.review(id, true, body.reason); // MOCK:
            return client.post<void>(PATH.approve(id), body);
        },

        /** 신청 거절 — 사유 필수(회원에게 보이고, 다시 신청할 수 있는 날은 서버가 설정대로 정한다) */
        reject: (id: string, body: TitleReasonRequest): Promise<void> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.review(id, false, body.reason); // MOCK:
            return client.post<void>(PATH.reject(id), body);
        },

        /** 이달의 작가 후보(그 달 우수 글 글쓴이 + 「후보에 넣기」) */
        monthlyCandidates: (month: string): Promise<MonthlyCandidates> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.monthly(month); // MOCK:
            return client.get<MonthlyCandidates>(PATH.monthly, { query: { month } });
        },

        /** 글 관리 ⋯ 메뉴 「이달의 작가 후보에 넣기」(달을 비우면 이번 달) */
        addCandidate: (body: MonthlyCandidateRequest): Promise<void> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.candidate(body.postId, true); // MOCK:
            return client.post<void>(PATH.monthly, body);
        },

        /** 글 관리 ⋯ 메뉴 「후보에서 빼기」 */
        removeCandidate: (body: MonthlyCandidateRequest): Promise<void> => {
            if (TITLE_ADMIN_MOCK) return TitleAdminMock.candidate(body.postId, false); // MOCK:
            return client.del<void>(PATH.monthly, undefined, body);
        },
    };
}
