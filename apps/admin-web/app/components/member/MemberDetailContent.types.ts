import type { ApiError } from '~/composables/common/useApiClient';
import type { MemberRestriction } from '~/composables/member/api/useMemberRestrictionApi.types';
import type { WithdrawnContact, WithdrawnContactRevealResult } from '~/composables/member/api/useWithdrawnContactApi.types';

/**
 * 회원 상세 서랍이 부모(회원 관리 화면)에게서 받아 구획까지 내려보내는 값 — 서랍(`MemberDetailDrawer`) → 본문(`MemberDetailContent`) → 구획.
 * 서버 호출은 부모가 상태 컴포저블(`useMemberRestrictions`·`useWithdrawnContact`)로 하고, 구획은 이벤트로 알린다(걸기·해제·열람 대화상자도 부모가 띄운다).
 */

/** 「기능 제한」 구획 — `useMemberRestrictions` 의 값 */
export interface MemberRestrictionsView {
    /** 아직 받지 못했으면 null */
    items: MemberRestriction[] | null;
    loading: boolean;
    error: ApiError | null;
}

/** 탈퇴 회원 연락처 블록 — `useWithdrawnContact` 의 값 */
export interface WithdrawnContactView {
    contact: WithdrawnContact | null;
    loading: boolean;
    error: ApiError | null;
    /** 열람한 이메일 — 다시 가리면 null */
    revealed: WithdrawnContactRevealResult | null;
    /** 다시 가릴 때까지 남은 초 */
    remaining: number;
}

/** 서랍·본문이 위로 올리는 이벤트 */
export interface MemberDetailEmits {
    /** 기능 제한 목록 다시 받기 */
    'retry-restrictions': [];
    /** [제한 걸기] */
    restrict: [];
    /** 진행 중인 제한의 [해제] */
    lift: [restriction: MemberRestriction];
    /** 탈퇴 회원 연락처 다시 받기 */
    'retry-contact': [];
    /** 탈퇴 회원 연락처 [열람] */
    'reveal-contact': [];
    /** 열린 연락처를 지금 가리기 */
    'mask-contact': [];
}
