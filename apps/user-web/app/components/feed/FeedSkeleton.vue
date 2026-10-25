<template>
    <!-- 글 줄 목록의 불러오는 중 자리 — 최신글·인기글·검색·회원 프로필이 함께 쓴다. 읽을 것이 없으므로 스크린리더에는 감춘다(상태 안내는 부르는 쪽이 한다) -->
    <div class="-mx-4 md:mx-0" aria-hidden="true" data-testid="feed-skeleton">
        <div v-for="n in count" :key="n" class="border-b border-surface px-4 py-3.5 md:px-2">
            <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-2/3'" />
            <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 줄 수 */
    count?: number;
}

withDefaults(defineProps<Props>(), { count: 7 });
</script>
