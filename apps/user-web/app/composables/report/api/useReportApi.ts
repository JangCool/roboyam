import type { ReportRequest } from '~/composables/report/api/useReportApi.types';
import { isMockDealPostId, mockDealReport } from '~/composables/hotdeal/api/mock/hotdeal-mock';

/**
 * 신고 API — 서버 주소(`/api/reports`)가 있는 유일한 곳. 상태는 없다. 글·댓글(이후 프로필·쪽지)이 같이 쓴다.
 * 오류: 로그인 필요 `401 UNAUTHENTICATED` · 신고 끔 `403 REPORT_DISABLED` · 대상 없음 `404 REPORT_TARGET_NOT_FOUND` · 이미 신고 `409 ALREADY_REPORTED` ·
 * 내 글 `400 CANNOT_REPORT_OWN` · 신고 차단 `403 REPORT_BLOCKED` · 간격 `429 REPORT_TOO_FAST` · 하루 한도 `429 REPORT_DAILY_LIMIT` · 정지 `403 ACCOUNT_RESTRICTED` · 권한 없음 `403 BOARD_RESTRICTED` ·
 * 입력 `400 VALIDATION`(`fields.detail` 등).
 * 핫딜 글 전용 사유 `DEAL_FALSE_PRICE`·`DEAL_HIDDEN_AD` 는 서버 후보(`hotdeal-contract.md` 「목업 가정」) — 일반 글에 보내면 `400 fields.reason`(가정).
 * D4: `DEAL_FALSE_PRICE` 신고는 서버가 종료 요청 `PRICE_CHANGED` 1건으로 접수한다(같은 회원 1회 — 이미 알렸으면 `409 DEAL_END_REQUEST_EXISTS`, 가정).
 *
 * 서버 연결됨(2026-10-22). ⚠ MOCK: 목업 핫딜 글(`mock-deal-*`) 신고는 서버에 글이 없어 목업이 받는다(`DEAL_FALSE_PRICE` 는 목업 종료 요청으로, 그 밖은 받은 척 — `// MOCK:` 분기).
 */
const PATH = {
    reports: '/api/reports',
};

export function useReportApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 신고 — 회원만. 204 */
        report: (body: ReportRequest): Promise<void> =>
            // MOCK:
            body.targetType === 'POST' && isMockDealPostId(body.targetId) ? mockDealReport(body.targetId, body.reason) :
            client.post<void>(PATH.reports, body),
    };
}
