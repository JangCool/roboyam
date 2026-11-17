import type {
    AdminTitle,
    AdminTitleApplicationList,
    AdminTitleRequest,
    MonthlyCandidateRequest,
    MonthlyCandidates,
    TitleApplicationStatusFilter,
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
 * 계약 `docs/features/member-title-contract.md` 「구현 결정」 3·7·「서버 구현 결과」. **서버 연결됨**(admin 서버 `AdminTitleController`). 권한은 서버가 판정한다.
 *
 *  - `GET  /api/admin/titles` → `AdminTitle[]`(`title.read`) · `POST /api/admin/titles` → 201(만들기 — `key` 선택) · `PUT /api/admin/titles/{key}`(고치기) `AdminTitleRequest` → 저장한 줄(`title.manage`)
 *    · 이름 6자 넘음·등급 이름과 겹침 `400 VALIDATION fields.name` · 칸 경로 키(`conditions[0].value`·`tierBronze`) · 이름 겹침 `409 TITLE_NAME_TAKEN` · 키 겹침 `409 TITLE_KEY_TAKEN`
 *    · 기본 칭호 받는 방법 바꾸기 `409 TITLE_BUILTIN_LOCKED`
 *  - `GET/PUT /api/admin/titles/settings` `{ monthlyMax, honoraryCount, reapplyDays }` → `TitleSettings`(저장 응답의 `honoraryGranted` = 이번에 소급해서 준 명예 작가 수)
 *  - `POST   /api/admin/titles/{key}/grants` `{ memberId, period?, tier?, reason? }` → 201 받은 줄 · `DELETE /api/admin/titles/{key}/grants/{grantId}` `{ reason }` → 204(`title.manage`)
 *  - `GET    /api/admin/titles/grants?key&memberId&page&size` → `TitleGrantPage`(주고 뺀 기록) · `GET /api/admin/members/{id}/titles` → `TitleGrantRow[]`(회원 상세)
 *  - `GET    /api/admin/titles/applications?status=PENDING|APPROVED|REJECTED|ALL&key` → `AdminTitleApplicationList`(`{ items }`) · `POST .../applications/{id}/approve|reject` `{ reason }` → 204(`title.review`)
 *  - `GET    /api/admin/titles/monthly-candidates?month=YYYY-MM` → `MonthlyCandidates` · `POST/DELETE /api/admin/titles/monthly-candidates` `{ postId, month? }` → 204(`title.manage`)
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
        list: (): Promise<AdminTitle[]> => client.get<AdminTitle[]>(PATH.root),

        /** 칭호 만들기 — `key` 를 비우면 서버가 정한다 */
        create: (body: AdminTitleRequest): Promise<AdminTitle> => client.post<AdminTitle>(PATH.root, body),

        /** 칭호 고치기 — `retroactive` 면 저장 뒤 그 칭호만 소급 지급(자동 칭호) */
        update: (key: string, body: AdminTitleRequest): Promise<AdminTitle> => client.put<AdminTitle>(PATH.one(key), body),

        /** 작가 칭호 설정 */
        settings: (): Promise<TitleSettings> => client.get<TitleSettings>(PATH.settings),

        /** 작가 칭호 설정 저장 — 기준을 내리면 서버가 새 기준을 채운 회원에게 바로 소급해서 준다(올려도 이미 받은 명예 작가는 그대로) */
        saveSettings: (body: TitleSettingsRequest): Promise<TitleSettings> => client.put<TitleSettings>(PATH.settings, body),

        /** 수여 — 이달의 작가는 `period: YYYY-MM`, 올해의 작가는 `YYYY`. 이달의 작가로 명예 작가가 따라오면 응답의 `honorary` */
        grant: (key: string, body: TitleGrantRequest): Promise<TitleGrantRow> => client.post<TitleGrantRow>(PATH.grants(key), body),

        /** 회수(사유 필수) — 줄은 기록으로 남는다 */
        revoke: (key: string, grantId: string, body: TitleReasonRequest): Promise<void> => client.del<void>(PATH.grant(key, grantId), undefined, body),

        /** 주고 뺀 기록 한 쪽 */
        grants: (query: TitleGrantQuery): Promise<TitleGrantPage> =>
            client.get<TitleGrantPage>(PATH.grantLog, { query: { key: query.key || undefined, memberId: query.memberId || undefined, page: query.page, size: query.size } }),

        /** 회원 한 명의 칭호(받은 것 + 회수한 것) — 회원 상세 「칭호」 구획 */
        memberTitles: (memberId: string): Promise<TitleGrantRow[]> => client.get<TitleGrantRow[]>(PATH.memberTitles(memberId)),

        /** 칭호 신청 목록(확인할 일) — 상태 기본은 서버가 PENDING */
        applications: (status: TitleApplicationStatusFilter, key: string | null): Promise<AdminTitleApplicationList> =>
            client.get<AdminTitleApplicationList>(PATH.applications, { query: { status, key: key ?? undefined } }),

        /** 신청 승인 */
        approve: (id: string, body: TitleReasonRequest): Promise<void> => client.post<void>(PATH.approve(id), body),

        /** 신청 거절 — 사유 필수(회원에게 보이고, 다시 신청할 수 있는 날은 서버가 설정대로 정한다) */
        reject: (id: string, body: TitleReasonRequest): Promise<void> => client.post<void>(PATH.reject(id), body),

        /** 이달의 작가 후보(그 달 우수 글 글쓴이 + 「후보에 넣기」) */
        monthlyCandidates: (month: string): Promise<MonthlyCandidates> => client.get<MonthlyCandidates>(PATH.monthly, { query: { month } }),

        /** 글 관리 ⋯ 메뉴 「이달의 작가 후보에 넣기」(달을 비우면 이번 달) */
        addCandidate: (body: MonthlyCandidateRequest): Promise<void> => client.post<void>(PATH.monthly, body),

        /** 글 관리 ⋯ 메뉴 「후보에서 빼기」 */
        removeCandidate: (body: MonthlyCandidateRequest): Promise<void> => client.del<void>(PATH.monthly, undefined, body),
    };
}
