<template>
    <!--
        로보티콘 › 로보티콘 심사(`/robotikon/reviews`) — 회원 작가가 낸 로보티콘 팩 신청을 심사한다(계약 `docs/features/emoticon-contract.md` 「목업 가정」 5·6).
        실제 API(S1·S2). 승인·반려는 본문 없는 204 — 끝나면 목록을 다시 읽는다. 승인 `{ price }` 는 가격 대화상자에서 고른다 — 유료는 설정이 켜져 있고(`rules.paidSalesEnabled`, 기본 꺼짐)
        작가가 정산 정보(`payoutRegistered`)·지금 판 계약 동의(`contractConsent.current`)를 갖췄을 때만(걸리면 서버 `400 fields.price` — 문구로 까닭을 가른다, 대화상자 칸 아래에 보인다).
        탭(숫자): 심사 대기(처음 신청) · 새 판 심사(판매 중인 팩을 고친 것 — 심사 중에도 옛 판이 나온다) · 반려 · 전체. 고른 탭은 `?status=`, 연 건은 `?id=`(알림 링크 후보).
        한 줄: 대표 · 팩 이름 · 작가(회원 관리 링크) · 개수 · 신청 시각 · 판(처음 / 새 판 vN) · 상태(반려·전체 탭 — 작가 탈퇴로 닫힌 반려는 「작가 탈퇴로 자동 반려」, 줄 `cause`). 줄을 누르면 서랍(`EmoticonReviewDrawer`).
        승인: 확인창(규격 어긋남이 있으면 그 수를 함께 말한다) → 토스트 → 서랍 닫기 → 목록 다시 읽기. 반려: `EmoticonReasonDialog`(자주 쓰는 사유 + 직접 입력, 5~200자 — 작가에게 그대로).
        이미 처리된 건(409 `EMOTICON_REVIEW_NOT_PENDING`)·없는 건(404)은 토스트 + 다시 읽기.
        권한(후보 — 서버 `Permission` 에 아직 없다): 조회 `emoticon.read`(메뉴) · 승인·반려 `emoticon.review`(없으면 단추가 없다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('emoticon.reviewsTitle')" :subtitle="t('emoticon.reviewsSubtitle')">
            <template #actions>
                <Button type="button" :label="t('emoticon.refresh')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading" @click="load" />
            </template>
        </PageTitle>

        <Message v-if="!canReview" severity="info" :closable="false" size="small">{{ t('emoticon.reviewReadOnly') }}</Message>

        <Tabs :value="tab" scrollable class="min-w-0" @update:value="(v: string | number) => selectTab(String(v))">
            <TabList :aria-label="t('emoticon.reviewTabs')">
                <Tab v-for="item in tabs" :key="item.value" :value="item.value">
                    <span class="inline-flex items-center gap-1.5">
                        {{ item.label }}
                        <Badge v-if="item.count !== null && item.count > 0 && item.value !== 'REJECTED'" :value="item.count" severity="info" size="small" />
                        <span v-else-if="item.count !== null" class="text-[11px] font-normal tabular-nums text-muted-color">{{ item.count }}</span>
                    </span>
                </Tab>
            </TabList>
        </Tabs>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable :value="rows" data-key="id" :loading="loading" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable row-hover class="min-w-0 cursor-pointer" @row-click="(e: { data: EmoticonReviewRow }) => openRow(e.data)">
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('emoticon.forbidden') : t('emoticon.reviewsLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="loading" @retry="load" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-check-circle" :title="t(`emoticon.reviewsEmpty.${tab}`)" />
                </template>

                <Column :header="t('emoticon.col.cover')" style="width: 4.5rem">
                    <template #body="{ data }">
                        <EmoticonPackCover :src="data.coverUrl" :alt="data.packName" />
                    </template>
                </Column>

                <Column :header="t('emoticon.col.name')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <button type="button" class="block max-w-full cursor-pointer truncate text-left text-[13px] font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click.stop="openRow(data)">
                            {{ data.packName }}
                        </button>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.creator')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <span v-if="data.creator.withdrawn" class="text-[13px] text-muted-color">{{ data.creator.nickname }}</span>
                        <NuxtLink v-else :to="UtilEmoticon.memberLink(data.creator.memberNo)" class="text-[13px] text-primary hover:underline" :title="t('emoticon.openMember')" @click.stop>{{ data.creator.nickname }}</NuxtLink>
                        <code class="block text-[11px] tabular-nums text-muted-color">{{ data.creator.memberNo }}</code>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.count')" style="min-width: 4.5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums">{{ data.itemCount }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.saleWish')" style="min-width: 6.5rem">
                    <template #body="{ data }">
                        <Tag :value="t(`emoticon.saleWish.${data.saleWish}`)" :severity="data.saleWish === 'PAID' ? 'info' : 'secondary'" />
                        <span v-if="data.currentPrice > 0" class="mt-0.5 block text-[11px] tabular-nums text-muted-color">{{ t('emoticon.price.current', { price: t('emoticon.wonValue', { amount: data.currentPrice.toLocaleString(locale) }) }) }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.submittedAt')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.shortDateTime(data.submittedAt) }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.version')" style="min-width: 6rem">
                    <template #body="{ data }">
                        <Tag :value="data.kind === 'UPDATE' ? t('emoticon.kindUpdate', { version: data.version }) : t('emoticon.kindFirst')" :severity="data.kind === 'UPDATE' ? 'contrast' : 'secondary'" />
                    </template>
                </Column>

                <Column v-if="tab === 'ALL' || tab === 'REJECTED'" :header="t('emoticon.col.status')" style="min-width: 6rem">
                    <template #body="{ data }">
                        <Tag :value="t(`emoticon.reviewStatus.${data.status}`)" :severity="UtilEmoticon.reviewStatusSeverity(data.status)" />
                        <!-- 작가 탈퇴로 시스템이 닫은 반려(줄 `cause`, V148 — 상세 `decision.cause` 와 같은 판정) -->
                        <span v-if="data.cause === 'CREATOR_WITHDRAWN'" class="mt-0.5 block text-[11px] leading-snug text-amber-700 dark:text-amber-400" data-testid="emoticon-review-creator-withdrawn">{{ t('emoticon.rejectedCreatorWithdrawn') }}</span>
                    </template>
                </Column>
            </DataTable>
        </div>

        <EmoticonReviewDrawer
            v-model:visible="drawerOpen"
            :summary="selected"
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :busy="working !== null"
            :approving="working === 'approve'"
            :can-review="canReview"
            @retry="loadDetail"
            @approve="approve"
            @reject="openReject"
        />

        <EmoticonPriceDialog
            v-if="detail"
            v-model:visible="approveOpen"
            mode="approve"
            :title="t('emoticon.approveTitle', { name: detail.packName })"
            :description="detail.kind === 'UPDATE' ? t('emoticon.approveBodyUpdate') : t('emoticon.approveBody')"
            :sale-wish="detail.saleWish"
            :current-price="detail.currentPrice"
            :tiers="detail.rules.priceTiers"
            :paid-sales-enabled="detail.rules.paidSalesEnabled"
            :spec-errors="approveSpecErrors"
            :paid-blockers="paidBlockers"
            :busy="working === 'approve'"
            :error="approveError"
            :field-errors="approveFieldErrors"
            @submit="submitApprove"
        />

        <EmoticonReasonDialog
            v-model:visible="rejectOpen"
            :title="t('emoticon.rejectTitle', { name: detail?.packName ?? '' })"
            :description="detail?.kind === 'UPDATE' ? t('emoticon.rejectBodyUpdate') : t('emoticon.rejectBody')"
            :label="t('emoticon.rejectReason')"
            :confirm-label="t('emoticon.actReject')"
            :presets="rejectPresets"
            :busy="working === 'reject'"
            :error="rejectError"
            :field-error="rejectFieldError"
            @submit="submitReject"
        />
    </div>
</template>

<script setup lang="ts">
import type { EmoticonReasonPreset } from '~/components/emoticon/EmoticonReasonDialog.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonReviewDetail, EmoticonReviewList, EmoticonReviewRow, EmoticonReviewTab } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import { EMOTICON_REVIEW_TABS } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import UtilEmoticon from '~/utils/util-emoticon';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('emoticon.reviewsTitle') });

const api = useEmoticonAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const route = useRoute();
const router = useRouter();

/** 승인·반려 권한(후보 — `util-permission-catalog.ts`) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canReview = computed(() => can('emoticon.review'));

function tabOf(value: unknown): EmoticonReviewTab {
    return typeof value === 'string' && (EMOTICON_REVIEW_TABS as readonly string[]).includes(value) ? (value as EmoticonReviewTab) : 'PENDING';
}

const tab = ref<EmoticonReviewTab>(tabOf(route.query.status));
const rows = ref<EmoticonReviewRow[]>([]);
const counts = ref<EmoticonReviewList['counts'] | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);

const tabs = computed(() =>
    EMOTICON_REVIEW_TABS.map((value) => ({
        value,
        label: t(`emoticon.reviewTab.${value}`),
        count: value === 'ALL' || !counts.value ? null : counts.value[value],
    })),
);

/** 늦게 도착한 앞 요청의 응답은 버린다(번호표) */
let ticket = 0;

async function load(): Promise<void> {
    const mine = ++ticket;
    loading.value = true;
    loadError.value = null;
    try {
        const found = await api.reviews(tab.value);
        if (mine !== ticket) return;
        rows.value = found.items;
        counts.value = found.counts;
    } catch (e) {
        if (mine !== ticket) return;
        rows.value = [];
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) loading.value = false;
    }
}

