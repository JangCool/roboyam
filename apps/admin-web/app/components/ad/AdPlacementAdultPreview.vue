<template>
    <!--
        ④ 19금 미리보기 — 19금 게시판 목록 · 19금 글 보기를 작게 그린다. 켬이면 위 세 묶음의 저장 전 폼 값대로 광고 자리가 생기고(날개는 「보일 화면」의 게시판 목록·글 보기를 따른다),
        끔이면 날개까지 모두 빠진다(user-web `UtilAd.boardAdsHidden`·`wingContextHidden`, 글 보기 `inlineBoard`).
    -->
    <AdPlacementFrame :caption="caption" :notes="notes" :unsaved="unsaved">
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
            <div class="flex min-w-0 flex-col gap-1">
                <span class="text-[0.6875rem] text-muted-color">{{ t('ad.previewAdultList') }}</span>
                <AdPlacementWingStage :left="wingOn('BOARD') && form.wings.left" :right="wingOn('BOARD') && form.wings.right">
                    <AdPlacementListDiagram :rows="LIST_ROWS" :ad-after="listAfter" :keep-all="LIST_ROWS" adult />
                </AdPlacementWingStage>
            </div>
            <div class="flex min-w-0 flex-col gap-1">
                <span class="text-[0.6875rem] text-muted-color">{{ t('ad.previewAdultPost') }}</span>
                <AdPlacementWingStage :left="wingOn('POST') && form.wings.left" :right="wingOn('POST') && form.wings.right">
                    <AdPlacementPostDiagram :ad="postAd" compact adult />
                </AdPlacementWingStage>
            </div>
        </div>
    </AdPlacementFrame>
</template>

<script setup lang="ts">
import type { AdPlacementsRequest, AdWingScreen } from '~/composables/ad/api/useAdApi.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    form: AdPlacementsRequest;
    unsaved?: boolean;
}

const props = withDefaults(defineProps<Props>(), { unsaved: false });
const { t } = useI18n();

const LIST_ROWS = 6;

const on = computed(() => props.form.adult.enabled === true);

function wingOn(screen: AdWingScreen): boolean {
    return on.value && props.form.wings.screens.includes(screen);
}

const listAfter = computed(() => (on.value && props.form.boardList.enabled ? UtilAd.feedSlot(LIST_ROWS, props.form.boardList.after) : null));
const postAd = computed(() => (on.value && props.form.postInline.enabled ? { type: props.form.postInline.adType, position: props.form.postInline.position } : null));

/** 켬인데 그릴 광고가 하나도 없다(다른 묶음이 모두 꺼짐) */
const nothing = computed(() => listAfter.value === null && postAd.value === null && !((wingOn('BOARD') || wingOn('POST')) && (props.form.wings.left || props.form.wings.right)));

const caption = computed(() => {
    if (!on.value) return t('ad.previewAdultOff');
    return nothing.value ? t('ad.previewAdultNothing') : t('ad.previewAdultOn');
});

const notes = computed(() => [t('ad.previewAdultAlways')]);
</script>
