import { mockKakaoTemplate, mockKakaoTemplates, mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { KakaoTemplateListItemView, KakaoTemplateView } from '~/composables/template/api/useKakaoTemplateApi.types';

/**
 * 카카오톡(알림톡) 템플릿 API — 서버 주소 문자열(`/api/admin/notification-templates/kakao…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * **준비 중**(알림 도구에서 카카오가 업체 계약 전) — 지금은 읽기만 있다. 만들기·고치기(카카오 검수 요청 포함)는 업체 계약 뒤 서버 단계에서 정한다(계약 「개정 4」 미확정).
 *   - `GET /api/admin/notification-templates/kakao` → 200 `KakaoTemplateListItemView[]`
 *   - `GET /api/admin/notification-templates/kakao/{id}` → 200 `KakaoTemplateView`(승인 본문 · 변수 대응표 · 버튼 · 발신 프로필 이름)
 *   공통 오류: 404 `TEMPLATE_NOT_FOUND` · 403 `PERMISSION_DENIED`(권한 `template.read`).
 *
 * ⚠ MOCK: 서버가 없어서 `./mock/notification-template-mock.ts` 를 부른다(예시 하나). 서버가 붙으면 `// MOCK:` 줄(과 `can`·목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/notification-templates/kakao',
    template: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
};

export function useKakaoTemplateApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 내 권한(`template.read`)으로 403 을 낸다
    const can = mockTemplateCan(useAdminPermission().can);
    void PATH;

    return {
        list: (): Promise<KakaoTemplateListItemView[]> =>
            // MOCK:
            mockKakaoTemplates(can),
        // REAL: client.get<KakaoTemplateListItemView[]>(PATH.root),

        get: (id: string): Promise<KakaoTemplateView> =>
            // MOCK:
            mockKakaoTemplate(can, id),
        // REAL: client.get<KakaoTemplateView>(PATH.template(id)),
    };
}
