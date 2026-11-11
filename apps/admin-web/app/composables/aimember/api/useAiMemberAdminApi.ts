import type {
    AiDraftBatchResult,
    AiDraftEditRequest,
    AiDraftListQuery,
    AiDraftListView,
    AiGroupAiOnlyResult,
    AiLogPage,
    AiLogQuery,
    AiMemberCreateRequest,
    AiMemberDetail,
    AiMemberFormOptions,
    AiMemberListView,
    AiMemberRow,
    AiMemberSaveRequest,
    AiMemberSettings,
    AiMemberSettingsRequest,
    AiModelCreateRequest,
    AiModelList,
    AiPromptPreview,
    AiPromptPreviewRequest,
    AiPromptSaveRequest,
    AiPromptSettings,
    AiProvider,
} from '~/composables/aimember/api/useAiMemberAdminApi.types';

/**
 * AI 회원 관리 API — 서버 주소 문자열(`/api/admin/ai-members…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/ai-members-contract.md`.
 * 서버 `server/admin` `aimember.controller.AiMemberAdminController`. 권한: 조회 `ai-member.read` · 쓰기 `ai-member.manage` · 제공사 키 `ai-member.secret`. 응답은 `Cache-Control: no-store`.
 *
 *   - `GET  /api/admin/ai-members` → `AiMemberListView`
 *   - `GET  /api/admin/ai-members/options` → `AiMemberFormOptions` — 제공사별 켜진 모델(모델 목록이 원천 — 가격·기본 표시 포함)·게시판(막힌 이유 포함)·AI 전용 소모임(열린 것만)·관계 상대 후보
 *   - `POST /api/admin/ai-members` `AiMemberCreateRequest` → 201 `AiMemberDetail` — 서버가 로그인할 수 없는 회원 계정을 만든다(처음엔 꺼짐)
 *       · 400 `VALIDATION`(`fields.nickname` + 고치기와 같은 칸) · 409 `NICKNAME_TAKEN`(칸 없이 — 화면이 닉네임 칸에 붙인다)
 *   - `GET  /api/admin/ai-members/{id}` → `AiMemberDetail` · 404 `AI_MEMBER_NOT_FOUND`
 *   - `PUT  /api/admin/ai-members/{id}` `AiMemberSaveRequest` → `AiMemberDetail` · 400 `VALIDATION`(`fields.provider`·`model`·`persona.*`·`bio`·`windows.*`·`dailyPosts`·`boards`·`groups`·`relation`·`avatarId`)
 *       관계는 서버가 상대 쪽까지 같이 적고(옛 상대는 풀고) 감사 로그를 남긴다. `avatarId` 가 빠지거나 null 이면 아바타는 그대로.
 *   - `PUT  /api/admin/ai-members/{id}/enabled` `{ enabled }` → `AiMemberRow`
 *   - `PUT  /api/admin/ai-members/groups/{groupId}` `{ aiOnly }` → `AiGroupAiOnlyResult` — AI 전용 소모임 지정·해제 · 404 `GROUP_NOT_FOUND` · 409 `GROUP_NOT_PUBLIC`(공개 소모임만 지정 — 서버 추가 예정)
 *   - `GET  /api/admin/ai-members/drafts?kind=&aiMemberId=` → `AiDraftListView` — 최신이 위
 *   - `POST /api/admin/ai-members/drafts/publish` `{ ids }` · `POST …/drafts/discard` `{ ids }` → `AiDraftBatchResult`(그 사이 처리된 건은 `skipped`)
 *       게시는 상태만 승인으로 바꾸고, 실제 등록은 다음 바퀴(최대 1분)에 api 서버 실행기가 기존 글·댓글 서비스로 한다(정책 9·11).
 *   - `POST /api/admin/ai-members/drafts/{id}/publish` `AiDraftEditRequest` → 수정 후 게시 · 404 `AI_DRAFT_NOT_FOUND` · 400 `VALIDATION`(`fields.title`·`body`) · 400 `BLOCKED_WORD`
 *   - `GET  /api/admin/ai-members/settings` · `PUT …/settings` `AiMemberSettingsRequest` → `AiMemberSettings` · 400 `VALIDATION`(`fields.instantScope`·`instantAccountIds`·`dailyCallLimit`)
 *   - `PUT  /api/admin/ai-members/settings/stopped` `{ stopped }` → 전체 정지(바로 반영 — 저장 줄을 거치지 않는다)
 *   - `PUT  /api/admin/ai-members/settings/keys/{provider}` `{ key }` → 제공사 API 키(암호화 저장, 값은 다시 내려 주지 않는다 — 정책 12) · 400 `fields.key` · 503 `CRYPTO_KEY_MISSING`
 *   - `GET  /api/admin/ai-members/logs?aiMemberId=&result=&kind=&page=&size=` → `AiLogPage`(page 1부터)
 *   - `GET  /api/admin/ai-members/models` → `AiModelList`(제공사 → 목록 차례) · 쓰기는 `ai-member.manage`, 모두 새 목록 전체를 돌려준다:
 *       `POST …/models` `AiModelCreateRequest` → 201 · 400 `VALIDATION`(`fields.provider`·`modelId`(형식·중복)·`label`·`priceInput`·`priceOutput`)
 *       `PUT  …/models/{id}/enabled` `{ enabled }` · `PUT …/models/{id}/default`(꺼진 모델이면 함께 켠다) · `DELETE …/models/{id}`
 *       · 404 `AI_MODEL_NOT_FOUND` · 409 `AI_MODEL_DEFAULT_LOCKED`(기본 모델은 끄거나 지울 수 없다) · 409 `AI_MODEL_IN_USE`(쓰는 계정이 있다 — 대신 끄기)
 *   - `GET  /api/admin/ai-members/prompts` → `AiPromptSettings`(지금 값 · 기본값 · 고정 규칙 · 자리표시 · 한도)
 *   - `PUT  /api/admin/ai-members/prompts` `AiPromptSaveRequest` → `AiPromptSettings`(`ai-member.manage`, 감사 `AI_PROMPT_UPDATE`)
 *       · 400 `VALIDATION`(`fields.common`·`post`·`news`·`comment`·`reply`·`moodChance`·`moods`·`moods[i].name`·`moods[i].prompt`·`newsRatio`·`newsKeyword`·`newsDays`·`paragraphSentences`)
 *   - `POST /api/admin/ai-members/prompts/preview` `AiPromptPreviewRequest` → `AiPromptPreview`(읽기 권한 · 제공사 호출 없음)
 *       · 404 `AI_MEMBER_NOT_FOUND` · 400 `VALIDATION`(`fields.aiMemberId`·`kind`·`mood`·`settings.*`)
 * 공통 오류: 403 `PERMISSION_DENIED`.
 */
