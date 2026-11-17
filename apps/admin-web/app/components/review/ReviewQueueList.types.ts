import type { ReviewQueueItem, SponsorHiddenRequest } from '~/composables/review/api/useReviewQueueApi.types';
import type { SanctionLine } from '~/composables/sanction/api/useSanctionApi.types';
import type { TrialDamageVerdict, AdminTrialDamageReport, TrialPhoto } from '~/composables/trial/api/useTrialApi.types';

/** 파손 판정 대화상자가 보이는 비교 거리 — 신고 + 보내기 전(입고·발송 전) 사진 + 반납 뒤 사진 */
export interface ReviewQueueDamageView {
    report: AdminTrialDamageReport;
    beforePhotos: TrialPhoto[];
    afterPhotos: TrialPhoto[];
}

/**
 * 확인할 일 목록(`ReviewQueueList`)이 부르는 처리 — 쓰는 화면이 API 컴포저블로 채워 넘긴다(목록은 서버를 모른다).
 * 다 되면 풀리고, 실패하면 `ApiError` 를 던진다(목록이 대화상자 안·토스트에 서버 문구를 보이고, 409·404·403 이면 다시 받는다).
 */
export interface ReviewQueueActions {
    approveTitle: (applicationId: string) => Promise<void>;
    rejectTitle: (applicationId: string, reason: string) => Promise<void>;
    approveProof: (proofId: string) => Promise<void>;
    rejectProof: (proofId: string, reason: string) => Promise<void>;
    /** 구매 인증 사진 주소(비공개 — 열람 기록) */
    proofPhotoUrl: (proofId: string) => string;
    /** 파손 판정 대화상자 — 그 모집 상세에서 신고·사진을 찾는다 */
    loadDamage: (trialId: string, reportId: string) => Promise<ReviewQueueDamageView>;
    decideDamage: (reportId: string, verdict: TrialDamageVerdict, note: string) => Promise<void>;
    /** 협찬 숨김 확인(협찬 고정 + 글쓴이 제재 곁들이기 — 점검 D2) → 곁들인 제재 줄(없으면 null) */
    confirmSponsor: (postId: string, body: SponsorHiddenRequest) => Promise<SanctionLine | null>;
    dismissSponsor: (postId: string, note: string) => Promise<void>;
    /**
     * 협찬 숨김 확인을 열 때 글쓴이 제재 현황을 받는다 — 회원 글은 `member.id`(`?userId=`), 익명 글은 `refs.anonymousPostId`(봉인 미리보기 `?anonymousPostId=`),
     * `refs.sanctionable` "false"(비회원·탈퇴·파기)면 받지 않는다. 결과는 목록의 `sponsorSanction` 속성으로
     */
    openSponsorAuthor: (row: ReviewQueueItem) => void;
    retrySponsorAuthor: () => void;
    /** 처리 뒤·충돌 뒤 다시 받기 */
    reload: () => Promise<void>;
}
