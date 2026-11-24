import type { OperatorMemberSearchCandidate } from '~/composables/operator/api/useOperatorMemberAccountApi.types';

/** 서버가 받는 검색어의 최소 길이(다듬은 뒤) — 이보다 짧으면 부르지 않는다(서버도 400 `fields.q`) */
export const OPERATOR_MEMBER_SEARCH_MIN = 2;

/** 운영자 서랍 「사이트 계정」 탭의 회원 검색(`OperatorMemberAccountPanel`)이 그리는 값 — 흐름은 `useOperatorMemberSearch` */
export interface OperatorMemberSearchView {
    /** 마지막으로 찾은 말(다듬은 값). '' = 아직 찾지 않았다 */
    query: string;
    /** 찾은 회원(최대 20명). null = 아직 찾지 않았거나 못 받았다 */
    items: OperatorMemberSearchCandidate[] | null;
    loading: boolean;
    /** 못 찾았을 때 서버 문구(`fields.q` 가 있으면 그 문구) */
    error: string;
}
