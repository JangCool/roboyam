import type {
    AiDraftBatchResult,
    AiDraftEditRequest,
    AiDraftListQuery,
    AiDraftListView,
    AiLogPage,
    AiLogQuery,
    AiMemberDetail,
    AiMemberFormOptions,
    AiMemberListView,
    AiMemberRow,
    AiMemberSaveRequest,
    AiMemberSettings,
    AiMemberSettingsRequest,
    AiProvider,
} from '~/composables/aimember/api/useAiMemberAdminApi.types';
// MOCK:
import AiMemberAdminMock from '~/composables/aimember/api/mock/ai-member-admin-mock';

/**
 * AI 회원 관리 API — 서버 주소 문자열(`/api/admin/ai-members…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/ai-members-contract.md`.
 * ⚠ 화면 확정 전 목업(2026-11-10) — 서버(ai_member_profiles·대기함·admin 서버 스케줄러)가 아직 없다. 메서드마다 `// MOCK:` 줄과 `// REAL:` 줄을 나란히 둔다.
 *
 * 계약 후보(서버 단계에서 확정 — 권한 이름도 후보):
 *   - `GET  /api/admin/ai-members` → `AiMemberListView`(`ai-member.read`)
 *   - `GET  /api/admin/ai-members/options` → `AiMemberFormOptions` — 제공사별 모델·게시판(막힌 이유 포함)·AI 전용 소모임·관계 상대 후보
 *   - `GET  /api/admin/ai-members/{id}` → `AiMemberDetail` · 404 `AI_MEMBER_NOT_FOUND`
 *   - `PUT  /api/admin/ai-members/{id}` `AiMemberSaveRequest` → `AiMemberDetail`(`ai-member.manage`) · 400 `VALIDATION`(`fields.model`·`persona.*`·`bio`·`windows.*`·`dailyPosts`·`boards`·`relation`)
 *     관계는 서버가 상대 쪽까지 같이 적고(옛 상대는 풀고) 감사 로그를 남긴다.
 *   - `PUT  /api/admin/ai-members/{id}/enabled` `{ enabled }` → `AiMemberRow`
 *   - `GET  /api/admin/ai-members/drafts?kind=&aiMemberId=` → `AiDraftListView` — 오래된 것이 아래(최신이 위)
 *   - `POST /api/admin/ai-members/drafts/publish` `{ ids }` · `POST …/drafts/discard` `{ ids }` → `AiDraftBatchResult`(그 사이 처리된 건은 `skipped`)
 *   - `POST /api/admin/ai-members/drafts/{id}/publish` `AiDraftEditRequest` → 수정 후 게시 · 404 `AI_DRAFT_NOT_FOUND`
 *     게시는 기존 글·댓글 서비스를 거친다(금지어·제재·캐시·알림 — 정책 9). 금지어에 걸리면 400 `BLOCKED_WORD`.
 *   - `GET  /api/admin/ai-members/settings` · `PUT …/settings` `AiMemberSettingsRequest` → `AiMemberSettings` · 400 `fields.instantAccountIds`(계정별인데 하나도 없음)·`fields.dailyCallLimit`
 *   - `PUT  /api/admin/ai-members/settings/stopped` `{ stopped }` → 전체 정지(바로 반영 — 저장 줄을 거치지 않는다)
 *   - `PUT  /api/admin/ai-members/settings/keys/{provider}` `{ key }` → 제공사 API 키(암호화 저장, 값은 다시 내려 주지 않는다 — 정책 12, 권한 `ai-member.secret` 후보)
 *   - `GET  /api/admin/ai-members/logs?aiMemberId=&result=&kind=&page=&size=` → `AiLogPage`
 * 공통 오류: 403 `PERMISSION_DENIED`.
 */
