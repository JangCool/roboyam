<template>
    <!--
        광고 자리 미리보기 틀 — 머리(「미리보기」 + 저장 전 꼬리표) · 고르는 칸(선택) · 도식 · 설명 한 줄 · 조건 안내.
        도식은 공개 화면 짜임을 단순하게 그린 그림(회색 상자·줄 + 광고 자리만 주 색상)이라 화면 읽기에는 설명 한 줄을 그림 이름으로 준다.
    -->
    <section class="flex min-w-0 flex-col gap-2.5 rounded-lg border border-(--line) bg-surface-50 p-3 dark:bg-surface-950" :aria-labelledby="titleId" data-testid="ad-placement-preview">
        <div class="flex min-h-6 flex-wrap items-center gap-2">
            <h3 :id="titleId" class="m-0 inline-flex items-center gap-1.5 text-xs font-medium text-muted-color">
                <i class="pi pi-eye text-[0.6875rem]" aria-hidden="true" />{{ t('ad.previewTitle') }}
            </h3>
            <Tag v-if="unsaved" severity="warn" :value="t('ad.previewUnsaved')" data-testid="ad-placement-preview-unsaved" />
        </div>
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
