<template>
    <!--
        팝업 미리보기 틀 — 공개 사이트 화면(흐린 자리표시 줄) 위에 어두운 막을 깔고 가운데에 팝업 카드(`SitePopupCard`)를 놓는다.
          PC: 넓은 틀(가로가 길다) · 모바일: 폭 360px 세로 틀. 여러 장이면 카드의 ‹ › 로 넘긴다(차례 = 목록 차례).
        보이는 팝업이 없으면 빈 화면 안내.
    -->
    <div
        class="relative mx-auto flex w-full items-center justify-center overflow-hidden rounded-xl border border-(--line) bg-surface-50 dark:bg-surface-950"
        :class="device === 'PC' ? 'min-h-[34rem] px-6 py-8' : 'min-h-[40rem] max-w-[360px] px-5 py-8'"
        data-testid="site-popup-preview-frame"
    >
        <!-- 사이트 자리표시 — 머리 줄과 글 줄 -->
        <div class="pointer-events-none absolute inset-0 flex flex-col gap-3 p-4" aria-hidden="true">
            <div class="h-8 rounded-md bg-surface-200 dark:bg-surface-800" />
            <div class="h-4 w-1/2 rounded bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 8" :key="n" class="h-3 rounded bg-surface-200/80 dark:bg-surface-800/80" :style="{ width: `${90 - ((n * 13) % 35)}%` }" />
        </div>
        <div class="pointer-events-none absolute inset-0 bg-black/50" aria-hidden="true" />

        <SitePopupCard v-if="current" :key="current.key" class="relative" :popup="current" :device="device" :index="index" :total="popups.length" @step="step" />
        <p v-else class="relative m-0 rounded-lg bg-white/90 px-4 py-3 text-center text-sm text-surface-700 dark:bg-surface-900/90 dark:text-surface-200">{{ emptyText || t('popup.previewNone') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { SitePopupDevice } from '~/composables/popup/api/useSitePopupApi.types';
import type { SitePopupPreview } from '~/utils/util-site-popup';

interface Props {
    popups: SitePopupPreview[];
    device?: SitePopupDevice;
    /** 보일 팝업이 없을 때 안내 — 비우면 기본 문구 */
    emptyText?: string;
}

const props = withDefaults(defineProps<Props>(), { device: 'PC', emptyText: '' });
const { t } = useI18n();

const index = ref(0);
const current = computed(() => props.popups[Math.min(index.value, props.popups.length - 1)] ?? null);

function step(delta: -1 | 1): void {
    const count = props.popups.length;
    if (count > 1) index.value = (index.value + delta + count) % count;
}

// 목록이 바뀌면(기기 바꾸기 등) 첫 장부터
watch(
    () => props.popups.map((popup) => popup.key).join(','),
    () => (index.value = 0),
);
</script>
