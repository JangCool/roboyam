<template>
    <!-- 이니셜 아바타 — 사진이 없는 관리자 계정이라 이름의 글자 하나를 보인다. 이름은 옆에 글자로 적히므로 장식이다(스크린리더는 건너뛴다) -->
    <Avatar :label="initial" shape="circle" :size="props.size" class="avatar-primary shrink-0" aria-hidden="true" />
</template>

<script setup lang="ts">
interface Props {
    name?: string;
    size?: 'normal' | 'large' | 'xlarge';
}

const props = withDefaults(defineProps<Props>(), { name: '', size: 'normal' });

/** 한글은 마지막 글자(이름), 영문은 첫 글자 */
const initial = computed(() => {
    const text = props.name.trim();
    if (!text) return '?';
    return /[가-힣]/.test(text) ? text.slice(-1) : (text[0] ?? '?').toUpperCase();
});
</script>
