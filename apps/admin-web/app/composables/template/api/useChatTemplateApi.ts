import {
    mockChatTemplate,
    mockChatTemplateHistory,
    mockChatTemplates,
    mockCreateChatTemplate,
    mockDeleteChatTemplate,
    mockTemplateCan,
    mockUpdateChatTemplate,
} from '~/composables/template/api/mock/notification-template-mock';
import type { ChatTemplateHistoryPage, ChatTemplateListItemView, ChatTemplateSaveRequest, ChatTemplateType, ChatTemplateView } from '~/composables/template/api/useChatTemplateApi.types';

/**
 * Slack · Mattermost 템플릿 API — 서버 주소 문자열(`/api/admin/notification-templates/{slack|mattermost}…`)이 있는 **유일한 곳**이다. 상태는 없다. 미리보기는 화면이 그린다(`UtilChatTemplate.card` — 목업 가정).
 *
 * 후보 계약(`docs/features/message-template-contract.md` 「개정 4」, 서버 단계에서 확정). 권한: 읽기 `template.read` · 쓰기 `template.manage`. 종류는 주소 조각(`slack` · `mattermost`)이다.
 *   - `GET    /api/admin/notification-templates/{type}` → 200 `ChatTemplateListItemView[]`(기본 템플릿 먼저, 쓰는 문구 수)
 *   - `GET    /api/admin/notification-templates/{type}/{id}` → 200 `ChatTemplateView`(사이트 이름 · 틀에서 쓸 수 있는 변수 포함)
 *   - `POST   /api/admin/notification-templates/{type}` `ChatTemplateSaveRequest` → 201 (409 `TEMPLATE_LIMIT` — 종류마다 20 가정)
 *   - `PUT    /api/admin/notification-templates/{type}/{id}` `ChatTemplateSaveRequest` → 200 (기본 템플릿 409 `TEMPLATE_BUILTIN`)
 *       · 400 `VALIDATION` `fields.name|colors.INFO|colors.WARNING|colors.CRITICAL|openLabel.ko|openLabel.en|frames.ko|frames.en` — 이름 · 팔레트 밖 색 · 버튼 글자(보일 때 필수·20자) · 틀의 `{{content}}` 한 번·500자·모르는 변수
 *   - `DELETE /api/admin/notification-templates/{type}/{id}` → 204 (쓰는 문구가 있으면 409 `TEMPLATE_IN_USE`, 기본 템플릿 409 `TEMPLATE_BUILTIN`)
 *   - `GET    /api/admin/notification-templates/{type}/{id}/history?page&size` → 200 설정 이력 모양
 *   공통 오류: 404 `TEMPLATE_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사 후보: `NOTIFICATION_TEMPLATE_CREATE|UPDATE|DELETE`(유형 `SLACK|MATTERMOST`).
 *
 * ⚠ MOCK: 서버가 없어서 `./mock/notification-template-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄(과 `can`·목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: (type: ChatTemplateType) => `/api/admin/notification-templates/${type.toLowerCase()}`,
    template: (type: ChatTemplateType, id: string) => `${PATH.root(type)}/${encodeURIComponent(id)}`,
    history: (type: ChatTemplateType, id: string) => `${PATH.template(type, id)}/history`,
};

export function useChatTemplateApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 내 권한(`template.read`·`template.manage`)으로 403 을 낸다
    const can = mockTemplateCan(useAdminPermission().can);
    void PATH;

    return {
        /** 그 종류의 템플릿 목록 */
        list: (type: ChatTemplateType): Promise<ChatTemplateListItemView[]> =>
            // MOCK:
            mockChatTemplates(can, type),
        // REAL: client.get<ChatTemplateListItemView[]>(PATH.root(type)),

        /** 템플릿 하나 */
        get: (type: ChatTemplateType, id: string): Promise<ChatTemplateView> =>
            // MOCK:
            mockChatTemplate(can, type, id),
        // REAL: client.get<ChatTemplateView>(PATH.template(type, id)),

        /** 사용자 템플릿을 만든다(복사해서 만들 때도 이것) */
        create: (type: ChatTemplateType, body: ChatTemplateSaveRequest): Promise<ChatTemplateView> =>
            // MOCK:
            mockCreateChatTemplate(can, type, body),
        // REAL: client.post<ChatTemplateView>(PATH.root(type), body),

        update: (type: ChatTemplateType, id: string, body: ChatTemplateSaveRequest): Promise<ChatTemplateView> =>
            // MOCK:
            mockUpdateChatTemplate(can, type, id, body),
        // REAL: client.put<ChatTemplateView>(PATH.template(type, id), body),

        /** 쓰는 문구가 있으면 서버가 409 `TEMPLATE_IN_USE` 로 거절한다 */
        remove: (type: ChatTemplateType, id: string): Promise<void> =>
            // MOCK:
            mockDeleteChatTemplate(can, type, id),
        // REAL: client.del<void>(PATH.template(type, id)),

        /** 변경 이력 — 최신이 위, `page` 는 0 부터 */
        history: (type: ChatTemplateType, id: string, page: number, size: number): Promise<ChatTemplateHistoryPage> =>
            // MOCK:
            mockChatTemplateHistory(can, type, id, page, size),
        // REAL: client.get<ChatTemplateHistoryPage>(PATH.history(type, id), { query: { page, size } }),
    };
}
