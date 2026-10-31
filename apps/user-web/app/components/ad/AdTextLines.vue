<template>
    <!--
        한 줄 광고(`TEXT_LINE`) N줄 — 받은 후보 중 비중 가중 무작위로 `count` 건을 겹치지 않게 골라(`UtilAd.pickMany`) 한 줄씩 그린다.
          · 줄마다 「광고」 표시 + 문구(40자까지 — `UtilAd.clipText`, 좁은 폭은 말줄임). 링크 규칙은 배너와 같다(새 탭 + `UtilAd.REL`, 누르면 클릭 `sendBeacon`).
          · 노출: 목록이 화면에 절반 이상 보였을 때 보인 줄마다 한 번.
          · 받는 중: 줄 수만큼 빈 줄 · 후보 없음: `keepEmpty` 면 「광고 문의」(`AdInquiry`), 아니면 아무것도 그리지 않는다.
        `slim` 은 얇은 줄(높이 32 · 13px — 글 보기의 두 카드 사이 틈처럼 좁은 자리).
        줄 높이는 고정(한 줄)이라 후보가 와도 아래가 밀리지 않는다(받는 중 빈 줄 높이 = 줄 높이).
    -->
    <div v-if="ads === null" class="flex flex-col" role="status" :aria-label="t('ad.loading')" data-testid="ad-text-lines">
        <div v-for="n in count" :key="n" class="flex items-center" :class="slim ? 'h-8' : 'h-10'"><div class="h-3.5 w-full animate-pulse rounded bg-surface-100 dark:bg-surface-800" :class="n % 2 ? 'max-w-sm' : 'max-w-xs'" /></div>
    </div>
    <ul v-else-if="picked.length" ref="root" class="m-0 flex list-none flex-col divide-y divide-hairline p-0" :aria-label="t('ad.textLinesLabel')" data-testid="ad-text-lines">
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
    <AdInquiry v-else-if="keepEmpty" :message="emptyMessage" :text="emptyText" />
</template>

<script setup lang="ts">
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    /** 한 줄 광고 후보 — null 이면 받는 중 */
    ads: PublicAd[] | null;
    /** 보일 줄 수(1~5) */
    count?: number;
    /** 얇은 줄(높이 32 · 13px) */
    slim?: boolean;
    placement: AdPlacement;
    keepEmpty?: boolean;
    emptyMessage?: boolean;
    emptyText?: string;
}

const props = withDefaults(defineProps<Props>(), { count: 3, slim: false, keepEmpty: false, emptyMessage: true, emptyText: '' });

const { t } = useI18n();
const tracking = usePublicAds();

const picked = ref<PublicAd[]>([]);

// 고르기 — 후보·줄 수가 바뀔 때만(보이던 줄이 모두 아직 있고 수가 맞으면 그대로)
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

// 노출 — 목록이 절반 이상 보였을 때 보인 줄마다 한 번
const root = ref<HTMLElement | null>(null);
useAdImpressions(
    root,
    () => picked.value,
    () => props.placement,
);
</script>