const PATH = {
    root: '/api/admin/ai-members',
    options: '/api/admin/ai-members/options',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    enabled: (id: string) => `${PATH.one(id)}/enabled`,
    group: (groupId: string) => `${PATH.root}/groups/${encodeURIComponent(groupId)}`,
    drafts: '/api/admin/ai-members/drafts',
    draftsPublish: '/api/admin/ai-members/drafts/publish',
    draftsDiscard: '/api/admin/ai-members/drafts/discard',
    draftPublish: (id: string) => `${PATH.drafts}/${encodeURIComponent(id)}/publish`,
    settings: '/api/admin/ai-members/settings',
    stopped: '/api/admin/ai-members/settings/stopped',
    key: (provider: AiProvider) => `${PATH.settings}/keys/${provider}`,
    logs: '/api/admin/ai-members/logs',
    models: '/api/admin/ai-members/models',
    model: (id: string) => `${PATH.models}/${encodeURIComponent(id)}`,
    prompts: '/api/admin/ai-members/prompts',
    promptPreview: '/api/admin/ai-members/prompts/preview',
};

export function useAiMemberAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (): Promise<AiMemberListView> => client.get<AiMemberListView>(PATH.root),

        options: (): Promise<AiMemberFormOptions> => client.get<AiMemberFormOptions>(PATH.options),

        /** 새 AI 회원(201) — 처음엔 꺼져 있다 */
        create: (body: AiMemberCreateRequest): Promise<AiMemberDetail> => client.post<AiMemberDetail>(PATH.root, body),

        detail: (id: string): Promise<AiMemberDetail> => client.get<AiMemberDetail>(PATH.one(id)),

        save: (id: string, body: AiMemberSaveRequest): Promise<AiMemberDetail> => client.put<AiMemberDetail>(PATH.one(id), body),

        setEnabled: (id: string, enabled: boolean): Promise<AiMemberRow> => client.put<AiMemberRow>(PATH.enabled(id), { enabled }),

        /** AI 전용 소모임 지정·해제 */
        setGroupAiOnly: (groupId: string, aiOnly: boolean): Promise<AiGroupAiOnlyResult> => client.put<AiGroupAiOnlyResult>(PATH.group(groupId), { aiOnly }),

        drafts: (query: AiDraftListQuery = {}): Promise<AiDraftListView> => client.get<AiDraftListView>(PATH.drafts, { query: { ...query } }),

        publish: (ids: string[]): Promise<AiDraftBatchResult> => client.post<AiDraftBatchResult>(PATH.draftsPublish, { ids }),

        publishEdited: (id: string, body: AiDraftEditRequest): Promise<AiDraftBatchResult> => client.post<AiDraftBatchResult>(PATH.draftPublish(id), body),

        discard: (ids: string[]): Promise<AiDraftBatchResult> => client.post<AiDraftBatchResult>(PATH.draftsDiscard, { ids }),

        settings: (): Promise<AiMemberSettings> => client.get<AiMemberSettings>(PATH.settings),

        saveSettings: (body: AiMemberSettingsRequest): Promise<AiMemberSettings> => client.put<AiMemberSettings>(PATH.settings, body),

        setStopped: (stopped: boolean): Promise<AiMemberSettings> => client.put<AiMemberSettings>(PATH.stopped, { stopped }),

        setKey: (provider: AiProvider, key: string): Promise<AiMemberSettings> => client.put<AiMemberSettings>(PATH.key(provider), { key }),

        logs: (query: AiLogQuery = {}): Promise<AiLogPage> => client.get<AiLogPage>(PATH.logs, { query: { ...query } }),

        /** 모델 목록 — 바꾸기는 모두 새 목록 전체를 돌려준다 */
        models: (): Promise<AiModelList> => client.get<AiModelList>(PATH.models),

        /** 모델 직접 추가(201) */
        createModel: (body: AiModelCreateRequest): Promise<AiModelList> => client.post<AiModelList>(PATH.models, body),

        setModelEnabled: (id: string, enabled: boolean): Promise<AiModelList> => client.put<AiModelList>(`${PATH.model(id)}/enabled`, { enabled }),

        setModelDefault: (id: string): Promise<AiModelList> => client.put<AiModelList>(`${PATH.model(id)}/default`, {}),

        deleteModel: (id: string): Promise<AiModelList> => client.del<AiModelList>(PATH.model(id)),

        /** 프롬프트 설정 */
        prompts: (): Promise<AiPromptSettings> => client.get<AiPromptSettings>(PATH.prompts),

        savePrompts: (body: AiPromptSaveRequest): Promise<AiPromptSettings> => client.put<AiPromptSettings>(PATH.prompts, body),

        /** 저장 전 화면 값으로 실제 보낼 메시지 미리 보기(제공사 호출 없음) */
        previewPrompt: (body: AiPromptPreviewRequest): Promise<AiPromptPreview> => client.post<AiPromptPreview>(PATH.promptPreview, body),
    };
}
