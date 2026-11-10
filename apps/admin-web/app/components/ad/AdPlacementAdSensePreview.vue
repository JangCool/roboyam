<template>
    <!--
        ⑥ 구글 애드센스 미리보기 — 저장 전 폼 값으로 그린다. 다른 탭처럼 공개 화면 도식 대신 세 덩이:
          ① 사용 단계 세 칸(끔 → 사이트 확인만 → 광고 표시, 지금 단계만 주 색상)
          ② 자리별 채우기 순서 — 「장부 광고 → 애드센스(단위 …)」 또는 「장부 광고만」. 광고 표시 단계가 아니면 애드센스 칸은 흐리게
          ③ 서버가 낼 것 — index.html 머리의 스크립트 한 줄 · `/ads.txt` 한 줄(게시자 ID 가 맞을 때만, 사이트 확인만·광고 표시 단계)
        조건 안내: 빠지는 곳(19금·광고 숨김·소모임 글·비밀글·관리자 미리보기) · 우리 노출·클릭 집계에 잡히지 않음 · 장부 광고 우선.
    -->
    <AdPlacementFrame :caption="caption" :notes="notes" :unsaved="unsaved">
        <div class="flex min-w-0 flex-col gap-3">
            <ol class="m-0 grid list-none grid-cols-3 gap-1 p-0" :aria-label="t('ad.adsenseMode')" data-testid="ad-placement-adsense-steps">
                <li
                    v-for="mode in UtilAdSense.MODES"
                    :key="mode"
                    class="flex min-h-10 items-center justify-center rounded-md border px-1.5 py-1 text-center text-[0.6875rem] leading-tight"
                    :class="mode === body.mode ? 'border-primary bg-primary-50 font-medium text-primary dark:bg-primary-950/40' : 'border-(--line) text-muted-color'"
                    :aria-current="mode === body.mode ? 'step' : undefined"
                >
                    {{ t(`ad.adsenseModeOption.${mode}`) }}
                </li>
            </ol>

            <!-- 자리별 채우기 순서 -->
            <ul class="m-0 flex list-none flex-col gap-1 rounded-md bg-surface-100 p-1.5 dark:bg-surface-950" data-testid="ad-placement-adsense-slots">
                <li v-for="row in rows" :key="row.key" class="flex min-w-0 items-center gap-2 rounded bg-(--p-content-background) px-2 py-1.5 text-[0.6875rem]">
                    <span class="min-w-0 flex-1 truncate">{{ t(`ad.adsenseUnit.${row.key}`) }}</span>
                    <span class="shrink-0 text-muted-color">{{ t('ad.previewAdsenseLedger') }}</span>
                    <template v-if="row.slot">
                        <i class="pi pi-arrow-right shrink-0 text-[0.5625rem] text-muted-color" aria-hidden="true" />
                        <span class="shrink-0 rounded px-1 py-0.5 font-mono tabular-nums" :class="showing ? 'bg-primary-50 text-primary dark:bg-primary-950/40' : 'bg-surface-100 text-muted-color dark:bg-surface-800'">
                            {{ t('ad.previewAdsenseUnit', { slot: row.slot }) }}
                        </span>
                    </template>
                    <span v-else class="shrink-0 text-muted-color">{{ t('ad.previewAdsenseLedgerOnly') }}</span>
                </li>
            </ul>

            <!-- 서버가 낼 것 -->
            <div v-if="body.mode !== 'OFF'" class="flex min-w-0 flex-col gap-1.5" data-testid="ad-placement-adsense-server">
                <template v-if="scriptTag && adsTxt">
                    <span class="text-[0.6875rem] text-muted-color">{{ t('ad.previewAdsenseScript') }}</span>
                    <code class="block overflow-x-auto rounded-md bg-surface-100 px-2 py-1.5 font-mono text-[0.625rem] leading-relaxed break-all whitespace-pre-wrap dark:bg-surface-950">{{ scriptTag }}</code>
                    <span class="text-[0.6875rem] text-muted-color">{{ t('ad.previewAdsenseAdsTxt') }}</span>
                    <code class="block overflow-x-auto rounded-md bg-surface-100 px-2 py-1.5 font-mono text-[0.625rem] leading-relaxed break-all whitespace-pre-wrap dark:bg-surface-950">{{ adsTxt }}</code>
                </template>
                <p v-else class="m-0 text-[0.6875rem] leading-relaxed text-red-600 dark:text-red-400">{{ t('ad.previewAdsenseNoClient') }}</p>
            </div>
        </div>
    </AdPlacementFrame>
</template>

<script setup lang="ts">
import type { AdSenseDisplaySlot, AdSenseSetting } from '~/composables/ad/api/useAdApi.types';
import UtilAdSense from '~/utils/util-adsense';

interface Props {
    /** 저장 전 폼 값 */
    adsense: AdSenseSetting;
    unsaved?: boolean;
}

const props = withDefaults(defineProps<Props>(), { unsaved: false });
const { t } = useI18n();

/** 다듬은 값(빈 글자 → null, `pub-…` → `ca-pub-…`) */
const body = computed(() => UtilAdSense.body(props.adsense));
const showing = computed(() => body.value.mode === 'SHOW');

const rows = computed<{ key: AdSenseDisplaySlot | 'BOARD_LIST'; slot: string | null }[]>(() => [
    ...UtilAdSense.DISPLAY_SLOTS.map((key) => ({ key, slot: body.value.units[key] })),
    { key: 'BOARD_LIST', slot: body.value.units.BOARD_LIST.slot && body.value.units.BOARD_LIST.layoutKey ? body.value.units.BOARD_LIST.slot : null },
]);

const scriptTag = computed(() => UtilAdSense.scriptTag(body.value.client));
const adsTxt = computed(() => UtilAdSense.adsTxtLine(body.value.client));

const caption = computed(() => {
    if (body.value.mode === 'OFF') return t('ad.previewAdsenseCaption.OFF');
    if (body.value.mode === 'VERIFY') return t('ad.previewAdsenseCaption.VERIFY');
    const used = UtilAdSense.usedSlots(props.adsense);
    return used > 0 ? t('ad.previewAdsenseCaption.SHOW', { n: used }) : t('ad.previewAdsenseCaptionNoUnits');
});

const notes = computed(() => {
    const out = [t('ad.previewAdsenseLedgerFirst'), t('ad.previewAdsenseExcluded'), t('ad.previewAdsenseNoStats')];
    if (body.value.adtest) out.unshift(t('ad.previewAdsenseAdtest'));
    return out;
});
</script>
