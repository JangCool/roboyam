<template>
    <!--
        글 보기 딜 카드(본문 위) — 차례:
          사업자 글(2차 — `source BUSINESS`)이면 첫 줄 광고 고지(「광고 — {상호}에서 비용을 내고 올린…」), 끝나도 [이 상품 새 딜 올리기] 없음, 작성자(사업자) 종료 확인은 「환불 없음 · 노출도 멈춤」
          → 제휴 링크면 첫 줄 고정 문구(05 §2-13 — 조건부 표현 없이 단정형, 첫 부분 표시. D5 — 서버가 지금 방식으로 계산한 `affiliateKind`: `MEMBER` 작성자 개인 제휴 · `SITE` 로보얌 제휴 · null 없음)
          → 상태 안내(내 글 의심: 「N명이 알렸어요 · {시각}에 자동 종료」 + [아직 진행 중이에요](확인창 「가격·재고를 고쳐 주세요」, `keepCount < keepMax` 일 때만 — 다 쓰면 「운영자 확인 중」)
            / 남의 글 의심: 「품절 알림이 들어온 딜이에요」 / 끝난 딜: 까닭·시각 + 내 글이고 `reopenUntil` 이 있으면 [다시 진행 중으로] + 남은 시간(D2), 기한 지남·이미 씀이면 안내만)
          → 이미지 · 배지 줄(제휴·수수료·HOT·임박·링크 바뀜·가격 수정 N회) → 가격 줄 → 쇼핑몰(→ 최종 호스트)·배송·재고·기간(마감 없으면 기간 종료 시각)·가격 기준(선택지·관부가세·메모)·쿠폰·딜 정보 수정(D3)
          → [구매하기](서버 경유 `buyUrl` = `/api/deals/{postId}/go` — 서버가 제휴 방식대로 바꿔 302, 외부 새 탭, `rel="nofollow sponsored noopener"`) · 끝난 딜이면 [이 상품 새 딜 올리기](`/write?board=&from=`) · 작성자 [딜 종료](확인창)
          → 「품절·가격 변경을 알려 주세요」(작성자에게는 없음, 비로그인은 로그인 안내, 이미 보냈으면 「보낸 사유: …」) → 사유 대화창 → 토스트
          → 「게시판 관리」(보는 사람이 그 게시판 매니저·스텝일 때만 — `moderation`, 사용자 결정 2026-11-01): 자리 표시 + [핫딜 종료](사유 필수 대화창) / 종료된 딜이면 [다시 진행 중으로](사유 선택 대화창).
            작성자 조치와 따로 그린다 — 작성자 본인이 매니저여도 작성자 종료·24시간 되돌리기·「아직 진행 중」은 그대로 두고 이 영역을 더 보인다(매니저 조치로 작성자 조치를 덮지 않는다).
            단추는 서버 `canEnd`(진행·의심·기간 지남)·`canReopen`(종료)대로 — 기간 지난 딜은 카드가 흐려도 [핫딜 종료]가 남는다.
          → 「같은 딜 N건」 묶음(줄마다 「이 글보다 N원 저렴/비쌈」) → 아래 고지 「로보얌은 판매자가 아니에요…」.
        사업자 글 작성자(`deal.business.applicationId` 가 있을 때 — 목업 2026-11-02): [일시 정지](신청 건을 받아 `BusinessPauseDialog` → 성공하면 토스트 + 신청 건 화면으로) · 「중단·환불은 신청 건에서 ›」.
        끝난 딜(종료·기간 지남)은 카드 몸통을 흐리게 하고 알리기·종료를 감춘다(구매하기는 남긴다 — 링크 확인용, 가정).
        서버 호출은 페이지가 내려 준 `actions` 로 한다. 성공하면 새 딜을 `update:deal` 로 올린다.
        게시판 관리 실패: 맡은 자리가 빠졌으면(`403 DEAL_MODERATE_FORBIDDEN`) `update:moderation` null 로 영역을 거두고, 그새 상태가 바뀌었으면(`409 DEAL_ENDED`·`DEAL_NOT_ENDED`·`404 DEAL_NOT_FOUND`) `stale` 로 페이지가 글을 다시 읽게 한다.
    -->
    <section class="my-3 flex flex-col gap-3 rounded-xl border border-surface p-3.5" :aria-label="t('hotdeal.cardLabel')" :aria-busy="busy" data-testid="hotdeal-card" :data-deal-view="deal.view" :data-deal-source="deal.source">
        <!-- 광고 고지(사업자 글 — 2차) — 카드 첫 줄 고정 -->
        <p v-if="business" class="m-0 flex items-start gap-1.5 rounded-lg bg-surface-100 px-3 py-2 text-sm font-medium dark:bg-surface-800" data-testid="hotdeal-card-business">
            <i class="pi pi-megaphone mt-0.5 text-xs" aria-hidden="true" />{{ t('hotdeal.bizNote', { name: deal.business?.name ?? '' }) }}
        </p>
        <!-- 제휴 고지 — 카드 첫 줄 고정 -->
        <p v-if="deal.affiliateKind" class="m-0 flex items-start gap-1.5 rounded-lg bg-sky-50 px-3 py-2 text-sm font-medium text-sky-900 dark:bg-sky-950/60 dark:text-sky-100" data-testid="hotdeal-card-affiliate" :data-affiliate-kind="deal.affiliateKind">
            <i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />{{ deal.affiliateKind === 'SITE' ? t('hotdeal.affiliateNoteSite') : t('hotdeal.affiliateNote') }}
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
                :href="deal.buyUrl"
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
                v-if="over && !business"
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

        <!-- 사업자 글 작성자(신청 건을 아는 사람만 — `deal.business.applicationId`) — [일시 정지](같은 확인 창 → 신청 건으로) · 중단·환불은 신청 건에서 -->
        <div v-if="bizApplicationId && !over" class="flex flex-wrap items-center gap-x-3 gap-y-1.5 border-t border-surface pt-3" data-testid="hotdeal-biz-owner">
            <Button
                v-if="actions.bizPause && actions.bizPauseInfo"
                type="button"
                :label="t('business.pause.pause')"
                icon="pi pi-pause"
                severity="warn"
                variant="outlined"
                size="small"
                :loading="pausePreparing"
                :disabled="busy"
                data-testid="hotdeal-biz-pause"
                @click="openPause"
            />
            <NuxtLink :to="UtilBusiness.applicationPath(bizApplicationId)" class="text-xs text-primary hover:underline" data-testid="hotdeal-biz-stop-link">{{ t('hotdeal.bizStopLink') }}</NuxtLink>
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

        <!-- 게시판 관리(그 게시판 매니저·스텝) — 작성자 조치와 따로 -->
        <div v-if="moderation && (moderation.canEnd || moderation.canReopen)" class="flex flex-col gap-2 border-t border-surface pt-3" data-testid="hotdeal-moderation" :data-role="moderation.role">
            <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm font-semibold">
                <i class="pi pi-shield text-xs text-muted-color" aria-hidden="true" />{{ t('hotdeal.moderateTitle') }}
                <Tag :value="moderatorLabel" severity="secondary" class="text-[0.6875rem] font-medium" data-testid="hotdeal-moderation-role" />
            </p>
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
                <Button
                    v-if="moderation.canEnd"
                    type="button"
                    :label="t('hotdeal.moderateEnd')"
                    icon="pi pi-stop-circle"
                    severity="danger"
                    variant="outlined"
                    size="small"
                    :disabled="busy"
                    data-testid="hotdeal-moderate-end"
                    @click="openModerate('end')"
                />
                <Button
                    v-if="moderation.canReopen"
                    type="button"
                    :label="t('hotdeal.reopen')"
                    icon="pi pi-replay"
                    severity="secondary"
                    variant="outlined"
                    size="small"
                    :disabled="busy"
                    data-testid="hotdeal-moderate-reopen"
                    @click="openModerate('reopen')"
                />
                <span class="text-xs text-muted-color">{{ moderation.canEnd ? t('hotdeal.moderateEndHelp') : t('hotdeal.moderateReopenHelp') }}</span>
            </div>
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
        <HotdealModerateDialog v-model:visible="moderateOpen" :mode="moderateMode" :loading="moderating" :error="moderateError" :field-error="moderateFieldError" @submit="onModerate" />
        <BusinessPauseDialog
            v-if="pauseInfo"
            :visible="pauseOpen"
            :end-label="pauseEndLabel"
            :left="Math.max(0, pauseInfo.limit - pauseInfo.used - 1)"
            deal
            :busy="pausing"
            :error="pauseError"
            @submit="onPause"
            @close="pauseOpen = false"
        />
    </section>
