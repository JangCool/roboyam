<template>
    <!--
        글 보기 딜 카드(본문 위) — 차례:
          제휴 링크면 첫 줄 고정 문구(05 §2-13 — 조건부 표현 없이 단정형, 첫 부분 표시)
          → 상태 안내(내 글 의심: 「N명이 알렸어요 · {시각}에 자동 종료」 + [아직 진행 중이에요](확인창 「가격·재고를 고쳐 주세요」, `keepCount < keepMax` 일 때만 — 다 쓰면 「운영자 확인 중」)
            / 남의 글 의심: 「품절 알림이 들어온 딜이에요」 / 끝난 딜: 까닭·시각 + 내 글이고 `reopenUntil` 이 있으면 [다시 진행 중으로] + 남은 시간(D2), 기한 지남·이미 씀이면 안내만)
          → 이미지 · 배지 줄(제휴·수수료·HOT·임박·링크 바뀜·가격 수정 N회) → 가격 줄 → 쇼핑몰(→ 최종 호스트)·배송·재고·기간(마감 없으면 기간 종료 시각)·가격 기준(선택지·관부가세·메모)·쿠폰·딜 정보 수정(D3)
          → [구매하기](외부 새 탭, `rel="nofollow sponsored noopener"`) · 끝난 딜이면 [이 상품 새 딜 올리기](`/write?board=&from=`) · 작성자 [딜 종료](확인창)
          → 「품절·가격 변경을 알려 주세요」(작성자에게는 없음, 비로그인은 로그인 안내, 이미 보냈으면 「보낸 사유: …」) → 사유 대화창 → 토스트
          → 「같은 딜 N건」 묶음(줄마다 「이 글보다 N원 저렴/비쌈」) → 아래 고지 「로보얌은 판매자가 아니에요…」.
        끝난 딜(종료·기간 지남)은 카드 몸통을 흐리게 하고 알리기·종료를 감춘다(구매하기는 남긴다 — 링크 확인용, 가정).
        서버 호출은 페이지가 내려 준 `actions` 로 한다. 성공하면 새 딜을 `update:deal` 로 올린다.
    -->
    <section class="my-3 flex flex-col gap-3 rounded-xl border border-surface p-3.5" :aria-label="t('hotdeal.cardLabel')" :aria-busy="busy" data-testid="hotdeal-card" :data-deal-view="deal.view">
        <!-- 제휴 고지 — 카드 첫 줄 고정 -->
        <p v-if="deal.affiliate" class="m-0 flex items-start gap-1.5 rounded-lg bg-sky-50 px-3 py-2 text-sm font-medium text-sky-900 dark:bg-sky-950/60 dark:text-sky-100" data-testid="hotdeal-card-affiliate">
            <i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />{{ t('hotdeal.affiliateNote') }}
        </p>

        <!-- 상태 안내 -->
        <Message v-if="deal.view === 'FLAGGED' && mine" severity="warn" :closable="false" size="small" data-testid="hotdeal-card-flagged-mine">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
                <span>{{ t('hotdeal.flaggedMine', { n: deal.endRequests.total, at: deal.autoEndAt ? time.full(deal.autoEndAt) : '—' }) }}</span>
                <span v-if="requestCounts" class="text-xs opacity-80">{{ requestCounts }}</span>
                <Button v-if="canKeep" type="button" :label="t('hotdeal.keep')" size="small" severity="warn" variant="outlined" :loading="keeping" data-testid="hotdeal-keep" @click="onKeep" />
                <span v-else class="inline-flex items-center gap-1 text-xs font-semibold" data-testid="hotdeal-keep-limit"><i class="pi pi-eye text-[0.6875rem]" aria-hidden="true" />{{ t('hotdeal.keepReview') }}</span>
            </div>
        </Message>
        <Message v-else-if="deal.view === 'FLAGGED'" severity="secondary" :closable="false" size="small" data-testid="hotdeal-card-flagged">
            {{ t('hotdeal.flaggedOther') }}<template v-if="requestCounts"> · {{ requestCounts }}</template>
        </Message>
        <div v-else-if="over" class="flex flex-col gap-2 rounded-lg bg-surface-100 px-3 py-2 dark:bg-surface-800" data-testid="hotdeal-card-over">
            <p class="m-0 flex flex-wrap items-center gap-x-2 text-sm font-medium">
                <i class="pi pi-ban text-xs text-muted-color" aria-hidden="true" />{{ overText }}
                <span v-if="deal.view === 'ENDED' && deal.endedAt" class="text-xs font-normal text-muted-color">{{ t('hotdeal.overAt', { at: time.full(deal.endedAt) }) }}</span>
            </p>
            <!-- 작성자 되돌리기(D2) -->
            <div v-if="reopenLeft" class="flex flex-wrap items-center gap-x-3 gap-y-1.5" data-testid="hotdeal-reopen">
                <span class="text-xs">{{ t('hotdeal.reopenHint', { left: reopenLeft }) }}</span>
                <Button type="button" :label="t('hotdeal.reopen')" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" :loading="reopening" data-testid="hotdeal-reopen-button" @click="onReopen" />
            </div>
            <p v-else-if="mine && deal.view === 'ENDED' && deal.endedBy === 'AUTHOR'" class="m-0 text-xs text-muted-color" data-testid="hotdeal-reopen-gone">{{ t('hotdeal.reopenGone') }}</p>
        </div>

        <!-- 몸통(끝난 딜은 흐리게) -->
        <div class="flex flex-col gap-3 sm:flex-row" :class="over ? 'opacity-55' : ''">
            <HotdealThumb :url="deal.image?.url" class="aspect-square w-full max-w-[11rem] self-center sm:w-36 sm:self-start" />
            <div class="flex min-w-0 flex-1 flex-col gap-2">
                <HotdealBadges :deal="deal" :flagged="false" />
                <HotdealPriceLine :deal="deal" size="lg" />
                <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
                    <dt class="text-muted-color">{{ t('hotdeal.rowStore') }}</dt>
                    <dd class="m-0 min-w-0 break-words">
                        {{ deal.storeName }}
                        <span class="text-xs text-muted-color">({{ deal.productHost }}<template v-if="deal.finalHost && deal.finalHost !== deal.productHost"> → {{ deal.finalHost }}</template>)</span>
                    </dd>
                    <dt class="text-muted-color">{{ t('hotdeal.rowShipping') }}</dt>
                    <dd class="m-0">{{ UtilHotdealMessages.shipping(t, deal.shippingFee) }}</dd>
                    <dt class="text-muted-color">{{ t('hotdeal.rowStock') }}</dt>
                    <dd class="m-0">{{ UtilHotdealMessages.stock(t, deal.stockState) }}</dd>
                    <dt class="text-muted-color">{{ t('hotdeal.rowPeriod') }}</dt>
                    <dd class="m-0">
                        <template v-if="deal.endsAt">{{ t('hotdeal.periodUntil', { at: time.full(deal.endsAt) }) }}</template>
                        <template v-else-if="deal.autoExpireAt">{{ t('hotdeal.periodAuto', { at: time.full(deal.autoExpireAt) }) }}</template>
                        <template v-else>{{ t('hotdeal.periodNone') }}</template>
                    </dd>
                    <template v-if="deal.priceBasisKind || deal.priceBasis">
                        <dt class="text-muted-color">{{ t('hotdeal.rowPriceBasis') }}</dt>
                        <dd class="m-0 flex min-w-0 flex-wrap items-center gap-x-1.5 break-words" data-testid="hotdeal-card-basis">
                            <Tag v-if="deal.priceBasisKind" :value="UtilHotdealMessages.priceBasis(t, deal.priceBasisKind)" severity="secondary" class="text-[0.6875rem]" />
                            <span v-if="deal.priceBasisKind === 'OVERSEAS'" class="text-xs text-muted-color">{{ deal.dutyIncluded ? t('hotdeal.dutyIncludedYes') : t('hotdeal.dutyIncludedNo') }}</span>
                            <span v-if="deal.priceBasis">{{ deal.priceBasis }}</span>
                        </dd>
                    </template>
                    <template v-if="deal.couponNote">
                        <dt class="text-muted-color">{{ t('hotdeal.rowCoupon') }}</dt>
                        <dd class="m-0 min-w-0 break-words">{{ deal.couponNote }}</dd>
                    </template>
                    <template v-if="revisionText">
                        <dt class="text-muted-color">{{ t('hotdeal.rowRevision') }}</dt>
                        <dd class="m-0 text-xs leading-5 text-muted-color" data-testid="hotdeal-card-revision">
                            {{ revisionText }}<template v-if="deal.revision.lastEditedAt"> · {{ t('hotdeal.revisionAt', { at: time.full(deal.revision.lastEditedAt) }) }}</template>
                        </dd>
                    </template>
                </dl>
            </div>
        </div>

        <!-- 구매하기 · 새 딜 올리기 · 작성자 종료 -->
        <div class="flex flex-wrap items-center gap-2">
            <Button
                as="a"
                :href="deal.productUrl"
                target="_blank"
                rel="nofollow sponsored noopener"
                :label="t('hotdeal.buy')"
                icon="pi pi-external-link"
                :severity="over ? 'secondary' : undefined"
                :aria-describedby="buyNoteId"
                data-testid="hotdeal-buy"
            />
            <span :id="buyNoteId" class="text-xs text-muted-color">{{ t('hotdeal.buyNote') }}</span>
            <Button
                v-if="over"
                as="router-link"
                :to="{ path: '/write', query: { board: boardSlug, from: postId } }"
                :label="t('hotdeal.repost')"
                icon="pi pi-plus"
                size="small"
                variant="outlined"
                class="ml-auto"
                data-testid="hotdeal-repost"
            />
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

        <!-- 같은 딜 묶음(다른 글) — 줄마다 이 글과 가격 차 -->
        <div v-if="deal.sameDealCount > 0" class="flex flex-col gap-1 border-t border-surface pt-3" data-testid="hotdeal-same-group">
            <p class="m-0 text-sm font-semibold">{{ t('hotdeal.sameGroup', { n: sameDeals?.length ?? deal.sameDealCount }) }}</p>
            <p v-if="sameDeals === null" class="m-0 text-xs text-muted-color">{{ t('hotdeal.sameGroupLoading') }}</p>
            <ul v-else class="m-0 flex list-none flex-col gap-1 p-0 text-sm">
                <li v-for="item in sameDeals" :key="item.postId" class="flex min-w-0 flex-wrap items-baseline gap-x-2">
                    <NuxtLink :to="`/posts/${item.postId}`" class="min-w-0 max-w-full truncate text-primary hover:underline">{{ item.title }}</NuxtLink>
                    <span class="shrink-0 text-xs font-semibold tabular-nums">{{ UtilHotdealMessages.won(t, item.dealPrice) }}</span>
                    <span class="shrink-0 text-xs" :class="item.dealPrice < deal.dealPrice ? 'font-medium text-red-700 dark:text-red-300' : 'text-muted-color'" data-testid="hotdeal-same-diff">
                        {{ UtilHotdealMessages.priceDiff(t, item.dealPrice, deal.dealPrice) }}
                    </span>
                    <span class="shrink-0 text-xs text-muted-color">{{ time.listLabel(item.createdAt) }}</span>
                </li>
            </ul>
        </div>

        <!-- 판매자 아님 고지 — 카드 아래 고정 -->
        <p class="m-0 border-t border-surface pt-2.5 text-xs text-muted-color" data-testid="hotdeal-card-not-seller">{{ t('hotdeal.notSeller') }}</p>

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
    /** 이 글 — 「이 상품 새 딜 올리기」(`/write?board=&from=`) */
    postId: string;
    /** 이 글의 게시판 slug */
    boardSlug: string;
    /** 내 글 — 종료·되돌리기·「아직 진행 중」, 알리기 단추 없음 */
    mine?: boolean;
    /** 로그인하지 않았다 — 알리기를 누르면 로그인 안내 */
    guest?: boolean;
    /** 같은 딜 목록 — 받는 중이면 null */
    sameDeals?: DealSameRef[] | null;
    actions: DealCardActions;
}

