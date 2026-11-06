import type { InjectionKey } from 'vue';
import type { PostRow } from '~/composables/board/api/usePostApi.types';

/** 소모임 글 목록의 공개 라벨 문맥 — 공개 소모임인가 */
const KEY: InjectionKey<Ref<boolean>> = Symbol('group-row-tag');

/**
 * 소모임 상세의 글 목록이 게시판 목록 부품(테마 `postList:*`)을 쓸 때, 글 줄 제목(`PostRowTitle`)에 공개 라벨(「비공개」·「공개 전환됨」)을 붙이게 한다.
 * 소모임 상세만 건다 — 최신글·인기글 같은 다른 목록의 소모임 글 줄에는 붙지 않는다. setup 에서 부른다.
 */
export function provideGroupRowTag(publicGroup: Ref<boolean>): void {
    provide(KEY, publicGroup);
}

/**
 * 이 글 줄에 붙일 공개 라벨 — 소모임 상세 목록이 아니거나 소모임 글이 아니면 null. 공개 소모임은 글이 처음부터 모두에게 보여 「공개 전환됨」을 붙이지 않는다(멤버 전용으로 돌린 글만 「비공개」).
 * setup 에서 부른다.
 */
export function useGroupRowTag(row: () => PostRow) {
    const publicGroup = inject(KEY, null);
    return computed(() => {
        const group = row().group;
        if (!publicGroup || !group) return null;
        if (publicGroup.value && group.visibility === 'PUBLIC') return null;
        return group.visibility;
    });
}
