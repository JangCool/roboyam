<template>
    <div v-if="ads === null || current || keepEmpty" ref="root" class="w-full min-w-0" :class="[widthClass, align === 'center' ? 'mx-auto' : '']" data-testid="ad-slot">
        <div v-if="ads === null" class="w-full animate-pulse rounded-(--gadget-card-radius) bg-surface-100 dark:bg-surface-800" :style="{ aspectRatio: frame.ratio }" role="status" :aria-label="t('ad.loading')" />
        <a
            v-else-if="current"
            :href="current.linkUrl"
            target="_blank"
            :rel="UtilAd.REL"
            class="relative block w-full overflow-hidden rounded-(--gadget-card-radius) bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:bg-surface-800"
            :style="{ aspectRatio: frame.ratio }"
            data-testid="ad-banner"
            @click="onClick"
            @auxclick="onClick"
        >
            <img v-if="!imageFailed && current.imageUrl" :src="current.imageUrl" :alt="current.altText" class="block size-full object-cover" loading="lazy" decoding="async" @error="imageFailed = true" />
            <span v-else class="grid size-full place-items-center px-3 text-center text-sm text-muted-color">{{ current.altText }}</span>
            <span class="absolute right-1.5 top-1.5 inline-flex items-center justify-center rounded bg-black/60 px-1.5 py-1 text-[0.625rem] font-semibold leading-none text-white" data-testid="ad-mark">
                <span class="relative top-[-0.09em]">{{ t('ad.mark') }}</span>
            </span>
            <span class="sr-only">{{ t('ad.newTab') }}</span>
        </a>
        <AdInquiry v-else :message="emptyMessage" :text="emptyText" :ratio="frame.ratio" />
    </div>
</template>

<script setup lang="ts">
import type { ImageAdType, PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    type: ImageAdType;
    /** null = 받는 중 */
    ads: PublicAd[] | null;
    placement: AdPlacement;
    keepEmpty?: boolean;
    /** 300px 배너의 자리 — 가젯은 다른 가젯 가장자리와 맞게 start */
    align?: 'start' | 'center';
    emptyMessage?: boolean;
    emptyText?: string;
}

const props = withDefaults(defineProps<Props>(), { keepEmpty: false, align: 'start', emptyMessage: true, emptyText: '' });

const { t } = useI18n();
const tracking = usePublicAds();

const frame = computed(() => UtilAd.frameOf(props.type));
/** 네모·작은 배너는 칸(`@container`)이 360px 이하면 칸을 채운다 — 컨테이너가 없는 글 보기는 300px */
const widthClass = computed(() => {
    if (props.type === 'WING_BANNER') return 'max-w-[160px]';
    return frame.value.maxWidth === '100%' ? '' : 'max-w-[300px] @max-[22.5rem]:max-w-none';
});
const current = ref<PublicAd | null>(null);
const imageFailed = ref(false);

watch(
    () => props.ads,
    (list) => {
        if (!list) {
            current.value = null;
            return;
        }
        if (UtilAd.stillIn(current.value, list)) return;
        current.value = UtilAd.pick(list);
        imageFailed.value = false;
    },
    { immediate: true },
);

function onClick(): void {
    if (current.value) tracking.click(current.value, props.placement);
}

const root = ref<HTMLElement | null>(null);
useAdImpressions(
    root,
    () => (current.value ? [current.value] : []),
    () => props.placement,
);
</script>
