<template>
    <!-- ① 글 보기 미리보기 — 저장 전 폼 값으로 그린다. 회색 무대(공개 화면 바탕) 위에 흰 카드를 그린다(날개 무대와 같은 바탕) -->
    <AdPlacementFrame :caption="caption" :notes="notes" :unsaved="unsaved">
        <div class="min-w-0 rounded-md bg-surface-100 p-1.5 dark:bg-surface-950">
            <AdPlacementPostDiagram :ad="enabled ? { type: adType, position } : null" />
        </div>
    </AdPlacementFrame>
</template>

<script setup lang="ts">
import type { AdPostInlinePosition, AdPostInlineType } from '~/composables/ad/api/useAdApi.types';

interface Props {
    enabled: boolean;
    adType: AdPostInlineType;
    position: AdPostInlinePosition;
    unsaved?: boolean;
}

const props = withDefaults(defineProps<Props>(), { unsaved: false });
const { t } = useI18n();

const caption = computed(() => {
    if (!props.enabled) return t('ad.previewPostOff');
    return t(`ad.previewPostCaption.${props.position}`, { type: t(`ad.postInlineTypeOption.${props.adType}`) });
});

const notes = computed(() => (props.enabled ? [t(`ad.previewPostShape.${props.adType}`), t('ad.previewNoAdsNote')] : []));
</script>
