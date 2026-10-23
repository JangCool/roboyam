import type { BlockedBy } from '~/composables/block/api/useBlockApi.types';
import UtilBlock from '~/utils/util-block';

/**
 * 이 기기에서 **방금 바꾼** 차단·이용자 메모 — 이미 받아 둔 화면(목록·댓글·프로필·작성자 이름)의 표시를 서버 응답을 기다리지 않고 바로 맞춘다(community 와 같은 방식).
 *
 * 서버는 글·댓글·작성자 응답에 `blocked`·`memo` 를 실어 준다(계약 후보 — 지금 로보얌 서버 응답에는 없다). 화면은 그 값 위에 바꾼 것을 겹친다(`blockedOf`·`memoOf`).
 * - **로그인한 사람마다 따로다** — 다른 계정으로 바뀌면 앞 사람이 바꾼 것은 쓰지 않는다(`viewerId`). 메모리에만 있다(`useState`).
 * - 이 함수는 가볍다 — 작성자 표시처럼 줄마다 쓰는 자리가 많아 로그인 상태만 `useState` 로 본다(요청 통로를 만들지 않는다).
 *
 * ⚠ MOCK: 서버 응답에 `blocked`·`memo` 가 아직 없어서 **목업 단계에는** 셸이 목업의 차단·단어·메모 한 벌을 받아 여기에 깔아 둔다(`useBlockOverridesLoader`).
 * 차단 단어로 접는 것(`WORD`)도 서버 몫인데, 목업 단계에는 서버 값이 없을 때(`undefined`) 화면이 받아 둔 단어로 대신 판정한다. 서버가 붙으면 그 갈래와 로더를 지운다.
 */

interface OverrideState {
    /** 이 값들을 바꾼 사람 — 지금 로그인한 사람과 다르면 쓰지 않는다 */
    viewerId: string | null;
    /** 회원 ID → 차단했다(true) / 풀었다(false) */
    blocks: Record<string, boolean>;
    /** 회원 ID → 남긴(고친) 메모. 지웠으면 null */
    memos: Record<string, string | null>;
    /** MOCK: 차단한 단어(서버가 `blocked: 'WORD'` 를 주기 전까지 화면이 대신 판정) */
    words: string[];
}

const STATE_KEY = 'block-overrides';

function emptyState(viewerId: string | null): OverrideState {
    return { viewerId, blocks: {}, memos: {}, words: [] };
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
         * @param server 서버가 준 `blocked` — 칸이 없으면(목업 단계) undefined
         * @param text MOCK: 단어 차단을 화면이 대신 볼 글자(글 제목·댓글 본문)
         */
        blockedOf(authorId: string | null | undefined, server: BlockedBy | null | undefined, text?: string | null): BlockedBy | null {
            // 내 글·내 댓글은 접지 않는다(서버도 내 것에는 `blocked` 를 싣지 않는다)
            if (authorId && authorId === user.value?.id) return null;
            const now = current();
            const resolved = UtilBlock.resolve(server ?? null, authorId ? now?.blocks[authorId] : undefined);
            // MOCK: 서버 값이 없을 때만 받아 둔 단어로 판정한다(서버가 붙으면 이 줄을 지운다)
            if (resolved === null && server === undefined && now && UtilBlock.wordHit(text, now.words)) return 'WORD';
            return resolved;
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

        /** MOCK: 차단 단어를 바꿨다 — 단어 차단 판정을 화면이 대신하는 동안만 쓴다 */
        setWords(words: string[]): void {
            const base = writable();
            state.value = { ...base, words: [...words] };
        },

        /** MOCK: 목업 상태 한 벌로 바꿔 깐다(로그인한 사람이 바뀌었을 때) */
        replaceAll(viewerId: string | null, snapshot: { users: string[]; words: string[]; memos: Record<string, string> }): void {
            state.value = {
                viewerId,
                blocks: Object.fromEntries(snapshot.users.map((id) => [id, true])),
                memos: { ...snapshot.memos },
                words: [...snapshot.words],
            };
        },

        /** 비운다(로그아웃) */
        reset(): void {
            state.value = emptyState(null);
        },
    };
}

/**
 * MOCK: 셸(`layouts/default.vue`)이 한 번 부른다 — 로그인한 사람이 바뀔 때마다 목업의 차단·단어·메모 한 벌을 받아 `useBlockOverrides` 에 깐다.
 * 서버가 붙으면 응답마다 `blocked`·`memo` 가 오므로 이 함수와 부르는 줄을 지운다. 실패는 조용히 넘긴다(접히지 않을 뿐이다).
 */
export function useBlockOverridesLoader(): void {
    const api = useBlockApi();
    const { user } = useAuth();
    const overrides = useBlockOverrides();
    let seq = 0;
    watch(
        () => user.value?.id ?? null,
        async (id) => {
            const mine = ++seq;
            if (!id) {
                overrides.reset();
                return;
            }
            try {
                const snapshot = await api.mockSnapshot();
                if (mine === seq) overrides.replaceAll(id, snapshot);
            } catch {
                /* 접히지 않을 뿐 — 화면은 그대로 */
            }
        },
        { immediate: true },
    );
}
