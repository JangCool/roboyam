import {
    mockCreateTemplate,
    mockDeleteTemplate,
    mockHistory,
    mockKindDetail,
    mockKinds,
    mockLayout,
    mockPreview,
    mockSaveKind,
    mockSaveLayout,
    mockTemplateCan,
    mockTest,
    mockUpdateTemplate,
} from '~/composables/template/api/mock/mail-template-mock';
import type {
    MailKindDetailView,
    MailKindSaveRequest,
    MailKindView,
    MailLayoutSaveRequest,
    MailLayoutView,
    MailPreviewRequest,
    MailPreviewView,
    MailTemplateHistoryPage,
    MailTemplateSaveRequest,
    MailTemplateTestRequest,
    MailTemplateTestResultView,
    MailTemplateView,
} from '~/composables/template/api/useMailTemplateApi.types';

/**
 * 메일 템플릿 API — 서버 주소 문자열(`/api/admin/mail-templates…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 후보 계약(`docs/features/message-template-contract.md` 「API 후보」, 서버 단계에서 확정). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF.
 * 권한: 읽기 `template.read` · 쓰기(종류 저장·템플릿·레이아웃·시험) `template.manage` — 서버 `Permission` 에는 아직 없다(화면 목업 후보).
 *   - `GET    /api/admin/mail-templates/kinds` → 200 `MailKindView[]`(묶음·보안 여부·변수·조건 선택지·쓰는 템플릿·규칙 수)
 *   - `GET    /api/admin/mail-templates/kinds/{kind}` → 200 `MailKindDetailView`(`defaults`·`selected`·`rules`·`templates`)
 *   - `PUT    /api/admin/mail-templates/kinds/{kind}` `MailKindSaveRequest` → 200 `MailKindDetailView` (400 `VALIDATION` `fields.selected.ko|en`·`rules[i].condition|templateId`)
 *   - `POST   /api/admin/mail-templates/templates` `MailTemplateSaveRequest` → 201 `MailTemplateView` (409 `TEMPLATE_LIMIT` — 종류당 20 가정)
 *   - `PUT    /api/admin/mail-templates/templates/{id}` `MailTemplateSaveRequest` → 200 `MailTemplateView` (기본 템플릿 409 `TEMPLATE_BUILTIN`)
 *   - `DELETE /api/admin/mail-templates/templates/{id}` → 204 (쓰는 중 409 `TEMPLATE_IN_USE`)
 *       · 템플릿 저장의 400 `VALIDATION` `fields.name|locale|subject|bodyHtml` — 모르는 변수 · 보안 메일 필수 변수 없음 · 길이 · 버튼(하나, 링크 변수만)
 *   - `POST   /api/admin/mail-templates/preview` `MailPreviewRequest` → 200 `{ subject, html }`(견본 값 + 공통 레이아웃)
 *       · 공통 레이아웃 미리보기는 `kind` 없이(공통 변수만) + 저장 전 `layout` 을 함께 보낸다
 *   - `POST   /api/admin/mail-templates/test` `MailTemplateTestRequest` → 200 `{ succeeded, reason, detail }`(메일 설정 시험과 같은 모양·한도 — 400 `fields.to`, 429 `MAIL_TEST_RATE_LIMITED`)
 *   - `GET·PUT /api/admin/mail-templates/layout` → 200 `MailLayoutView` (400 `fields.frames.ko|en` — `{{content}}` 한 번)
 *   - `GET    /api/admin/mail-templates/templates/{id}/history?page&size` → 200 `{ items, total, page, size }`(설정 이력 모양, 바뀐 칸 `name|locale|subject|bodyHtml`)
 *   공통 오류: 404 `TEMPLATE_NOT_FOUND`·`MAIL_KIND_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사 후보: `MAIL_TEMPLATE_CREATE|UPDATE|DELETE` · `MAIL_KIND_UPDATE` · `MAIL_LAYOUT_UPDATE`.
 *
 * ⚠ MOCK: 서버가 없어서 `./mock/mail-template-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄(과 `can`·목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/mail-templates',
    kinds: () => `${PATH.root}/kinds`,
    kind: (kind: string) => `${PATH.kinds()}/${encodeURIComponent(kind)}`,
    templates: () => `${PATH.root}/templates`,
    template: (id: string) => `${PATH.templates()}/${encodeURIComponent(id)}`,
    history: (id: string) => `${PATH.template(id)}/history`,
    preview: () => `${PATH.root}/preview`,
    test: () => `${PATH.root}/test`,
    layout: () => `${PATH.root}/layout`,
};

export function useMailTemplateApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 내 권한(`template.read`·`template.manage`, `?mock=template-readonly|template-denied`)으로 403 을 낸다
    const can = mockTemplateCan(useAdminPermission().can);
    void PATH;

    return {
        /** 메일 종류 목록 */
        kinds: (): Promise<MailKindView[]> =>
            // MOCK:
            mockKinds(can),
        // REAL: client.get<MailKindView[]>(PATH.kinds()),

        /** 한 종류의 쓰는 템플릿·규칙·템플릿 목록 */
        kind: (kind: string): Promise<MailKindDetailView> =>
            // MOCK:
            mockKindDetail(can, kind),
        // REAL: client.get<MailKindDetailView>(PATH.kind(kind)),

        /** 쓰는 템플릿(언어별)과 규칙(목록 전체, 차례 그대로)을 한 번에 저장한다 */
        saveKind: (kind: string, body: MailKindSaveRequest): Promise<MailKindDetailView> =>
            // MOCK:
            mockSaveKind(can, kind, body),
        // REAL: client.put<MailKindDetailView>(PATH.kind(kind), body),

        /** 사용자 템플릿을 만든다(기본 템플릿·다른 템플릿을 복사해 만들 때도 이것) */
        create: (body: MailTemplateSaveRequest): Promise<MailTemplateView> =>
            // MOCK:
            mockCreateTemplate(can, body),
        // REAL: client.post<MailTemplateView>(PATH.templates(), body),

        update: (id: string, body: MailTemplateSaveRequest): Promise<MailTemplateView> =>
            // MOCK:
            mockUpdateTemplate(can, id, body),
        // REAL: client.put<MailTemplateView>(PATH.template(id), body),

        /** 쓰는 중이면 서버가 409 `TEMPLATE_IN_USE` 로 거절한다 */
        remove: (id: string): Promise<void> =>
            // MOCK:
            mockDeleteTemplate(can, id),
        // REAL: client.del<void>(PATH.template(id)),

        /** 견본 값 + 공통 레이아웃으로 그린 미리보기 */
        preview: (body: MailPreviewRequest): Promise<MailPreviewView> =>
            // MOCK:
            mockPreview(can, body),
        // REAL: client.post<MailPreviewView>(PATH.preview(), body),

        /** 나에게 시험 발송 — 받는 주소는 이 호출의 인자로만 지나간다 */
        test: (body: MailTemplateTestRequest): Promise<MailTemplateTestResultView> =>
            // MOCK:
            mockTest(can, body),
        // REAL: client.post<MailTemplateTestResultView>(PATH.test(), body),

        layout: (): Promise<MailLayoutView> =>
            // MOCK:
            mockLayout(can),
        // REAL: client.get<MailLayoutView>(PATH.layout()),

        saveLayout: (body: MailLayoutSaveRequest): Promise<MailLayoutView> =>
            // MOCK:
            mockSaveLayout(can, body),
        // REAL: client.put<MailLayoutView>(PATH.layout(), body),

        /** 템플릿 변경 이력 — 최신이 위, `page` 는 0 부터 */
        history: (id: string, page: number, size: number): Promise<MailTemplateHistoryPage> =>
            // MOCK:
            mockHistory(can, id, page, size),
        // REAL: client.get<MailTemplateHistoryPage>(PATH.history(id), { query: { page, size } }),
    };
}
