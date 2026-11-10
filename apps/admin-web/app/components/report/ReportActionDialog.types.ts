import type { ApiError } from '~/composables/common/useApiClient';
import type { ReportReasonCode } from '~/composables/report/api/useReportApi.types';
import type { SanctionPreview, SanctionWithReport } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 처리 대화창의 「작성자 제재 곁들이기」·「제재만」이 보는 값 — 부른 화면(신고 관리)이 작성자 현황(사건 상세의 `authorCard.sanction`, 서랍 밖 사건은 `useSanctionPreview`)을 내려 준다.
 * member-sanction-contract 결정 4. 비회원·탈퇴 회원 작성자는 `eligible=false`(제재 불가 안내).
 */
export interface ReportSanctionContext {
    /** 작성자가 제재할 수 있는 회원인가(회원 · 탈퇴하지 않음) */
    eligible: boolean;
    /** 작성자 닉네임(안내 문구) */
    authorName: string;
    /** 봉인된 익명 작성자(anonymous-board-contract) — 처리에 곁들이지 않고 서랍의 「이 글 작성자 제재」로 준다(안내만) */
    sealed?: boolean;
    preview: SanctionPreview | null;
    loading: boolean;
    error: ApiError | null;
    perm: { canManage: boolean; canBan: boolean };
}

/** 처리 대화창이 올리는 값 */
export interface ReportActPayload {
    reasonCode: ReportReasonCode | null;
    note: string;
    falseReport: boolean;
    /** 작성자 제재 — 곁들이지 않으면 null(「제재만」은 늘 있다) */
    sanction: SanctionWithReport | null;
}
