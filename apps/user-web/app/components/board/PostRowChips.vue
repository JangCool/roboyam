<template>
    <!--
        글 줄의 칩 묶음(post-chip-position-contract) — (소모임 상세면 공개 라벨) · 말머리 · 처리 상태 · 19. 비밀글 자물쇠·고정 압정은 칩이 아니라 제목 앞 아이콘(`PostRowTitle`)이다.
        `inline` 이면 제목 앞 한 줄 안(`BEFORE` — `PostRowTitle` 이 그린다), 아니면 제목 위·닉네임 아래의 칩 줄(`PostRowFrame`). 칩 줄은 칩이 하나도 없으면 아무것도 그리지 않는다.
        기본 슬롯 = 이 부품이 모르는 앞쪽 칩(섞인 목록의 게시판 이름·「어제」 등 — `FeedPostItem`).
        `general` 이면 말머리가 없을 때 흐린 「일반」 자리 칩(「제목 위」 윗줄 — 칩 열이 줄마다 같은 자리에서 시작하게)
    -->
    <component :is="inline ? 'span' : 'div'" v-if="inline || hasAny" :class="inline ? '' : 'flex min-w-0 flex-wrap items-center gap-1'" :data-testid="inline ? undefined : 'post-row-chips'">
        <slot />
        <!-- 소모임 상세 목록의 공개 라벨(`provideGroupRowTag` 를 건 목록에서만) — 글자가 늘 있다(색만으로 가르지 않는다) -->
        <GroupTag v-if="groupTag" :tone="groupTag === 'PUBLIC' ? 'blue' : 'gray'" :class="chipClass" data-testid="group-post-visibility">{{ UtilGroupMessages.postRowVisibility(t, groupTag) }}</GroupTag>
        <PostPrefixTag v-if="prefixShown" :prefix="row.prefix!" :class="chipClass" />
        <span
            v-else-if="noneShown"
            class="inline-flex shrink-0 items-center justify-center whitespace-nowrap rounded border border-surface px-1.5 py-[3px] text-[0.6875rem] font-medium leading-none text-muted-color"
            data-testid="post-row-prefix-none"
        >
            <span class="relative top-[-0.09em]">{{ t('board.prefixNone') }}</span>
        </span>
        <PostStatusTag v-if="row.processStatus" :status="row.processStatus" :class="chipClass" />
        <span v-if="row.adult" class="inline-flex size-4 shrink-0 items-center justify-center rounded-full bg-red-600 text-[0.5625rem] font-bold leading-none text-white" :class="chipClass" :title="t('board.adultMark')">
            <span class="relative top-[-0.09em]" aria-hidden="true">19</span>
            <span class="sr-only">{{ t('board.adultMark') }}</span>
        </span>
    </component>
</template>

<script setup lang="ts">
import type { PostRow } from '~/composables/board/api/usePostApi.types';
import UtilGroupMessages from '~/utils/util-group-messages';

interface Props {
    row: PostRow;
    showPrefix?: boolean;
    /** 제목 앞 한 줄 안에 붙인다(`BEFORE`) — 칩마다 오른쪽 여백·글자 기준선 맞춤 */
    inline?: boolean;
    /** 말머리가 없을 때 흐린 「일반」 칩(「제목 위」 윗줄만) */
    general?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    showPrefix: true,
    inline: false,
    general: false,
});

const slots = useSlots();
const { t } = useI18n();
/** 소모임 상세 목록의 공개 라벨 — 그 밖의 목록은 null */
const groupTag = useGroupRowTag(() => props.row);
const chipClass = computed(() => (props.inline ? 'mr-1 align-[1px]' : ''));
const prefixShown = computed(() => Boolean(props.row.prefix) && props.showPrefix);
const noneShown = computed(() => props.general && !props.inline && !prefixShown.value);
/** 칩 줄에 그릴 것이 있는가 — 없으면 칩 줄 자체를 그리지 않는다 */
const hasAny = computed(() => Boolean(slots.default) || Boolean(groupTag.value) || prefixShown.value || noneShown.value || Boolean(props.row.processStatus) || props.row.adult);
</script>
