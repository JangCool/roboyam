import type { DebateOpinionActions } from '~/components/debate/DebateOpinionsDrawer.types';
import type { DebateSettingsActions } from '~/components/debate/DebateSettingsDialog.types';

/**
 * 글 토론 재사용 부품(의견 서랍 `DebateOpinionsDrawer` · 설정 대화창 `DebateSettingsDialog`)의 서버 동작 — 하이라이트 화면과 (다음 작업) 게시글 관리가 같이 쓴다.
 * 모두 글 번호로 `/api/admin/debates/{postId}/…`(`useDebateApi`). 상태는 없다. 실패는 `ApiError` 를 그대로 던진다.
 */
export function useDebateOpinionActions(): DebateOpinionActions {
    const api = useDebateApi();
    return {
        load: (postId, query) => api.opinions(postId, query),
        pin: (postId, commentId, on) => (on ? api.pin(postId, commentId) : api.unpin(postId, commentId)),
        hide: (postId, commentId, on) => (on ? api.hide(postId, commentId) : api.unhide(postId, commentId)),
        pinRequests: (postId) => api.pinRequests(postId),
        approvePinRequest: (postId, requestId) => api.approvePinRequest(postId, requestId),
        rejectPinRequest: (postId, requestId, reason) => api.rejectPinRequest(postId, requestId, reason),
    };
}

export function useDebateSettingsActions(): DebateSettingsActions {
    const api = useDebateApi();
    return {
        get: (postId) => api.get(postId),
        save: (postId, body) => api.save(postId, body),
    };
}
