<template>
    <!--
        핫딜 글 서랍 — 핫딜 글 목록의 줄을 누르면(또는 `?deal=` 로 들어오면) 연다. 글 관리 서랍(`PostDetailDrawer`)과 같은 손놀림: 열리는 쪽은 `prefs.drawerPosition`, 표를 가리지 않고(`modal=false`)
        열린 채 다른 줄을 누르면 그 글로 바뀐다. 서랍·표 줄·떠 있는 창 밖을 누르면 닫는다.
        머리: 제목 · 상태 알약 · 글 번호(복사) · 공개 글 열기(새 탭) · 이전/다음 글.
        본문: 딜 카드(이미지 · 가격 줄 · 쇼핑몰·호스트 · 상품 링크 `rel="nofollow sponsored noopener"` 새 탭 · 제휴 · 배송 · 재고 · 기간 · 쿠폰·가격 기준) →
          상태와 종료 경위(누가·언제 · 의심 시각·자동 종료 예정) → 종료 요청 사유별 수(요청자 신원은 없다) → 신고 대기(신고 관리 링크) → 같은 딜 N건(누르면 그 글로) → 글 정보(게시판·말머리·작성자·등록 시각).
        바닥: [종료](진행·의심·기간 지남) / [재개](종료) — 권한(`hotdeal.manage`)이 없으면 단추 없이 읽기 전용 안내. 서버 호출은 하지 않는다(부른 화면이 한다).
    -->
    <Drawer
        :visible="open"
        class="hotdeal-detail-drawer"
        :modal="false"
        :dismissable="false"
        :position="prefs.drawerPosition"
        :aria-labelledby="titleId"
        :style="{ width: '40rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => (open = v)"
        @after-hide="emit('closed')"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 flex-col gap-1.5">
                <div class="flex min-w-0 items-start gap-2">
                    <h2 :id="titleId" class="min-w-0 flex-1 text-base font-semibold break-words [overflow-wrap:anywhere]">{{ head ? head.title : t('hotdeal.drawerTitle') }}</h2>
                    <div class="flex shrink-0 items-center gap-0.5">
                        <Button v-tooltip.bottom="t('hotdeal.prev')" type="button" icon="pi pi-chevron-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('hotdeal.prev')" :disabled="!hasPrev" @click="emit('prev')" />
                        <Button v-tooltip.bottom="t('hotdeal.next')" type="button" icon="pi pi-chevron-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('hotdeal.next')" :disabled="!hasNext" @click="emit('next')" />
                    </div>
                </div>
                <div v-if="head" class="flex flex-wrap items-center gap-2">
                    <HotdealViewTag :view="head.deal.view" />
                    <button
                        v-tooltip.bottom="t('content.copyPostId')"
                        type="button"
                        class="inline-flex items-center gap-1 rounded px-1 text-xs tabular-nums text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                        :aria-label="t('content.copyPostIdLabel', { id: head.postId })"
                        @click="copyId(head.postId)"
                    >
                        <i class="pi pi-copy text-[10px]" aria-hidden="true" />#{{ head.postId }}
                    </button>
                    <a
                        v-if="detail?.publicUrl"
                        :href="detail.publicUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                    >
                        <i class="pi pi-external-link text-[10px]" aria-hidden="true" />{{ t('hotdeal.openPublic') }}
                    </a>
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('hotdeal.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!detail" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <div class="flex gap-3">
                <Skeleton width="7rem" height="7rem" />
                <div class="flex flex-1 flex-col gap-2">
                    <Skeleton width="50%" height="1.5rem" />
                    <Skeleton v-for="n in 3" :key="n" height="1rem" />
                </div>
            </div>
            <Skeleton v-for="n in 4" :key="`b-${n}`" height="1rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <!-- 딜 카드 -->
            <section class="flex flex-col gap-3 rounded-xl border border-(--line) p-3" :aria-labelledby="`${titleId}-deal`">
                <h3 :id="`${titleId}-deal`" class="sr-only">{{ t('hotdeal.dealCard') }}</h3>
                <div class="flex gap-3">
                    <HotdealThumb :url="deal.image?.url ?? null" :alt="t('hotdeal.imageAlt', { title: row.title })" size="lg" :dim="dim" />
                    <div class="flex min-w-0 flex-1 flex-col gap-1.5">
                        <HotdealPriceLine :deal-price="deal.dealPrice" :normal-price="deal.normalPrice" :discount-rate="deal.discountRate" large :ended="dim" />
                        <p class="m-0 flex flex-wrap items-center gap-1.5 text-[13px]">
                            <span class="font-medium">{{ deal.storeName }}</span>
                            <span class="text-xs text-muted-color">{{ deal.productHost }}</span>
                            <Tag v-if="deal.affiliate" :value="t('hotdeal.affiliate')" severity="info" class="text-[11px]!" />
                        </p>
                        <a
                            :href="deal.productUrl"
                            target="_blank"
                            rel="nofollow sponsored noopener"
                            class="inline-flex min-w-0 items-center gap-1 self-start rounded text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        >
                            <i class="pi pi-external-link text-[10px]" aria-hidden="true" /><span class="truncate [overflow-wrap:anywhere]">{{ t('hotdeal.openProduct') }}</span>
                        </a>
                        <p class="m-0 text-[11px] break-all text-muted-color">{{ deal.productUrl }}</p>
                    </div>
                </div>
                <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                    <dt class="text-muted-color">{{ t('hotdeal.shipping') }}</dt>
                    <dd class="m-0">{{ UtilHotdealMessages.shipping(t, locale, deal.shippingFee) }}</dd>
                    <dt class="text-muted-color">{{ t('hotdeal.stock') }}</dt>
                    <dd class="m-0">{{ t(`hotdeal.stockState.${deal.stockState}`) }}</dd>
                    <dt class="text-muted-color">{{ t('hotdeal.period') }}</dt>
                    <dd class="m-0 tabular-nums">{{ UtilHotdealMessages.period(t, time, deal) }}</dd>
                    <template v-if="deal.couponNote">
                        <dt class="text-muted-color">{{ t('hotdeal.coupon') }}</dt>
                        <dd class="m-0 break-words">{{ deal.couponNote }}</dd>
                    </template>
                    <template v-if="deal.priceBasis">
                        <dt class="text-muted-color">{{ t('hotdeal.priceBasis') }}</dt>
                        <dd class="m-0 break-words">{{ deal.priceBasis }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ t('hotdeal.image') }}</dt>
                    <dd class="m-0">{{ deal.image ? t(`hotdeal.imageSource.${deal.image.source}`) : t('hotdeal.noImage') }}</dd>
                </dl>
            </section>

            <!-- 상태와 종료 경위 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-state`">
                <h3 :id="`${titleId}-state`" class="text-sm font-semibold">{{ t('hotdeal.stateTitle') }}</h3>
                <Message v-if="stateLines.length" :severity="deal.view === 'FLAGGED' ? 'warn' : deal.view === 'ENDED' ? 'secondary' : 'info'" size="small" :closable="false" :icon="UtilHotdeal.viewIcon(deal.view)">
                    <span class="flex flex-col gap-0.5">
                        <span v-for="(line, i) in stateLines" :key="i">{{ line }}</span>
                    </span>
                </Message>
                <p v-else class="m-0 text-[13px] text-muted-color">{{ t('hotdeal.stateActive') }}</p>
            </section>

            <!-- 종료 요청 — 사유별 수만(요청자 신원은 누구에게도 보이지 않는다) -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-req`">
                <h3 :id="`${titleId}-req`" class="text-sm font-semibold">{{ t('hotdeal.endRequestsTitle', { n: deal.endRequests.total }) }}</h3>
                <ul v-if="deal.endRequests.total > 0" class="m-0 flex list-none flex-wrap gap-2 p-0">
                    <li v-for="r in requestRows" :key="r.key">
                        <Tag :value="`${t(`hotdeal.endReason.${r.key}`)} ${r.n}`" :severity="r.n > 0 ? 'warn' : 'secondary'" class="text-[11px]!" />
                    </li>
                </ul>
                <p class="m-0 text-xs text-muted-color">{{ deal.endRequests.total > 0 ? t('hotdeal.endRequestsPrivate') : t('hotdeal.endRequestsNone') }}</p>
            </section>

            <!-- 신고 · 같은 딜 · 글 정보 -->
            <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-2 text-[13px]">
                <dt class="text-muted-color">{{ t('hotdeal.col.reports') }}</dt>
                <dd class="m-0" :class="row.openReports > 0 ? 'font-medium text-red-600 dark:text-red-400' : ''">
                    <NuxtLink v-if="row.openReports > 0 && canReadReports" :to="UtilPost.reportsLink(row.postId)" class="rounded hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        {{ t('hotdeal.reportsPending', { n: row.openReports }) }} <i class="pi pi-arrow-right text-[10px]" aria-hidden="true" />
                    </NuxtLink>
                    <template v-else>{{ t('hotdeal.reportsPending', { n: row.openReports }) }}</template>
                </dd>

                <dt class="text-muted-color">{{ t('hotdeal.sameDeals') }}</dt>
                <dd class="m-0 flex flex-col gap-1">
                    <span :class="deal.sameDealCount > 0 ? 'font-medium' : 'text-muted-color'">{{ t('hotdeal.sameDealCount', { n: deal.sameDealCount }) }}</span>
                    <ul v-if="detail.sameDeals.length" class="m-0 flex list-none flex-col gap-1 p-0">
                        <li v-for="s in detail.sameDeals" :key="s.postId" class="flex min-w-0 flex-wrap items-center gap-1.5 text-xs">
                            <HotdealViewTag :view="s.view" />
                            <button type="button" class="min-w-0 text-left break-words text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click="emit('open-deal', s.postId)">{{ s.title }}</button>
                            <span class="tabular-nums text-muted-color">{{ UtilHotdealMessages.won(t, locale, s.dealPrice) }} · {{ time.listLabel(s.createdAt) }}</span>
                        </li>
                    </ul>
                    <span class="text-xs text-muted-color">{{ t('hotdeal.sameDealsNote') }}</span>
                </dd>

                <dt class="text-muted-color">{{ t('hotdeal.col.board') }}</dt>
                <dd class="m-0">
                    {{ localizedName(row.board.name, row.board.nameEn) }}<span v-if="row.prefix" class="ml-1 text-muted-color">[{{ row.prefix.name }}]</span>
                </dd>

                <dt class="text-muted-color">{{ t('hotdeal.col.author') }}</dt>
                <dd class="m-0 flex flex-wrap items-center gap-2">
                    <span :class="row.author.withdrawn ? 'text-muted-color' : ''">{{ UtilHotdealMessages.author(t, row.author) }}</span>
                    <NuxtLink v-if="canReadMembers && !row.author.withdrawn" to="/members" class="text-xs text-primary hover:underline">{{ t('hotdeal.findMember') }}</NuxtLink>
                </dd>

                <dt class="text-muted-color">{{ t('hotdeal.col.createdAt') }}</dt>
                <dd class="m-0 tabular-nums">{{ time.full(row.createdAt) }}</dd>
            </dl>
        </div>

        <template v-if="detail && !error" #footer>
            <div class="flex w-full flex-col gap-2 border-t border-(--line) pt-3">
                <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('hotdeal.readOnly') }}</p>
                <div v-else class="flex flex-wrap justify-end gap-2">
                    <Button v-if="UtilHotdeal.canReopen(deal.view)" type="button" :label="t('hotdeal.action.REOPEN')" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" :disabled="busy || loading" @click="emit('act', 'REOPEN')" />
                    <Button v-if="UtilHotdeal.canEnd(deal.view)" type="button" :label="t('hotdeal.action.END')" icon="pi pi-ban" size="small" severity="danger" :disabled="busy || loading" @click="emit('act', 'END')" />
                </div>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AdminDealDetail, AdminDealRow } from '~/composables/hotdeal/api/useHotdealApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';
