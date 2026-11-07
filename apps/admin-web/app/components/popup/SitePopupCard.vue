<template>
    <!--
        팝업 한 장의 모양(관리자 미리보기용) — 공개 사이트 `SitePopupLayer`(user-web `components/shell/`)의 카드와 같은 크기·배치·글자를 쓴다. 두 곳을 같이 고친다.
          PC: 폭 400px · 모바일: 화면 폭 - 좌우 1.25rem(미리보기 틀 안에서는 틀 폭을 채운다).
          이미지: 그림 한 장(가로 맞춤, 세로는 그림 비율 — 화면 높이의 70% 를 넘으면 줄여 담는다). 텍스트: 제목(선택) · 내용(줄바꿈 유지) · 링크가 있으면 단추.
          여러 장이면 위 오른쪽에 「1 / 3」, 양옆에 ‹ › (공개 사이트는 밀어서도 넘긴다).
          아래 줄: 왼쪽 「보지 않기」(운영자가 고른 것 — 「닫기」만이면 비운다) · 오른쪽 「닫기」.
        미리보기라 링크·단추를 눌러도 이동하지 않는다(링크 주소는 마우스를 올리면 보인다).
    -->
    <div class="flex w-full flex-col overflow-hidden rounded-xl bg-white text-surface-900 shadow-2xl ring-1 ring-black/5 dark:bg-surface-900 dark:text-surface-0" :class="device === 'PC' ? 'max-w-[400px]' : 'max-w-full'" data-testid="site-popup-card">
        <div class="relative">
            <template v-if="popup.kind === 'IMAGE'">
                <img v-if="popup.imageUrl" :src="popup.imageUrl" :alt="popup.imageAlt ?? ''" class="block max-h-[min(70vh,600px)] w-full object-contain" :class="popup.link ? 'cursor-pointer' : ''" :title="popup.link ?? undefined" />
                <div v-else class="flex aspect-[4/5] w-full flex-col items-center justify-center gap-2 bg-surface-100 text-sm text-muted-color dark:bg-surface-800">
                    <i class="pi pi-image text-2xl" aria-hidden="true" />
                    {{ t('popup.previewNoImage') }}
                </div>
            </template>
            <div v-else class="flex flex-col gap-3 px-6 pb-5 pt-6">
                <h2 v-if="popup.heading" class="m-0 text-lg font-bold leading-snug [overflow-wrap:anywhere]">{{ popup.heading }}</h2>
                <p class="m-0 max-h-[min(50vh,420px)] overflow-y-auto whitespace-pre-line text-sm leading-relaxed text-surface-700 [overflow-wrap:anywhere] dark:text-surface-200">{{ popup.body || t('popup.previewNoBody') }}</p>
                <span v-if="popup.link" class="mt-1 inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary text-sm font-semibold text-primary-contrast" :title="popup.link">
                    {{ popup.buttonLabel || t('popup.buttonDefault') }}
                </span>
            </div>

            <template v-if="total > 1">
                <span class="absolute right-3 top-3 rounded-full bg-black/55 px-2 py-0.5 text-[11px] font-medium tabular-nums text-white">{{ index + 1 }} / {{ total }}</span>
                <button type="button" class="absolute left-2 top-1/2 inline-flex size-8 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full border-0 bg-black/40 text-white hover:bg-black/60" :aria-label="t('popup.prev')" @click="emit('step', -1)">
                    <i class="pi pi-chevron-left text-xs" aria-hidden="true" />
                </button>
                <button type="button" class="absolute right-2 top-1/2 inline-flex size-8 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full border-0 bg-black/40 text-white hover:bg-black/60" :aria-label="t('popup.next')" @click="emit('step', 1)">
                    <i class="pi pi-chevron-right text-xs" aria-hidden="true" />
                </button>
            </template>
        </div>

        <div class="flex h-11 shrink-0 items-center justify-between gap-3 bg-neutral-900 px-4 text-[13px] text-neutral-100">
            <span v-if="popup.hideOption !== 'NONE'" class="inline-flex min-w-0 items-center gap-1.5">
                <i class="pi pi-check-circle text-[0.8rem] text-neutral-400" aria-hidden="true" />
                <span class="truncate">{{ t(`popup.hide.${popup.hideOption}`) }}</span>
            </span>
            <span v-else />
            <span class="inline-flex shrink-0 items-center gap-1 font-medium">
                {{ t('popup.close') }}
                <i class="pi pi-times text-[0.7rem]" aria-hidden="true" />
            </span>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { SitePopupDevice } from '~/composables/popup/api/useSitePopupApi.types';
import type { SitePopupPreview } from '~/utils/util-site-popup';

interface Props {
    popup: SitePopupPreview;
    device?: SitePopupDevice;
    /** 여러 장일 때 지금 몇 번째(0부터)·전체 수 */
    index?: number;
    total?: number;
}

withDefaults(defineProps<Props>(), { device: 'PC', index: 0, total: 1 });
const emit = defineEmits<{ step: [delta: -1 | 1] }>();
const { t } = useI18n();
</script>
