import type { MailTemplateAudienceQuery, MailTemplateHistoryPage, MailTemplateListItemView, MailTemplateSaveRequest, MailTemplateView } from '~/composables/template/api/useMailTemplateApi.types';

/**
 * 메일 템플릿 API — 서버 주소 문자열(`/api/admin/notification-templates/mail…`)이 있는 **유일한 곳**이다. 상태는 없다. 미리보기는 문구 API 의 `preview`(저장 전 `template` 을 함께 보낸다).
 *
 * 계약(서버 `NotificationTemplateController`, `docs/features/message-template-contract.md` 「서버 구현 결과(엔진·관리 API)」). 권한: 읽기 `template.read` · 쓰기 `template.manage`.
 * id: 기본 템플릿 `mail-member` · `mail-operator`(코드가 준다), 사용자 템플릿 Snowflake(문자열).
 *   - `GET    /api/admin/notification-templates/mail?audience=` → 200 `MailTemplateListItemView[]`(기본 템플릿 먼저, 쓰는 대상, 쓰는 문구 수)
 *   - `GET    /api/admin/notification-templates/mail/{id}` → 200 `MailTemplateView`(사이트 이름·로고 주소·쓰는 대상별 쓸 수 있는 변수·받는 사람별 쓰는 문구 수 포함)
 *   - `POST   /api/admin/notification-templates/mail` `MailTemplateSaveRequest` → 201 `MailTemplateView` (409 `TEMPLATE_LIMIT` — 20)
 *   - `PUT    /api/admin/notification-templates/mail/{id}` `MailTemplateSaveRequest` → 200 `MailTemplateView` (기본 템플릿 409 `TEMPLATE_BUILTIN`)
 *       · 400 `VALIDATION` `fields.name|audience|headerColor|footer.ko|footer.en|frames.ko|frames.en` — 이름 · 쓰는 문구와 맞지 않는 쓰는 대상 · 팔레트 밖 색 · 꼬리말 길이·모르는 변수(쓰는 대상의 공통 변수만) · 틀의 `{{content}}` 한 번
 *   - `DELETE /api/admin/notification-templates/mail/{id}` → 204 (쓰는 문구가 있으면 409 `TEMPLATE_IN_USE`, 기본 템플릿 409 `TEMPLATE_BUILTIN`)
 *   - `GET    /api/admin/notification-templates/mail/{id}/history?page&size` → 200 `{ items, total, page, size }`(설정 이력 모양)
 *   공통 오류: 404 `TEMPLATE_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사: `NOTIFICATION_TEMPLATE_CREATE|UPDATE|DELETE`.
 */
const PATH = {
    root: '/api/admin/notification-templates/mail',
    template: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    history: (id: string) => `${PATH.template(id)}/history`,
};

export function useMailTemplateApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 메일 템플릿 목록(쓰는 대상으로 거를 수 있다) */
        list: (query: MailTemplateAudienceQuery = {}): Promise<MailTemplateListItemView[]> => client.get<MailTemplateListItemView[]>(PATH.root, { query }),

        /** 메일 템플릿 하나 */
        get: (id: string): Promise<MailTemplateView> => client.get<MailTemplateView>(PATH.template(id)),

        /** 사용자 메일 템플릿을 만든다(복사해서 만들 때도 이것) */
        create: (body: MailTemplateSaveRequest): Promise<MailTemplateView> => client.post<MailTemplateView>(PATH.root, body),

        update: (id: string, body: MailTemplateSaveRequest): Promise<MailTemplateView> => client.put<MailTemplateView>(PATH.template(id), body),

        /** 쓰는 문구가 있으면 서버가 409 `TEMPLATE_IN_USE` 로 거절한다 */
        remove: (id: string): Promise<void> => client.del<void>(PATH.template(id)),

        /** 변경 이력 — 최신이 위, `page` 는 0 부터 */
        history: (id: string, page: number, size: number): Promise<MailTemplateHistoryPage> => client.get<MailTemplateHistoryPage>(PATH.history(id), { query: { page, size } }),
    };
}