import UtilPost from '~/utils/util-post';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 머리를 바로 그릴 줄 — 받은 상세의 줄, 아직이면 누른 목록 줄 */
    item?: AdminDealRow | null;
    detail?: AdminDealDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: boolean;
    hasPrev?: boolean;
    hasNext?: boolean;
    /** `hotdeal.manage` — 종료·재개 단추 */
    canManage?: boolean;
    /** `report.read` — 신고 대기를 신고 관리로 잇는다 */
    canReadReports?: boolean;
    /** `member.read` — 회원 관리로 가는 링크 */
    canReadMembers?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    item: null,
    detail: null,
    loading: false,
    error: null,
    busy: false,
    hasPrev: false,
    hasNext: false,
    canManage: false,
    canReadReports: false,
    canReadMembers: false,
});
const emit = defineEmits<{
    retry: [];
    act: [kind: 'END' | 'REOPEN'];
    prev: [];
    next: [];
    /** 같은 딜 줄을 눌렀다 — 그 글로 서랍을 바꾼다 */
    'open-deal': [postId: string];
    closed: [];
}>();

const { t, locale } = useI18n();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const time = useDisplayTime();
const toast = useAppToast();
const titleId = useId();

const head = computed(() => props.detail?.row ?? props.item);
/** 본문이 그리는 줄 — 템플릿은 `detail` 이 있을 때만 읽는다 */
const row = computed(() => props.detail?.row as AdminDealRow);
const deal = computed(() => row.value.deal);
const dim = computed(() => deal.value.view === 'ENDED' || deal.value.view === 'EXPIRED');
const stateLines = computed(() => (props.detail ? UtilHotdealMessages.stateLines(t, time.value, props.detail) : []));
const requestRows = computed(() => [
    { key: 'SOLD_OUT', n: deal.value.endRequests.bySoldOut },
    { key: 'PRICE_CHANGED', n: deal.value.endRequests.byPriceChanged },
    { key: 'LINK_DEAD', n: deal.value.endRequests.byLinkDead },
]);

async function copyId(id: string): Promise<void> {
    try {
        await navigator.clipboard.writeText(id);
        toast.success(t('content.postIdCopied'));
    } catch {
        toast.error(t('content.copyFailed'));
    }
}

/** 바깥 누르기 — 서랍·표 줄·떠 있는 창 안이면 무시하고, 그 밖이면 닫는다(글 관리 서랍과 같은 방식) */
const INSIDE = '.p-drawer, .p-drawer-mask, tbody tr, .p-dialog, .p-dialog-mask, .p-overlay, .p-select-overlay, .p-menu, .p-popover, .p-tooltip, .p-toast, [role="alertdialog"], [role="listbox"], [data-hotdeal-keep-drawer]';
function onPointerDown(e: PointerEvent): void {
    const target = e.target as Element | null;
    if (!target || target.closest(INSIDE)) return;
    open.value = false;
}
watch(
    open,
    (isOpen) => {
        if (isOpen) document.addEventListener('pointerdown', onPointerDown, true);
        else document.removeEventListener('pointerdown', onPointerDown, true);
    },
    { immediate: true },
);
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown, true));
</script>
