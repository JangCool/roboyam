<template>
    <!--
        광고 자리 미리보기 틀 — 머리(「미리보기」 + 저장 전 꼬리표) · 고르는 칸(선택) · 도식 · 설명 한 줄 · 조건 안내.
        도식은 공개 화면 짜임을 단순하게 그린 그림(회색 상자·줄 + 광고 자리만 주 색상)이라 화면 읽기에는 설명 한 줄을 그림 이름으로 준다.
        카드 바탕은 다른 카드와 같은 내용 바탕(`--p-content-background`, 라이트 흰색·다크 카드 색)이고, 도식은 회색 무대(라이트 surface-100 · 다크 surface-950 — `AdPlacementWingStage` 와 같은 바탕) 위에 그려 카드 바탕과 구분된다.
        머리(높이 min-h-16 · 아래 구분선)는 가운데 설정 카드 머리와 같아 두 카드의 선이 한 줄에 선다(`pages/ads/placements.vue`).
    -->
    <section class="flex min-w-0 flex-col rounded-xl border border-(--line) bg-(--p-content-background)" :aria-labelledby="titleId" data-testid="ad-placement-preview">
        <div class="flex min-h-16 items-center gap-2 border-b border-(--line) px-4 py-3" data-testid="ad-placement-preview-head">
            <h2 :id="titleId" class="m-0 inline-flex min-w-0 flex-1 items-center gap-1.5 text-sm font-medium">
                <i class="pi pi-eye text-xs text-muted-color" aria-hidden="true" />{{ t('ad.previewTitle') }}
            </h2>
            <Tag v-if="unsaved" severity="warn" :value="t('ad.previewUnsaved')" class="shrink-0" data-testid="ad-placement-preview-unsaved" />
        </div>
        <div class="flex min-w-0 flex-col gap-2.5 p-4">
            <slot name="controls" />
            <div role="img" :aria-label="caption" class="min-w-0">
                <slot />
            </div>
            <p class="m-0 text-xs leading-relaxed" aria-live="polite" data-testid="ad-placement-preview-caption">{{ caption }}</p>
            <ul v-if="notes.length" class="m-0 flex list-none flex-col gap-1 p-0">
                <li v-for="(note, i) in notes" :key="i" class="flex items-start gap-1.5 text-[0.6875rem] leading-relaxed text-muted-color">
                    <i class="pi pi-info-circle mt-[0.2em] shrink-0 text-[0.625rem]" aria-hidden="true" /><span class="min-w-0">{{ note }}</span>
                </li>
            </ul>
        </div>
    </section>
</template>

<script setup lang="ts">
interface Props {
    /** 도식 아래 설명 한 줄(그림 이름으로도 쓴다) */
    caption: string;
    /** 조건 안내(창 너비·빠지는 게시판 등) */
    notes?: string[];
    /** 저장된 값과 다른 폼 값으로 그리는 중 */
    unsaved?: boolean;
}

withDefaults(defineProps<Props>(), { notes: () => [], unsaved: false });
const { t } = useI18n();
const titleId = useId();
</script>