const PATH = {
    root: '/api/admin/ai-members',
    options: '/api/admin/ai-members/options',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    enabled: (id: string) => `${PATH.one(id)}/enabled`,
    drafts: '/api/admin/ai-members/drafts',
    draftsPublish: '/api/admin/ai-members/drafts/publish',
    draftsDiscard: '/api/admin/ai-members/drafts/discard',
    draftPublish: (id: string) => `${PATH.drafts}/${encodeURIComponent(id)}/publish`,
    settings: '/api/admin/ai-members/settings',
    stopped: '/api/admin/ai-members/settings/stopped',
    key: (provider: AiProvider) => `${PATH.settings}/keys/${provider}`,
    logs: '/api/admin/ai-members/logs',
};

export function useAiMemberAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 검수자·바꾼 사람 — 서버는 세션에서 안다
    const { user } = useAdminAuth();
    const me = () => user.value?.nickname ?? '운영자';

    return {
        list(): Promise<AiMemberListView> {
            // MOCK:
            return AiMemberAdminMock.list();
            // REAL: return client.get<AiMemberListView>(PATH.root);
        },

        options(): Promise<AiMemberFormOptions> {
            // MOCK:
            return AiMemberAdminMock.formOptions();
            // REAL: return client.get<AiMemberFormOptions>(PATH.options);
        },

        detail(id: string): Promise<AiMemberDetail> {
            // MOCK:
            return AiMemberAdminMock.detail(id);
            // REAL: return client.get<AiMemberDetail>(PATH.one(id));
        },

        save(id: string, body: AiMemberSaveRequest): Promise<AiMemberDetail> {
            // MOCK:
            return AiMemberAdminMock.save(id, body, me());
            // REAL: return client.put<AiMemberDetail>(PATH.one(id), body);
        },

        setEnabled(id: string, enabled: boolean): Promise<AiMemberRow> {
            // MOCK:
            return AiMemberAdminMock.setEnabled(id, enabled);
            // REAL: return client.put<AiMemberRow>(PATH.enabled(id), { enabled });
        },

        drafts(query: AiDraftListQuery = {}): Promise<AiDraftListView> {
            // MOCK:
            return AiMemberAdminMock.drafts(query);
            // REAL: return client.get<AiDraftListView>(PATH.drafts, { query: { ...query } });
        },

        publish(ids: string[]): Promise<AiDraftBatchResult> {
            // MOCK:
            return AiMemberAdminMock.publish(ids, me());
            // REAL: return client.post<AiDraftBatchResult>(PATH.draftsPublish, { ids });
        },

        publishEdited(id: string, body: AiDraftEditRequest): Promise<AiDraftBatchResult> {
            // MOCK:
            return AiMemberAdminMock.publishEdited(id, body, me());
            // REAL: return client.post<AiDraftBatchResult>(PATH.draftPublish(id), body);
        },

        discard(ids: string[]): Promise<AiDraftBatchResult> {
            // MOCK:
            return AiMemberAdminMock.discard(ids, me());
            // REAL: return client.post<AiDraftBatchResult>(PATH.draftsDiscard, { ids });
        },

        settings(): Promise<AiMemberSettings> {
            // MOCK:
            return AiMemberAdminMock.settings();
            // REAL: return client.get<AiMemberSettings>(PATH.settings);
        },

        saveSettings(body: AiMemberSettingsRequest): Promise<AiMemberSettings> {
            // MOCK:
            return AiMemberAdminMock.saveSettings(body);
            // REAL: return client.put<AiMemberSettings>(PATH.settings, body);
        },

        setStopped(stopped: boolean): Promise<AiMemberSettings> {
            // MOCK:
            return AiMemberAdminMock.setStopped(stopped, me());
            // REAL: return client.put<AiMemberSettings>(PATH.stopped, { stopped });
        },

        setKey(provider: AiProvider, key: string): Promise<AiMemberSettings> {
            // MOCK:
            return AiMemberAdminMock.setKey(provider, key, me());
            // REAL: return client.put<AiMemberSettings>(PATH.key(provider), { key });
        },

        logs(query: AiLogQuery = {}): Promise<AiLogPage> {
            // MOCK:
            return AiMemberAdminMock.logs(query);
            // REAL: return client.get<AiLogPage>(PATH.logs, { query: { ...query } });
        },
    };
}
