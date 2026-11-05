<template>
    <!-- 알약 칩 한 묶음(하나만 눌린다) — 로보티콘 상점의 가격·정렬 거르기, 팩 상세의 미리보기 바탕. 모양은 포인트 상점 아바타 탭의 카테고리 칩(`AvatarCategoryChips`)과 같은 클래스다 -->
    <div role="group" :aria-label="label" class="flex flex-wrap gap-1.5" :data-testid="testid || undefined">
        <button
            v-for="opt in options"
            :key="opt.value"
            type="button"
            class="inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-full border px-3 py-0.5 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            :class="
                model === opt.value
                    ? 'border-surface-900 bg-surface-900 font-semibold text-surface-0 dark:border-surface-300 dark:bg-surface-300 dark:text-surface-900'
                    : 'border-surface bg-surface-0 text-color hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800'
            "
            :aria-pressed="model === opt.value"
            @click="model = opt.value"
        >
            <i v-if="opt.icon" :class="[opt.icon, 'text-xs']" aria-hidden="true" />{{ opt.label }}
        </button>
    </div>
</template>

<script setup lang="ts" generic="T extends string">
import type { EmoticonChipOption } from '~/components/emoticon/EmoticonChipGroup.types';

interface Props {
    options: EmoticonChipOption<T>[];
    /** 스크린리더용 묶음 이름 */
    label: string;
    testid?: string;
}

withDefaults(defineProps<Props>(), {
    testid: '',
});

/** 눌린 칩의 값 */
const model = defineModel<T>({ required: true });
</script>