const props = withDefaults(defineProps<Props>(), { mine: false, guest: false, sameDeals: null });
const emit = defineEmits<{ 'update:deal': [deal: PostDeal] }>();

/** 다시 눌러도 같은 결과인 거절 — 알리고 대화창을 닫는다 */
const FINAL_CODES: ReadonlySet<string> = new Set([
    'DEAL_END_REQUEST_EXISTS',
    'DEAL_END_REQUEST_OWN',
    'DEAL_END_REQUEST_NOT_ALLOWED',
    'DEAL_END_REQUEST_AUTHOR_LIMIT',
    'DEAL_ENDED',
    'DEAL_END_REQUEST_DAILY_LIMIT',
    'GRADE_TOO_LOW',
    'ACCOUNT_RESTRICTED',
    'EMAIL_NOT_VERIFIED',
]);

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const buyNoteId = useId();

const over = computed(() => UtilHotdeal.isOver(props.deal.view));
const overText = computed(() => UtilHotdealMessages.overText(t, props.deal));
const requestCounts = computed(() => UtilHotdealMessages.requestCounts(t, props.deal.endRequests));
const revisionText = computed(() => UtilHotdealMessages.revision(t, props.deal.revision));
const canKeep = computed(() => UtilHotdeal.canKeep(props.deal));
/** 되돌리기 남은 시간 글자 — 내 글 · 작성자 종료 · 기한 안일 때만(서버 `reopenUntil`) */
const reopenLeft = computed(() => {
    const until = props.mine && props.deal.view === 'ENDED' && props.deal.reopenUntil ? Date.parse(props.deal.reopenUntil) - Date.now() : 0;
    return until > 0 ? UtilHotdealMessages.remaining(t, until) : '';
});

