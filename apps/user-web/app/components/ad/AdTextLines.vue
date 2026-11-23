<template>
    <div v-if="ads === null" class="flex flex-col" role="status" :aria-label="t('ad.loading')" data-ad-slot="TEXT_LINE" data-testid="ad-text-lines">
        <div v-for="n in count" :key="n" class="flex items-center" :class="slim ? 'h-8' : 'h-10'"><div class="h-3.5 w-full animate-pulse rounded bg-surface-100 dark:bg-surface-800" :class="n % 2 ? 'max-w-sm' : 'max-w-xs'" /></div>
    </div>
    <ul v-else-if="picked.length" ref="root" class="m-0 flex list-none flex-col divide-y divide-hairline p-0" :aria-label="t('ad.textLinesLabel')" data-ad-slot="TEXT_LINE" data-testid="ad-text-lines">
        <li v-for="ad in picked" :key="ad.id" class="flex min-w-0 items-center gap-2" :class="slim ? 'h-8 text-[0.8125rem]' : 'h-10 text-sm'">
            <PointShopTag tone="neutral">{{ t('ad.mark') }}</PointShopTag>
            <a
                :href="ad.linkUrl"
                target="_blank"
                :rel="UtilAd.REL"
                class="min-w-0 flex-1 truncate rounded text-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                data-testid="ad-text-line"
                @click="onClick(ad)"
                @auxclick="onClick(ad)"
            >
                {{ UtilAd.clipText(ad.text) }}<span class="sr-only"> · {{ t('ad.newTab') }}</span>
            </a>
        </li>
    </ul>
    <AdInquiry v-else-if="keepEmpty" :message="emptyMessage" :text="emptyText" data-ad-slot="TEXT_LINE" data-ad-slot-empty="" />
    <div v-else-if="adSlotMode" :class="slim ? 'h-8' : 'h-10'" data-ad-slot="TEXT_LINE" data-ad-slot-empty="" aria-hidden="true" />
</template>

<script setup lang="ts">
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    /** null = 받는 중 */
    ads: PublicAd[] | null;
    /** 1~5 */
    count?: number;
    slim?: boolean;
    placement: AdPlacement;
    keepEmpty?: boolean;
    emptyMessage?: boolean;
    emptyText?: string;
}

const props = withDefaults(defineProps<Props>(), { count: 3, slim: false, keepEmpty: false, emptyMessage: true, emptyText: '' });

const { t } = useI18n();
const tracking = usePublicAds();
/** 관리자 「광고 자리 관리」 미리보기 창인가 — 빈 자리도 틀을 남긴다 */
const { adSlotMode } = useAdminPreview();

const picked = ref<PublicAd[]>([]);

watch(
    [() => props.ads, () => props.count],
    ([list, count]) => {
        if (!list) {
            picked.value = [];
            return;
        }
        const want = Math.min(count, list.length);
        if (picked.value.length === want && picked.value.every((ad) => UtilAd.stillIn(ad, list))) return;
        picked.value = UtilAd.pickMany(list, count);
    },
    { immediate: true },
);

function onClick(ad: PublicAd): void {
    tracking.click(ad, props.placement);
}

const root = ref<HTMLElement | null>(null);
useAdImpressions(
    root,
    () => picked.value,
    () => props.placement,
);
</script>
