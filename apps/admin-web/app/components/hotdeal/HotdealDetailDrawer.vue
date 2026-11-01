<template>
    <!--
        핫딜 글 서랍 — 핫딜 글 목록의 줄을 누르면(또는 `?deal=` 로 들어오면) 연다. 글 관리 서랍(`PostDetailDrawer`)과 같은 손놀림: 열리는 쪽은 `prefs.drawerPosition`, 표를 가리지 않고(`modal=false`)
        열린 채 다른 줄을 누르면 그 글로 바뀐다. 서랍·표 줄·떠 있는 창 밖을 누르면 닫는다.
        머리: 제목 · 상태 알약 · 글 번호(복사) · 공개 글 열기(새 탭) · 이전/다음 글.
        본문: 딜 카드(이미지 · 가격 줄 · 쇼핑몰·호스트 · 상품 링크 `rel="nofollow sponsored noopener"` 새 탭 · 제휴 · 배송 · 재고 · 기간 · 쿠폰·가격 기준) →
          상태와 종료 경위(누가·언제 · 의심 시각·자동 종료 예정 · 작성자 되돌리기 기한(D2) · keep 횟수 N/M) → 종료 요청 사유별 수(요청자 신원은 없다) → 신고 대기(신고 관리 링크) → 같은 딜 N건(누르면 그 글로) → 글 정보(게시판·말머리·사업자(2차 사업자 핫딜 — 상호 · [핫딜 심사에서 열기])·작성자·등록 시각).
          맨 위에 「차단 도메인 진행 중」 경고(지금 규칙에서 상품·최종 호스트가 차단), 딜 카드에 최종 호스트 · HOT · 「링크 바뀜」·「가격 수정 N회」 · 가격 기준 배지.
          제휴 링크 방식이 사이트 제휴(`detail.affiliateMode = SITE`)면 상품 주소 아래 「구매 링크: 사이트 제휴로 변환 — 프로그램」 / 「개인 제휴 — 지금은 클릭 때 떼어져요」 / 「제휴 없이 원래 링크」 한 줄(D5).
        탭 「수정 이력」(D3): 처음 열 때 부른 화면이 받는다(`need-revisions`). 줄 = 시각 · 누가(작성자/운영진) · 바뀐 칸 전 → 후, 호스트가 바뀐 줄은 노랑 테두리 + 「링크 바뀜」. 6개월 보존 안내 한 줄.
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

        <Tabs v-else v-model:value="tab" class="hotdeal-drawer-tabs">
            <TabList>
                <Tab value="info">{{ t('hotdeal.tabInfo') }}</Tab>
                <Tab value="revisions">
                    {{ t('hotdeal.tabRevisions') }}
                    <Tag v-if="deal.revision.linkChanged" :value="t('hotdeal.linkChanged')" severity="warn" class="ml-1 text-[10px]!" />
                </Tab>
            </TabList>
            <TabPanels class="px-0">
                <TabPanel value="info">
        <div class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <!-- 차단 도메인 진행 중 -->
            <Message v-if="row.blockedHost" severity="error" size="small" :closable="false" icon="pi pi-ban">{{ t('hotdeal.blockedHostNote', { host: row.blockedHost }) }}</Message>

            <!-- 딜 카드 -->
            <section class="flex flex-col gap-3 rounded-xl border border-(--line) p-3" :aria-labelledby="`${titleId}-deal`">
                <h3 :id="`${titleId}-deal`" class="sr-only">{{ t('hotdeal.dealCard') }}</h3>
                <div class="flex gap-3">
                    <HotdealThumb :url="deal.image?.url ?? null" :alt="t('hotdeal.imageAlt', { title: row.title })" size="lg" :dim="dim" />
                    <div class="flex min-w-0 flex-1 flex-col gap-1.5">
                        <HotdealPriceLine :deal-price="deal.dealPrice" :normal-price="deal.normalPrice" :discount-rate="deal.discountRate" large :ended="dim" />
                        <p class="m-0 flex flex-wrap items-center gap-1.5 text-[13px]">
                            <span class="font-medium">{{ deal.storeName }}</span>
                            <span class="text-xs text-muted-color">{{ deal.productHost }}<template v-if="deal.finalHost"> → {{ deal.finalHost }}</template></span>
                            <Tag v-if="deal.affiliate" :value="t('hotdeal.affiliate')" severity="info" class="text-[11px]!" />
                            <Tag v-if="deal.hot" value="HOT" severity="danger" class="text-[11px]!" />
                        </p>
                        <p v-if="deal.revision.linkChanged || deal.revision.priceEdits > 0" class="m-0 flex flex-wrap items-center gap-1">
                            <Tag v-if="deal.revision.linkChanged" :value="t('hotdeal.linkChanged')" icon="pi pi-link" severity="warn" class="text-[11px]!" />
                            <Tag v-if="deal.revision.priceEdits > 0" :value="t('hotdeal.priceEdits', { n: deal.revision.priceEdits })" severity="secondary" class="text-[11px]!" />
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
                        <!-- 사이트 제휴 방식일 때 구매 링크가 어떻게 바뀌는지(D5 — 저장된 링크는 그대로, 클릭 때 변환) -->
                        <p v-if="buyLinkNote" class="m-0 flex items-start gap-1.5 text-[11px] leading-relaxed" :class="deal.affiliateKind === 'SITE' ? 'text-sky-700 dark:text-sky-400' : 'text-muted-color'">
                            <i class="pi pi-link mt-0.5 text-[10px]" aria-hidden="true" />
                            <span><b class="font-medium">{{ t('hotdeal.buyLink') }}</b> {{ buyLinkNote }}</span>
                        </p>
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
                    <template v-if="deal.priceBasisKind || deal.priceBasis">
                        <dt class="text-muted-color">{{ t('hotdeal.priceBasis') }}</dt>
                        <dd class="m-0 flex flex-wrap items-center gap-1.5 break-words">
                            <Tag v-if="deal.priceBasisKind" :value="t(`hotdeal.priceBasisKind.${deal.priceBasisKind}`)" severity="secondary" class="text-[11px]!" />
                            <span v-if="deal.priceBasisKind === 'OVERSEAS'" class="text-xs text-muted-color">{{ t(deal.dutyIncluded ? 'hotdeal.dutyIncluded' : 'hotdeal.dutyExcluded') }}</span>
                            <span v-if="deal.priceBasis">{{ deal.priceBasis }}</span>
                        </dd>
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

                <!-- 사업자 핫딜 글(2차) — 상호 · 그 글을 올린 신청 건 -->
                <template v-if="business">
                    <dt class="text-muted-color">{{ t('hotdeal.business') }}</dt>
                    <dd class="m-0 flex flex-col gap-1">
                        <span class="flex flex-wrap items-center gap-1.5">
                            <Tag :value="t('hotdeal.businessAd')" icon="pi pi-megaphone" severity="warn" class="text-[11px]!" />
                            <span class="font-medium">{{ business.name }}</span>
                            <NuxtLink :to="reviewLink" class="inline-flex items-center gap-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                                {{ t('hotdeal.openReview') }} <i class="pi pi-arrow-right text-[10px]" aria-hidden="true" />
                            </NuxtLink>
                        </span>
                        <span class="text-xs text-muted-color">{{ t('hotdeal.businessNote') }}</span>
                    </dd>
                </template>

                <dt class="text-muted-color">{{ t('hotdeal.col.author') }}</dt>
                <dd class="m-0 flex flex-wrap items-center gap-2">
                    <span :class="row.author.withdrawn ? 'text-muted-color' : ''">{{ UtilHotdealMessages.author(t, row.author) }}</span>
                    <NuxtLink v-if="canReadMembers && !row.author.withdrawn" to="/members" class="text-xs text-primary hover:underline">{{ t('hotdeal.findMember') }}</NuxtLink>
                </dd>

                <dt class="text-muted-color">{{ t('hotdeal.col.createdAt') }}</dt>
                <dd class="m-0 tabular-nums">{{ time.full(row.createdAt) }}</dd>
            </dl>
        </div>
                </TabPanel>

                <!-- 수정 이력(D3) — 딜 칸 전·후, 호스트가 바뀐 줄 강조 -->
                <TabPanel value="revisions">
                    <div class="flex flex-col gap-3 pt-1">
                        <p class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-history text-[11px]" aria-hidden="true" />{{ t('hotdeal.revision.retention', { months: revisions?.retentionMonths ?? 6 }) }}</p>
                        <ErrorState v-if="revisionsError" :title="t('hotdeal.revision.loadFailed')" :description="revisionsError.message" retry :retrying="revisionsLoading" @retry="emit('need-revisions')" />
                        <div v-else-if="!revisions" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')" aria-busy="true">
                            <Skeleton v-for="n in 3" :key="n" height="3.5rem" />
                        </div>
                        <EmptyState v-else-if="revisions.items.length === 0" icon="pi pi-history" :title="t('hotdeal.revision.empty')" :description="t('hotdeal.revision.emptyHint')" />
                        <ol v-else class="m-0 flex list-none flex-col gap-2 p-0" :aria-busy="revisionsLoading">
                            <li
                                v-for="rev in revisions.items"
                                :key="rev.id"
                                class="flex flex-col gap-1.5 rounded-lg border px-3 py-2"
                                :class="rev.hostChanged ? 'border-amber-400 bg-amber-50/60 dark:border-amber-700 dark:bg-amber-950/30' : 'border-(--line)'"
                            >
                                <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                                    <Tag v-if="rev.hostChanged" :value="t('hotdeal.linkChanged')" icon="pi pi-link" severity="warn" class="text-[11px]!" />
                                    <Tag :value="t(`hotdeal.revision.actor.${rev.actor.kind}`)" :severity="rev.actor.kind === 'MANAGER' ? 'info' : 'secondary'" class="text-[11px]!" />
                                    <span class="font-medium text-color">{{ rev.actor.name }}</span>
                                    <span aria-hidden="true">·</span>
                                    <time :datetime="rev.at" :title="time.full(rev.at)" class="tabular-nums">{{ time.full(rev.at) }}</time>
                                </div>
                                <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-1 text-[13px]">
                                    <template v-for="c in rev.changes" :key="c.field">
                                        <dt class="text-muted-color">{{ t(`hotdeal.revision.field.${c.field}`) }}</dt>
                                        <dd class="m-0 flex min-w-0 flex-wrap items-center gap-x-1.5 break-all">
                                            <s class="text-muted-color">{{ UtilHotdealMessages.revisionValue(t, locale, time, c.field, c.before) }}</s>
                                            <i class="pi pi-arrow-right text-[10px] text-muted-color" :aria-label="t('hotdeal.revision.to')" role="img" />
                                            <span class="font-medium">{{ UtilHotdealMessages.revisionValue(t, locale, time, c.field, c.after) }}</span>
                                        </dd>
                                    </template>
                                </dl>
                                <p v-if="rev.hostChanged" class="m-0 text-xs text-amber-800 dark:text-amber-300">{{ t('hotdeal.revision.hostChangedNote') }}</p>
                            </li>
                        </ol>
                    </div>
                </TabPanel>
            </TabPanels>
        </Tabs>

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
import { mockQuery } from '~/composables/common/mock/mock-util';
import type { AdminDealDetail, AdminDealRow, DealRevisionListView } from '~/composables/hotdeal/api/useHotdealApi.types';
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
    /** 수정 이력(「수정 이력」 탭) — 아직 안 받았으면 null(탭을 열면 `need-revisions`) */
    revisions?: DealRevisionListView | null;
    revisionsLoading?: boolean;
    revisionsError?: ApiError | null;
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
    revisions: null,
    revisionsLoading: false,
    revisionsError: null,
});
const emit = defineEmits<{
    retry: [];
    act: [kind: 'END' | 'REOPEN'];
    prev: [];
    next: [];
    /** 같은 딜 줄을 눌렀다 — 그 글로 서랍을 바꾼다 */
    'open-deal': [postId: string];
    /** 「수정 이력」 탭이 열렸는데 이력이 없다 — 부른 화면이 받는다 */
    'need-revisions': [];
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
const buyLinkNote = computed(() => (props.detail ? UtilHotdealMessages.buyLinkNote(t, props.detail.affiliateMode, deal.value) : ''));
const stateLines = computed(() => (props.detail ? UtilHotdealMessages.stateLines(t, time.value, props.detail) : []));
/** 탭 — 정보 · 수정 이력. 이력 탭이 열려 있고 이력이 비었으면(글이 바뀌었거나 처음) 받아 달라고 한다 */
const tab = ref<'info' | 'revisions'>('info');
watch(
    () => [tab.value, props.detail?.row.postId, props.revisions, props.revisionsLoading, props.revisionsError] as const,
    ([which, postId, revs, busyRevs, failed]) => {
        if (which === 'revisions' && postId && !revs && !busyRevs && !failed) emit('need-revisions');
    },
);
watch(open, (isOpen) => {
    if (!isOpen) tab.value = 'info';
});

/** 사업자 핫딜 글(2차)의 사업자 — 서버가 아직 안 주면 없음 */
const business = computed(() => (props.detail && UtilHotdeal.isBusiness(row.value) ? (row.value.business ?? null) : null));
/** 핫딜 심사의 그 신청 건. 목업 스위치는 그대로 싣는다 */
const reviewLink = computed(() => ({
    path: '/hotdeal-reviews',
    query: {
        id: business.value?.applicationId ?? '',
        ...mockQuery(), // MOCK: 목업 건끼리 오가게 `?mock=` 를 싣는다 — 서버가 붙으면 이 줄과 import 를 지운다
    },
}));

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
