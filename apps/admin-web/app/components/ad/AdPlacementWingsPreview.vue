<template>
    <!--
        ② 날개 배너 미리보기 — 저장 전 폼 값으로 그린다. 위 알약으로 공개 화면 넷(홈·가젯 페이지·게시판 목록·글 보기) 중 하나를 골라 본다(저장하지 않는 값).
        알약의 ✓ 는 「보일 화면」에 들어 있는 화면이다. 고른 화면이 빠져 있으면 날개 없이 그린다.
    -->
    <AdPlacementFrame :caption="caption" :notes="notes" :unsaved="unsaved">
        <template #controls>
            <div class="flex flex-wrap items-center gap-1.5" role="group" :aria-labelledby="pickId">
                <span :id="pickId" class="text-[0.6875rem] text-muted-color">{{ t('ad.previewWingsPick') }}</span>
                <AdPlacementChip v-for="s in UtilAd.WING_SCREENS" :key="s" :pressed="screen === s" :data-testid="`ad-placement-wing-screen-${s}`" @click="screen = s">
                    <i v-if="screens.includes(s)" class="pi pi-check text-[0.5625rem]" aria-hidden="true" />
                    {{ t(`ad.wingScreen.${s}`) }}
                    <span v-if="screens.includes(s)" class="sr-only">{{ t('ad.previewWingsIncluded') }}</span>
                </AdPlacementChip>
            </div>
        </template>
        <AdPlacementWingStage :left="shown && left" :right="shown && right">
            <!-- 홈 · 가젯 페이지 — 가젯 상자 -->
            <div v-if="screen === 'HOME' || screen === 'PAGE'" class="flex flex-col gap-1.5">
                <span v-if="screen === 'PAGE'" class="block h-2 w-2/5 rounded-full bg-surface-300 dark:bg-surface-600" />
                <div class="grid grid-cols-2 gap-1.5">
                    <div v-for="n in 5" :key="n" class="rounded-md border border-(--line) bg-(--p-content-background) p-1.5" :class="n === 1 ? 'col-span-2 h-10' : 'h-12'">
                        <span class="block h-1.5 w-1/2 rounded-full bg-surface-200 dark:bg-surface-700" />
                    </div>
                </div>
            </div>
            <AdPlacementListDiagram v-else-if="screen === 'BOARD'" :rows="7" />
            <AdPlacementPostDiagram v-else />
        </AdPlacementWingStage>
    </AdPlacementFrame>
</template>

<script setup lang="ts">
import type { AdWingScreen } from '~/composables/ad/api/useAdApi.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    left: boolean;
    right: boolean;
    screens: readonly AdWingScreen[];
    unsaved?: boolean;
}

const props = withDefaults(defineProps<Props>(), { unsaved: false });
const { t } = useI18n();
const pickId = useId();

/** 처음 볼 화면 — 「보일 화면」의 첫 화면(없으면 홈) */
const screen = ref<AdWingScreen>(UtilAd.WING_SCREENS.find((s) => props.screens.includes(s)) ?? 'HOME');

const shown = computed(() => props.screens.includes(screen.value));

const caption = computed(() => {
    const name = t(`ad.wingScreen.${screen.value}`);
    if (!props.left && !props.right) return t('ad.previewWingsOff');
    if (!shown.value) return t('ad.previewWingsNotOnScreen', { screen: name });
    const sides = props.left && props.right ? 'BOTH' : props.left ? 'LEFT' : 'RIGHT';
    return t(`ad.previewWingsCaption.${sides}`, { screen: name });
});

const notes = computed(() => {
    if (!props.left && !props.right) return [];
    return [
        t('ad.previewWingsWidth', { base: UtilAd.wingMinViewport(UtilAd.PUBLIC_CONTENT.base), wide: UtilAd.wingMinViewport(UtilAd.PUBLIC_CONTENT.wide), margin: UtilAd.WING.width + UtilAd.WING.gap + UtilAd.WING.edge }),
        t('ad.previewWingsExcluded'),
        t('ad.previewNoAdsNote'),
    ];
});
</script>
