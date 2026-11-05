<template>
    <!-- 내 아이템(`/me/items`)과 같은 틀 — 바깥 화면 카드 하나에 머리(제목 + 상점 단추) · 구획마다 panel-card · 구획 안은 줄(머리카락 선으로 나눈다) -->
    <div class="content-card flex flex-col gap-4">
        <header class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-lg font-bold">{{ t('emoticon.myTitle') }}</h1>
            <Button as="router-link" :to="UtilEmoticon.PATH" :label="t('emoticon.shopTitle')" icon="pi pi-shopping-bag" size="small" severity="secondary" variant="outlined" data-testid="my-emoticons-shop" />
        </header>

        <!-- 가진 팩 -->
        <div v-if="packs === undefined" class="flex flex-col gap-4" role="status" :aria-label="t('emoticon.loading')" data-testid="my-emoticons-loading">
            <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 3" :key="n" class="h-16 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="error && !packs?.length" :title="t('emoticon.myLoadFailed')" retry data-testid="my-emoticons-error" @retry="mine.reload()" />
        <EmptyState v-else-if="!packs || packs.length === 0" class="panel-card" :title="t('emoticon.myEmpty')" :description="t('emoticon.myEmptyText')" icon="pi pi-face-smile" data-testid="my-emoticons-empty">
            <Button as="router-link" :to="UtilEmoticon.PATH" :label="t('emoticon.shopTitle')" />
        </EmptyState>

        <section v-else class="panel-card" :aria-label="t('emoticon.myTitle')">
            <ul class="m-0 list-none p-0" data-testid="my-emoticons-list">
                <li v-for="pack in packs" :key="pack.id" class="border-t border-surface px-4 py-3 first:border-t-0" data-testid="my-emoticons-row">
                    <div class="flex flex-wrap items-center gap-3">
                        <EmoticonPackCover :src="pack.coverUrl" size="sm" />
                        <div class="min-w-0 flex-1">
                            <p class="m-0 flex flex-wrap items-center gap-1.5 text-sm font-semibold">
                                <NuxtLink :to="UtilEmoticon.packPath(pack.id)" class="min-w-0 break-words rounded hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ pack.name }}</NuxtLink>
                                <PointShopTag v-if="pack.mine" tone="success" data-testid="my-emoticons-mine">{{ t('emoticon.mineTag') }}</PointShopTag>
                                <PointShopTag v-else :tone="pack.purchaseId ? 'primary' : 'neutral'">{{ pack.purchaseId ? t('emoticon.boughtTag') : t('emoticon.free') }}</PointShopTag>
                            </p>
                            <p class="m-0 mt-0.5 text-xs text-muted-color">
                                {{ pack.creatorNickname ?? t('emoticon.official') }} · {{ t('emoticon.countTag', { count: pack.emoticons.length }) }} ·
                                <time :datetime="pack.acquiredAt">{{ t(pack.mine ? 'emoticon.publishedAt' : pack.purchaseId ? 'emoticon.boughtAt' : 'emoticon.acquiredAt', { date: time.listLabel(pack.acquiredAt) }) }}</time>
                            </p>
                            <p v-if="pack.suspended" class="m-0 mt-1 text-xs text-amber-700 dark:text-amber-400">{{ t('emoticon.suspendedOwned') }}</p>
                        </div>
                        <!-- 무료로 받은 팩만 목록에서 뺀다 — 산 팩은 아래 구매 내역에서 환불(조건이 맞을 때), 내가 만든 팩은 빼지 않는다(후속 결정 1) -->
                        <div v-if="!pack.purchaseId && !pack.mine" class="flex shrink-0 gap-2 max-sm:w-full max-sm:justify-end">
                            <Button
                                type="button"
                                :label="t('emoticon.removeFromList')"
                                size="small"
                                severity="secondary"
                                variant="text"
                                :loading="removing === pack.id"
                                :disabled="removing !== null"
                                data-testid="my-emoticons-remove"
                                @click="remove(pack.id, pack.name)"
                            />
                        </div>
                    </div>
                    <!-- 미리 몇 개 — 고르기 창과 같은 64px(모바일 48px) -->
                    <div class="mt-2 flex flex-wrap gap-1" aria-hidden="true">
                        <img v-for="e in pack.emoticons.slice(0, 8)" :key="e.id" :src="e.url" alt="" class="size-12 object-contain sm:size-16" loading="lazy" decoding="async" />
                    </div>
                </li>
            </ul>
        </section>

        <!-- 구매 내역 — 결제 금액·시각·환불 가능 기한, 조건이 맞으면 환불 -->
        <section class="panel-card" :aria-labelledby="`${uid}-purchases`" data-testid="my-emoticons-purchases">
            <h2 :id="`${uid}-purchases`" class="m-0 px-4 pt-4 text-sm font-semibold">{{ t('emoticon.purchasesTitle') }}</h2>
            <div v-if="purchaseStatus === 'loading'" class="m-4 h-16 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('emoticon.loading')" />
            <p v-else-if="purchaseStatus === 'error'" class="m-0 flex flex-wrap items-center gap-2 px-4 py-3 text-sm text-muted-color" role="alert">
                {{ t('emoticon.purchasesLoadFailed') }}
                <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="loadPurchases" />
            </p>
            <p v-else-if="purchases.length === 0" class="m-0 px-4 py-3 text-sm text-muted-color">{{ t('emoticon.purchasesEmpty') }}</p>
            <ul v-else class="m-0 mt-2 list-none p-0">
                <li v-for="p in purchases" :key="p.id" class="flex flex-wrap items-center gap-3 border-t border-surface px-4 py-3" :class="p.status === 'REFUNDED' ? 'opacity-70' : ''" data-testid="my-emoticons-purchase">
                    <EmoticonPackCover :src="p.coverUrl" size="sm" />
                    <div class="min-w-0 flex-1">
                        <p class="m-0 flex flex-wrap items-center gap-1.5 text-sm">
                            <span class="min-w-0 break-words font-semibold">{{ p.packName }}</span>
                            <span class="font-semibold tabular-nums">{{ UtilEmoticonMessages.won(t, p.amount) }}</span>
                            <PointShopTag v-if="p.status === 'REFUNDED'" tone="neutral">{{ t('emoticon.refundedTag') }}</PointShopTag>
                            <PointShopTag v-else-if="p.usedAt" tone="warn">{{ t('emoticon.usedTag') }}</PointShopTag>
                            <PointShopTag v-else-if="p.refundable" tone="success">{{ t('emoticon.refundableTag') }}</PointShopTag>
                        </p>
                        <p class="m-0 mt-0.5 text-xs text-muted-color">
                            <time :datetime="p.paidAt">{{ t('emoticon.paidAt', { date: time.full(p.paidAt) }) }}</time>
                            <template v-if="p.status === 'REFUNDED' && p.refundedAt"> · {{ t('emoticon.refundedAt', { date: time.full(p.refundedAt) }) }}</template>
                            <template v-else-if="p.usedAt"> · {{ t('emoticon.usedNoRefund') }}</template>
                            <template v-else-if="p.refundable"> · {{ t('emoticon.refundUntil', { date: time.full(p.refundUntil) }) }}</template>
                            <template v-else> · {{ t('emoticon.refundExpired') }}</template>
                        </p>
                        <!-- 판매자는 사이트(카카오 방식) · 작가는 따로 · 카드 영수증 -->
                        <p class="m-0 mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-muted-color" data-testid="my-emoticons-seller">
                            <span>{{ t('emoticon.sellerLabel') }} {{ p.sellerName ?? sellerFallback }}</span>
                            <span v-if="p.creatorNickname">{{ t('emoticon.creatorLabel') }} {{ p.creatorNickname }}</span>
                            <a
                                v-if="p.receiptUrl"
                                :href="p.receiptUrl"
                                target="_blank"
                                rel="noopener noreferrer"
                                class="inline-flex items-center gap-0.5 rounded text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                            >
                                <i class="pi pi-receipt text-[0.625rem]" aria-hidden="true" />{{ t('emoticon.receipt') }}<span class="sr-only">{{ t('emoticon.newTab') }}</span>
                            </a>
                        </p>
                    </div>
                    <!-- 환불 — 내 아이템의 환불 단추와 같은 모양(작은 글자 단추) -->
                    <div v-if="p.refundable" class="flex shrink-0 gap-2 max-sm:w-full max-sm:justify-end">
                        <Button
                            type="button"
                            :label="t('emoticon.refund')"
                            size="small"
                            severity="secondary"
                            variant="text"
                            :loading="refunding === p.id"
                            :disabled="refunding !== null"
                            data-testid="my-emoticons-refund"
                            @click="refund(p)"
                        />
                    </div>
                </li>
            </ul>
            <p class="m-0 border-t border-surface px-4 py-3 text-xs leading-relaxed text-muted-color">{{ t('emoticon.purchasesNote', { days: refundDays }) }}</p>
        </section>

        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('emoticon.myNote') }}</p>
        <p v-if="allPacks" class="m-0 inline-flex items-start gap-1.5 text-xs leading-relaxed text-muted-color" data-testid="my-emoticons-operator">
            <i class="pi pi-shield mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('emoticon.operatorAllPacksNote') }}
        </p>
    </div>
