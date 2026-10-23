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
 * 계약(서버 `NotificationMessageController`, `docs/features/message-template-contract.md` 「서버 구현 결과(엔진·관리 API)」). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF.
 * 권한: 읽기·미리보기 `template.read` · 쓰기(대상 저장·문구·시험) `template.manage`.
 *   대상 = 이벤트(회원·운영자) 또는 필수 안내 메일. 문구 하나 = 대상 × 채널(`IN_APP|EMAIL|SLACK|MATTERMOST|KAKAO`) × 언어. 앱 알림이 아니면 그 채널의 템플릿 하나(`templateId`).
 *   id: 기본 문구 `builtin-<대상 코드>-<채널>-<언어>`, 사용자 문구 Snowflake(문자열). 「준비 중」 회원 이벤트는 대상에 없다(상세는 404 `MESSAGE_TARGET_NOT_FOUND`).
 *   - `GET    /api/admin/notification-messages/targets?type=&audience=` → 200 `MessageTargetView[]`(대상과 채널별 요약 — 변수·조건·언어별 쓰는 문구·규칙 수·준비 중)
 *   - `GET    /api/admin/notification-messages/targets/{type}/{code}` → 200 `MessageTargetDetailView`(채널마다 `defaults`·`selected`·`rules`·`messages`)
 *   - `PUT    /api/admin/notification-messages/targets/{type}/{code}` `MessageTargetSaveRequest` → 200 `MessageTargetDetailView`
 *       · 400 `VALIDATION` `fields.channels.<채널>.selected.ko|en` · `channels.<채널>.rules[i].condition|messageId` · `channels.<채널>.rules`(10개 넘음) · `channels.<채널>`(대상에 없는 채널 · 준비 중 채널)
 *   - `GET    /api/admin/notification-messages/messages` → 200 `MessageListItemView[]`(모든 대상·채널 — 본문 없이, 대상 이름·템플릿 이름·쓰는 곳 포함)
 *   - `GET    /api/admin/notification-messages/messages/{id}` → 200 `MessageView`
 *   - `POST   /api/admin/notification-messages/messages` `MessageSaveRequest` → 201 `MessageView` (409 `MESSAGE_LIMIT` — 대상·채널당 20, 409 `CHANNEL_PREPARING` — 카카오톡)
 *   - `PUT    /api/admin/notification-messages/messages/{id}` `MessageSaveRequest` → 200 `MessageView` (기본 문구 409 `MESSAGE_BUILTIN`, 대상·채널은 바꾸지 못한다)
 *   - `DELETE /api/admin/notification-messages/messages/{id}` → 204 (쓰는 중 409 `MESSAGE_IN_USE`)
 *       · 문구 저장의 400 `VALIDATION` `fields.name|locale|templateId|subject|body|channel` — 채널마다 다른 검사(이메일: 버튼·보안 메일 필수 변수·메일 템플릿의 받는 사람 / Slack·Mattermost: 제목 한 줄 200자·본문 1500자·그 종류의 템플릿 / 앱 알림: 제목 한 줄 100자·본문 300자), 모르는 변수(다른 받는 사람의 공통 변수 포함)
 *   - `POST   /api/admin/notification-messages/preview` `MailPreviewRequest` → 200 `{ subject, html }`(이메일만 — 견본 값 + 메일 템플릿, 또는 저장 전 `template`)
 *   - `POST   /api/admin/notification-messages/test` `MailMessageTestRequest` → 200 `{ succeeded, reason, detail }`(이메일 시험 발송 — 메일 설정 시험과 같은 모양·한도, 400 `fields.to`, 429 `MAIL_TEST_RATE_LIMITED`)
 *   - `POST   /api/admin/notification-messages/chat-test` `ChatMessageTestRequest` → 200 `{ ok, message }`(Slack·Mattermost 문구를 고른 템플릿을 씌워 그 종류의 알림 도구로 시험 게시, 400 `fields.channelId`, 429 `MAIL_TEST_RATE_LIMITED` — 알림 도구 연결 시험과 같은 창)
 *   - `GET    /api/admin/notification-messages/messages/{id}/history?page&size` → 200 `{ items, total, page, size }`(설정 이력 모양, 바뀐 칸 `name|locale|templateId|subject|body`)
 *   공통 오류: 404 `MESSAGE_NOT_FOUND`·`MESSAGE_TARGET_NOT_FOUND`·`TEMPLATE_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사: `NOTIFICATION_MESSAGE_CREATE|UPDATE|DELETE|TARGET_UPDATE|TEST`.
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
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 대상(이벤트 · 필수 안내 메일) 목록 — 채널별 요약 포함 */
        targets: (query: MessageTargetQuery = {}): Promise<MessageTargetView[]> => client.get<MessageTargetView[]>(PATH.targets(), { query }),

        /** 대상 하나의 채널별 쓸 문구·규칙·문구 목록 */
        target: (type: MessageTargetType, code: string): Promise<MessageTargetDetailView> => client.get<MessageTargetDetailView>(PATH.target(type, code)),

        /** 채널마다 쓸 문구(언어별)와 규칙(목록 전체, 차례 그대로)을 한 번에 저장한다 */
        saveTarget: (type: MessageTargetType, code: string, body: MessageTargetSaveRequest): Promise<MessageTargetDetailView> =>
            client.put<MessageTargetDetailView>(PATH.target(type, code), body),

        /** 모든 문구 목록(본문 없이) */
        messages: (): Promise<MessageListItemView[]> => client.get<MessageListItemView[]>(PATH.messages()),

        /** 문구 하나(본문 포함) */
        message: (id: string): Promise<MessageView> => client.get<MessageView>(PATH.message(id)),

        /** 사용자 문구를 만든다(복사해서 만들 때도 이것) */
        create: (body: MessageSaveRequest): Promise<MessageView> => client.post<MessageView>(PATH.messages(), body),

        update: (id: string, body: MessageSaveRequest): Promise<MessageView> => client.put<MessageView>(PATH.message(id), body),

        /** 쓰는 중이면 서버가 409 `MESSAGE_IN_USE` 로 거절한다 */
        remove: (id: string): Promise<void> => client.del<void>(PATH.message(id)),

        /** 이메일 미리보기 — 견본 값 + 메일 템플릿 */
        preview: (body: MailPreviewRequest): Promise<MailPreviewView> => client.post<MailPreviewView>(PATH.preview(), body),

        /** 이메일 시험 발송 — 받는 주소는 이 호출의 인자로만 지나간다 */
        test: (body: MailMessageTestRequest): Promise<MailMessageTestResultView> => client.post<MailMessageTestResultView>(PATH.test(), body),

        /** Slack·Mattermost 시험 — 고른 알림 도구로 템플릿을 씌우고 견본 값을 채운 글을 올린다 */
        chatTest: (body: ChatMessageTestRequest): Promise<ChatMessageTestResultView> => client.post<ChatMessageTestResultView>(PATH.chatTest(), body),

        /** 문구 변경 이력 — 최신이 위, `page` 는 0 부터 */
        history: (id: string, page: number, size: number): Promise<MessageHistoryPage> => client.get<MessageHistoryPage>(PATH.history(id), { query: { page, size } }),
    };
}
