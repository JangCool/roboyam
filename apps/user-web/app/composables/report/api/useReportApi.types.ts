/**
 * 신고 API 의 모양 — 서버 `ReportRequest` 와 같다(2026-10-22 연결). 사유 코드는 관리자 신고 정책의 `BoardReportReason`(`apps/admin-web/app/composables/board/api/useBoardApi.types.ts`)과 같다.
 */

/** 스팸·광고 · 욕설·비방 · 음란·선정 · 불법촬영물·아동성착취물 · 개인정보 노출·명예훼손 · 기타 */
export type ReportReason = 'SPAM_AD' | 'ABUSE' | 'OBSCENE' | 'ILLEGAL_FILMING' | 'RIGHTS' | 'OTHER';

/** 화면에 보일 차례 */
export const REPORT_REASONS: readonly ReportReason[] = ['SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'OTHER'];

export type ReportTargetType = 'POST' | 'COMMENT';

/** `POST /api/reports` — 회원만. 같은 대상은 한 번만(`409 ALREADY_REPORTED`) */
export interface ReportRequest {
    targetType: ReportTargetType;
    targetId: string;
    reason: ReportReason;
    /** 기타 사유의 설명 — 0~300자(가정) */
    detail: string | null;
}