</template>

<script setup lang="ts">
import type { MyEmoticonPurchase } from '~/composables/emoticon/api/useMyEmoticonsApi.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';
import UtilIdempotency from '~/utils/util-idempotency';
import { ApiError } from '~/composables/common/useApiClient';

/**
 * 내 로보티콘 `/me/robotikon` — 로그인 필요.
 * - 가진 팩: 대표 그림·이름·작가·개수·받은(산) 날·미리 몇 개. **무료 팩만** 「목록에서 빼기」(확인창 — 다시 받을 수 있다, 계약 가정 11). 목록은 고르기 창과 같은 상태(`useMyEmoticons`).
 * - 구매 내역: 결제 금액·시각·환불 가능 기한·판매자(사이트 — 카카오 방식)·작가·카드 영수증 링크. 환불 기한 안 + 한 번도 쓰지 않았으면(서버 판정 `refundable`) 「환불」(확인 → 결제 취소, 가정 3),
 *   썼으면 「사용함 — 환불 불가」. 환불하면 그 팩은 고르기 창에서 사라진다(이미 쓴 글의 토큰은 그대로 보인다). 이름은 산 때의 이름, 대표 그림은 지금 상점 판의 그림.
 *   판매자 이름(`sellerName`)이 비어 오면(사업자 정보 미설정) 사이트 사업자 정보(`useCommerceInfo`)의 상호, 그것도 없으면 사이트 이름.
 * - 모두 실제 API(S1 가진 팩·빼기, S3 구매 내역·환불). 환불 요청 키는 구매마다 하나 — 네트워크·5xx 는 같은 키로 다시, 서버가 판정한 4xx(429 포함)면 다음은 새 키.
 *   산 팩은 빼기 단추가 없다(서버도 `409 EMOTICON_PACK_PAID`). 그 오류가 오면(그새 산 경우) 가진 팩을 다시 받는다.
 * - 내가 만든 팩(`mine` — 후속 결정 1, 사지 않아도 가진다): 「내가 만든 팩」 딱지 · 「상점에 나옴」 날짜 · 빼기 단추 없음(서버 `409 EMOTICON_PACK_OWNED` `reason: "CREATOR"`).
 * - 운영자 회원 계정(`allPacks`)이면 맨 아래에 「상점의 판매 중 팩을 모두 쓸 수 있어요」 한 줄(목록에는 실제로 가진 팩만).
 * - 모양은 내 아이템(`/me/items`)과 같다. 내 정보 메뉴의 「내 로보티콘」 줄(`useMeMenu`)이 오는 화면이라 그 틀(`meMenu`) 안에 둔다.
 */
