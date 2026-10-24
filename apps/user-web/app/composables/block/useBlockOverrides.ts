import type { BlockedBy } from '~/composables/block/api/useBlockApi.types';
import UtilBlock from '~/utils/util-block';

/**
 * 이 기기에서 **방금 바꾼** 차단·이용자 메모 — 이미 받아 둔 화면(목록·댓글·프로필·작성자 이름)의 표시를 서버 응답을 기다리지 않고 바로 맞춘다(community 와 같은 방식).
 *
 * 서버는 게시판 글 목록 줄(`PostRow.blocked`)·댓글(`CommentView.blocked`)·최신·인기·검색·프로필 작성글/댓글 줄에 `USER|WORD|null`, 작성자에 `memo` 를 실어 준다. 화면은 그 값 위에 바꾼 것을 겹친다(`blockedOf`·`memoOf`).
 * 차단 단어(`WORD`) 판정은 서버만 한다 — 단어를 바꾸면 다시 받은 목록부터 반영된다.
 * - **로그인한 사람마다 따로다** — 다른 계정으로 바뀌면 앞 사람이 바꾼 것은 쓰지 않는다(`viewerId`). 메모리에만 있다(`useState`).
 * - 이 함수는 가볍다 — 작성자 표시처럼 줄마다 쓰는 자리가 많아 로그인 상태만 `useState` 로 본다(요청 통로를 만들지 않는다).
 */

interface OverrideState {
    /** 이 값들을 바꾼 사람 — 지금 로그인한 사람과 다르면 쓰지 않는다 */
    viewerId: string | null;
    /** 회원 ID → 차단했다(true) / 풀었다(false) */
    blocks: Record<string, boolean>;
    /** 회원 ID → 남긴(고친) 메모. 지웠으면 null */
    memos: Record<string, string | null>;
}

const STATE_KEY = 'block-overrides';

function emptyState(viewerId: string | null): OverrideState {
    return { viewerId, blocks: {}, memos: {} };
}

export function useBlockOverrides() {
    const user = useState<{ id: string } | null>(AUTH_USER_STATE_KEY);
    const state = useState<OverrideState>(STATE_KEY, () => emptyState(null));

    /** 지금 로그인한 사람이 바꾼 것인가 */
    const current = (): OverrideState | null => (user.value && state.value.viewerId === user.value.id ? state.value : null);

    /** 지금 사람의 것으로 쓴다 — 앞 사람의 것이 남아 있으면 비우고 시작한다 */
    function writable(): OverrideState {
        return current() ?? emptyState(user.value?.id ?? null);
    }

    return {
        /**
         * 글·댓글의 차단 표시 — 서버 값에 방금 바꾼 것을 겹친다.
         * @param authorId 글쓴이·댓글쓴이의 회원 ID(비회원·탈퇴는 null)
         * @param server 서버가 준 `blocked` — 칸을 주지 않는 목록이면 undefined
         */
        blockedOf(authorId: string | null | undefined, server: BlockedBy | null | undefined): BlockedBy | null {
            // 내 글·내 댓글은 접지 않는다(서버도 내 것에는 `blocked` 를 싣지 않는다)
            if (authorId && authorId === user.value?.id) return null;
            return UtilBlock.resolve(server ?? null, authorId ? current()?.blocks[authorId] : undefined);
        },

        /**
         * 이 회원을 차단했는가 — 서버 값(`blockedByMe` 등)에 방금 바꾼 것을 겹친다.
         * @param userId 회원 ID
         * @param server 서버가 준 "차단했다" 여부(없으면 false)
         */
        userBlocked(userId: string | null | undefined, server: boolean | null | undefined): boolean {
            return UtilBlock.resolve(server ? 'USER' : null, userId ? current()?.blocks[userId] : undefined) === 'USER';
        },

        /**
         * 이 회원에게 남긴 메모 — 서버 값에 방금 바꾼 것을 겹친다.
         * @param userId 회원 ID(비회원·탈퇴는 null)
         * @param server 서버가 준 `memo`(없으면 null)
         */
        memoOf(userId: string | null | undefined, server: string | null | undefined): string | null {
            if (!userId) return server ?? null;
            const memos = current()?.memos;
            return memos && userId in memos ? (memos[userId] ?? null) : (server ?? null);
        },

        /** 방금 차단했다(true)·풀었다(false) */
        setBlocked(userId: string, blocked: boolean): void {
            const base = writable();
            state.value = { ...base, blocks: { ...base.blocks, [userId]: blocked } };
        },

        /** 방금 메모를 남겼다(고쳤다)·지웠다(null) */
        setMemo(userId: string, memo: string | null): void {
            const base = writable();
            state.value = { ...base, memos: { ...base.memos, [userId]: memo } };
        },
    };
}
