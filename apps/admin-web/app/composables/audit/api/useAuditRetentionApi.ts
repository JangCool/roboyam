import type { AuditRetentionSaveRequest, AuditRetentionView } from '~/composables/audit/api/useAuditRetentionApi.types';

/**
 * 감사 기록 보관 기간 API — 서버 주소 문자열(`/api/admin/audit-logs/retention`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 감사 기록 목록(`useAuditApi`)과 같은 서버 자원 아래에 있지만 화면 조각(보관 기간 대화상자)이 따로라 파일을 나눴다.
 * 정책: `docs/domain/commerce.md` 13-4 「보관 기간」 — 매일 정리 배치(`audit-log-retention`, 03:30)가 분류별 기간이 지난 기록을 지운다. 하한보다 짧게는 못 줄인다.
 *
 *  - `GET /api/admin/audit-logs/retention`(`audit.read`) → `AuditRetentionView`(`items` 는 일반 운영 · 개인정보 열람·내려받기 · 돈 관련 차례, `canEdit` = 루트인가)
 *  - `PUT /api/admin/audit-logs/retention`(**최고 관리자만** — 권한 코드로 위임하지 않는다) `{ days: { <분류>: 하한~3650 } }`(세 분류 모두) → 같은 모양.
 *      400 `VALIDATION` `fields.days.<분류>` · 403 `PERMISSION_DENIED`. 저장하면 서버가 감사(`AUDIT_RETENTION_UPDATE`, 전→후)에 남긴다. 쓰기는 CSRF.
 */
const PATH = {
    retention: '/api/admin/audit-logs/retention',
};

export function useAuditRetentionApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 분류별 보관 기간 */
        retention: (): Promise<AuditRetentionView> => client.get<AuditRetentionView>(PATH.retention),

        /** 보관 기간 저장(세 분류를 한 번에) */
        saveRetention: (body: AuditRetentionSaveRequest): Promise<AuditRetentionView> => client.put<AuditRetentionView>(PATH.retention, body),
    };
}
