import { mockCreateLayout, mockDeleteLayout, mockLayout, mockLayoutHistory, mockLayouts, mockTemplateCan, mockUpdateLayout } from '~/composables/template/api/mock/mail-template-mock';
import type { MailLayoutHistoryPage, MailLayoutListItemView, MailLayoutSaveRequest, MailLayoutView } from '~/composables/template/api/useMailLayoutApi.types';

/**
 * 메일 레이아웃 API — 서버 주소 문자열(`/api/admin/mail-layouts…`)이 있는 **유일한 곳**이다. 상태는 없다. 미리보기는 템플릿 API 의 `preview`(저장 전 `layout` 을 함께 보낸다).
 *
 * 후보 계약(`docs/features/message-template-contract.md` 「개정」, 서버 단계에서 확정). 권한: 읽기 `template.read` · 쓰기 `template.manage`(레이아웃 포함).
 *   - `GET    /api/admin/mail-layouts` → 200 `MailLayoutListItemView[]`(기본 레이아웃 먼저, 쓰는 템플릿 수)
 *   - `GET    /api/admin/mail-layouts/{id}` → 200 `MailLayoutView`(사이트 이름·로고 주소·쓸 수 있는 변수 포함)
 *   - `POST   /api/admin/mail-layouts` `MailLayoutSaveRequest` → 201 `MailLayoutView` (409 `LAYOUT_LIMIT` — 20 가정)
 *   - `PUT    /api/admin/mail-layouts/{id}` `MailLayoutSaveRequest` → 200 `MailLayoutView` (기본 레이아웃 409 `LAYOUT_BUILTIN`)
 *       · 400 `VALIDATION` `fields.name|headerColor|footer.ko|footer.en|frames.ko|frames.en` — 이름 · 팔레트 밖 색 · 꼬리말 길이·모르는 변수 · 틀의 `{{content}}` 한 번
 *   - `DELETE /api/admin/mail-layouts/{id}` → 204 (쓰는 템플릿이 있으면 409 `LAYOUT_IN_USE`, 기본 레이아웃 409 `LAYOUT_BUILTIN`)
 *   - `GET    /api/admin/mail-layouts/{id}/history?page&size` → 200 `{ items, total, page, size }`(설정 이력 모양)
 *   공통 오류: 404 `LAYOUT_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사 후보: `MAIL_LAYOUT_CREATE|UPDATE|DELETE`.
 *
 * ⚠ MOCK: 서버가 없어서 `./mock/mail-template-mock.ts` 를 부른다(템플릿과 상태를 함께 쓴다). 서버가 붙으면 `// MOCK:` 줄(과 `can`·목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/mail-layouts',
    layout: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    history: (id: string) => `${PATH.layout(id)}/history`,
};

export function useMailLayoutApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 내 권한(`template.read`·`template.manage`)으로 403 을 낸다
    const can = mockTemplateCan(useAdminPermission().can);
    void PATH;

    return {
        /** 레이아웃 목록 */
        list: (): Promise<MailLayoutListItemView[]> =>
            // MOCK:
            mockLayouts(can),
        // REAL: client.get<MailLayoutListItemView[]>(PATH.root),

        /** 레이아웃 하나 */
        get: (id: string): Promise<MailLayoutView> =>
            // MOCK:
            mockLayout(can, id),
        // REAL: client.get<MailLayoutView>(PATH.layout(id)),

        /** 사용자 레이아웃을 만든다(복사해서 만들 때도 이것) */
        create: (body: MailLayoutSaveRequest): Promise<MailLayoutView> =>
            // MOCK:
            mockCreateLayout(can, body),
        // REAL: client.post<MailLayoutView>(PATH.root, body),

        update: (id: string, body: MailLayoutSaveRequest): Promise<MailLayoutView> =>
            // MOCK:
            mockUpdateLayout(can, id, body),
        // REAL: client.put<MailLayoutView>(PATH.layout(id), body),

        /** 쓰는 템플릿이 있으면 서버가 409 `LAYOUT_IN_USE` 로 거절한다 */
        remove: (id: string): Promise<void> =>
            // MOCK:
            mockDeleteLayout(can, id),
        // REAL: client.del<void>(PATH.layout(id)),

        /** 레이아웃 변경 이력 — 최신이 위, `page` 는 0 부터 */
        history: (id: string, page: number, size: number): Promise<MailLayoutHistoryPage> =>
            // MOCK:
            mockLayoutHistory(can, id, page, size),
        // REAL: client.get<MailLayoutHistoryPage>(PATH.history(id), { query: { page, size } }),
    };
}
