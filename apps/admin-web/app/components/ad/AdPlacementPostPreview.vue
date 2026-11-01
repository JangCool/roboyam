<template>
    <!-- ① 글 보기 미리보기 — 저장 전 폼 값으로 그린다 -->
    <AdPlacementFrame :caption="caption" :notes="notes" :unsaved="unsaved">
        <AdPlacementPostDiagram :ad="enabled ? { type: adType, position } : null" />
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
