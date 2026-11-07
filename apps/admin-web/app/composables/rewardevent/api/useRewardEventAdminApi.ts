import type {
    RewardCommentPreview,
    RewardCommentPreviewRequest,
    RewardDeliveredRequest,
    RewardDraw,
    RewardDrawRequest,
    RewardEventAudit,
    RewardEventCatalog,
    RewardEventDetail,
    RewardEventPage,
    RewardEventQuery,
    RewardEventSaveRequest,
    RewardGrantPreview,
    RewardGrantRequest,
    RewardMemberRef,
    RewardRecipient,
    RewardRecipientAddRequest,
    RewardRecipientPage,
    RewardRecipientQuery,
    RewardRecipientResolveResult,
    RewardRevokeRequest,
    RewardRun,
    RewardTargetPreview,
    RewardTargetPreviewRequest,
} from '~/composables/rewardevent/api/useRewardEventAdminApi.types';

/**
 * 보상 이벤트 관리자 API — 서버 주소 문자열(`/api/admin/reward-events/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 서버: admin `RewardEventAdminController`(→ `RewardEventAdminService`, shared `com.community.rewardevent`). 계약 정본 `docs/features/event-admin-contract.md`.
 * 「사이트 팝업」(이벤트·프로모션 알림 창)과 다른 업무 — 늘 「보상 이벤트」라고 부른다.
 *
 * 권한(서버 `Permission`): 조회 `reward-event.read` · 만들기·고치기·후보·당첨자·추첨·시작·마감·취소 `reward-event.manage`
 *   · 지급 실행·재시도·회수·직접 전달 완료 `reward-event.grant` · 1인 포인트 합이 고액 문턱(포인트 정책 `adjustLargeThreshold`) 초과이거나 **일괄 방식에 포인트 보상이 있는** 지급은
 *   `point.adjust.large` 도. 없으면 `403 PERMISSION_DENIED`(고액은 `403 POINT_ADJUST_LARGE_REQUIRED`). 쓰기는 CSRF(`useApiClient`), 틀린 칸은 `400 VALIDATION fields.<칸 경로>`(예 `tiers[0].rewards[1].points`).
 * 감사: 만듦·고침·시작·마감·자동 마감·추첨·당첨자 추가·빼기·지급·재시도·회수·전달 완료·취소를 서버가 남긴다(지급·재시도·회수는 돈 관련 5년).
 *
 * 주소:
 *  - `GET  /api/admin/reward-events?status&mode&q&page&size` → `RewardEventPage`(번호 내림차순, `counts` = 검색어·방식을 적용한 상태별 건수)
 *  - `GET  /api/admin/reward-events/{id}` → `RewardEventDetail`(상태·숫자·서버가 정한 `actions`·진행 중 실행 `runningRun`) · 없으면 `404 REWARD_EVENT_NOT_FOUND`
 *  - `POST /api/admin/reward-events` `RewardEventSaveRequest` → `201` + `RewardEventDetail`(준비 상태)
 *  - `PUT  /api/admin/reward-events/{id}` 같은 본문 → `RewardEventDetail`. 상태별 고치기 범위(`actions.editScope`) 밖 칸이 바뀌면 `409 REWARD_EVENT_LOCKED_FIELD`(`fields` 에 그 칸 경로 하나),
 *       이미 받은(선정된) 수보다 인원을 줄이면 `400 fields.firstCome.limit|tiers[i].quota`. 준비 상태에서 고치면 보상 줄 번호가 모두 새로 바뀐다(등수는 번호 유지) — 응답으로 다시 채운다
 *  - `POST …/{id}/start` · `…/close` · `…/cancel { reason }`(5~200자) → `RewardEventDetail`. 상태가 안 맞으면 `409 REWARD_EVENT_STATE`, 끝 시각이 지났으면 `409 REWARD_EVENT_PERIOD_PASSED`,
 *       지급이 하나라도 있으면 취소는 `409 REWARD_EVENT_HAS_GRANTS`(회수를 쓴다)
 *  - 후보·당첨자: `POST …/{id}/recipients/resolve { lines }`(500줄까지) · `GET /api/admin/reward-events/member-search?q=`(20명)
 *       · `POST …/{id}/recipients { tierId, memberIds }`(500명까지, 등수 인원 넘으면 `409 REWARD_EVENT_TIER_FULL`)
 *       · `DELETE …/{id}/recipients/{recipientId}` → 200 + `RewardEventDetail`(지급 전만 — `409 REWARD_RECIPIENT_GRANTED`, 없으면 `404 REWARD_RECIPIENT_NOT_FOUND`)
 *  - 미리 보기(이벤트 저장 전에도 쓰므로 번호 없이): `POST /api/admin/reward-events/preview/comments`(글이 없으면 `404 POST_NOT_FOUND`) · `POST /api/admin/reward-events/preview/targets`
 *  - 추첨: `POST …/{id}/draws { tierId, count }` → `RewardDraw`(서버가 추첨 번호(seed, 16자리 16진수)로 뽑고 기록 — 후보가 모자라면 `409 REWARD_DRAW_NOT_ENOUGH`,
 *       댓글 추첨 설정이 없으면 `409 REWARD_EVENT_NO_COMMENT_SOURCE`) · `GET …/{id}/draws`
 *  - 지급: `GET …/{id}/grants/preview` → `RewardGrantPreview`(`largeRequired` 는 서버 판정) · `POST …/{id}/grants { idempotencyKey }` → `RewardRun`(일괄 대량은 `RUNNING` + 진행률, 나눠서 처리)
 *       · `POST …/{id}/grants/retry`(실패만) · `POST …/{id}/revocations { recipientIds, reason }`(500명까지 — 회수할 사람이 없으면 `409 REWARD_REVOKE_NOTHING`)
 *       · `POST …/{id}/recipients/{rid}/manual-delivered { lineId, memo }` → `RewardRecipient`(전달 대기가 아니면 `409 REWARD_DELIVERY_STATE`)
 *  - 조회: `GET …/{id}/recipients?status&tierId&q&page&size` → `RewardRecipientPage` · `GET …/{id}/runs` · `GET …/{id}/audit`(한 줄 = 서버 번들 문구, 요청 언어)
 *  - 보상 고르기용: `GET /api/admin/reward-events/catalog` → `RewardEventCatalog`(상점 품목·로보티콘 팩 `selectable`·등급·고액 문턱)
 */
