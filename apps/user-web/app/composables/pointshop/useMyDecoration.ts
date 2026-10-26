import { AUTH_USER_STATE_KEY } from '~/composables/common/useApiClient';
import type { AuthorDecoration } from '~/composables/pointshop/api/usePointShopApi.types';
import type { MyEquipped } from '~/composables/pointshop/api/useMyItemsApi.types';
import UtilAvatar from '~/utils/util-avatar';
import UtilPointShop from '~/utils/util-point-shop';

/**
 * 내 장식(닉네임 색·프로필 테두리)과 프로필 아바타 — 이 기기에서 **방금 장착·해제·고른** 것을 작성자 표시(`PostAuthorLabel`)·공개 프로필 머리·글 보기 머리·`/me` 에 바로 겹친다
 * (`useBlockOverrides` 와 같은 방식 — 이미 받아 둔 화면이 서버 응답을 다시 받지 않아도 맞는다).
 *
 * 서버는 작성자(`PostAuthor.decoration`·`avatarUrl`)·공개 프로필·쪽지 상대·내 프로필에 장식과 아바타를 실어 준다(만료된 장착은 null, 비회원 아바타는 null).
 * - **작성자가 나**이고 방금 바꾼 값이 있으면 그것, 아니면 서버 값.
 * - 남이면 서버 값 그대로. 칸이 없는 응답(옛 응답)이면 장식 없음 · 아바타는 `basic-default`(회원) — 서버 규칙과 같다.
 * - **로그인한 사람마다 따로다** — 다른 계정으로 바뀌면 앞 사람의 값은 쓰지 않는다. 메모리에만 있다(`useState`). 서버 값이 늘 정본이고, 겹치기는 새로고침하면 없어진다.
 * - 이 함수는 가볍다(작성자 표시처럼 줄마다 쓰는 자리가 많다) — 요청 통로를 만들지 않는다. `/me`·미리보기처럼 서버 값을 따로 받지 않는 곳은 `mine()`·`myAvatarUrl()` 에 서버 값을 넘긴다.
 */

interface DecorationState {
    /** 이 값을 바꾼 사람 */
    ownerId: string;
    decoration: AuthorDecoration;
}

interface AvatarState {
    ownerId: string;
    url: string;
}

export function useMyDecoration() {
    const user = useState<{ id: string } | null>(AUTH_USER_STATE_KEY);
    const state = useState<DecorationState | null>('pointshop-my-decoration', () => null);
    /** 방금 고른 내 아바타(그림 주소) */
    const avatarState = useState<AvatarState | null>('pointshop-my-avatar', () => null);

    const isMe = (memberId: string | null | undefined) => Boolean(memberId) && memberId === user.value?.id;
    /** 지금 로그인한 사람이 방금 바꾼 장식 — 없으면 undefined */
    const changed = (): AuthorDecoration | undefined => (state.value && user.value && state.value.ownerId === user.value.id ? state.value.decoration : undefined);
    /** 지금 로그인한 사람이 방금 고른 아바타 — 없으면 undefined */
    const changedAvatar = (): string | undefined => (avatarState.value && user.value && avatarState.value.ownerId === user.value.id ? avatarState.value.url : undefined);

    /**
     * 작성자 한 명의 장식
     * @param memberId 회원 ID(비회원·탈퇴는 null — 장식 없음)
     * @param server 서버가 준 `decoration` — 칸을 주지 않는 응답이면 undefined
     */
    function decorationOf(memberId: string | null | undefined, server: AuthorDecoration | null | undefined): AuthorDecoration | null {
        if (!memberId) return null;
        if (isMe(memberId)) {
            const mine = changed();
            if (mine !== undefined) return mine;
        }
        return server ?? null;
    }

    /**
     * 작성자 한 명의 아바타 그림 주소
     * @param memberId 회원 ID(비회원·탈퇴는 null — 이니셜)
     * @param server 서버가 준 `avatarUrl` — 칸을 주지 않는 응답이면 undefined
     */
    function avatarOf(memberId: string | null | undefined, server: string | null | undefined): string | null {
        // 비회원·탈퇴, 그리고 서버가 일부러 비운 자리(숨긴 글·지운 댓글 — null)는 이니셜
        if (!memberId || server === null) return null;
        if (isMe(memberId)) {
            const mine = changedAvatar();
            if (mine !== undefined) return mine;
        }
        return server || UtilAvatar.FALLBACK_URL;
    }

    return {
        decorationOf,
        avatarOf,

        /**
         * 지금 내 장식 — 방금 바꾼 것, 없으면 넘긴 서버 값(내 아이템의 장착·`/me` 등)
         * @param server 서버가 준 내 장식(모르면 생략)
         */
        mine: (server?: AuthorDecoration | null): AuthorDecoration | null => decorationOf(user.value?.id ?? null, server),

        /**
         * 지금 내 아바타 — 방금 고른 것, 없으면 넘긴 서버 값(내 프로필 `avatarUrl`), 그것도 없으면 `basic-default`
         * @param server 서버가 준 내 `avatarUrl`(모르면 생략)
         */
        myAvatarUrl: (server?: string | null): string => avatarOf(user.value?.id ?? null, server) ?? UtilAvatar.FALLBACK_URL,

        /** 장착·해제 응답(또는 내 아이템 목록에서 읽은 장착)을 넣는다 */
        applyEquipped(equipped: MyEquipped): void {
            if (!user.value) return;
            state.value = { ownerId: user.value.id, decoration: UtilPointShop.decorationOf(equipped) };
        },

        /** 아바타를 골랐다 — 고르기 응답(또는 내 프로필)의 `avatarUrl` 을 넣는다 */
        applyAvatar(url: string | null | undefined): void {
            if (!user.value) return;
            avatarState.value = { ownerId: user.value.id, url: url || UtilAvatar.FALLBACK_URL };
        },
    };
}
