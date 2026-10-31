<template>
    <!--
        핫딜 노출 광고(「핫딜 홈 상단」 `HOTDEAL_TOP` · 「핫딜 말머리 상단」 `HOTDEAL_CATEGORY`) — 핫딜 목록 맨 위에 핫딜 글을 「광고」 표시와 함께 카드로(`count` 건, 비중 가중 무작위 · 겹치지 않게).
        카드: 사진(없으면 꼬리표 아이콘) · 「핫딜」「광고」 딱지 · 쇼핑몰(·말머리) · 글 제목(그 핫딜 글로 — 사이트 안 링크) · 할인율 · 딜가 · 정상가. 카드 면은 `panel-card`(흰 카드 안에서는 머리카락 선 — 테마가 정한다).
        받는 중·후보 없음은 아무것도 그리지 않는다(목록 위가 비면 목록이 그대로 위로). 노출은 절반 이상 보였을 때 보인 카드마다 한 번, 누르면 클릭을 남긴다.
        **붙인 곳**: 게시판 목록(`pages/boards/[slug].vue`)의 목록 위 — 핫딜 게시판(`dealEnabled`)의 첫 쪽(검색 아님)만. 핫딜 전용 공개 목록 화면은 아직 없다.
    -->
    <section v-if="picked.length" ref="root" class="mt-3 flex flex-col gap-2" :aria-label="label" :data-placement="placement" data-testid="ad-hotdeal">
        <article v-for="ad in picked" :key="ad.id" class="panel-card flex min-w-0 items-center gap-3 p-3">
            <span class="grid size-14 shrink-0 place-items-center overflow-hidden rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
                <img v-if="ad.deal?.thumbnailUrl" :src="ad.deal.thumbnailUrl" alt="" class="size-full object-cover" loading="lazy" decoding="async" />
                <i v-else class="pi pi-tag text-lg" />
            </span>
            <div class="flex min-w-0 flex-1 flex-col gap-1">
                <p class="m-0 flex min-w-0 flex-wrap items-center gap-1.5 text-xs text-muted-color">
                    <PointShopTag tone="danger">{{ t('ad.hotdealMark') }}</PointShopTag>
                    <PointShopTag tone="neutral">{{ t('ad.mark') }}</PointShopTag>
                    <span class="min-w-0 truncate">{{ ad.deal?.shopName }}<template v-if="ad.deal?.prefixName || prefixName"> · {{ ad.deal?.prefixName || prefixName }}</template></span>
                </p>
                <NuxtLink
                    :to="ad.linkUrl"
                    class="truncate rounded text-[0.9375rem] font-semibold text-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                    data-testid="ad-hotdeal-link"
                    @click="onClick(ad)"
                >
                    {{ ad.deal?.title }}
                </NuxtLink>
                <p v-if="ad.deal" class="m-0 flex items-baseline gap-2 text-sm">
                    <span v-if="UtilAd.discount(ad.deal)" class="font-bold tabular-nums text-red-600 dark:text-red-400">{{ UtilAd.discount(ad.deal) }}%</span>
                    <span class="font-semibold tabular-nums">{{ t('ad.price', { amount: ad.deal.dealPrice.toLocaleString('ko-KR') }) }}</span>
                    <s v-if="ad.deal.listPrice" class="text-xs tabular-nums text-muted-color">{{ t('ad.price', { amount: ad.deal.listPrice.toLocaleString('ko-KR') }) }}</s>
                </p>
            </div>
        </article>
    </section>
</template>

<script setup lang="ts">
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    /** 핫딜 노출 후보 — null 이면 받는 중(그리지 않는다) */
    ads: PublicAd[] | null;
    placement: Extract<AdPlacement, 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY'>;
    /** 고른 말머리 이름(말머리 상단) */
    prefixName?: string;
    /** 보일 카드 수 */
    count?: number;
}

const props = withDefaults(defineProps<Props>(), { prefixName: '', count: 2 });

const { t } = useI18n();
const tracking = usePublicAds();

const label = computed(() => (props.placement === 'HOTDEAL_CATEGORY' && props.prefixName ? t('ad.hotdealCategoryLabel', { name: props.prefixName }) : t('ad.hotdealTopLabel')));

const picked = ref<PublicAd[]>([]);
watch(
    () => props.ads,
    (list) => {
        if (!list) {
            picked.value = [];
            return;
        }
        const want = Math.min(props.count, list.length);
        if (picked.value.length === want && picked.value.every((ad) => UtilAd.stillIn(ad, list))) return;
        picked.value = UtilAd.pickMany(list, props.count);
    },
    { immediate: true },
);

function onClick(ad: PublicAd): void {
    tracking.click(ad, props.placement);
}

// 노출 — 절반 이상 보였을 때 보인 카드마다 한 번
const root = ref<HTMLElement | null>(null);
useAdImpressions(
    root,
    () => picked.value,
    () => props.placement,
);
</script>
