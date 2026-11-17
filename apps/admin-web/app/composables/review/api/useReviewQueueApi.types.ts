/**
 * 확인할 일 목록(관리자 — trial-group-contract 「구현 결정」 7) 이 주고받는 모양 — 서버 admin `ReviewQueueService.Item`·`Page` 와 같은 이름. 칭호 신청과 체험단 확인 일을 한 목록으로.
 */
import type { SanctionWithReport } from '~/composables/sanction/api/useSanctionApi.types';
import type { TrialMember } from '~/composables/trial/api/useTrialApi.types';

/** 종류 — 칭호 신청(`title.review`·`title.read`) · 구매 인증 · 반납 확인 · 파손 신고 · 협찬 숨김(`trial.review`) */
export type ReviewQueueType = 'TITLE_APPLICATION' | 'PURCHASE_PROOF' | 'RETURN_CHECK' | 'DAMAGE_REPORT' | 'SPONSOR_HIDDEN';

/** 상태 거르기(서버 `status`) — 처리할 것(기본) · 처리됨 · 전체 */
export type ReviewQueueStatus = 'PENDING' | 'DONE' | 'ALL';

/**
 * 확인할 일 한 줄(서버 `Item`). `status` 는 종류마다 — 칭호 신청·구매 인증 PENDING|APPROVED|REJECTED · 반납 확인 PENDING|RETURNED|UNRETURNED ·
 * 파손 신고 REPORTED|OBJECTED|AWAITING(처리할 것) 또는 판정(NORMAL_WEAR …) · 협찬 숨김 PENDING|CONFIRMED|DISMISSED.
 * `title` = 칭호 이름 · 글 제목 · 모집 제목. `note` = 거절 사유 · 회원 이의(없으면 신고 설명) · 메모. `refs` = 처리 화면으로 갈 때 쓰는 ID(종류마다 — 아래).
 */
export interface ReviewQueueItem {
    type: ReviewQueueType;
    /** 그 종류 대상의 ID(칭호 신청 · 구매 인증 · 뽑힌 줄 · 파손 신고 · 글) */
    id: string;
    status: string;
    title: string | null;
    member: TrialMember | null;
    /** 처리할 것은 들어온 때, 처리된 것은 처리한 때 */
    at: string | null;
    note: string | null;
    /** 칭호 `titleKey` · 구매 인증 `postId`·`boardSlug` · 반납 확인 `trialId` · 파손 신고 `trialId`·`participantId` · 협찬 숨김 `postId`·`boardSlug`·`reportCount` */
    refs: Record<string, string>;
}

/** `GET /api/admin/review-queue?type=&status=` 응답 — 종류마다 대기 수(볼 수 있는 종류만) */
export interface ReviewQueueList {
    items: ReviewQueueItem[];
    pending: Partial<Record<ReviewQueueType, number>>;
}

/**
 * `POST /api/admin/review-queue/sponsor-hidden/{postId}/confirm` 본문(서버 `SponsorHiddenActionService.Request`) — `note` 처리 글(300자),
 * `sanction` 글쓴이 제재(신고 처리와 같은 모양 — `reason` null 이면 처리 글, 둘 다 없으면 400 `fields.sanction.reason`). 무시에는 `note` 만.
 */
export interface SponsorHiddenRequest {
    note: string;
    sanction: SanctionWithReport | null;
}