/** 오류 안내 — 핫딜 코드면 코드 문구, 아니면 서버 문구 */
function errorText(e: unknown): string {
    return UtilHotdealMessages.dealError(t, e) ?? UtilBoardMessages.of(t, e);
}

/* ── 알리기 ── */
const requestOpen = ref(false);
const requesting = ref(false);
const requestError = ref('');
const keeping = ref(false);
const ending = ref(false);
const reopening = ref(false);
const busy = computed(() => requesting.value || keeping.value || ending.value || reopening.value);

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
            toast.error(errorText(e), 8000);
        } else requestError.value = UtilBoardMessages.of(t, e);
    } finally {
        requesting.value = false;
    }
}

/* ── 작성자 ── */
async function onKeep(): Promise<void> {
    if (keeping.value) return;
    const left = props.deal.keepMax === 0 ? null : props.deal.keepMax - props.deal.keepCount - 1;
    const ok = await confirm({
        title: t('hotdeal.keepConfirmTitle'),
        message: left === null ? t('hotdeal.keepConfirmMessage') : t('hotdeal.keepConfirmMessageLeft', { n: Math.max(0, left) }),
        confirmLabel: t('hotdeal.keep'),
    });
    if (!ok) return;
    keeping.value = true;
    try {
        emit('update:deal', await props.actions.keep());
        toast.success(t('hotdeal.kept'));
    } catch (e) {
        // 그새 횟수를 다 썼다 — 「운영자 확인 중」으로 바꾼다
        if (e instanceof ApiError && e.code === 'DEAL_KEEP_LIMIT') emit('update:deal', { ...props.deal, keepCount: Math.max(props.deal.keepCount, props.deal.keepMax) });
        toast.error(errorText(e), 8000);
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

async function onReopen(): Promise<void> {
    if (reopening.value) return;
    const ok = await confirm({ title: t('hotdeal.reopenTitle'), message: t('hotdeal.reopenMessage'), confirmLabel: t('hotdeal.reopen') });
    if (!ok) return;
    reopening.value = true;
    try {
        emit('update:deal', await props.actions.reopen());
        toast.success(t('hotdeal.reopened'));
    } catch (e) {
        // 그새 기한이 지났거나 이미 썼다 — 단추를 거둔다
        if (e instanceof ApiError && (e.code === 'DEAL_REOPEN_EXPIRED' || e.code === 'DEAL_REOPEN_USED')) emit('update:deal', { ...props.deal, reopenUntil: null });
        toast.error(errorText(e), 8000);
    } finally {
        reopening.value = false;
    }
}
</script>
