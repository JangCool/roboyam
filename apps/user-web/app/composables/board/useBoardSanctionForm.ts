import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { BoardSanctionPreview, BoardSanctionWho, BoardStaffQuota } from '~/composables/board/api/useBoardStaffApi.types';
import type { BoardSanctionDialogState, BoardSanctionDialogValue } from '~/components/board/BoardSanctionDialog.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';

/** 대화창이 칸 아래에 보이는 서버 `fields` 키 — 그 밖의 키는 폼 아래 한 줄 */
const FIELD_KEYS: ReadonlySet<string> = new Set(['reasonCode', 'reasonText']);

/**
 * 제재할 작성자 — 누른 글·댓글에서.
 * 익명 글·댓글(`author.kind === 'ANONYMOUS'` — anonymous-board-contract 정책 9)이면 `anonymous: true`, `userId` 는 null 이고 `nickname` 은 화면에 보일 이름(「이 글 작성자」·「이 댓글 작성자」 — 닉네임이 아니다).
 * 그때 요청은 회원 ID 대신 계기 댓글이 있으면 `anonymousCommentId`, 없으면 `anonymousPostId` 로 간다(서버가 작성자를 찾고 계기도 그 글·댓글로 둔다)
 */
export interface BoardSanctionTargetRef {
    userId: string | null;
    nickname: string;
    /** 익명(봉인) 작성자 */
    anonymous?: boolean;
    /** 계기 글 */
    postId: string | null;
    /** 계기 댓글(댓글 ⋯ 메뉴에서 열었을 때) */
    commentId: string | null;
}

/** 요청의 대상 칸 — 회원이면 `userId`, 익명이면 그 댓글·글. 가리킬 것이 없으면 null */
function whoOf(target: BoardSanctionTargetRef): BoardSanctionWho | null {
    if (target.anonymous) {
        if (target.commentId) return { anonymousCommentId: target.commentId };
        return target.postId ? { anonymousPostId: target.postId } : null;
    }
    return target.userId ? { userId: target.userId } : null;
}

/**
 * 글 보기의 「작성자 제재」 흐름(board-sanction-contract) — **대상 고르기 → 현황 읽기(`preview`) → 단계·사유 → 제출 → 토스트·남은 한도 갱신**.
 * 한 화면(글 보기)의 값이라 `useState` 에 두지 않는다. 서버와 말하는 것은 `useBoardStaffApi`, 그리기는 `BoardSanctionDialog`.
 * 결과는 오류 **코드**로 가른다(`UtilBoardSanction.outcomeOf`):
 *  - 한도 초과 `409 BOARD_STAFF_DAILY_LIMIT` → 서버 문구 + 남은 0건으로 맞춘다(`limit`·`used`)
 *  - 대상 불가 `409 BOARD_STAFF_TARGET_PROTECTED` → 폼 대신 서버 문구(「제재할 수 없는 회원이에요」). 익명 작성자가 대상 보호로 막힌 `409 ANONYMOUS_TARGET_UNAVAILABLE`(「지금 이 작성자에게는 제재를 걸 수 없어요.」)도 같다
 *  - 익명 작성자(`target.anonymous`)는 회원 ID 대신 그 글·댓글로 묻고 주며, 결과 안내에도 닉네임을 쓰지 않는다(「익명 작성자에게 …」)
 *  - 이미 제한 중 `409 BOARD_STAFF_ALREADY_RESTRICTED` → 서버 문구 + 현황을 다시 읽어 제한 단계를 막는다(고른 사유·덧붙임은 그대로)
 *  - 입력 `400 VALIDATION` → `fields.reasonCode|reasonText` 는 칸 아래, `fields.kind|postId|commentId` 는 폼 아래 한 줄 · 권한 없음(403 `BOARD_STAFF_FORBIDDEN`) → 대화창을 닫고 토스트 · 그 밖 → 폼 아래 한 줄
 * 성공·한도 초과 때 게시판의 `viewer.staffTools`(게시판 머리 「오늘 남은 제재 n건」)도 같은 값으로 고친다.
 * ⚠ setup 에서 부른다 — 번역은 `useTranslate`(뒤에서도 안전)로 잡아 둔다.
 */
