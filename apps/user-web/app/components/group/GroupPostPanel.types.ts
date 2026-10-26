import type { PostGroupVisibility } from '~/composables/board/api/usePostApi.types';
import type {
    GroupPostVisibilityResult,
    GroupPublishDecideResult,
    GroupPublishRejectResult,
    GroupPublishRequestResult,
} from '~/composables/group/api/useGroupApi.types';

/**
 * 글 상세의 소모임 공개 범위 패널(`GroupPostPanel`)이 부르는 동작 — 페이지가 `useGroupApi` 로 만들어 내려 준다(컴포넌트는 서버를 부르지 않는다).
 * 오류는 그대로 던진다(`ApiError`) — 패널이 코드로 갈라(`UtilGroupVisibility.failureKind`) 알린다.
 */
export interface GroupPostPanelActions {
    /** 작성자의 공개 요청 — 한마디(없으면 빈 글자), 개인정보 확인은 늘 true */
    request: (note: string) => Promise<GroupPublishRequestResult>;
    /** 작성자의 요청 취소(멱등) */
    cancel: () => Promise<void>;
    /** 처리자의 승인 */
    approve: () => Promise<GroupPublishDecideResult>;
    /** 처리자의 거절 — 사유(없으면 빈 글자) */
    reject: (reason: string) => Promise<GroupPublishRejectResult>;
    /** 글 공개 범위 — 직접 공개·되돌리기·멤버 전용으로 돌리기·다시 공개(옛 서버는 작성자 전환·되돌리기) */
    setVisibility: (visibility: PostGroupVisibility, acknowledged: boolean) => Promise<GroupPostVisibilityResult>;
}
