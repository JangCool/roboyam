<template>
    <!--
        글 보기 딜 카드(본문 위) — 상태 안내(내 글 의심: 「N명이 알렸어요 · {시각}에 자동 종료」 + [아직 진행 중이에요] / 남의 글 의심: 「품절 알림이 들어온 딜이에요」 / 끝난 딜: 까닭·시각)
          → 이미지 · 배지 줄 → 가격 줄 → 쇼핑몰·배송·재고·기간·쿠폰·가격 기준 → [구매하기](외부 새 탭, `rel="nofollow sponsored noopener"`) · 작성자 [딜 종료](확인창)
          → 「품절·가격 변경을 알려 주세요」(작성자에게는 없음, 비로그인은 로그인 안내, 이미 보냈으면 「보낸 사유: …」) → 사유 대화창 → 토스트 → 「같은 딜 N건」 묶음.
        끝난 딜(종료·기간 지남)은 카드 몸통을 흐리게 하고 알리기·종료를 감춘다(구매하기는 남긴다 — 링크 확인용, 가정).
        서버 호출은 페이지가 내려 준 `actions` 로 한다. 성공하면 새 딜을 `update:deal` 로 올린다.
    -->
    <section class="my-3 flex flex-col gap-3 rounded-xl border border-surface p-3.5" :aria-label="t('hotdeal.cardLabel')" :aria-busy="busy" data-testid="hotdeal-card" :data-deal-view="deal.view">
        <!-- 상태 안내 -->
        <Message v-if="deal.view === 'FLAGGED' && mine" severity="warn" :closable="false" size="small" data-testid="hotdeal-card-flagged-mine">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
                <span>{{ t('hotdeal.flaggedMine', { n: deal.endRequests.total, at: deal.autoEndAt ? time.full(deal.autoEndAt) : '—' }) }}</span>
                <span v-if="requestCounts" class="text-xs opacity-80">{{ requestCounts }}</span>
                <Button type="button" :label="t('hotdeal.keep')" size="small" severity="warn" variant="outlined" :loading="keeping" data-testid="hotdeal-keep" @click="onKeep" />
            </div>
        </Message>
        <Message v-else-if="deal.view === 'FLAGGED'" severity="secondary" :closable="false" size="small" data-testid="hotdeal-card-flagged">
            {{ t('hotdeal.flaggedOther') }}<template v-if="requestCounts"> · {{ requestCounts }}</template>
        </Message>
        <p v-else-if="over" class="m-0 flex flex-wrap items-center gap-x-2 rounded-lg bg-surface-100 px-3 py-2 text-sm font-medium dark:bg-surface-800" data-testid="hotdeal-card-over">
            <i class="pi pi-ban text-xs text-muted-color" aria-hidden="true" />{{ overText }}
            <span v-if="deal.view === 'ENDED' && deal.endedAt" class="text-xs font-normal text-muted-color">{{ t('hotdeal.overAt', { at: time.full(deal.endedAt) }) }}</span>
        </p>

        <!-- 몸통(끝난 딜은 흐리게) -->
        <div class="flex flex-col gap-3 sm:flex-row" :class="over ? 'opacity-55' : ''">
            <HotdealThumb :url="deal.image?.url" class="aspect-square w-full max-w-[11rem] self-center sm:w-36 sm:self-start" />
            <div class="flex min-w-0 flex-1 flex-col gap-2">
                <HotdealBadges :deal="deal" :flagged="false" />
                <HotdealPriceLine :deal="deal" size="lg" />
                <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
                    <dt class="text-muted-color">{{ t('hotdeal.rowStore') }}</dt>
                    <dd class="m-0 min-w-0 break-words">{{ deal.storeName }} <span class="text-xs text-muted-color">({{ deal.productHost }})</span></dd>
                    <dt class="text-muted-color">{{ t('hotdeal.rowShipping') }}</dt>
                    <dd class="m-0">{{ UtilHotdealMessages.shipping(t, deal.shippingFee) }}</dd>
                    <dt class="text-muted-color">{{ t('hotdeal.rowStock') }}</dt>
                    <dd class="m-0">{{ UtilHotdealMessages.stock(t, deal.stockState) }}</dd>
                    <dt class="text-muted-color">{{ t('hotdeal.rowPeriod') }}</dt>
                    <dd class="m-0">{{ deal.endsAt ? t('hotdeal.periodUntil', { at: time.full(deal.endsAt) }) : t('hotdeal.periodNone') }}</dd>
                    <template v-if="deal.couponNote">
                        <dt class="text-muted-color">{{ t('hotdeal.rowCoupon') }}</dt>
                        <dd class="m-0 min-w-0 break-words">{{ deal.couponNote }}</dd>
                    </template>
                    <template v-if="deal.priceBasis">
                        <dt class="text-muted-color">{{ t('hotdeal.rowPriceBasis') }}</dt>
                        <dd class="m-0 min-w-0 break-words">{{ deal.priceBasis }}</dd>
                    </template>
                </dl>
            </div>
        </div>

        <p v-if="deal.affiliate" class="m-0 flex items-start gap-1.5 text-xs text-muted-color" data-testid="hotdeal-card-affiliate">
            <i class="pi pi-info-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('hotdeal.affiliateNote') }}
        </p>

        <!-- 구매하기 · 작성자 종료 -->
        <div class="flex flex-wrap items-center gap-2">
            <Button
                as="a"
                :href="deal.productUrl"
                target="_blank"
                rel="nofollow sponsored noopener"
                :label="t('hotdeal.buy')"
                icon="pi pi-external-link"
                :aria-describedby="buyNoteId"
                data-testid="hotdeal-buy"
            />
            <span :id="buyNoteId" class="text-xs text-muted-color">{{ t('hotdeal.buyNote') }}</span>
            <Button v-if="mine && !over" type="button" :label="t('hotdeal.authorEnd')" icon="pi pi-stop-circle" severity="secondary" variant="outlined" size="small" class="ml-auto" :loading="ending" data-testid="hotdeal-author-end" @click="onEnd" />
        </div>

        <!-- 품절·가격 변경 알리기(작성자에게는 없음) -->
        <div v-if="!mine && !over" class="flex flex-col gap-1 border-t border-surface pt-3">
            <p v-if="deal.endRequests.mine" class="m-0 flex items-center gap-1.5 text-sm text-muted-color" data-testid="hotdeal-end-sent">
                <i class="pi pi-check-circle text-xs" aria-hidden="true" />{{ t('hotdeal.endRequestSent', { reason: UtilHotdealMessages.endReason(t, deal.endRequests.mine) }) }}
            </p>
            <div v-else>
                <Button type="button" :label="t('hotdeal.endRequest')" icon="pi pi-flag" severity="secondary" variant="text" size="small" class="-ml-2" data-testid="hotdeal-end-request" @click="openRequest" />
            </div>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('hotdeal.endRequestHelp') }}</p>
        </div>

        <!-- 같은 딜 묶음(다른 글) -->
        <div v-if="deal.sameDealCount > 0" class="flex flex-col gap-1 border-t border-surface pt-3" data-testid="hotdeal-same-group">
            <p class="m-0 text-sm font-semibold">{{ t('hotdeal.sameGroup', { n: sameDeals?.length ?? deal.sameDealCount }) }}</p>
            <p v-if="sameDeals === null" class="m-0 text-xs text-muted-color">{{ t('hotdeal.sameGroupLoading') }}</p>
            <ul v-else class="m-0 flex list-none flex-col gap-0.5 p-0 text-sm">
                <li v-for="item in sameDeals" :key="item.postId" class="flex min-w-0 items-baseline gap-2">
                    <NuxtLink :to="`/posts/${item.postId}`" class="min-w-0 truncate text-primary hover:underline">{{ item.title }}</NuxtLink>
                    <span class="shrink-0 text-xs font-semibold tabular-nums">{{ UtilHotdealMessages.won(t, item.dealPrice) }}</span>
                    <span class="shrink-0 text-xs text-muted-color">{{ time.listLabel(item.createdAt) }}</span>
                </li>
            </ul>
        </div>

        <HotdealEndRequestDialog v-model:visible="requestOpen" :loading="requesting" :error="requestError" @submit="onRequest" />
    </section>
