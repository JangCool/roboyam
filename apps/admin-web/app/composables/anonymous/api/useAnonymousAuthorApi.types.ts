/**
 * 익명 작성자 열람 API 가 주고받는 모양 — 계약 `docs/features/anonymous-board-contract.md` 「API 계약」 그대로(서버 `server/admin` 익명 작성자 열람 DTO 와 같은 칸 이름).
 * 익명 게시판의 글·댓글 작성자는 관리자 화면에도 봉인(`author.sealed`)으로만 나간다 — 신원은 이 API 로만 연다(권한 `anonymous-author.reveal` · 사유 · 메모 · 감사 기록 · 다른 운영자 알림).
 * ID 는 모두 문자열(Snowflake).
 */

/** 열람 사유 — 수사기관 요청 · 명예훼손·권리침해 신고 · 반복 도배·악용 · 기타 */
export type AnonymousRevealReason = 'LEGAL_REQUEST' | 'RIGHTS_REPORT' | 'ABUSE' | 'OTHER';

/** 열람 사유 — 고르는 차례 */
export const ANONYMOUS_REVEAL_REASONS: readonly AnonymousRevealReason[] = ['LEGAL_REQUEST', 'RIGHTS_REPORT', 'ABUSE', 'OTHER'];

/** 메모 최대 글자 수(필수, 1~500자 — 서버 `400 fields.memo` 와 같은 규칙) */
export const ANONYMOUS_REVEAL_MEMO_MAX = 500;

/** 봉인된 작성자를 가리키는 글·댓글 — 둘 중 하나만 채운다(댓글이면 `commentId`) */
export interface AnonymousTarget {
    postId: string | null;
    commentId: string | null;
}

/**
 * `POST /api/admin/anonymous-authors/reveal` — `postId` 또는 `commentId` 하나 + 사유 + 메모.
 * 400 `VALIDATION`(`fields.reason`·`fields.memo`, 둘 다/둘 다 없음) · 403 `PERMISSION_DENIED`(`anonymous-author.reveal` 없음) · 404(글·댓글 없음) · 409(익명 글이 아님)
 */
export interface AnonymousRevealRequest {
    postId?: string;
    commentId?: string;
    reason: AnonymousRevealReason;
    memo: string;
}

/**
 * 열람 결과(회원 요약) — 탈퇴로 작성자 정보가 지워졌으면 `withdrawn: true`(닉네임·회원 ID 가 null 일 수 있다 — 화면은 「탈퇴한 회원」).
 * `status` 는 회원 관리의 계정 상태와 같은 값(ACTIVE·SUSPENDED·BANNED …)
 */
export interface AnonymousRevealResult {
    memberId: string | null;
    nickname: string | null;
    status: string | null;
    withdrawn: boolean;
}