</template>

<script setup lang="ts">
import type { DealEndReason, DealModeration, DealSameRef, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealCardActions, DealPauseInfo } from '~/components/hotdeal/HotdealDealCard.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBusiness from '~/utils/util-business';
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
    /** 게시판 관리(글 상세 `dealModeration`) — 보는 사람이 그 게시판 매니저·스텝이 아니면 null */
    moderation?: DealModeration | null;
    actions: DealCardActions;
}

const props = withDefaults(defineProps<Props>(), { mine: false, guest: false, sameDeals: null, moderation: null });
const emit = defineEmits<{
    'update:deal': [deal: PostDeal];
    /** 게시판 관리 자리가 빠졌다(`403 DEAL_MODERATE_FORBIDDEN`) — null 을 올리면 페이지가 영역을 거둔다 */
    'update:moderation': [moderation: DealModeration | null];
    /** 그새 딜 상태가 바뀌었다 — 페이지가 글을 다시 읽어 딜·게시판 관리를 지금 값으로 바꾼다 */
    stale: [];
}>();

/** 게시판 관리에서 그새 상태가 바뀐 거절 — 대화창을 닫고 글을 다시 읽는다 */
const MODERATE_STALE_CODES: ReadonlySet<string> = new Set(['DEAL_ENDED', 'DEAL_NOT_ENDED', 'DEAL_NOT_FOUND']);

