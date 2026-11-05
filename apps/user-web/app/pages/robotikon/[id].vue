<template>
    <div class="mx-auto flex max-w-3xl flex-col gap-4">
        <NuxtLink :to="UtilEmoticon.PATH" class="inline-flex w-fit items-center gap-1 rounded text-sm text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="emoticon-detail-back">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" />{{ t('emoticon.shopTitle') }}
        </NuxtLink>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('emoticon.loading')" data-testid="emoticon-detail-loading">
            <div class="h-40 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-72 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'notfound'" code="404" :title="t('emoticon.notFoundTitle')" :description="loadError" data-testid="emoticon-detail-notfound">
            <Button as="router-link" :to="UtilEmoticon.PATH" :label="t('emoticon.shopTitle')" />
        </ErrorState>
        <ErrorState v-else-if="status === 'error' || !pack" :title="t('emoticon.loadFailed')" :description="loadError" retry data-testid="emoticon-detail-error" @retry="load" />

        <template v-else>
            <!-- 머리: 대표 그림 · 이름 · 작가 · 설명 · 태그 · 가격 · 받기/구매 -->
            <section class="flex flex-wrap gap-4 panel-card p-4" :aria-labelledby="titleId" data-testid="emoticon-detail">
                <EmoticonPackCover :src="pack.coverUrl" size="lg" />
                <div class="min-w-0 flex-1">
                    <h1 :id="titleId" class="m-0 text-lg font-bold leading-snug">{{ pack.name }}</h1>
                    <p class="m-0 mt-0.5 text-sm text-muted-color">{{ pack.creator ? t('emoticon.byCreator', { name: pack.creator.nickname }) : t('emoticon.official') }}</p>
                    <p v-if="pack.description" class="m-0 mt-2 whitespace-pre-line break-words text-sm leading-relaxed">{{ pack.description }}</p>
                    <ul v-if="pack.tags.length" class="m-0 mt-2 flex list-none flex-wrap gap-1 p-0" :aria-label="t('emoticon.tagsLabel')">
                        <li v-for="tag in pack.tags" :key="tag">
                            <NuxtLink :to="{ path: UtilEmoticon.PATH, query: { q: tag } }" class="rounded text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">#{{ tag }}</NuxtLink>
                        </li>
                    </ul>
                    <div class="mt-3 flex flex-wrap items-center gap-1.5">
                        <span class="mr-1 text-xl font-bold tabular-nums" :class="paid ? 'text-primary' : 'text-green-700 dark:text-green-400'" data-testid="emoticon-detail-price">{{ UtilEmoticonMessages.price(t, pack.price) }}</span>
                        <PointShopTag>{{ t('emoticon.countTag', { count: pack.count }) }}</PointShopTag>
                        <PointShopTag>{{ t('emoticon.usagePermanent') }}</PointShopTag>
                        <span class="text-xs tabular-nums text-muted-color">{{ t(paid ? 'emoticon.buyCount' : 'emoticon.acquireCount', { count: pack.acquireCount.toLocaleString() }) }}</span>
                    </div>

                    <!-- 받기 · 구매 -->
                    <div class="mt-4 flex flex-col gap-1.5" data-testid="emoticon-detail-acquire">
                        <div v-if="pack.saleState === 'SUSPENDED'" role="status" class="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
                            {{ pack.suspendKind === 'CREATOR_WITHDRAWN' ? t('emoticon.suspendedCreatorWithdrawn') : pack.acquired ? t('emoticon.suspendedOwned') : t('emoticon.suspendedText') }}
                        </div>
                        <template v-else-if="usable">
                            <p class="m-0 inline-flex items-center gap-1.5 text-sm font-semibold text-green-700 dark:text-green-400" role="status" data-testid="emoticon-detail-acquired">
                                <i :class="operatorHere ? 'pi pi-shield' : 'pi pi-check-circle'" aria-hidden="true" />{{ usableLine }}
                            </p>
                            <NuxtLink :to="UtilEmoticon.MY_PATH" class="w-fit rounded text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('emoticon.myTitle') }}</NuxtLink>
                        </template>
                        <Button v-else-if="!user" as="router-link" :to="UtilRedirect.toLogin(route.fullPath)" :label="paid ? t('emoticon.loginToBuy') : t('emoticon.loginToAcquire')" icon="pi pi-sign-in" class="w-full sm:w-fit" data-testid="emoticon-detail-login" />
                        <Button
                            v-else-if="paid && !reacquire"
                            type="button"
                            :label="t('emoticon.buyFor', { price: UtilEmoticonMessages.won(t, pack.price) })"
                            icon="pi pi-shopping-cart"
                            :loading="starting || pay.busy.value"
                            :disabled="pay.fakeOpen.value"
                            class="w-full sm:w-fit"
                            data-testid="emoticon-detail-buy"
                            @click="openBuy"
                        />
                        <Button v-else type="button" :label="t('emoticon.acquireFree')" icon="pi pi-download" :loading="acquiring" class="w-full sm:w-fit" data-testid="emoticon-detail-acquire-button" @click="acquire" />

                        <!-- 결제 결과 한 줄 — 창 닫기·실패·받지 않은 결제·완료 요청 실패 -->
                        <p v-if="!usable && pay.note.value" class="m-0 text-sm text-muted-color" role="status" data-testid="emoticon-detail-pay-note">{{ t(`emoticon.payNote.${pay.note.value}`) }}</p>
                        <Message v-if="buyNotice" severity="warn" variant="simple" size="small" role="alert" data-testid="emoticon-detail-buy-notice">{{ buyNotice }}</Message>
                        <Message v-if="rejectedLine" severity="warn" variant="simple" size="small" role="alert">{{ rejectedLine }}</Message>
                        <Message v-if="pay.completeError.value" severity="error" variant="simple" size="small" role="alert">{{ t('emoticon.payCompleteFailed', { message: pay.completeError.value }) }}</Message>
                        <Message v-if="acquireError" severity="error" variant="simple" size="small" role="alert">{{ acquireError }}</Message>
                        <p v-if="paid && !reacquire && !usable && pack.saleState === 'ON_SALE'" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('emoticon.refundRule', { days: refundDays }) }}</p>
                    </div>
                </div>
            </section>

            <!-- 로보티콘 전부 — 글·댓글과 같은 100px(모바일 80px), 움직이는 그림 그대로. 밝은·어두운 바탕을 바꿔 본다 -->
            <section class="panel-card p-4" :aria-labelledby="`${titleId}-grid`" data-testid="emoticon-detail-grid">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h2 :id="`${titleId}-grid`" class="m-0 text-sm font-semibold">{{ t('emoticon.previewTitle', { count: pack.count }) }}</h2>
                    <div role="group" :aria-label="t('emoticon.previewBackground')" class="flex gap-1" data-testid="emoticon-detail-bg">
                        <Button type="button" icon="pi pi-sun" :label="t('emoticon.bgLight')" size="small" :severity="dark ? 'secondary' : undefined" :variant="dark ? 'text' : 'outlined'" :aria-pressed="!dark" @click="dark = false" />
                        <Button type="button" icon="pi pi-moon" :label="t('emoticon.bgDark')" size="small" :severity="dark ? undefined : 'secondary'" :variant="dark ? 'outlined' : 'text'" :aria-pressed="dark" @click="dark = true" />
                    </div>
                </div>
                <ul class="m-0 mt-3 grid list-none grid-cols-[repeat(auto-fill,minmax(5.5rem,1fr))] gap-2 rounded-xl p-3 sm:grid-cols-[repeat(auto-fill,minmax(7rem,1fr))]" :style="{ background: dark ? BG_DARK : BG_LIGHT }">
                    <li v-for="e in pack.emoticons" :key="e.id" class="flex flex-col items-center gap-1">
                        <img :src="e.url" :alt="e.label" class="size-20 object-contain sm:size-[100px]" width="100" height="100" loading="lazy" decoding="async" />
                        <span class="max-w-full truncate text-[0.6875rem]" :style="{ color: dark ? '#cbd5e1' : '#475569' }">{{ e.label }}</span>
                    </li>
                </ul>
                <p class="m-0 mt-2 text-xs leading-relaxed text-muted-color">{{ t('emoticon.previewNote') }}</p>
            </section>

            <!-- 판매자 — 유료 팩은 사이트가 판매자(카카오 방식), 작가는 따로 -->
            <EmoticonSellerInfo v-if="paid" :business="sellerBusiness" :site-name="siteLabel" :creator-name="pack.creator?.nickname ?? ''" />
        </template>

        <!-- 구매 확인 — 팩 이름·가격·환불 규칙 동의(commerce.md 7-3 과 같은 규칙). [결제하기] → 본인인증 확인 → 결제 준비 → 결제창 -->
        <Dialog
            v-model:visible="buyOpen"
            modal
            :draggable="false"
            :closable="!starting"
            :header="t('emoticon.buyTitle')"
            :style="{ width: 'min(26rem, calc(100vw - 2rem))' }"
            data-testid="emoticon-buy-dialog"
        >
            <form v-if="pack" class="flex flex-col gap-4" novalidate @submit.prevent="startPurchase">
                <div class="flex items-center gap-3">
                    <EmoticonPackCover :src="pack.coverUrl" size="md" />
                    <div class="min-w-0">
                        <p class="m-0 truncate font-semibold">{{ pack.name }}</p>
                        <p class="m-0 mt-0.5 text-lg font-bold tabular-nums text-primary">{{ UtilEmoticonMessages.won(t, pack.price) }}</p>
                    </div>
                </div>
                <EmoticonSellerInfo :business="sellerBusiness" :site-name="siteLabel" :creator-name="pack.creator?.nickname ?? ''" compact />
                <ul class="m-0 flex flex-col gap-1 rounded-lg bg-surface-50 py-2.5 pl-8 pr-3 text-sm leading-relaxed dark:bg-surface-950">
                    <li>{{ t('emoticon.buyRuleUsed') }}</li>
                    <li>{{ t('emoticon.buyRuleRefund', { days: refundDays }) }}</li>
                    <li>{{ t('emoticon.buyRulePermanent') }}</li>
                    <li>{{ t('emoticon.buyRuleIdentity') }}</li>
                </ul>
                <div class="flex flex-col gap-0.5">
                    <div class="flex items-start gap-2">
                        <Checkbox v-model="buyAgreed" :input-id="`${titleId}-agree`" binary :invalid="Boolean(buyAgreeError)" class="mt-0.5" />
                        <label :for="`${titleId}-agree`" class="text-sm leading-relaxed">{{ t('emoticon.buyAgree', { days: refundDays }) }}</label>
                    </div>
                    <p v-if="buyAgreeError || buyAgreeServerError" role="alert" class="m-0 pl-7 text-xs text-red-600 dark:text-red-400">{{ buyAgreeError || buyAgreeServerError }}</p>
                </div>
                <Message v-if="buyError" severity="error" variant="simple" size="small" role="alert">{{ buyError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="starting" @click="buyOpen = false" />
                    <Button type="submit" :label="t('emoticon.payNow', { price: UtilEmoticonMessages.won(t, pack.price) })" icon="pi pi-credit-card" :loading="starting" data-testid="emoticon-buy-submit" />
                </div>
            </form>
        </Dialog>

        <PaymentFakeCheckoutDialog
            :visible="pay.fakeOpen.value"
            :checkout="pay.target.value?.checkout ?? null"
            :expires-at="pay.target.value?.expiresAt ?? ''"
            :busy="pay.fakeBusy.value"
            :error="pay.fakeError.value"
            @submit="pay.submitFake"
            @close="pay.closeFake"
        />
    </div>
</template>

<script setup lang="ts">
import type { EmoticonPackDetail, EmoticonPurchaseStart } from '~/composables/emoticon/api/useEmoticonApi.types';
import type { PaymentResult } from '~/composables/payment/api/usePaymentApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 로보티콘 팩 상세 `/robotikon/{id}` — 누구나 본다. 로보티콘 전부(움직이는 GIF 그대로, 글·댓글과 같은 100px), 이름표, 작가, 설명·태그, 미리보기 바탕(밝은·어두운) 바꾸기.
 * - 무료 팩: 「받기(무료)」 → 「받음 · 글·댓글에서 쓸 수 있어요」.
 * - 유료 팩(실결제, 포인트로는 사지 않는다 — 계약 「가격과 결제」): 「구매 2,500원」 → 구매 확인(이름·가격·환불 규칙 동의) → 본인인증 확인(`useIdentityGate`, 용도 `ROBOTIKON`, 만 19세 이상)
 *   → 결제 준비(`POST …/purchase` — 결제 모듈 주문 `ROBOTIKON_PACK`) → 공용 결제창(`useEmoticonCheckout` = `usePaymentCheckout`, 가짜 게이트웨이면 `PaymentFakeCheckoutDialog`)
 *   → 완료 요청 → 받아들인 결제면 「구매 완료 · 글·댓글에서 쓸 수 있어요」 + 보유. 창 닫기·실패·받지 않은 결제(자동 취소 — 결과의 `anomaly` 와 다시 읽은 팩으로 고른 로보티콘 전용 문구
 *   `UtilEmoticonMessages.payRejected`)는 한 줄로 알리고 다시 살 수 있다.
 * - 내가 만든 팩(후속 결정 1 — 내 로보티콘 `mine` 또는 작가가 나)은 구매·받기 대신 「내가 만든 팩 — 바로 쓸 수 있어요」. 결제 준비 `409 EMOTICON_PACK_OWNED` 의 최상위 `reason`
 *   `CREATOR`(내가 만든 팩)·`OPERATOR`(운영자 회원 계정 — 모든 팩)도 같은 자리에 「쓸 수 있어요」 한 줄로 바꾼다(받기로 다시 넣지 않는다).
 * - 운영자 회원 계정: 팩 상세 `operatorUsable`(판매 중 팩이면 가진 팩이어도 true)이면 구매·받기 대신 「운영자 계정은 판매 중인 모든 팩을 쓸 수 있어요」.
 *   `acquired` 는 「정말 가졌는가」 그대로 — 받기 응답의 `acquired: false` 를 「받음」으로 바꾸지 않는다.
 *   모바일 리디렉션은 이 화면으로 돌아와 `resumeRedirect()` 로 이어 간다.
 * - 결제 준비 오류(서버 S3): 동의 `400 fields.agreeRefundNotice` 는 동의 칸 아래, 그 밖은 서버 문구 한 줄. 404·판매 중단·무료가 됨·이미 가짐은 팩을 다시 읽어 화면을 맞춘다.
 *   `409 EMOTICON_PACK_OWNED` 인데 다시 읽은 팩이 `acquired: false` 면 무료일 때 받았다 뺀 회원이다 — 구매 대신 「받기」 단추를 보인다(서버가 받기로 다시 넣어 준다).
 *   본인인증 `403 IDENTITY_REQUIRED`·`IDENTITY_UNDERAGE` 는 화면의 인증 상태가 서버와 어긋난 것 — 상태를 새로 받아 다음 시도에 확인 대화상자가 뜨게 한다.
 * - 판매자 표시(카카오 방식): 유료 팩은 사이트가 판매자 — 상세 아래와 구매 확인창에 사이트 사업자 정보(`useCommerceInfo`)와 「작가」를 따로 보인다.
 * - 가지게 되면 내 로보티콘 상태(`useMyEmoticons`)를 다시 받아 고르기 창에 바로 보인다. 판매 중단 팩은 가진 사람만 본다. 받기·유료 구매 모두 실제 API(S1·S3).
 */
const { t } = useI18n();
const route = useRoute();
const api = useEmoticonApi();
const mine = useMyEmoticons();
const catalog = useEmoticonCatalog();
const toast = useAppToast();
const gate = useIdentityGate();
const identity = useIdentity();
const { user } = useAuth();
const titleId = useId();

/** 미리보기 바탕 — 지금 테마와 무관하게 두 바탕을 고른다 */
const BG_LIGHT = '#ffffff';
const BG_DARK = '#0f172a';
const dark = ref(false);

const id = computed(() => String(route.params.id ?? ''));
const pack = ref<EmoticonPackDetail | null>(null);
const status = ref<'loading' | 'ready' | 'error' | 'notfound'>('loading');
const loadError = ref('');
const refundDays = ref(7);
let seq = 0;

const paid = computed(() => UtilEmoticon.paid(pack.value?.price ?? 0));
/**
 * 가진 팩을 산 것인가 — 내 로보티콘의 `purchaseId` 로 정한다(유료일 때 산 팩이 무료가 됐어도 「구매 완료」, 무료일 때 받은 팩이 유료가 됐으면 「받음」).
 * 내 로보티콘을 아직 못 받았으면 지금 가격으로 짐작한다
 */
const bought = computed(() => {
    const own = mine.packs.value?.find((p) => p.id === pack.value?.id);
    return own ? Boolean(own.purchaseId) : paid.value;
});
/** 무료일 때 받았다 뺀 유료 팩 — 결제 준비가 `409 EMOTICON_PACK_OWNED` 였는데 다시 읽은 팩이 `acquired: false`. 구매 대신 받기 단추(이 팩·이 회원에서만) */
const reacquireFor = ref<string | null>(null);
const reacquire = computed(() => reacquireFor.value !== null && reacquireFor.value === pack.value?.id);
/** 결제 준비가 막혀 구매 확인창을 다시 열지 않는 경우의 한 줄(서버 문구 — 판매 중단·무료가 됨·이미 가짐·유료 판매 꺼짐·본인인증) */
const buyNotice = ref('');
/** 결제 준비가 `409 EMOTICON_PACK_OWNED` + `reason` 으로 알려 준 「사지 않아도 쓰는」 팩 — 작가 자기 팩(`CREATOR`) · 운영자 회원 계정(`OPERATOR`). 이 팩·이 회원에서만 */
const ownedReason = ref<{ packId: string; reason: 'CREATOR' | 'OPERATOR' } | null>(null);
/**
 * 내가 만든 팩(후속 결정 1 — 사지 않아도 가진다). 상점에 나온 팩(`acquired`)이면서 내 로보티콘 줄의 `mine` 이거나 작가가 나다(내 로보티콘을 아직 못 받았을 때).
 * 상점에 나오기 전(임시·심사 중·반려)은 가진 것이 아니다 — 서버가 `acquired: false` 를 준다
 */
const createdByMe = computed(() => {
    const current = pack.value;
    if (!current) return false;
    if (ownedReason.value?.packId === current.id && ownedReason.value.reason === 'CREATOR') return true;
    if (!current.acquired) return false;
    const own = mine.packs.value?.find((p) => p.id === current.id);
    return own ? own.mine : Boolean(user.value && current.creator?.memberId === user.value.id);
});
/**
 * 운영자 회원 계정이라 쓰는 팩 — 팩 상세의 `operatorUsable`(운영자 회원 계정 + 판매 중, 가진 팩이어도 true)로 구매·받기 단추를 미리 감춘다.
 * 그 칸이 낡았을 때의 그물: 결제 준비의 `409 EMOTICON_PACK_OWNED` + `reason: "OPERATOR"`
 */
const operatorHere = computed(() => {
    const current = pack.value;
    if (!current) return false;
    return current.operatorUsable === true || (ownedReason.value?.packId === current.id && ownedReason.value.reason === 'OPERATOR');
});
/** 이 팩을 지금 쓸 수 있다 — 가짐 · 내가 만든 팩 · 운영자 회원 계정 */
const usable = computed(() => Boolean(pack.value?.acquired) || createdByMe.value || operatorHere.value);
/** 쓸 수 있을 때의 한 줄 — 운영자 회원 계정이면 그 안내가 먼저다(가진 팩·내가 만든 팩이어도) */
const usableLine = computed(() => {
    if (operatorHere.value) return t('emoticon.operatorUsable');
    if (createdByMe.value) return t('emoticon.mineUsable');
    return bought.value ? t('emoticon.boughtUsable') : t('emoticon.acquiredUsable');
});

/** 판매자 표시 — 사이트 사업자 정보(공구·푸터와 같은 `useCommerceInfo`, 칸이 다 찼을 때만). 없으면 사이트 이름만 */
const commerce = useCommerceInfo();
const { siteLabel } = useSiteName();
const sellerBusiness = computed(() => {
    const info = commerce.info.value;
    return info?.ready ? info.business : null;
});

useSeoMeta({ title: () => pack.value?.name ?? t('emoticon.shopTitle') });

async function load(): Promise<void> {
    const mineSeq = ++seq;
    status.value = 'loading';
    loadError.value = '';
    try {
        const result = await api.pack(id.value);
        if (mineSeq !== seq) return;
        pack.value = result;
        status.value = 'ready';
    } catch (e) {
        if (mineSeq !== seq) return;
        loadError.value = UtilEmoticonMessages.error(t, e);
        status.value = e instanceof ApiError && e.status === 404 ? 'notfound' : 'error';
    }
}

/** 보던 화면을 그대로 두고 팩만 다시 읽는다(받지 못하면 그대로) — 결제 결과·결제 준비 거절 뒤 */
async function refresh(): Promise<EmoticonPackDetail | null> {
    const mineSeq = seq;
    try {
        const fresh = await api.pack(id.value);
        if (mineSeq !== seq) return null;
        pack.value = fresh;
        return fresh;
    } catch {
        return null;
    }
}

watch(
    [id, () => user.value?.id ?? null],
    () => {
        reacquireFor.value = null;
        ownedReason.value = null;
        buyNotice.value = '';
        void load();
    },
    { immediate: true },
);
onBeforeUnmount(() => seq++);
onMounted(() => {
    // 환불 날 수는 설정 값 — 못 받으면 기본 7일 그대로 둔다
    void api
        .rules()
        .then((r) => (refundDays.value = r.refundDays))
        .catch(() => undefined);
    // 모바일 결제사 화면에서 돌아왔다면 이어 간다
    void pay.resumeRedirect();
    void commerce.ensureLoaded();
    void mine.ensureLoaded();
});

/* ── 무료 팩 받기 ── */
const acquiring = ref(false);
const acquireError = ref('');

async function acquire(): Promise<void> {
    const current = pack.value;
    if (!current || acquiring.value) return;
    acquiring.value = true;
    acquireError.value = '';
    try {
        const result = await api.acquire(current.id);
        // `acquired` 는 「정말 가졌는가」 — 운영자 회원 계정은 200 인데 false 일 수 있다(아무것도 바뀌지 않는다). 서버 값을 그대로 둔다
        pack.value = { ...current, acquired: result.acquired, acquireCount: result.acquireCount };
        reacquireFor.value = null;
        buyNotice.value = '';
        // 고르기 창(댓글·글 에디터)에 바로 보이게 내 팩을 다시 받는다. 앱을 연 뒤 상점에 나온 팩이면 카탈로그에 없을 수 있어 사본도 다시 묻는다(바뀌지 않았으면 304)
        await Promise.all([mine.reload(), catalog.refresh()]);
        if (result.acquired) toast.success(t('emoticon.acquiredToast', { name: current.name }));
        else void refresh(); // 받지 않았다(운영자 회원 계정) — 팩을 다시 읽어 `operatorUsable` 안내로 맞춘다
    } catch (e) {
        acquireError.value = UtilEmoticonMessages.error(t, e);
    } finally {
        acquiring.value = false;
    }
}

/* ── 유료 팩 구매 ── */
const buyOpen = ref(false);
const buyAgreed = ref(false);
const buyTried = ref(false);
const buyError = ref('');
/** 서버가 동의 칸을 거절했다(`400 VALIDATION fields.agreeRefundNotice`) — 서버 문구 그대로 */
const buyAgreeServerError = ref('');
const starting = ref(false);
/** 받지 않은 결제(금액 불일치·늦은 결제·도메인 거절 — 결제 모듈이 전액 자동 취소) */
const rejected = ref<PaymentResult | null>(null);
const buyAgreeError = computed(() => (buyTried.value && !buyAgreed.value ? t('emoticon.buyAgreeRequired') : ''));
/** 받지 않은 결제의 한 줄 — 로보티콘 전용 문구(자동 취소 + 까닭 한 줄, 까닭은 다시 읽은 팩 상태로 좁힌다 · 까닭이 없으면 「아직 확정되지 않음」) */
const rejectedLine = computed(() => {
    const result = rejected.value;
    if (!result) return '';
    return UtilEmoticonMessages.payRejected(t, result.anomaly, pack.value);
});

const pay = useEmoticonCheckout(onPaid);

function openBuy(): void {
    buyAgreed.value = false;
    buyTried.value = false;
    buyError.value = '';
    buyAgreeServerError.value = '';
    buyNotice.value = '';
    buyOpen.value = true;
}

/** 팩 상태가 바뀌어 구매할 수 없는 거절 — 확인창을 다시 열지 않고 팩을 다시 읽는다 */
const STATE_CODES = new Set(['EMOTICON_PACK_SUSPENDED', 'EMOTICON_PACK_FREE', 'EMOTICON_PAID_SALES_CLOSED', 'EMOTICON_PACK_OWNED']);
/** 회원 자격·본인인증 거절 — 확인창을 다시 열지 않는다(다시 눌러도 같은 답) */
const MEMBER_CODES = new Set(['ACCOUNT_RESTRICTED', 'EMAIL_NOT_VERIFIED', 'IDENTITY_REQUIRED', 'IDENTITY_UNDERAGE']);

/**
 * 결제 준비 거절 — 서버 차례: 401 → `403 ACCOUNT_RESTRICTED`·`EMAIL_NOT_VERIFIED` → `403 IDENTITY_REQUIRED`·`IDENTITY_UNDERAGE` → `400 fields.agreeRefundNotice`
 * → 404 → `409 EMOTICON_PACK_SUSPENDED`·`EMOTICON_PACK_FREE`·`EMOTICON_PAID_SALES_CLOSED`·`EMOTICON_PACK_OWNED`. 문구는 서버 문구 그대로.
 */
async function onPurchaseError(e: unknown, packId: string): Promise<void> {
    const message = UtilEmoticonMessages.error(t, e);
    if (!(e instanceof ApiError)) {
        buyError.value = message;
        buyOpen.value = true;
        return;
    }
    if (e.status === 404) {
        void load();
        return;
    }
    if (e.code === 'VALIDATION' && e.field('agreeRefundNotice')) {
        buyAgreed.value = false;
        buyAgreeServerError.value = e.field('agreeRefundNotice') ?? '';
        buyOpen.value = true;
        return;
    }
    if (MEMBER_CODES.has(e.code)) {
        buyNotice.value = message;
        // 화면의 인증 상태가 서버와 어긋났다 — 새로 받아 다음 시도에 확인 대화상자가 뜨게 한다
        if (e.code === 'IDENTITY_REQUIRED' || e.code === 'IDENTITY_UNDERAGE') void identity.load();
        return;
    }
    if (e.code === 'EMOTICON_PACK_OWNED') {
        // 사지 않아도 쓰는 팩(후속 결정 1 · 운영자 회원 계정) — 구매 단추 대신 「쓸 수 있어요」 한 줄. 받기로 다시 넣지 않는다
        const reason = e.extraText('reason');
        if (reason === 'CREATOR' || reason === 'OPERATOR') {
            ownedReason.value = { packId, reason };
            buyNotice.value = '';
            await Promise.all([refresh(), mine.reload()]);
            return;
        }
    }
    if (STATE_CODES.has(e.code)) {
        buyNotice.value = message;
        const fresh = await refresh();
        if (e.code === 'EMOTICON_PACK_OWNED') {
            // 다시 읽어도 가지지 않았다 = 무료일 때 받았다 뺀 회원 — 구매 대신 받기로 다시 넣는다
            if (fresh && !fresh.acquired) reacquireFor.value = packId;
            else void mine.reload();
        }
        return;
    }
    buyError.value = message;
    buyOpen.value = true;
}

async function startPurchase(): Promise<void> {
    const current = pack.value;
    buyTried.value = true;
    if (!current || starting.value || !buyAgreed.value) return;
    buyError.value = '';
    buyAgreeServerError.value = '';
    buyNotice.value = '';
    starting.value = true;
    try {
        // 현금 결제는 본인인증 필수(commerce.md D3) — 미인증이면 안내 대화상자가 뜬다
        buyOpen.value = false;
        if (!(await gate.ensure({ purpose: 'ROBOTIKON' }))) return;
        rejected.value = null;
        const target: EmoticonPurchaseStart = await api.purchase(current.id, { agreeRefundNotice: true });
        pay.open(target);
    } catch (e) {
        await onPurchaseError(e, current.id);
    } finally {
        starting.value = false;
    }
}

/** 완료 요청의 결과 — 받아들였으면 보유, 아니면 까닭 한 줄. 어느 쪽이든 팩은 서버가 정한 값으로 다시 받는다(그새 무료가 됐거나 가졌을 수 있다) */
async function onPaid(result: PaymentResult): Promise<void> {
    if (!result.accepted) {
        rejected.value = result;
        await Promise.all([refresh(), mine.reload()]);
        return;
    }
    const name = pack.value?.name ?? '';
    // 보유·산 수는 서버가 정한 값으로 다시 받는다(화면에서 더하지 않는다). 받지 못하면 보던 화면 그대로
    await Promise.all([refresh(), mine.reload()]);
    toast.success(t('emoticon.boughtToast', { name }), 6000);
}
</script>
