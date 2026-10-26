<template>
    <!-- 소모임 공개 범위 칩(5단계) — 「공개」(지구) · 「비공개」(자물쇠). 색만으로 가르지 않도록 글자와 아이콘이 늘 있고, 스크린리더는 「공개 소모임」으로 읽는다.
         값이 없으면(공개 범위 기능이 꺼진 서버의 비공개 소모임) 아무것도 그리지 않는다. 목록·내 소모임 줄과 상세 머리·글 머리가 같이 쓴다.
         알약 정렬은 components.md 2-4(아이콘 + 글자 → items-baseline, 위 여백을 줄이고 아래를 늘려 한글이 내려앉는 것을 함께 올린다) -->
    <span
        v-if="value"
        class="inline-flex shrink-0 items-baseline justify-center gap-1 whitespace-nowrap rounded px-1.5 pb-[0.3125rem] pt-[0.1875rem] text-[0.6875rem] font-semibold leading-none"
        :class="value === 'PUBLIC' ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300' : 'bg-surface-200 text-surface-700 dark:bg-surface-700 dark:text-surface-200'"
        :data-visibility="value"
        data-testid="group-visibility-chip"
    >
        <i :class="[UtilGroupVisibilityMessages.chipIcon(value), 'text-[0.625rem]']" aria-hidden="true" />
        <span aria-hidden="true">{{ UtilGroupVisibilityMessages.chipLabel(t, value) }}</span>
        <span class="sr-only">{{ UtilGroupVisibilityMessages.nameOf(t, value) }}</span>
    </span>
</template>

<script setup lang="ts">
import UtilGroupVisibility from '~/utils/util-group-visibility';
import UtilGroupVisibilityMessages from '~/utils/util-group-visibility-messages';

interface Props {
    /** 응답의 칸 그대로 — `null`·칸 없음·모르는 값이면 그리지 않는다 */
    visibility?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
    visibility: null,
});

const { t } = useI18n();
const value = computed(() => UtilGroupVisibility.of(props.visibility));
</script>
