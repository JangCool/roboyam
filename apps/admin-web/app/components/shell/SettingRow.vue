<template>
    <!-- 화면 설정의 한 줄 — 이름 아래에 고르는 부품(슬롯)을 둔다. 이름은 묶음(group)의 라벨이라 스크린리더가 함께 읽는다 -->
    <section class="setting-row" role="group" :aria-labelledby="labelId">
        <div :id="labelId" class="mb-2 text-sm">{{ label }}</div>
        <slot />
    </section>
</template>

<script setup lang="ts">
interface Props {
    label: string;
}

withDefaults(defineProps<Props>(), {});
const labelId = useId();
</script>
