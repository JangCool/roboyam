import {
    mockCreateMailTemplate,
    mockDeleteMailTemplate,
    mockMailTemplate,
    mockMailTemplateHistory,
    mockMailTemplates,
    mockTemplateCan,
    mockUpdateMailTemplate,
} from '~/composables/template/api/mock/notification-template-mock';
import type { MailTemplateAudienceQuery, MailTemplateHistoryPage, MailTemplateListItemView, MailTemplateSaveRequest, MailTemplateView } from '~/composables/template/api/useMailTemplateApi.types';

/**
 * 메일 템플릿 API — 서버 주소 문자열(`/api/admin/notification-templates/mail…`)이 있는 **유일한 곳**이다. 상태는 없다. 미리보기는 문구 API 의 `preview`(저장 전 `template` 을 함께 보낸다).
 *
 * 후보 계약(`docs/features/message-template-contract.md` 「개정 4」 — 개정 3 의 `/api/admin/mail-layouts` 를 옮겼다, 서버 단계에서 확정). 권한: 읽기 `template.read` · 쓰기 `template.manage`.
 *   - `GET    /api/admin/notification-templates/mail?audience=` → 200 `MailTemplateListItemView[]`(기본 템플릿 먼저, 쓰는 대상, 쓰는 문구 수)
 *   - `GET    /api/admin/notification-templates/mail/{id}` → 200 `MailTemplateView`(사이트 이름·로고 주소·쓰는 대상별 쓸 수 있는 변수·받는 사람별 쓰는 문구 수 포함)
 *   - `POST   /api/admin/notification-templates/mail` `MailTemplateSaveRequest` → 201 `MailTemplateView` (409 `TEMPLATE_LIMIT` — 20 가정)
 *   - `PUT    /api/admin/notification-templates/mail/{id}` `MailTemplateSaveRequest` → 200 `MailTemplateView` (기본 템플릿 409 `TEMPLATE_BUILTIN`)
 *       · 400 `VALIDATION` `fields.name|audience|headerColor|footer.ko|footer.en|frames.ko|frames.en` — 이름 · 쓰는 문구와 맞지 않는 쓰는 대상 · 팔레트 밖 색 · 꼬리말 길이·모르는 변수(쓰는 대상의 공통 변수만) · 틀의 `{{content}}` 한 번
 *   - `DELETE /api/admin/notification-templates/mail/{id}` → 204 (쓰는 문구가 있으면 409 `TEMPLATE_IN_USE`, 기본 템플릿 409 `TEMPLATE_BUILTIN`)
 *   - `GET    /api/admin/notification-templates/mail/{id}/history?page&size` → 200 `{ items, total, page, size }`(설정 이력 모양)
 *   공통 오류: 404 `TEMPLATE_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사 후보: `NOTIFICATION_TEMPLATE_CREATE|UPDATE|DELETE`(유형 `MAIL`).
 *
 * ⚠ MOCK: 서버가 없어서 `./mock/notification-template-mock.ts` 를 부른다(문구와 상태를 함께 쓴다). 서버가 붙으면 `// MOCK:` 줄(과 `can`·목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/notification-templates/mail',
    template: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    history: (id: string) => `${PATH.template(id)}/history`,
};

export function useMailTemplateApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 내 권한(`template.read`·`template.manage`)으로 403 을 낸다
    const can = mockTemplateCan(useAdminPermission().can);
    void PATH;

    return {
        /** 메일 템플릿 목록(쓰는 대상으로 거를 수 있다) */
        list: (query: MailTemplateAudienceQuery = {}): Promise<MailTemplateListItemView[]> =>
            // MOCK:
            mockMailTemplates(can, query),
        // REAL: client.get<MailTemplateListItemView[]>(PATH.root, { query }),

        /** 메일 템플릿 하나 */
        get: (id: string): Promise<MailTemplateView> =>
            // MOCK:
            mockMailTemplate(can, id),
        // REAL: client.get<MailTemplateView>(PATH.template(id)),

        /** 사용자 메일 템플릿을 만든다(복사해서 만들 때도 이것) */
        create: (body: MailTemplateSaveRequest): Promise<MailTemplateView> =>
            // MOCK:
            mockCreateMailTemplate(can, body),
        // REAL: client.post<MailTemplateView>(PATH.root, body),

        update: (id: string, body: MailTemplateSaveRequest): Promise<MailTemplateView> =>
            // MOCK:
            mockUpdateMailTemplate(can, id, body),
        // REAL: client.put<MailTemplateView>(PATH.template(id), body),

        /** 쓰는 문구가 있으면 서버가 409 `TEMPLATE_IN_USE` 로 거절한다 */
        remove: (id: string): Promise<void> =>
            // MOCK:
            mockDeleteMailTemplate(can, id),
        // REAL: client.del<void>(PATH.template(id)),

        /** 변경 이력 — 최신이 위, `page` 는 0 부터 */
        history: (id: string, page: number, size: number): Promise<MailTemplateHistoryPage> =>
            // MOCK:
            mockMailTemplateHistory(can, id, page, size),
        // REAL: client.get<MailTemplateHistoryPage>(PATH.history(id), { query: { page, size } }),
    };
}
