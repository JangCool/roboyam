<template>
    <!--
        팝업 미리보기 틀 — 공개 사이트 화면(흐린 자리표시 줄) 위에 어두운 막을 깔고 가운데에 팝업 창을 놓는다. 공개 `SitePopupLayer` 와 같은 배치:
          띄울 창 번호가 같은 팝업끼리 한 창(`SitePopupCard`)에서 ‹ › 로 넘기고(목록 차례), 번호가 다르면
          PC: 여러 창 배치(`layout`)대로 가로로 나란히(틀이 좁으면 창이 줄어든다) 또는 위아래로 쌓기(창 번호 차례) · 모바일(폭 360px 세로 틀): 창을 하나씩 — 아래 [다음 창]으로 공개 사이트에서 앞 창을 닫은 다음을 본다.
        보이는 팝업이 없으면 빈 화면 안내.
    -->
    <div class="flex flex-col gap-2">
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

            <div v-if="shown.length" class="relative flex w-full items-center justify-center gap-4" :class="vertical ? 'flex-col' : ''">
                <div v-for="group in shown" :key="group.no" class="flex min-w-0 flex-col items-center gap-1.5" :class="device !== 'PC' ? 'w-full' : vertical ? 'w-full max-w-[400px]' : 'max-w-[400px] flex-1'">
                    <span v-if="windows.length > 1" class="rounded-full bg-black/55 px-2 py-0.5 text-[11px] font-medium text-white">{{ t('popup.windowNo', { n: group.no }) }}</span>
                    <SitePopupCard
                        :key="currentOf(group).key"
                        :popup="currentOf(group)"
                        :device="device"
                        :index="indexOf(group)"
                        :total="group.items.length"
                        @step="(delta) => step(group, delta)"
                    />
                </div>
            </div>
            <p v-else class="relative m-0 rounded-lg bg-white/90 px-4 py-3 text-center text-sm text-surface-700 dark:bg-surface-900/90 dark:text-surface-200">{{ emptyText || t('popup.previewNone') }}</p>
        </div>

        <!-- 모바일 — 창이 여럿이면 차례로 -->
        <div v-if="device === 'MOBILE' && windows.length > 1" class="flex items-center justify-center gap-2 text-xs text-muted-color">
            <Button type="button" icon="pi pi-angle-left" size="small" severity="secondary" variant="text" :aria-label="t('popup.previewPrevWindow')" :disabled="mobileWindow === 0" @click="mobileWindow--" />
            <span class="tabular-nums">{{ t('popup.previewWindowStep', { n: mobileWindow + 1, total: windows.length }) }}</span>
            <Button type="button" icon="pi pi-angle-right" size="small" severity="secondary" variant="text" :aria-label="t('popup.previewNextWindow')" :disabled="mobileWindow >= windows.length - 1" @click="mobileWindow++" />
        </div>
    </div>
</template>

<script setup lang="ts">
import type { SitePopupDevice, SitePopupLayout } from '~/composables/popup/api/useSitePopupApi.types';
import type { SitePopupPreview } from '~/utils/util-site-popup';

interface Props {
    popups: SitePopupPreview[];
    device?: SitePopupDevice;
    /** 보일 팝업이 없을 때 안내 — 비우면 기본 문구 */
    emptyText?: string;
    /** 여러 창 배치(PC) */
    layout?: SitePopupLayout;
}

interface PreviewWindow {
    no: number;
    items: SitePopupPreview[];
}

const props = withDefaults(defineProps<Props>(), { device: 'PC', emptyText: '', layout: 'HORIZONTAL' });
/** PC 에서 창을 위아래로 쌓는다 */
const vertical = computed(() => props.device === 'PC' && props.layout === 'VERTICAL');
const { t } = useI18n();

/** 창 번호별로 묶은 것(창 번호 차례, 창 안은 받은 차례) */
const windows = computed<PreviewWindow[]>(() => {
    const map = new Map<number, SitePopupPreview[]>();
    for (const popup of props.popups) map.set(popup.windowNo, [...(map.get(popup.windowNo) ?? []), popup]);
    return [...map.entries()].sort(([a], [b]) => a - b).map(([no, items]) => ({ no, items }));
});

/** 모바일에서 지금 보는 창(0부터) */
const mobileWindow = ref(0);
const shown = computed(() => (props.device === 'PC' ? windows.value : windows.value.slice(mobileWindow.value, mobileWindow.value + 1)));

/** 창마다 지금 장(0부터) */
const indexes = ref<Record<number, number>>({});

function indexOf(group: PreviewWindow): number {
    return Math.min(indexes.value[group.no] ?? 0, group.items.length - 1);
}

function currentOf(group: PreviewWindow): SitePopupPreview {
    return group.items[indexOf(group)]!;
}

function step(group: PreviewWindow, delta: -1 | 1): void {
    const count = group.items.length;
    if (count > 1) indexes.value = { ...indexes.value, [group.no]: (indexOf(group) + delta + count) % count };
}

// 목록이 바뀌면(기기 바꾸기 등) 첫 창·첫 장부터
watch(
    () => props.popups.map((popup) => `${popup.key}:${popup.windowNo}`).join(','),
    () => {
        indexes.value = {};
        mobileWindow.value = 0;
    },
);
</script>