const PATH = {
    root: '/api/admin/reward-events',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    action: (id: string, action: 'start' | 'close' | 'cancel') => `${PATH.one(id)}/${action}`,
    recipients: (id: string) => `${PATH.one(id)}/recipients`,
    recipient: (id: string, rid: string) => `${PATH.recipients(id)}/${encodeURIComponent(rid)}`,
    resolve: (id: string) => `${PATH.recipients(id)}/resolve`,
    delivered: (id: string, rid: string) => `${PATH.recipient(id, rid)}/manual-delivered`,
    memberSearch: () => `${PATH.root}/member-search`,
    previewComments: () => `${PATH.root}/preview/comments`,
    previewTargets: () => `${PATH.root}/preview/targets`,
    draws: (id: string) => `${PATH.one(id)}/draws`,
    grants: (id: string) => `${PATH.one(id)}/grants`,
    grantPreview: (id: string) => `${PATH.grants(id)}/preview`,
    retry: (id: string) => `${PATH.grants(id)}/retry`,
    revocations: (id: string) => `${PATH.one(id)}/revocations`,
    runs: (id: string) => `${PATH.one(id)}/runs`,
    audit: (id: string) => `${PATH.one(id)}/audit`,
    catalog: () => `${PATH.root}/catalog`,
};

