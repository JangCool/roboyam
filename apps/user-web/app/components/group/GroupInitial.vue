<template>
    <!-- 소모임 대표 이미지 상자 — 이미지가 없거나 못 읽으면 이름의 첫 글자. 움직이는 GIF·WebP 는 그대로 움직인다. 장식이라 스크린리더는 건너뛴다(이름이 옆에 있다) -->
    <span v-if="image" class="block shrink-0 overflow-hidden bg-surface-100 dark:bg-surface-800" :class="SIZE_CLASS[size]" aria-hidden="true" data-testid="group-image">
        <img :src="image" alt="" class="size-full object-cover" decoding="async" @error="failed = image" />
    </span>
    <span
        v-else
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
    /** 대표 이미지 주소 — 없으면 첫 글자 */
    src?: string | null;
    /** `md` 목록 줄 · `lg` 상세·관리 머리·지금 뜨는 카드 · `xl` 이미지 고르기 미리보기 */
    size?: 'md' | 'lg' | 'xl';
}

const props = withDefaults(defineProps<Props>(), {
    src: null,
    size: 'md',
});

const SIZE_CLASS = {
    md: 'size-9 rounded-full text-sm',
    lg: 'size-11 rounded-xl text-base',
    xl: 'size-16 rounded-2xl text-2xl',
} as const;

/** 못 읽은 주소 — 같은 주소면 글자로 돌아간다(주소가 바뀌면 다시 해 본다) */
const failed = ref<string | null>(null);
const image = computed(() => (props.src && props.src !== failed.value ? props.src : null));
</script>