export function useBoardSanctionForm(board: () => BoardDetail | null) {
    const api = useBoardStaffApi();
    const t = useTranslate();
    const toast = useAppToast();

    const visible = ref(false);
    const target = ref<BoardSanctionTargetRef | null>(null);
    const state = ref<BoardSanctionDialogState>('loading');
    const preview = ref<BoardSanctionPreview | null>(null);
    const loadError = ref('');
    const submitting = ref(false);
    const formError = ref('');
    const fieldErrors = ref<Record<string, string>>({});

    let seq = 0;
    let abort: AbortController | null = null;

    /** 남은 한도를 맞춘다 — null 은 하루 한도 없음(운영자 회원 계정) */
    function setQuota(quota: BoardStaffQuota | null, resetsAt: string | null): void {
        if (preview.value) preview.value = { ...preview.value, quota, resetsAt: resetsAt ?? preview.value.resetsAt };
        const tools = board()?.viewer.staffTools;
        if (tools) {
            tools.sanction = quota;
            if (resetsAt) tools.resetsAt = resetsAt;
        }
    }

    async function loadPreview(options: { silent?: boolean } = {}): Promise<void> {
        const b = board();
        const ref = target.value;
        const who = ref ? whoOf(ref) : null;
        if (!b || !who) return;
        const mine = ++seq;
        abort?.abort();
        const controller = new AbortController();
        abort = controller;
        if (!options.silent) {
            state.value = 'loading';
            preview.value = null;
        }
        loadError.value = '';
        try {
            const res = await api.preview(b.id, who, controller.signal);
            if (mine !== seq) return;
            preview.value = res;
            state.value = 'ready';
            setQuota(res.quota, res.resetsAt);
        } catch (e) {
            if (mine !== seq) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            loadError.value = UtilBoardMessages.of(t, e);
            state.value = UtilBoardSanction.outcomeOf(e) === 'TARGET_PROTECTED' ? 'protected' : 'error';
        }
    }

    /** 대화창을 연다 — 앞 대상의 입력·오류는 비운다 */
    function open(next: BoardSanctionTargetRef): void {
        target.value = next;
        formError.value = '';
        fieldErrors.value = {};
        visible.value = true;
        void loadPreview();
    }

    function close(): void {
        visible.value = false;
        seq++;
        abort?.abort();
    }

    async function submit(value: BoardSanctionDialogValue): Promise<void> {
        const b = board();
        const ref = target.value;
        const who = ref ? whoOf(ref) : null;
        if (!b || !ref || !who || submitting.value) return;
        submitting.value = true;
        formError.value = '';
        fieldErrors.value = {};
        try {
            const res = await api.issue({
                boardId: b.id,
                ...who,
                kind: value.kind,
                reasonCode: value.reasonCode,
                reasonText: value.reasonText,
                // 익명 대상은 계기도 서버가 그 글·댓글로 둔다
                postId: ref.anonymous ? null : ref.postId,
                commentId: ref.anonymous ? null : ref.commentId,
            });
            setQuota(res.quota, res.resetsAt);
            visible.value = false;
            toast.success(
                ref.anonymous ? UtilBoardSanctionMessages.issuedToastSealed(t, res.kind, res.quota) : UtilBoardSanctionMessages.issuedToast(t, ref.nickname, res.kind, res.quota),
                5000,
            );
        } catch (e) {
            const outcome = UtilBoardSanction.outcomeOf(e);
            const message = UtilBoardMessages.of(t, e);
            if (outcome === 'DAILY_LIMIT') {
                const limit = UtilBoardSanction.limitOf(e);
                if (limit) setQuota({ limit: limit.limit, used: limit.used, remaining: Math.max(0, limit.limit - limit.used) }, limit.resetsAt);
                formError.value = message;
            } else if (outcome === 'TARGET_PROTECTED') {
                loadError.value = message;
                state.value = 'protected';
            } else if (outcome === 'ALREADY_RESTRICTED') {
                formError.value = message;
                void loadPreview({ silent: true });
            } else if (outcome === 'VALIDATION' && e instanceof ApiError && e.fields && Object.keys(e.fields).length) {
                fieldErrors.value = { ...e.fields };
                // 대화창에 칸이 없는 오류(`fields.kind` · 계기 `fields.postId|commentId` — 그새 글·댓글이 지워졌거나 대상의 것이 아님)는 폼 아래 한 줄로
                const other = Object.entries(e.fields).find(([key]) => !FIELD_KEYS.has(key));
                if (other) formError.value = other[1];
            } else if (outcome === 'FORBIDDEN') {
                // 그새 매니저·스텝이 아니게 됐다 — 닫고 알린다(다음에 게시판을 읽으면 메뉴도 사라진다)
                visible.value = false;
                toast.error(message, 8000);
            } else formError.value = message;
        } finally {
            submitting.value = false;
        }
    }

    onBeforeUnmount(() => abort?.abort());

    return { visible, target, state, preview, loadError, submitting, formError, fieldErrors, open, close, submit, retry: () => loadPreview() };
}