export function useRewardEventAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 목록 한 쪽 — 번호 내림차순 */
        list: (query: RewardEventQuery): Promise<RewardEventPage> => client.get<RewardEventPage>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<RewardEventDetail> => client.get<RewardEventDetail>(PATH.one(id)),

        /** 보상 고르기용 품목·팩·등급·고액 문턱 */
        catalog: (): Promise<RewardEventCatalog> => client.get<RewardEventCatalog>(PATH.catalog()),

        /** 만들기(201) — 준비 상태 상세 */
        create: (body: RewardEventSaveRequest): Promise<RewardEventDetail> => client.post<RewardEventDetail>(PATH.root, body),

        /** 고치기 — 준비 상태면 보상 줄 번호가 새로 바뀌므로 응답 상세로 다시 그린다 */
        update: (id: string, body: RewardEventSaveRequest): Promise<RewardEventDetail> => client.put<RewardEventDetail>(PATH.one(id), body),

        start: (id: string): Promise<RewardEventDetail> => client.post<RewardEventDetail>(PATH.action(id, 'start')),

        close: (id: string): Promise<RewardEventDetail> => client.post<RewardEventDetail>(PATH.action(id, 'close')),

        /** 취소 — 지급 전만, 사유 5~200자 */
        cancel: (id: string, reason: string): Promise<RewardEventDetail> => client.post<RewardEventDetail>(PATH.action(id, 'cancel'), { reason }),

        recipients: (id: string, query: RewardRecipientQuery): Promise<RewardRecipientPage> => client.get<RewardRecipientPage>(PATH.recipients(id), { query: { ...query } }),

        /** 붙여 넣은 줄(회원 번호·닉네임·이메일, 500줄까지) → 찾은 회원 / 못 찾은 줄 */
        resolveRecipients: (id: string, lines: string[]): Promise<RewardRecipientResolveResult> => client.post<RewardRecipientResolveResult>(PATH.resolve(id), { lines }),

        searchMembers: (q: string): Promise<RewardMemberRef[]> => client.get<RewardMemberRef[]>(PATH.memberSearch(), { query: { q } }),

        /** 당첨자 넣기 — 회원 번호(숫자 문자열) 500명까지 */
        addRecipients: (id: string, body: RewardRecipientAddRequest): Promise<RewardEventDetail> => client.post<RewardEventDetail>(PATH.recipients(id), body),

        /** 당첨자 빼기(지급 전만) — 200 + 상세 */
        removeRecipient: (id: string, recipientId: string): Promise<RewardEventDetail> => client.del<RewardEventDetail>(PATH.recipient(id, recipientId)),

        previewComments: (body: RewardCommentPreviewRequest): Promise<RewardCommentPreview> => client.post<RewardCommentPreview>(PATH.previewComments(), body),

        previewTargets: (body: RewardTargetPreviewRequest): Promise<RewardTargetPreview> => client.post<RewardTargetPreview>(PATH.previewTargets(), body),

        draw: (id: string, body: RewardDrawRequest): Promise<RewardDraw> => client.post<RewardDraw>(PATH.draws(id), body),

        draws: (id: string): Promise<RewardDraw[]> => client.get<RewardDraw[]>(PATH.draws(id)),

        grantPreview: (id: string): Promise<RewardGrantPreview> => client.get<RewardGrantPreview>(PATH.grantPreview(id)),

        /** 지급 실행 — 본문 `idempotencyKey` 로 두 번 눌러도 한 번(같은 키는 처음 실행을 돌려준다) */
        grant: (id: string, body: RewardGrantRequest): Promise<RewardRun> => client.post<RewardRun>(PATH.grants(id), body),

        retry: (id: string): Promise<RewardRun> => client.post<RewardRun>(PATH.retry(id)),

        /** 선택 회수 — 받는 사람 500명까지, 사유 5~200자 */
        revoke: (id: string, body: RewardRevokeRequest): Promise<RewardRun> => client.post<RewardRun>(PATH.revocations(id), body),

        markDelivered: (id: string, recipientId: string, body: RewardDeliveredRequest): Promise<RewardRecipient> => client.post<RewardRecipient>(PATH.delivered(id, recipientId), body),

        runs: (id: string): Promise<RewardRun[]> => client.get<RewardRun[]>(PATH.runs(id)),

        audit: (id: string): Promise<RewardEventAudit[]> => client.get<RewardEventAudit[]>(PATH.audit(id)),
    };
}
