<template>
    <tr v-if="variant === 'table'" ref="root" class="border-b border-surface hover:bg-surface-50 dark:hover:bg-surface-900" data-testid="ad-feed-row">
        <td class="py-2.5 pr-3">
            <a :href="ad.linkUrl" target="_blank" :rel="UtilAd.REL" class="flex min-w-0 items-center gap-1.5 hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click="onClick" @auxclick="onClick">
                <PointShopTag tone="neutral">{{ t('ad.mark') }}</PointShopTag>
                <span class="min-w-0 truncate">{{ text }}</span>
                <span class="sr-only">{{ t('ad.newTab') }}</span>
            </a>
        </td>
        <td class="truncate py-2.5 text-xs text-muted-color">{{ ad.advertiserName ?? '' }}</td>
        <td class="py-2.5" />
        <td class="py-2.5" />
        <td class="py-2.5 pr-1" />
    </tr>
    <div v-else ref="root" data-testid="ad-feed-row">
        <a
            :href="ad.linkUrl"
            target="_blank"
            :rel="UtilAd.REL"
            class="group flex min-w-0 items-center gap-1.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
            :class="LINE[variant]"
            @click="onClick"
            @auxclick="onClick"
        >
            <PointShopTag tone="neutral">{{ t('ad.mark') }}</PointShopTag>
            <span class="min-w-0 truncate group-hover:underline">{{ text }}</span>
            <span class="sr-only">{{ t('ad.newTab') }}</span>
        </a>
    </div>
</template>

<script setup lang="ts">
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import UtilAd from '~/utils/util-ad';

type FeedVariant = 'table' | 'compact' | 'headline' | 'article' | 'card';

interface Props {
    ad: PublicAd;
    variant: FeedVariant;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const tracking = usePublicAds();

/** 그 목록의 글 줄 링크와 같은 여백·글꼴 */
const LINE: Record<Exclude<FeedVariant, 'table'>, string> = {
    compact: 'px-4 py-2.5 text-sm leading-snug',
    headline: 'py-2.5 text-[15.5px] leading-[23px]',
    article: 'py-3 text-[15.5px] leading-[23px]',
    card: 'px-3 py-2 text-sm leading-snug',
};

const text = computed(() => UtilAd.clipText(props.ad.text));

function onClick(): void {
    tracking.click(props.ad, 'BOARD_LIST');
}

const root = ref<HTMLElement | null>(null);
useAdImpressions(
    root,
    () => [props.ad],
    () => 'BOARD_LIST',
);
</script>