definePageMeta({ middleware: 'auth', meMenu: true });
const { t } = useI18n();
useSeoMeta({ title: () => t('emoticon.myTitle'), robots: 'noindex' });

const uid = useId();
const mine = useMyEmoticons();
const { packs, allPacks, error } = mine;
const api = useMyEmoticonsApi();
const publicApi = useEmoticonApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const commerce = useCommerceInfo();
const { siteLabel } = useSiteName();
/** 서버가 판매자 이름을 주지 않았을 때 — 사이트 사업자 상호(칸이 다 찼을 때만), 없으면 사이트 이름(`EmoticonSellerInfo` 와 같은 순서) */
const sellerFallback = computed(() => {
    const info = commerce.info.value;
    return (info?.ready ? info.business?.companyName : '') || siteLabel.value;
});

const purchases = ref<MyEmoticonPurchase[]>([]);
const purchaseStatus = ref<'loading' | 'ready' | 'error'>('loading');
const refundDays = ref(7);

async function loadPurchases(): Promise<void> {
    purchaseStatus.value = 'loading';
    try {
        purchases.value = (await api.purchases()).items;
        purchaseStatus.value = 'ready';
    } catch {
        purchaseStatus.value = 'error';
    }
}

// 들어올 때마다 새로 받는다(다른 기기에서 받거나 산 팩, 고르기 창에서 쓴 것도 보이게)
onMounted(() => {
    void mine.reload();
    void loadPurchases();
    void commerce.ensureLoaded();
    void publicApi
        .rules()
        .then((r) => (refundDays.value = r.refundDays))
        .catch(() => undefined);
});

