import type { KakaoTemplateListItemView, KakaoTemplateView } from '~/composables/template/api/useKakaoTemplateApi.types';

/**
 * 카카오톡(알림톡) 템플릿 API — 서버 주소 문자열(`/api/admin/notification-templates/kakao…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * **준비 중**(알림 도구에서 카카오가 업체 계약 전) — 읽기만 있다(서버 표 `notification_kakao_templates` 는 아직 행이 없어 목록이 비어 있다). 만들기·고치기(카카오 검수 요청 포함)는 업체 계약 뒤 정한다(계약 미확정 10).
 *   - `GET /api/admin/notification-templates/kakao` → 200 `KakaoTemplateListItemView[]`
 *   - `GET /api/admin/notification-templates/kakao/{id}` → 200 `KakaoTemplateView`(승인 본문 · 변수 대응표 · 버튼 · 발신 프로필 이름)
 *   공통 오류: 404 `TEMPLATE_NOT_FOUND` · 403 `PERMISSION_DENIED`(권한 `template.read`).
 */
const PATH = {
    root: '/api/admin/notification-templates/kakao',
    template: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
};

export function useKakaoTemplateApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (): Promise<KakaoTemplateListItemView[]> => client.get<KakaoTemplateListItemView[]>(PATH.root),

        get: (id: string): Promise<KakaoTemplateView> => client.get<KakaoTemplateView>(PATH.template(id)),
    };
}
