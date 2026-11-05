import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { BoardStaffActionKind, BoardStaffActionReasonCode, BoardStaffQuota } from '~/composables/board/api/useBoardStaffApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBoardStaffAction from '~/utils/util-board-staff-action';
import UtilBoardStaffActionMessages from '~/utils/util-board-staff-action-messages';

/** 처리할 대상 — 누른 글·댓글에서 */
export interface BoardStaffActionTarget {
    kind: BoardStaffActionKind;
    postId: string;
    /** 댓글 삭제일 때만 */
    commentId: string | null;
    /** 대화창에 보일 글 제목 또는 댓글 앞부분 */
    text: string;
}

/** 처리 뒤 부르는 쪽이 할 일 — 성공(`done`)이면 글은 게시판 목록으로, 댓글은 목록 새로 · 그새 상태가 바뀌었으면(`stale`) 다시 읽기 */
export type BoardStaffActionFinish = (target: BoardStaffActionTarget, result: 'done' | 'stale') => void;

/**
 * 글 보기의 스텝 처리 흐름(board-staff-action-contract) — **대상 고르기 → 사유 시트(사유 → 확인) → 제출 → 토스트(남은 횟수)·뒤처리**.
 * 한 화면(글 보기)의 값이라 `useState` 에 두지 않는다. 서버와 말하는 것은 `useBoardStaffApi`, 그리기는 `BoardStaffActionDialog`.
 * 결과는 오류 **코드**로 가른다(`UtilBoardStaffAction.outcomeOf`):
 *  - 한도 초과 `409 BOARD_STAFF_DAILY_LIMIT`(`kind`·`limit`·`used`·`resetsAt`) → 서버 문구(대화창 안) + 남은 0건으로 맞춘다
 *  - 처리할 수 없는 대상 `409 BOARD_STAFF_TARGET_PROTECTED`(공지·고정·같은 게시판 운영진의 것 등) → 닫고 서버 문구 토스트
 *  - 이미 숨김·삭제 `409 POST_STATE_INVALID`·`COMMENT_STATE_INVALID`, 그새 없어짐 `404 POST_NOT_FOUND`·`COMMENT_NOT_FOUND` → 닫고 토스트 + 다시 읽기(`stale`)
 *  - 권한 없음 `403 BOARD_STAFF_FORBIDDEN` → 닫고 토스트 · 입력 `400 fields.reasonCode` → 사유 칸 아래 · 그 밖 → 대화창 안 한 줄
 * 성공·한도 초과 때 게시판의 `viewer.staffTools.post|comment`(게시판 머리 운영 줄)도 같은 값으로 고친다.
 * 운영자 회원 계정(`staffTools.operator`)은 한도가 null(하루 한도 없음)로 오고 처리 응답의 `quota` 도 null — 토스트는 남은 횟수 없이 「글을 숨겼어요」.
 * ⚠ setup 에서 부른다 — 번역은 `useTranslate`(뒤에서도 안전)로 잡아 둔다.
 */
export function useBoardStaffActionForm(board: () => BoardDetail | null, finish: BoardStaffActionFinish) {
    const api = useBoardStaffApi();
    const t = useTranslate();
    const toast = useAppToast();

    const visible = ref(false);
    const target = ref<BoardStaffActionTarget | null>(null);
    const submitting = ref(false);
    const formError = ref('');
    const reasonFieldError = ref('');

    /** 지금 대상 종류의 남은 한도(게시판 응답의 `viewer.staffTools`) — 운영자 회원 계정은 null(한도 없음) */
    const quota = computed<BoardStaffQuota | null>(() => {
        const tools = board()?.viewer.staffTools;
        const kind = target.value?.kind;
        if (!tools || !kind) return null;
        return UtilBoardStaffAction.isPostKind(kind) ? tools.post : tools.comment;
    });
    /** 하루 한도 없음 — 운영자 회원 계정(`staffTools.operator`) */
    const unlimited = computed(() => board()?.viewer.staffTools?.operator === true);

    function setQuota(isPost: boolean, next: BoardStaffQuota | null, resetsAt: string | null): void {
        const tools = board()?.viewer.staffTools;
        if (!tools) return;
        if (isPost) tools.post = next;
        else tools.comment = next;
        if (resetsAt) tools.resetsAt = resetsAt;
    }

    /** 대화창을 연다 — 앞 대상의 오류는 비운다 */
    function open(next: BoardStaffActionTarget): void {
        target.value = next;
        formError.value = '';
        reasonFieldError.value = '';
        visible.value = true;
    }

    function close(): void {
        if (submitting.value) return;
        visible.value = false;
    }

    async function submit(reasonCode: BoardStaffActionReasonCode): Promise<void> {
        const who = target.value;
        if (!who || submitting.value) return;
        submitting.value = true;
        formError.value = '';
        reasonFieldError.value = '';
        const isPost = UtilBoardStaffAction.isPostKind(who.kind);
        try {
            const res =
                who.kind === 'POST_HIDE'
                    ? await api.hidePost(who.postId, { reasonCode })
                    : who.kind === 'POST_DELETE'
                      ? await api.deletePost(who.postId, { reasonCode })
                      : await api.deleteComment(who.commentId ?? '', { postId: who.postId, reasonCode });
            setQuota(isPost, res.quota, res.resetsAt);
            visible.value = false;
            toast.success(UtilBoardStaffActionMessages.doneToast(t, who.kind, res.quota), 5000);
            finish(who, 'done');
        } catch (e) {
            const outcome = UtilBoardStaffAction.outcomeOf(e);
            const message = UtilBoardMessages.of(t, e);
            if (outcome === 'DAILY_LIMIT') {
                const limit = UtilBoardStaffAction.limitOf(e);
                if (limit) {
                    const forPost = limit.kind ? limit.kind === 'POST' : isPost;
                    setQuota(forPost, { limit: limit.limit, used: limit.used, remaining: Math.max(0, limit.limit - limit.used) }, limit.resetsAt);
                }
                formError.value = message;
            } else if (outcome === 'VALIDATION' && e instanceof ApiError && e.field('reasonCode')) {
                reasonFieldError.value = e.field('reasonCode') ?? '';
            } else if (outcome === 'TARGET_PROTECTED' || outcome === 'FORBIDDEN' || outcome === 'LOGIN') {
                visible.value = false;
                toast.error(message, 8000);
            } else if (outcome === 'STALE') {
                // 그새 숨겨졌거나 지워졌다 — 닫고 알린 뒤 지금 상태로 다시 읽는다
                visible.value = false;
                toast.error(message, 8000);
                finish(who, 'stale');
            } else formError.value = message;
        } finally {
            submitting.value = false;
        }
    }

    return { visible, target, quota, unlimited, submitting, formError, reasonFieldError, open, close, submit };
}