const removing = ref<string | null>(null);

async function remove(packId: string, name: string): Promise<void> {
    if (removing.value) return;
    const ok = await confirm({ title: t('emoticon.removeConfirmTitle', { name }), message: t('emoticon.removeConfirmText'), confirmLabel: t('emoticon.removeFromList'), tone: 'danger' });
    if (!ok) return;
    removing.value = packId;
    try {
        await mine.remove(packId);
        toast.success(t('emoticon.removed', { name }));
    } catch (e) {
        toast.error(UtilEmoticonMessages.error(t, e), 8000);
        // 산 팩은 뺄 수 없다(`409 EMOTICON_PACK_PAID` — 그새 산 경우) — 가진 팩·구매 내역을 다시 받아 단추를 맞춘다
        if (e instanceof ApiError && e.code === 'EMOTICON_PACK_PAID') void Promise.all([mine.reload(), loadPurchases()]);
        // 내가 만든 팩은 뺄 수 없다(`409 EMOTICON_PACK_OWNED` + `reason: "CREATOR"` — 화면이 받은 `mine` 이 낡았다) — 가진 팩을 다시 받아 단추를 숨긴다
        else if (e instanceof ApiError && e.code === 'EMOTICON_PACK_OWNED') void mine.reload();
    } finally {
        removing.value = null;
    }
}

const refunding = ref<string | null>(null);
/** 구매마다 환불 요청 키(성공하거나 서버가 판정한 오류가 오기 전까지 같은 키) */
const refundKeys = new Map<string, string>();

async function refund(p: MyEmoticonPurchase): Promise<void> {
    if (refunding.value) return;
    const ok = await confirm({
        title: t('emoticon.refundConfirmTitle', { name: p.packName }),
        message: t('emoticon.refundConfirmText', { amount: UtilEmoticonMessages.won(t, p.amount) }),
        confirmLabel: t('emoticon.refund'),
        tone: 'danger',
    });
    if (!ok) return;
    refunding.value = p.id;
    // 환불 한 번의 뜻 = 키 하나 — 네트워크가 끊겨 다시 눌러도 같은 키로 보내 두 번 취소하지 않는다(서버가 처음 결과를 돌려준다). 끝난 뒤에 버린다
    const key = refundKeys.get(p.id) ?? UtilIdempotency.newKey();
    refundKeys.set(p.id, key);
    try {
        await api.refund(p.id, key);
        refundKeys.delete(p.id);
        toast.success(t('emoticon.refunded', { name: p.packName, amount: UtilEmoticonMessages.won(t, p.amount) }), 6000);
        await Promise.all([loadPurchases(), mine.reload()]);
    } catch (e) {
        // 서버가 판정한 오류(4xx)면 그 뜻은 끝났다 — 다음 시도는 새 키. 네트워크·5xx 는 같은 키로 다시 보낸다
        if (e instanceof ApiError && e.status >= 400 && e.status < 500) refundKeys.delete(p.id);
        toast.error(UtilEmoticonMessages.error(t, e), 8000);
        // 그새 써서 환불할 수 없게 됐을 수 있다 — 다시 읽는다
        void loadPurchases();
    } finally {
        refunding.value = null;
    }
}
</script>
