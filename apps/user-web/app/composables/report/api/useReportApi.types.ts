/**
 * 신고 API 의 모양 — 서버 `ReportRequest` 와 같다(2026-10-22 연결). 사유 코드는 관리자 신고 정책의 `BoardReportReason`(`apps/admin-web/app/composables/board/api/useBoardApi.types.ts`)과 같다.
 */

/**
 * 글·댓글: 스팸·광고 · 욕설·비방 · 음란·선정 · 불법촬영물·아동성착취물 · 개인정보 노출·명예훼손 · 기타.
 * 소모임(서버 `ReportReasons.GROUP_ALL`, V95): 홍보·영업 목적 · 오프라인 모임 위험 · 종목 추천·유료 모집(투자 고지 게시판의 소모임만) · 기타. 두 체계를 섞어 보내면 `400 fields.reason`.
 */
export type ReportReason = 'SPAM_AD' | 'ABUSE' | 'OBSCENE' | 'ILLEGAL_FILMING' | 'RIGHTS' | 'PROMOTION' | 'OFFLINE_RISK' | 'STOCK_RECOMMENDATION' | 'OTHER';

/** 글·댓글 사유 — 화면에 보일 차례 */
export const REPORT_REASONS: readonly ReportReason[] = ['SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'OTHER'];

/** 소모임 사유 — 화면에 보일 차례. `STOCK_RECOMMENDATION` 은 투자 고지 소모임(`stockNotice`)에만 보인다 */
export const GROUP_REPORT_REASONS: readonly ReportReason[] = ['PROMOTION', 'OFFLINE_RISK', 'STOCK_RECOMMENDATION', 'OTHER'];

/**
 * 신고 대상 — 글 · 댓글 · 소모임(`targetId` = 소모임 ID — 열린 소모임만(그 밖 `404 REPORT_TARGET_NOT_FOUND`), 모임장 본인은 `400 CANNOT_REPORT_OWN`).
 * 소모임은 서버 `ReportService`(V95, community `docs/api-report.md` 1절)와 같다 — 자동 숨김은 없다.
 */
export type ReportTargetType = 'POST' | 'COMMENT' | 'GROUP';

/** `POST /api/reports` — 회원만. 같은 대상은 한 번만(`409 ALREADY_REPORTED`) */
export interface ReportRequest {
    targetType: ReportTargetType;
    targetId: string;
    reason: ReportReason;
    /** 기타 사유의 설명 — 0~300자(가정) */
    detail: string | null;
}
