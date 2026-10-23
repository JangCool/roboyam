import {
    mockChatTest,
    mockCreateMessage,
    mockDeleteMessage,
    mockMessage,
    mockMessageHistory,
    mockMessages,
    mockPreview,
    mockSaveTarget,
    mockTarget,
    mockTargets,
    mockTemplateCan,
    mockTest,
    mockUpdateMessage,
} from '~/composables/template/api/mock/notification-template-mock';
import type {
    ChatMessageTestRequest,
    ChatMessageTestResultView,
    MailMessageTestRequest,
    MailMessageTestResultView,
    MailPreviewRequest,
    MailPreviewView,
    MessageHistoryPage,
    MessageListItemView,
    MessageSaveRequest,
    MessageTargetDetailView,
    MessageTargetQuery,
    MessageTargetSaveRequest,
    MessageTargetType,
    MessageTargetView,
    MessageView,
} from '~/composables/template/api/useNotificationMessageApi.types';

/**
 * 알림 문구 API — 서버 주소 문자열(`/api/admin/notification-messages…`)이 있는 **유일한 곳**이다. 상태는 없다. 템플릿(겉모양)은 도구별 API(`useMailTemplateApi` · `useChatTemplateApi` · `useKakaoTemplateApi`).
 *
 * 후보 계약(`docs/features/message-template-contract.md` 「개정 4」 — 개정 3 의 `/api/admin/notification-templates/{targets|templates}` 를 이름만 바꿔 옮겼다, 서버 단계에서 확정). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF.
 * 권한: 읽기 `template.read` · 쓰기(대상 저장·문구·시험) `template.manage` — 서버 `Permission` 에는 아직 없다(화면 목업 후보).
 *   대상 = 이벤트(회원·운영자) 또는 필수 안내 메일. 문구 하나 = 대상 × 채널(`IN_APP|EMAIL|SLACK|MATTERMOST|KAKAO`) × 언어. 앱 알림이 아니면 그 채널의 템플릿 하나(`templateId`).
 *   - `GET    /api/admin/notification-messages/targets?type=&audience=` → 200 `MessageTargetView[]`(대상과 채널별 요약 — 변수·조건·언어별 쓰는 문구·규칙 수·준비 중)
 *   - `GET    /api/admin/notification-messages/targets/{type}/{code}` → 200 `MessageTargetDetailView`(채널마다 `defaults`·`selected`·`rules`·`messages`)
 *   - `PUT    /api/admin/notification-messages/targets/{type}/{code}` `MessageTargetSaveRequest` → 200 `MessageTargetDetailView`
 *       · 400 `VALIDATION` `fields.channels.<채널>.selected.ko|en` · `channels.<채널>.rules[i].condition|messageId` · `channels.<채널>.rules`(10개 넘음) · `channels.<채널>`(대상에 없는 채널 · 준비 중 채널)
 *   - `GET    /api/admin/notification-messages/messages` → 200 `MessageListItemView[]`(모든 대상·채널 — 본문 없이, 대상 이름·템플릿 이름·쓰는 곳 포함)
 *   - `GET    /api/admin/notification-messages/messages/{id}` → 200 `MessageView`
 *   - `POST   /api/admin/notification-messages/messages` `MessageSaveRequest` → 201 `MessageView` (409 `MESSAGE_LIMIT` — 대상·채널당 20 가정, 409 `CHANNEL_PREPARING` — 카카오톡)
 *   - `PUT    /api/admin/notification-messages/messages/{id}` `MessageSaveRequest` → 200 `MessageView` (기본 문구 409 `MESSAGE_BUILTIN`, 대상·채널은 바꾸지 못한다)
 *   - `DELETE /api/admin/notification-messages/messages/{id}` → 204 (쓰는 중 409 `MESSAGE_IN_USE`)
 *       · 문구 저장의 400 `VALIDATION` `fields.name|locale|templateId|subject|body|channel` — 채널마다 다른 검사(이메일: 버튼·보안 메일 필수 변수·메일 템플릿의 받는 사람 / Slack·Mattermost: 제목 한 줄 200자·본문 1500자·그 종류의 템플릿 / 앱 알림: 제목 한 줄 100자·본문 300자), 모르는 변수(다른 받는 사람의 공통 변수 포함)
 *   - `POST   /api/admin/notification-messages/preview` `MailPreviewRequest` → 200 `{ subject, html }`(이메일만 — 견본 값 + 메일 템플릿, 또는 저장 전 `template`)
 *   - `POST   /api/admin/notification-messages/test` `MailMessageTestRequest` → 200 `{ succeeded, reason, detail }`(이메일 시험 발송 — 메일 설정 시험과 같은 모양·한도, 400 `fields.to`, 429 `MAIL_TEST_RATE_LIMITED`)
 *   - `POST   /api/admin/notification-messages/chat-test` `ChatMessageTestRequest` → 200 `{ ok, message }`(Slack·Mattermost 문구를 고른 템플릿을 씌워 그 종류의 알림 도구로 시험 게시, 400 `fields.channelId`, 429)
 *   - `GET    /api/admin/notification-messages/messages/{id}/history?page&size` → 200 `{ items, total, page, size }`(설정 이력 모양, 바뀐 칸 `name|locale|templateId|subject|body`)
 *   공통 오류: 404 `MESSAGE_NOT_FOUND`·`MESSAGE_TARGET_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사 후보: `NOTIFICATION_MESSAGE_CREATE|UPDATE|DELETE` · `NOTIFICATION_MESSAGE_TARGET_UPDATE`.
 *
 * ⚠ MOCK: 서버가 없어서 `./mock/notification-template-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄(과 `can`·목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/notification-messages',
    targets: () => `${PATH.root}/targets`,
    target: (type: MessageTargetType, code: string) => `${PATH.targets()}/${type}/${encodeURIComponent(code)}`,
    messages: () => `${PATH.root}/messages`,
    message: (id: string) => `${PATH.messages()}/${encodeURIComponent(id)}`,
    history: (id: string) => `${PATH.message(id)}/history`,
    preview: () => `${PATH.root}/preview`,
    test: () => `${PATH.root}/test`,
    chatTest: () => `${PATH.root}/chat-test`,
};

export function useNotificationMessageApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 내 권한(`template.read`·`template.manage`, `?mock=template-readonly|template-denied`)으로 403 을 낸다
    const can = mockTemplateCan(useAdminPermission().can);
    void PATH;

    return {
        /** 대상(이벤트 · 필수 안내 메일) 목록 — 채널별 요약 포함 */
        targets: (query: MessageTargetQuery = {}): Promise<MessageTargetView[]> =>
            // MOCK:
            mockTargets(can, query),
        // REAL: client.get<MessageTargetView[]>(PATH.targets(), { query }),

        /** 대상 하나의 채널별 쓸 문구·규칙·문구 목록 */
        target: (type: MessageTargetType, code: string): Promise<MessageTargetDetailView> =>
            // MOCK:
            mockTarget(can, type, code),
        // REAL: client.get<MessageTargetDetailView>(PATH.target(type, code)),

        /** 채널마다 쓸 문구(언어별)와 규칙(목록 전체, 차례 그대로)을 한 번에 저장한다 */
        saveTarget: (type: MessageTargetType, code: string, body: MessageTargetSaveRequest): Promise<MessageTargetDetailView> =>
            // MOCK:
            mockSaveTarget(can, type, code, body),
        // REAL: client.put<MessageTargetDetailView>(PATH.target(type, code), body),

        /** 모든 문구 목록(본문 없이) */
        messages: (): Promise<MessageListItemView[]> =>
            // MOCK:
            mockMessages(can),
        // REAL: client.get<MessageListItemView[]>(PATH.messages()),

        /** 문구 하나(본문 포함) */
        message: (id: string): Promise<MessageView> =>
            // MOCK:
            mockMessage(can, id),
        // REAL: client.get<MessageView>(PATH.message(id)),

        /** 사용자 문구를 만든다(복사해서 만들 때도 이것) */
        create: (body: MessageSaveRequest): Promise<MessageView> =>
            // MOCK:
            mockCreateMessage(can, body),
        // REAL: client.post<MessageView>(PATH.messages(), body),

        update: (id: string, body: MessageSaveRequest): Promise<MessageView> =>
            // MOCK:
            mockUpdateMessage(can, id, body),
        // REAL: client.put<MessageView>(PATH.message(id), body),

        /** 쓰는 중이면 서버가 409 `MESSAGE_IN_USE` 로 거절한다 */
        remove: (id: string): Promise<void> =>
            // MOCK:
            mockDeleteMessage(can, id),
        // REAL: client.del<void>(PATH.message(id)),

        /** 이메일 미리보기 — 견본 값 + 메일 템플릿 */
        preview: (body: MailPreviewRequest): Promise<MailPreviewView> =>
            // MOCK:
            mockPreview(can, body),
        // REAL: client.post<MailPreviewView>(PATH.preview(), body),

        /** 이메일 시험 발송 — 받는 주소는 이 호출의 인자로만 지나간다 */
        test: (body: MailMessageTestRequest): Promise<MailMessageTestResultView> =>
            // MOCK:
            mockTest(can, body),
        // REAL: client.post<MailMessageTestResultView>(PATH.test(), body),

        /** Slack·Mattermost 시험 — 고른 알림 도구로 템플릿을 씌우고 견본 값을 채운 글을 올린다 */
        chatTest: (body: ChatMessageTestRequest): Promise<ChatMessageTestResultView> =>
            // MOCK:
            mockChatTest(can, body),
        // REAL: client.post<ChatMessageTestResultView>(PATH.chatTest(), body),

        /** 문구 변경 이력 — 최신이 위, `page` 는 0 부터 */
        history: (id: string, page: number, size: number): Promise<MessageHistoryPage> =>
            // MOCK:
            mockMessageHistory(can, id, page, size),
        // REAL: client.get<MessageHistoryPage>(PATH.history(id), { query: { page, size } }),
    };
}