function selectTab(value: string): void {
    const next = tabOf(value);
    if (next === tab.value) return;
    tab.value = next;
    void router.replace({ query: { ...route.query, status: next === 'PENDING' ? undefined : next } });
    void load();
}

// ── 서랍 ─
const drawerOpen = ref(false);
const selected = ref<EmoticonReviewRow | null>(null);
const selectedId = ref<string | null>(null);
const detail = ref<EmoticonReviewDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);
let detailTicket = 0;

async function loadDetail(): Promise<void> {
    const id = selectedId.value;
    if (!id) return;
    const mine = ++detailTicket;
    detailLoading.value = true;
    detailError.value = null;
    try {
        const found = await api.review(id);
        if (mine !== detailTicket) return;
        detail.value = found;
    } catch (e) {
        if (mine !== detailTicket) return;
        detailError.value = toApiError(e);
    } finally {
        if (mine === detailTicket) detailLoading.value = false;
    }
}

function openId(id: string, row: EmoticonReviewRow | null): void {
    selectedId.value = id;
    selected.value = row;
    detail.value = null;
    drawerOpen.value = true;
    if (route.query.id !== id) void router.replace({ query: { ...route.query, id } });
    void loadDetail();
}

function openRow(row: EmoticonReviewRow): void {
    openId(row.id, row);
}

