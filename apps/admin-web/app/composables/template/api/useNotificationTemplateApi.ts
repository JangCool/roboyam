import {
    mockChatTest,
    mockCreateTemplate,
    mockDeleteTemplate,
    mockHistory,
    mockPreview,
    mockSaveTarget,
    mockTarget,
    mockTargets,
    mockTemplate,
    mockTemplateCan,
    mockTemplates,
    mockTest,
    mockUpdateTemplate,
} from '~/composables/template/api/mock/notification-template-mock';
import type {
    ChatTemplateTestRequest,
    ChatTemplateTestResultView,
    MailPreviewRequest,
    MailPreviewView,
    MailTemplateTestRequest,
    MailTemplateTestResultView,
    TemplateHistoryPage,
    TemplateListItemView,
    TemplateSaveRequest,
    TemplateTargetDetailView,
    TemplateTargetQuery,
    TemplateTargetSaveRequest,
    TemplateTargetType,
    TemplateTargetView,
    TemplateView,
} from '~/composables/template/api/useNotificationTemplateApi.types';

/**
 * 알림 템플릿 API — 서버 주소 문자열(`/api/admin/notification-templates…`)이 있는 **유일한 곳**이다. 상태는 없다. 메일 레이아웃은 `useMailLayoutApi`(`/api/admin/mail-layouts`).
 *
 * 후보 계약(`docs/features/message-template-contract.md` 「개정 3」, 서버 단계에서 확정). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF.
 * 권한: 읽기 `template.read` · 쓰기(대상 저장·템플릿·시험) `template.manage` — 서버 `Permission` 에는 아직 없다(화면 목업 후보).
 *   대상 = 이벤트(회원·운영자) 또는 필수 안내 메일. 템플릿 하나 = 대상 × 채널(`IN_APP|EMAIL|CHAT`) × 언어.
 *   - `GET    /api/admin/notification-templates/targets?type=&audience=` → 200 `TemplateTargetView[]`(대상과 채널별 요약 — 변수·조건·언어별 쓰는 템플릿·규칙 수)
 *   - `GET    /api/admin/notification-templates/targets/{type}/{code}` → 200 `TemplateTargetDetailView`(채널마다 `defaults`·`selected`·`rules`·`templates`)
 *   - `PUT    /api/admin/notification-templates/targets/{type}/{code}` `TemplateTargetSaveRequest` → 200 `TemplateTargetDetailView`
 *       · 400 `VALIDATION` `fields.channels.<채널>.selected.ko|en` · `channels.<채널>.rules[i].condition|templateId` · `channels.<채널>.rules`(10개 넘음) · `channels.<채널>`(대상에 없는 채널)
 *   - `GET    /api/admin/notification-templates/templates` → 200 `TemplateListItemView[]`(모든 대상·채널 — 본문 없이, 대상 이름·레이아웃 이름·쓰는 곳 포함)
 *   - `GET    /api/admin/notification-templates/templates/{id}` → 200 `TemplateView`
 *   - `POST   /api/admin/notification-templates/templates` `TemplateSaveRequest` → 201 `TemplateView` (409 `TEMPLATE_LIMIT` — 대상·채널당 20 가정)
 *   - `PUT    /api/admin/notification-templates/templates/{id}` `TemplateSaveRequest` → 200 `TemplateView` (기본 템플릿 409 `TEMPLATE_BUILTIN`, 대상·채널은 바꾸지 못한다)
 *   - `DELETE /api/admin/notification-templates/templates/{id}` → 204 (쓰는 중 409 `TEMPLATE_IN_USE`)
 *       · 템플릿 저장의 400 `VALIDATION` `fields.name|locale|layoutId|subject|body|channel` — 채널마다 다른 검사(이메일: 버튼·보안 메일 필수 변수·레이아웃 / 채팅: 본문 2000자 / 앱 알림: 제목 한 줄 100자·본문 300자), 모르는 변수(다른 받는 사람의 공통 변수 포함)
 *   - `POST   /api/admin/notification-templates/preview` `MailPreviewRequest` → 200 `{ subject, html }`(이메일만 — 견본 값 + 레이아웃, 또는 저장 전 `layout`)
 *   - `POST   /api/admin/notification-templates/test` `MailTemplateTestRequest` → 200 `{ succeeded, reason, detail }`(이메일 시험 발송 — 메일 설정 시험과 같은 모양·한도, 400 `fields.to`, 429 `MAIL_TEST_RATE_LIMITED`)
 *   - `POST   /api/admin/notification-templates/chat-test` `ChatTemplateTestRequest` → 200 `{ ok, message }`(채팅 글을 고른 알림 도구로 시험 게시, 400 `fields.channelId`, 429)
 *   - `GET    /api/admin/notification-templates/templates/{id}/history?page&size` → 200 `{ items, total, page, size }`(설정 이력 모양, 바뀐 칸 `name|locale|layoutId|subject|body`)
 *   공통 오류: 404 `TEMPLATE_NOT_FOUND`·`TEMPLATE_TARGET_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사 후보: `NOTIFICATION_TEMPLATE_CREATE|UPDATE|DELETE` · `NOTIFICATION_TEMPLATE_TARGET_UPDATE`.
 *   없앤 것(개정 3): `/api/admin/mail-templates/**`(메일 종류 `kinds` — 대상(`targets`)이 대신한다).
 *
 * ⚠ MOCK: 서버가 없어서 `./mock/notification-template-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄(과 `can`·목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/notification-templates',
    targets: () => `${PATH.root}/targets`,
    target: (type: TemplateTargetType, code: string) => `${PATH.targets()}/${type}/${encodeURIComponent(code)}`,
    templates: () => `${PATH.root}/templates`,
    template: (id: string) => `${PATH.templates()}/${encodeURIComponent(id)}`,
    history: (id: string) => `${PATH.template(id)}/history`,
    preview: () => `${PATH.root}/preview`,
    test: () => `${PATH.root}/test`,
    chatTest: () => `${PATH.root}/chat-test`,
};

export function useNotificationTemplateApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 내 권한(`template.read`·`template.manage`, `?mock=template-readonly|template-denied`)으로 403 을 낸다
    const can = mockTemplateCan(useAdminPermission().can);
    void PATH;

    return {
        /** 대상(이벤트 · 필수 안내 메일) 목록 — 채널별 요약 포함 */
        targets: (query: TemplateTargetQuery = {}): Promise<TemplateTargetView[]> =>
            // MOCK:
            mockTargets(can, query),
        // REAL: client.get<TemplateTargetView[]>(PATH.targets(), { query }),

        /** 대상 하나의 채널별 쓸 템플릿·규칙·템플릿 목록 */
        target: (type: TemplateTargetType, code: string): Promise<TemplateTargetDetailView> =>
            // MOCK:
            mockTarget(can, type, code),
        // REAL: client.get<TemplateTargetDetailView>(PATH.target(type, code)),

        /** 채널마다 쓸 템플릿(언어별)과 규칙(목록 전체, 차례 그대로)을 한 번에 저장한다 */
        saveTarget: (type: TemplateTargetType, code: string, body: TemplateTargetSaveRequest): Promise<TemplateTargetDetailView> =>
            // MOCK:
            mockSaveTarget(can, type, code, body),
        // REAL: client.put<TemplateTargetDetailView>(PATH.target(type, code), body),

        /** 모든 템플릿 목록(본문 없이) */
        templates: (): Promise<TemplateListItemView[]> =>
            // MOCK:
            mockTemplates(can),
        // REAL: client.get<TemplateListItemView[]>(PATH.templates()),

        /** 템플릿 하나(본문 포함) */
        template: (id: string): Promise<TemplateView> =>
            // MOCK:
            mockTemplate(can, id),
        // REAL: client.get<TemplateView>(PATH.template(id)),

        /** 사용자 템플릿을 만든다(복사해서 만들 때도 이것) */
        create: (body: TemplateSaveRequest): Promise<TemplateView> =>
            // MOCK:
            mockCreateTemplate(can, body),
        // REAL: client.post<TemplateView>(PATH.templates(), body),

        update: (id: string, body: TemplateSaveRequest): Promise<TemplateView> =>
            // MOCK:
            mockUpdateTemplate(can, id, body),
        // REAL: client.put<TemplateView>(PATH.template(id), body),

        /** 쓰는 중이면 서버가 409 `TEMPLATE_IN_USE` 로 거절한다 */
        remove: (id: string): Promise<void> =>
            // MOCK:
            mockDeleteTemplate(can, id),
        // REAL: client.del<void>(PATH.template(id)),

        /** 이메일 미리보기 — 견본 값 + 레이아웃 */
        preview: (body: MailPreviewRequest): Promise<MailPreviewView> =>
            // MOCK:
            mockPreview(can, body),
        // REAL: client.post<MailPreviewView>(PATH.preview(), body),

        /** 이메일 시험 발송 — 받는 주소는 이 호출의 인자로만 지나간다 */
        test: (body: MailTemplateTestRequest): Promise<MailTemplateTestResultView> =>
            // MOCK:
            mockTest(can, body),
        // REAL: client.post<MailTemplateTestResultView>(PATH.test(), body),

        /** 채팅 시험 — 고른 알림 도구(Slack·Mattermost)로 견본 값을 채운 글을 올린다 */
        chatTest: (body: ChatTemplateTestRequest): Promise<ChatTemplateTestResultView> =>
            // MOCK:
            mockChatTest(can, body),
        // REAL: client.post<ChatTemplateTestResultView>(PATH.chatTest(), body),

        /** 템플릿 변경 이력 — 최신이 위, `page` 는 0 부터 */
        history: (id: string, page: number, size: number): Promise<TemplateHistoryPage> =>
            // MOCK:
            mockHistory(can, id, page, size),
        // REAL: client.get<TemplateHistoryPage>(PATH.history(id), { query: { page, size } }),
    };
}