</template>

<script setup lang="ts">
import type { DealEndReason, DealSameRef, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealCardActions } from '~/components/hotdeal/HotdealDealCard.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

interface Props {
    deal: PostDeal;
    /** 내 글 — 종료·「아직 진행 중」, 알리기 단추 없음 */
    mine?: boolean;
    /** 로그인하지 않았다 — 알리기를 누르면 로그인 안내 */
    guest?: boolean;
    /** 같은 딜 목록 — 받는 중이면 null */
    sameDeals?: DealSameRef[] | null;
    actions: DealCardActions;
}

const props = withDefaults(defineProps<Props>(), { mine: false, guest: false, sameDeals: null });
const emit = defineEmits<{ 'update:deal': [deal: PostDeal] }>();

/** 다시 눌러도 같은 결과인 거절 — 서버 문구를 알리고 대화창을 닫는다 */
const FINAL_CODES: ReadonlySet<string> = new Set(['DEAL_END_REQUEST_EXISTS', 'DEAL_END_REQUEST_OWN', 'DEAL_ENDED', 'DEAL_END_REQUEST_DAILY_LIMIT', 'GRADE_TOO_LOW', 'ACCOUNT_RESTRICTED', 'EMAIL_NOT_VERIFIED']);

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const buyNoteId = useId();