// 서랍을 닫으면 주소의 `?id=` 를 지운다
watch(drawerOpen, (visible) => {
    if (!visible && route.query.id) void router.replace({ query: { ...route.query, id: undefined } });
});

onMounted(() => {
    void load();
    if (typeof route.query.id === 'string' && route.query.id) openId(route.query.id, null);
});

// ── 처리 ─
const working = ref<'approve' | 'reject' | null>(null);

/** 이미 처리됐거나 없는 건 — 서랍을 닫고 다시 읽는다 */
function handleGone(e: ApiError): boolean {
    if (e.status !== 404 && e.status !== 409) return false;
    toast.show(e.message, { life: 6000 });
    drawerOpen.value = false;
    void load();
    return true;
}

// 승인 — 가격 고르기 대화상자(카카오 방식: 유료 희망이면 설정의 가격 단계 중 하나, 「무료로 승인」도 된다)
const approveOpen = ref(false);
const approveSpecErrors = ref(0);
const approveError = ref('');
const approveFieldErrors = ref<Record<string, string>>({});
/** 유료로 승인하면 안 되는 까닭 — 상품화 계약 미동의(또는 옛 판) · 정산 정보 없음 */
const paidBlockers = computed(() => {
    const d = detail.value;
    if (!d || d.saleWish !== 'PAID') return [];
    const out: string[] = [];
    if (!d.contractConsent?.current) out.push(t('emoticon.price.blockContract'));
    if (!d.payoutRegistered) out.push(t('emoticon.price.blockPayout'));
    return out;
});

function approve(specErrors: number): void {
    if (!detail.value || working.value || !canReview.value) return;
    approveSpecErrors.value = specErrors;
    approveError.value = '';
    approveFieldErrors.value = {};
    approveOpen.value = true;
}

async function submitApprove(body: { price: number }): Promise<void> {
    const target = detail.value;
    if (!target || working.value) return;
    working.value = 'approve';
    approveError.value = '';
    approveFieldErrors.value = {};
    try {
        await api.approve(target.id, { price: body.price });
        approveOpen.value = false;
        toast.success(body.price > 0 ? t('emoticon.approvedPaid', { name: target.packName, price: body.price.toLocaleString(locale.value) }) : t('emoticon.approved', { name: target.packName }));
        drawerOpen.value = false;
        void load();
    } catch (e) {
        const error = toApiError(e);
        if (handleGone(error)) approveOpen.value = false;
        else if (error.code === 'VALIDATION' && error.fields) approveFieldErrors.value = error.fields;
        else approveError.value = `${t('emoticon.actionFailed')} — ${error.message}`;
    } finally {
        working.value = null;
    }
}

// 반려 — 자주 쓰는 사유(누르면 문구가 채워진다)
const rejectOpen = ref(false);
const rejectError = ref('');
const rejectFieldError = ref('');
const rejectPresets = computed<EmoticonReasonPreset[]>(() =>
    UtilEmoticon.REJECT_PRESETS.map((code) => ({ code, label: t(`emoticon.rejectPreset.${code}`), text: t(`emoticon.rejectPresetText.${code}`) })),
);

function openReject(): void {
    if (!detail.value || working.value || !canReview.value) return;
    rejectError.value = '';
    rejectFieldError.value = '';
    rejectOpen.value = true;
}

async function submitReject(reason: string): Promise<void> {
    const target = detail.value;
    if (!target || working.value) return;
    working.value = 'reject';
    rejectError.value = '';
    rejectFieldError.value = '';
    try {
        await api.reject(target.id, reason);
        rejectOpen.value = false;
        drawerOpen.value = false;
        toast.success(t('emoticon.rejected', { name: target.packName }));
        void load();
    } catch (e) {
        const error = toApiError(e);
        if (handleGone(error)) rejectOpen.value = false;
        else if (error.field('reason')) rejectFieldError.value = error.field('reason')!;
        else rejectError.value = `${t('emoticon.actionFailed')} — ${error.message}`;
    } finally {
        working.value = null;
    }
}
</script>
