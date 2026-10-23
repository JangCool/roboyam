import type { ChatTemplateHistoryPage, ChatTemplateListItemView, ChatTemplateSaveRequest, ChatTemplateType, ChatTemplateView } from '~/composables/template/api/useChatTemplateApi.types';

/**
 * Slack · Mattermost 템플릿 API — 서버 주소 문자열(`/api/admin/notification-templates/{slack|mattermost}…`)이 있는 **유일한 곳**이다. 상태는 없다. 미리보기는 화면이 그린다(`UtilChatTemplate.card`).
 *
 * 계약(서버 `NotificationTemplateController`, `docs/features/message-template-contract.md` 「서버 구현 결과(엔진·관리 API)」). 권한: 읽기 `template.read` · 쓰기 `template.manage`. 종류는 주소 조각(`slack` · `mattermost`)이다.
 * id: 기본 템플릿 `slack-default` · `mattermost-default`(코드가 준다), 사용자 템플릿 Snowflake(문자열).
 *   - `GET    /api/admin/notification-templates/{type}` → 200 `ChatTemplateListItemView[]`(기본 템플릿 먼저, 쓰는 문구 수)
 *   - `GET    /api/admin/notification-templates/{type}/{id}` → 200 `ChatTemplateView`(사이트 이름 · 틀에서 쓸 수 있는 변수 포함)
 *   - `POST   /api/admin/notification-templates/{type}` `ChatTemplateSaveRequest` → 201 (409 `TEMPLATE_LIMIT` — 종류마다 20)
 *   - `PUT    /api/admin/notification-templates/{type}/{id}` `ChatTemplateSaveRequest` → 200 (기본 템플릿 409 `TEMPLATE_BUILTIN`)
 *       · 400 `VALIDATION` `fields.name|colors.INFO|colors.WARNING|colors.CRITICAL|openLabel.ko|openLabel.en|frames.ko|frames.en` — 이름 · 팔레트 밖 색 · 버튼 글자(보일 때 필수·20자) · 틀의 `{{content}}` 한 번·500자·모르는 변수
 *   - `DELETE /api/admin/notification-templates/{type}/{id}` → 204 (쓰는 문구가 있으면 409 `TEMPLATE_IN_USE`, 기본 템플릿 409 `TEMPLATE_BUILTIN`)
 *   - `GET    /api/admin/notification-templates/{type}/{id}/history?page&size` → 200 설정 이력 모양
 *   공통 오류: 404 `TEMPLATE_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사: `NOTIFICATION_TEMPLATE_CREATE|UPDATE|DELETE`.
 */
const PATH = {
    root: (type: ChatTemplateType) => `/api/admin/notification-templates/${type.toLowerCase()}`,
    template: (type: ChatTemplateType, id: string) => `${PATH.root(type)}/${encodeURIComponent(id)}`,
    history: (type: ChatTemplateType, id: string) => `${PATH.template(type, id)}/history`,
};

export function useChatTemplateApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 그 종류의 템플릿 목록 */
        list: (type: ChatTemplateType): Promise<ChatTemplateListItemView[]> => client.get<ChatTemplateListItemView[]>(PATH.root(type)),

        /** 템플릿 하나 */
        get: (type: ChatTemplateType, id: string): Promise<ChatTemplateView> => client.get<ChatTemplateView>(PATH.template(type, id)),

        /** 사용자 템플릿을 만든다(복사해서 만들 때도 이것) */
        create: (type: ChatTemplateType, body: ChatTemplateSaveRequest): Promise<ChatTemplateView> => client.post<ChatTemplateView>(PATH.root(type), body),

        update: (type: ChatTemplateType, id: string, body: ChatTemplateSaveRequest): Promise<ChatTemplateView> => client.put<ChatTemplateView>(PATH.template(type, id), body),

        /** 쓰는 문구가 있으면 서버가 409 `TEMPLATE_IN_USE` 로 거절한다 */
        remove: (type: ChatTemplateType, id: string): Promise<void> => client.del<void>(PATH.template(type, id)),

        /** 변경 이력 — 최신이 위, `page` 는 0 부터 */
        history: (type: ChatTemplateType, id: string, page: number, size: number): Promise<ChatTemplateHistoryPage> =>
            client.get<ChatTemplateHistoryPage>(PATH.history(type, id), { query: { page, size } }),
    };
}
