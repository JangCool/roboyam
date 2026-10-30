<template>
    <!--
        광고 모습 미리보기 — 종류별 틀(넓은·띠·네모·작은 배너는 비율 상자, 한 줄은 글자 줄) + 늘 「광고」 표시(H4).
        링크는 새 탭 + `rel="sponsored nofollow noopener noreferrer"`(공개 `AdSlot` 과 같은 약속). 광고 신청 소재 단계와 신청 건 상세가 같이 쓴다.
    -->
    <figure class="m-0 flex flex-col gap-1.5" data-testid="business-ad-preview">
        <figcaption class="text-xs font-medium text-muted-color">{{ t('business.ad.previewTitle') }} · {{ t(`business.adType.${adType}`) }}</figcaption>
        <div class="rounded-lg border border-dashed border-hairline-strong bg-page p-3">
            <!-- 한 줄 광고 -->
            <p v-if="adType === 'TEXT_LINE'" class="m-0 flex min-w-0 items-center gap-2 rounded-md bg-card px-3 py-2 text-sm">
                <PointShopTag tone="neutral">{{ t('business.ad.adMark') }}</PointShopTag>
                <component :is="linkUrl ? 'a' : 'span'" v-bind="linkAttrs" class="min-w-0 truncate" :class="text ? 'text-color' : 'text-muted-color'">{{ text || t('business.ad.previewTextEmpty') }}</component>
            </p>
            <!-- 배너 -->
            <div v-else class="mx-auto" :style="{ maxWidth: FRAME[adType].maxWidth }">
                <component :is="linkUrl ? 'a' : 'div'" v-bind="linkAttrs" class="relative block overflow-hidden rounded-md bg-surface-100 dark:bg-surface-800" :style="{ aspectRatio: FRAME[adType].ratio }">
                    <img v-if="imageUrl" :src="imageUrl" :alt="altText" class="size-full object-cover" />
                    <span v-else class="grid size-full place-items-center px-2 text-center text-xs text-muted-color">
                        <span><i class="pi pi-image mb-1 block text-lg" aria-hidden="true" />{{ t('business.ad.previewImageEmpty') }}</span>
                    </span>
                    <span class="absolute right-1.5 top-1.5 inline-flex items-center justify-center rounded bg-black/60 px-1.5 py-1 text-[0.625rem] font-semibold leading-none text-white">
                        <span class="relative top-[-0.09em]">{{ t('business.ad.adMark') }}</span>
                    </span>
                </component>
            </div>
        </div>
    </figure>
</template>

<script setup lang="ts">
import type { AdType } from '~/composables/business/api/useBusinessApi.types';

interface Props {
    adType: AdType;
    imageUrl?: string | null;
    linkUrl?: string;
    altText?: string;
    text?: string;
}

const props = withDefaults(defineProps<Props>(), { imageUrl: null, linkUrl: '', altText: '', text: '' });

const { t } = useI18n();

/** 종류별 틀 — 비율은 규격(1200×300 등)과 같게 */
const FRAME: Record<AdType, { ratio: string; maxWidth: string }> = {
    WIDE: { ratio: '4 / 1', maxWidth: '100%' },
    STRIP: { ratio: '12 / 1', maxWidth: '100%' },
    SQUARE: { ratio: '6 / 5', maxWidth: '300px' },
    SMALL: { ratio: '3 / 1', maxWidth: '300px' },
    TEXT_LINE: { ratio: 'auto', maxWidth: '100%' },
};

const linkAttrs = computed(() => (props.linkUrl ? { href: props.linkUrl, target: '_blank', rel: 'sponsored nofollow noopener noreferrer' } : {}));
</script>
