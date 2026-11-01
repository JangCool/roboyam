<template>
    <!--
        신청 건 오른쪽 칸 — 공개 화면에서 어떻게 보일지(배너 / 한 줄 / 핫딜 카드). 모든 미리보기에 「광고」 표시가 붙고,
        링크는 `rel="sponsored nofollow noopener"`(공개 노출과 같다 — noreferrer 는 광고주 유입 집계 때문에 뺀다). 날개 배너는 세로 틀(4:15, 폭 160px). 실제 테마 틀이 아니라 목업 모양이다.
        사업자 핫딜 글(2차): 올라갈 게시판·말머리 · 상품 이미지 · 「광고」 + 상호 · 재고(옛 값 PLENTY·LIMITED 도 같은 문구) — 새 칸이 없으면 예전 모양.
    -->
    <div class="flex flex-col gap-3 p-3">
        <h3 class="m-0 text-sm font-semibold">{{ t('bizApplication.preview') }}</h3>

        <div class="rounded-lg border border-dashed border-(--line) bg-(--p-content-hover-background) p-3">
            <template v-if="content.type === 'AD_BANNER'">
                <div v-if="adType === 'WING_BANNER'" class="relative mx-auto w-[160px] max-w-full overflow-hidden rounded bg-(--p-content-background)" style="aspect-ratio: 4 / 15" data-testid="biz-app-preview-wing">
                    <img :src="content.imageUrl" :alt="content.altText" class="block size-full object-cover" />
                    <span class="absolute top-1 right-1 inline-flex items-center rounded bg-black/60 px-1.5 py-0.5 text-[10px] leading-none text-white">{{ t('bizApplication.adMark') }}</span>
                </div>
                <div v-else class="relative mx-auto w-fit max-w-full">
                    <img :src="content.imageUrl" :alt="content.altText" class="block h-auto max-w-full rounded" />
                    <span class="absolute top-1 right-1 inline-flex items-center rounded bg-black/60 px-1.5 py-0.5 text-[10px] leading-none text-white">{{ t('bizApplication.adMark') }}</span>
                </div>
                <dl class="m-0 mt-3 grid grid-cols-[5rem_1fr] gap-x-2 gap-y-1 text-xs">
                    <dt class="text-muted-color">{{ t('bizApplication.altText') }}</dt>
                    <dd class="m-0 [overflow-wrap:anywhere]">{{ content.altText }}</dd>
                    <dt class="text-muted-color">{{ t('bizApplication.link') }}</dt>
                    <dd class="m-0 [overflow-wrap:anywhere]"><a :href="content.linkUrl" target="_blank" rel="sponsored nofollow noopener" class="underline">{{ content.linkUrl }}</a></dd>
                </dl>
            </template>

            <template v-else-if="content.type === 'AD_TEXT_LINE'">
                <div class="flex items-center gap-2 rounded bg-(--p-content-background) px-3 py-2 text-[13px]">
                    <span class="inline-flex shrink-0 items-center rounded border border-(--line) px-1.5 py-0.5 text-[10px] leading-none text-muted-color">{{ t('bizApplication.adMark') }}</span>
                    <a :href="content.linkUrl" target="_blank" rel="sponsored nofollow noopener" class="min-w-0 truncate hover:underline">{{ content.text }}</a>
                </div>
                <p class="m-0 mt-2 text-xs text-muted-color">{{ t('bizApplication.textLength', { n: content.text.length }) }}</p>
            </template>

            <template v-else>
                <p v-if="content.type === 'HOTDEAL_PLACEMENT'" class="m-0 mb-2 text-xs font-medium">{{ adType === 'HOTDEAL_CATEGORY' ? t('bizApplication.placementCategory', { name: content.categoryName ?? '—' }) : t('bizApplication.placementTop') }}</p>
                <!-- 사업자 핫딜 글(2차) — 올라갈 게시판·말머리(서버가 아직 안 주면 감춘다) -->
                <p v-if="content.type === 'HOTDEAL_POST' && content.board" class="m-0 mb-2 flex flex-wrap items-center gap-1 text-xs">
                    <i class="pi pi-list text-[10px] text-muted-color" aria-hidden="true" />
                    <span class="font-medium">{{ t('bizApplication.postBoardLine', { board: content.board.name }) }}</span>
                    <span v-if="content.prefix" class="text-muted-color">[{{ content.prefix.name }}]</span>
                </p>
                <article class="flex gap-3 rounded bg-(--p-content-background) px-3 py-2.5">
                    <img v-if="content.type === 'HOTDEAL_POST' && content.imageUrl" :src="content.imageUrl" :alt="t('bizApplication.dealImageAlt', { title: content.title })" class="size-16 shrink-0 rounded object-cover" />
                    <div class="flex min-w-0 flex-1 flex-col gap-1.5">
                        <span class="flex flex-wrap items-center gap-1.5">
                            <span class="inline-flex items-center rounded bg-rose-600 px-1.5 py-0.5 text-[10px] leading-none text-white">{{ t('bizApplication.hotdealMark') }}</span>
                            <span class="inline-flex items-center rounded border border-(--line) px-1.5 py-0.5 text-[10px] leading-none text-muted-color">{{ t('bizApplication.adMark') }}</span>
                            <span v-if="advertiser" class="text-[11px] font-medium">{{ advertiser }}</span>
                            <span class="text-[11px] text-muted-color">{{ content.mallName }}</span>
                        </span>
                        <strong class="text-[14px] leading-snug">{{ content.type === 'HOTDEAL_POST' ? content.title : content.postTitle }}</strong>
                        <span class="flex items-baseline gap-2">
                            <span v-if="discount > 0" class="text-[15px] font-bold text-rose-600 tabular-nums">{{ discount }}%</span>
                            <span class="text-[14px] font-semibold tabular-nums">{{ UtilBizApplicationMessages.won(t, content.price, locale) }}</span>
                            <s v-if="content.originalPrice" class="text-[12px] text-muted-color tabular-nums">{{ UtilBizApplicationMessages.won(t, content.originalPrice, locale) }}</s>
                        </span>
                        <p v-if="content.type === 'HOTDEAL_POST'" class="m-0 text-xs leading-relaxed text-muted-color">{{ content.body }}</p>
                    </div>
                </article>
                <template v-if="content.type === 'HOTDEAL_POST'">
                    <p class="m-0 mt-2 text-xs [overflow-wrap:anywhere]">{{ t('bizApplication.link') }}: <a :href="content.dealUrl" target="_blank" rel="sponsored nofollow noopener" class="underline">{{ content.dealUrl }}</a></p>
                    <p v-if="content.stock" class="m-0 mt-1 text-xs">{{ t('bizApplication.stockLabel') }}: {{ t(`bizApplication.stock.${content.stock}`) }}</p>
                </template>
            </template>
        </div>

        <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />{{ t('bizApplication.previewNote') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { BizAdType, BizApplicationContent } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    content: BizApplicationContent;
    adType?: BizAdType | null;
    /** 상호 — 사업자 핫딜 글(2차)은 「광고」 + 상호로 올라간다 */
    businessName?: string | null;
}

const props = withDefaults(defineProps<Props>(), { adType: null, businessName: null });

const { t, locale } = useI18n();

/** 「광고」 옆 상호 — 게시판이 정해진 사업자 핫딜 글(2차)만(서버가 아직 안 주면 예전 모양) */
const advertiser = computed(() => (props.content.type === 'HOTDEAL_POST' && props.content.board ? props.businessName : null));

const discount = computed(() => {
    const c = props.content;
    if (c.type !== 'HOTDEAL_POST' && c.type !== 'HOTDEAL_PLACEMENT') return 0;
    return c.originalPrice && c.originalPrice > 0 ? Math.round((1 - c.price / c.originalPrice) * 100) : 0;
});
</script>
