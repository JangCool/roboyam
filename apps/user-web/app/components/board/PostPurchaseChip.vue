<template>
    <!--
        글 줄의 구입 경로 칩(trial-group-contract — 체험단 · 구매 인증 · 직접 구매 · 협찬, 대여·없음은 칩 없음). 서버가 주는 글만(구입 경로 받는 게시판).
        게시판 목록 칩 묶음(`PostRowChips`)과 섞인 목록(홈 글 목록 가젯 `GadgetPostRow` · 하이라이트 `GadgetHighlight` — 점검 D11, 서버 `PostFeedRowView.purchaseSource`)이 같은 부품.
        `inline` 이면 제목 앞 한 줄 안(오른쪽 여백·기준선 맞춤).
    -->
    <span v-if="chip" :class="[BASE, toneClass, inline ? 'mr-1 align-[1px]' : '']" :data-purchase="chip" data-testid="post-row-purchase">
        <i v-if="chip === 'PROOF'" class="pi pi-verified text-[0.5625rem]" aria-hidden="true" />
        <span class="chip-label">{{ t(`trial.chip.${chip}`) }}</span>
    </span>
</template>

<script setup lang="ts">
import type { PostRow } from '~/composables/board/api/usePostApi.types';
import UtilTrial from '~/utils/util-trial';

const BASE = 'inline-flex shrink-0 items-center justify-center gap-0.5 whitespace-nowrap rounded px-1.5 py-[3px] text-[0.6875rem] font-medium leading-none';

interface Props {
    row: Pick<PostRow, 'purchaseSource' | 'purchaseProofApproved'>;
    /** 제목 앞 한 줄 안에 붙인다 */
    inline?: boolean;
}

const props = withDefaults(defineProps<Props>(), { inline: false });

const { t } = useI18n();

const chip = computed(() => UtilTrial.chipOf(props.row.purchaseSource, props.row.purchaseProofApproved));
const toneClass = computed(() => {
    switch (chip.value) {
        case 'TRIAL':
            return 'bg-violet-100 text-violet-900 dark:bg-violet-900/40 dark:text-violet-200';
        case 'PROOF':
            return 'bg-green-100 text-green-900 dark:bg-green-900/40 dark:text-green-200';
        case 'SPONSORED':
            return 'bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200';
        default:
            return 'border border-surface text-muted-color';
    }
});
</script>
