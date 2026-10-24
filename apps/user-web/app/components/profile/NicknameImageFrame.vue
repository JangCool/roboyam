<template>
    <!-- 닉네임 이미지를 실제 비율(4:1)의 틀에 보인다 — 신청 칸의 미리보기·대기·반려·적용 중 카드가 같이 쓴다. 틀이 먼저 서 있어 이미지가 늦게 와도 아래가 밀리지 않는다 -->
    <div class="aspect-[4/1] w-full max-w-60 overflow-hidden rounded-md border border-surface bg-surface-100 dark:bg-surface-800" data-testid="nickname-image-frame">
        <img v-if="!failed" :src="src" :alt="alt" class="block size-full object-contain" decoding="async" @error="failed = true" />
        <p v-else class="m-0 grid size-full place-items-center px-2 text-center text-xs text-muted-color">{{ t('nicknameImage.frameFailed') }}</p>
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 이미지 주소 — 서버가 준 것 또는 방금 고른 파일의 미리보기(`blob:`) */
    src: string;
    alt: string;
}

const props = withDefaults(defineProps<Props>(), {});
const { t } = useI18n();

const failed = ref(false);
watch(
    () => props.src,
    () => (failed.value = false),
);
</script>