const over = computed(() => UtilHotdeal.isOver(props.deal.view));
const overText = computed(() => UtilHotdealMessages.overText(t, props.deal));
const requestCounts = computed(() => UtilHotdealMessages.requestCounts(t, props.deal.endRequests));

/* ── 알리기 ── */
const requestOpen = ref(false);
const requesting = ref(false);
const requestError = ref('');
const keeping = ref(false);
const ending = ref(false);
const busy = computed(() => requesting.value || keeping.value || ending.value);

function openRequest(): void {
    if (props.guest) return prompt('dealEnd');
    requestError.value = '';
    requestOpen.value = true;
}

async function onRequest(reason: DealEndReason): Promise<void> {
    if (requesting.value) return;
    requesting.value = true;
    requestError.value = '';
    try {
        emit('update:deal', await props.actions.endRequest(reason));
        requestOpen.value = false;
        toast.success(t('hotdeal.endRequestDone'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
            requestOpen.value = false;
            prompt('dealEnd');
        } else if (e instanceof ApiError && FINAL_CODES.has(e.code)) {
            requestOpen.value = false;
            toast.error(e.message, 8000);
        } else requestError.value = UtilBoardMessages.of(t, e);
    } finally {
        requesting.value = false;
    }
}

/* ── 작성자 ── */
async function onKeep(): Promise<void> {
    if (keeping.value) return;
    keeping.value = true;
    try {
        emit('update:deal', await props.actions.keep());
        toast.success(t('hotdeal.kept'));
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        keeping.value = false;
    }
}

async function onEnd(): Promise<void> {
    if (ending.value) return;
    const ok = await confirm({ title: t('hotdeal.authorEndTitle'), message: t('hotdeal.authorEndMessage'), confirmLabel: t('hotdeal.authorEnd'), tone: 'danger' });
    if (!ok) return;
    ending.value = true;
    try {
        emit('update:deal', await props.actions.end());
        toast.success(t('hotdeal.authorEnded'));
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        ending.value = false;
    }
}
</script>
