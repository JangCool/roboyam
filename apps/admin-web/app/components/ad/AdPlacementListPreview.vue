<template>
    <!-- ③ 게시판 목록 한 줄 미리보기 — 저장 전 폼 값으로 그린다. 「예시 일반 글 수」는 미리보기 안에서만 바꾸는 값(저장하지 않는다) -->
    <AdPlacementFrame :caption="caption" :notes="notes" :unsaved="unsaved">
        <template #controls>
            <div class="flex flex-wrap items-center gap-1.5" role="group" :aria-labelledby="pickId">
                <span :id="pickId" class="text-[0.6875rem] text-muted-color">{{ t('ad.previewListRowsPick') }}</span>
                <AdPlacementChip v-for="n in SAMPLE_ROWS" :key="n" :pressed="rows === n" :data-testid="`ad-placement-list-rows-${n}`" @click="rows = n">
                    {{ t('ad.previewListRowsOption', { n }) }}
                </AdPlacementChip>
            </div>
        </template>
        <!-- 회색 무대(공개 화면 바탕) 위에 흰 목록 카드 — 날개 무대와 같은 바탕 -->
        <div class="min-w-0 rounded-md bg-surface-100 p-1.5 dark:bg-surface-950">
            <AdPlacementListDiagram :rows="rows" :ad-after="adAfter" />
        </div>
    </AdPlacementFrame>
</template>

<script setup lang="ts">
import UtilAd from '~/utils/util-ad';

interface Props {
    enabled: boolean;
    after: number;
    unsaved?: boolean;
}

const props = withDefaults(defineProps<Props>(), { unsaved: false });
const { t } = useI18n();
const pickId = useId();

/** 미리보기 예시 일반 글 수 — 적은 목록(맨 끝에 붙는 경우)·보통·긴 목록 */
const SAMPLE_ROWS = [3, 10, 20] as const;
const rows = ref<number>(10);

const adAfter = computed(() => (props.enabled ? UtilAd.feedSlot(rows.value, props.after) : null));

const caption = computed(() => {
    if (!props.enabled) return t('ad.previewListOff');
    if (rows.value < props.after) return t('ad.previewListShort', { rows: rows.value });
    return t('ad.previewListCaption', { after: props.after });
});

const notes = computed(() => (props.enabled ? [t('ad.previewListConditions'), t('ad.previewNoAdsNote')] : []));
</script>