/** 다시 눌러도 같은 결과인 거절 — 알리고 대화창을 닫는다 */
const FINAL_CODES: ReadonlySet<string> = new Set([
    'DEAL_END_REQUEST_EXISTS',
    'DEAL_END_REQUEST_OWN',
    'DEAL_END_REQUEST_NOT_ALLOWED',
    'DEAL_END_REQUEST_AUTHOR_LIMIT',
    'DEAL_ENDED',
    'DEAL_NOT_FOUND',
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
/** 사업자 글(2차) — 광고 고지 · 새 딜 올리기 없음 · 종료 확인 문구가 다르다 */
const business = computed(() => UtilHotdeal.isBusiness(props.deal));
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
const moderating = ref(false);
const pausePreparing = ref(false);
const pausing = ref(false);
const busy = computed(() => requesting.value || keeping.value || ending.value || reopening.value || moderating.value || pausePreparing.value || pausing.value);

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
    // 사업자 글은 되돌리기가 없고 환불도 없다 — 노출 자리도 같이 멈춘다
    const ok = await confirm({ title: t('hotdeal.authorEndTitle'), message: business.value ? t('hotdeal.bizEndMessage') : t('hotdeal.authorEndMessage'), confirmLabel: t('hotdeal.authorEnd'), tone: 'danger' });
    if (!ok) return;
    ending.value = true;
    try {
        emit('update:deal', await props.actions.end());
        toast.success(t('hotdeal.authorEnded'));
    } catch (e) {
        toast.error(errorText(e), 8000);
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

/* ── 사업자 글 작성자 — 일시 정지(목업 2026-11-02) ── */
/** 그 글의 신청 건 — 내 사업자 글이고 서버가 실어 줬을 때만 */
const bizApplicationId = computed(() => (props.mine && business.value ? (props.deal.business?.applicationId ?? null) : null));
const pauseOpen = ref(false);
const pauseError = ref('');
const pauseInfo = ref<DealPauseInfo | null>(null);
const pauseEndLabel = computed(() => (pauseInfo.value ? time.value.serviceDate(pauseInfo.value.endDate) : ''));

/** 확인 창을 열기 전에 신청 건(끝 날짜·남은 횟수)을 받는다 — 정지할 수 없거나 다 썼으면 알리고 열지 않는다 */
async function openPause(): Promise<void> {
    if (!props.actions.bizPauseInfo || pausePreparing.value) return;
    pausePreparing.value = true;
    try {
        const info = await props.actions.bizPauseInfo();
        if (!info) toast.error(t('hotdeal.bizPauseUnavailable'), 8000);
        else if (info.used >= info.limit) toast.error(t('hotdeal.bizPauseLimit'), 8000);
        else {
            pauseInfo.value = info;
            pauseError.value = '';
            pauseOpen.value = true;
        }
    } catch (e) {
        toast.error(errorText(e), 8000);
    } finally {
        pausePreparing.value = false;
    }
}

/** 정지 — 성공하면 토스트 + 신청 건 화면으로(다시 켜기·중단은 거기서) */
async function onPause(): Promise<void> {
    const appId = bizApplicationId.value;
    if (!props.actions.bizPause || pausing.value || !appId) return;
    pausing.value = true;
    pauseError.value = '';
    try {
        await props.actions.bizPause();
        pauseOpen.value = false;
        toast.success(t('business.pause.pausedDone'));
        await navigateTo(UtilBusiness.applicationPath(appId));
    } catch (e) {
        // 그새 다 썼거나 상태가 바뀌었다 — 알리고 닫는다(신청 건에서 지금 상태를 본다)
        if (e instanceof ApiError && (e.code === 'BIZ_PAUSE_LIMIT' || e.code === 'BIZ_APPLICATION_STATE_CHANGED')) {
            pauseOpen.value = false;
            toast.error(e.message, 8000);
        } else pauseError.value = UtilBoardMessages.of(t, e);
    } finally {
        pausing.value = false;
    }
}

/* ── 게시판 관리(그 게시판 매니저·스텝) ── */
const moderateOpen = ref(false);
const moderateMode = ref<'end' | 'reopen'>('end');
const moderateError = ref('');
const moderateFieldError = ref('');
const moderatorLabel = computed(() => (props.moderation?.role === 'STAFF' ? t('hotdeal.moderatorStaff') : t('hotdeal.moderatorManager')));

function openModerate(mode: 'end' | 'reopen'): void {
    moderateMode.value = mode;
    moderateError.value = '';
    moderateFieldError.value = '';
    moderateOpen.value = true;
}

async function onModerate(reason: string): Promise<void> {
    if (moderating.value) return;
    const mode = moderateMode.value;
    moderating.value = true;
    moderateError.value = '';
    moderateFieldError.value = '';
    try {
        emit('update:deal', mode === 'end' ? await props.actions.moderateEnd(reason) : await props.actions.moderateReopen(reason || null));
        moderateOpen.value = false;
        toast.success(mode === 'end' ? t('hotdeal.moderateEnded') : t('hotdeal.moderateReopened'));
    } catch (e) {
        const reasonError = e instanceof ApiError ? e.field('reason') : undefined;
        if (reasonError) moderateFieldError.value = reasonError;
        else if (e instanceof ApiError && e.code === 'DEAL_MODERATE_FORBIDDEN') {
            // 매니저·스텝에서 빠졌거나 정지 — 영역을 거둔다
            moderateOpen.value = false;
            emit('update:moderation', null);
            toast.error(UtilHotdealMessages.dealError(t, e, 'moderate') ?? e.message, 8000);
        } else if (e instanceof ApiError && MODERATE_STALE_CODES.has(e.code)) {
            // 그새 다른 사람이 종료·재개했다 — 글을 다시 읽어 지금 상태로
            moderateOpen.value = false;
            emit('stale');
            toast.error(UtilHotdealMessages.dealError(t, e, 'moderate') ?? e.message, 8000);
        } else if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
            moderateOpen.value = false;
            toast.error(e.message, 8000);
        } else moderateError.value = UtilBoardMessages.of(t, e);
    } finally {
        moderating.value = false;
    }
}
</script>
