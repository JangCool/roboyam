import type { PollView } from '~/composables/board/api/usePollApi.types';

/**
 * 투표 카드(`PostPollCard`)가 부르는 동작 — 페이지가 `usePollApi` 로 만들어 내려 준다(컴포넌트는 서버를 부르지 않는다).
 * 오류는 그대로 던진다(`ApiError`) — 카드가 코드로 갈라 알린다.
 */
export interface PollCardActions {
    /** 투표·다시 투표 → 새 투표 */
    vote: (optionIds: string[]) => Promise<PollView>;
    /** 지금 투표 다시 읽기(`GET …/poll`) */
    reload: () => Promise<PollView>;
    /** 글쓴이의 [투표 지우기](`DELETE …/poll`) */
    remove: () => Promise<void>;
}
