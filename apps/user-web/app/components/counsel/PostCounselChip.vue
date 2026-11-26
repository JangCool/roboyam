<template>
    <!--
        글 줄의 AI 연애상담소 상태 칩(ai-love-counsel-contract — 「AI 상담소」 말머리 글만, 서버 `PostRow.counsel`) — 채택 · AI 답변 n · 답변 기다리는 중. 위험 사연(`RISK`)은 칩 없음(목록에서 드러내지 않는다).
        글 줄 칩 묶음(`PostRowChips`)이 말머리·처리 상태·구입 경로 다음에 그린다 — 목록 모양(LIST·CARD·GALLERY)·칩 위치와 상관없이 같은 자리 규칙. `inline` 이면 제목 앞 한 줄 안(오른쪽 여백·기준선 맞춤).
        글자 세로 맞춤은 components.md 2-4(아이콘 + 글자는 `items-baseline`, 글자에만 `chip-label`).
    -->
    <span v-if="label" :class="[BASE, toneClass, inline ? 'mr-1 align-[1px]' : '']" :data-counsel="counsel!.status" data-testid="post-row-counsel">
        <span class="sr-only">{{ t('counsel.chipLabel') }}: </span>
        <i v-if="counsel!.status === 'PICKED'" class="pi pi-check text-[0.5625rem]" aria-hidden="true" />
        <span class="chip-label">{{ label }}</span>
    </span>
</template>

<script setup lang="ts">
import type { PostCounselRow } from '~/composables/board/api/usePostApi.types';

const BASE = 'inline-flex shrink-0 items-baseline justify-center gap-0.5 whitespace-nowrap rounded px-1.5 py-[3px] text-[0.6875rem] font-medium leading-none';

interface Props {
    counsel: PostCounselRow | null;
    /** 제목 앞 한 줄 안에 붙인다 */
    inline?: boolean;
}

const props = withDefaults(defineProps<Props>(), { inline: false });

const { t } = useI18n();

const label = computed(() => {
    const c = props.counsel;
    if (!c) return '';
    if (c.status === 'PICKED') return t('counsel.chipPicked');
    if (c.status === 'ANSWERED') return t('counsel.chipAnswered', { n: c.aiAnswerCount });
    if (c.status === 'WAITING') return t('counsel.chipWaiting');
    return '';
});
const toneClass = computed(() => {
    switch (props.counsel?.status) {
        case 'PICKED':
            return 'bg-green-100 font-semibold text-green-900 dark:bg-green-900/40 dark:text-green-200';
        case 'ANSWERED':
            return 'bg-violet-100 text-violet-900 dark:bg-violet-900/40 dark:text-violet-200';
        default:
            return 'border border-surface text-muted-color';
    }
});
</script>
