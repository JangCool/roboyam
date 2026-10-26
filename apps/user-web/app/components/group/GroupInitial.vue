<template>
    <!-- 소모임 이름의 첫 글자 상자 — 목록에는 프로필 그림이 없다(community 와 같다). 장식이라 스크린리더는 건너뛴다 -->
    <span
        class="grid shrink-0 place-items-center bg-primary-100 font-bold leading-none text-primary-700 dark:bg-primary-950 dark:text-primary-300"
        :class="SIZE_CLASS[size]"
        aria-hidden="true"
        data-testid="group-initial"
        >{{ UtilGroup.initial(name) }}</span
    >
</template>

<script setup lang="ts">
import UtilGroup from '~/utils/util-group';

interface Props {
    name: string;
    /** `md` 목록 줄 · `lg` 상세·관리 머리 */
    size?: 'md' | 'lg';
}

const props = withDefaults(defineProps<Props>(), {
    size: 'md',
});

const SIZE_CLASS = {
    md: 'size-9 rounded-full text-sm',
    lg: 'size-11 rounded-xl text-base',
} as const;
</script>
