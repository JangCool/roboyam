<template>
    <div class="mx-auto flex max-w-2xl flex-col gap-4">
        <NuxtLink :to="UtilPointShop.PATH" class="inline-flex w-fit items-center gap-1 rounded text-sm text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="pointshop-back">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" />{{ term('POINT_SHOP') }}
        </NuxtLink>

        <EmptyState v-if="businessAccount" :title="t('point.businessTitle')" :description="t('point.businessText')" icon="pi pi-wallet" />

        <div v-else-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('pointShop.loading')" data-testid="pointshop-detail-loading">
            <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-48 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'notfound'" code="404" :title="t('pointShop.notFoundTitle')" :description="loadError" data-testid="pointshop-detail-notfound">
            <Button as="router-link" :to="UtilPointShop.PATH" :label="term('POINT_SHOP')" />
        </ErrorState>
        <ErrorState v-else-if="status === 'error' || !item" :title="t('pointShop.loadFailed')" :description="loadError" retry data-testid="pointshop-detail-error" @retry="load" />

        <template v-else>
            <!-- 머리: 이름 · 설명 · 가격 · 딱지 -->
            <section class="panel-card p-4" :aria-labelledby="titleId" data-testid="pointshop-detail">
                <h1 :id="titleId" class="m-0 text-lg font-bold leading-snug">{{ localizedName(item.name, item.nameEn) }}</h1>
                <p class="m-0 mt-1 whitespace-pre-line break-words text-sm leading-relaxed text-muted-color">{{ localizedName(item.description, item.descriptionEn) }}</p>
                <div class="mt-3 flex flex-wrap items-center gap-1.5">
                    <span class="mr-1 text-xl font-bold tabular-nums text-primary" data-testid="pointshop-price">{{ UtilPoint.points(item.price) }}</span>
                    <PointShopTag>{{ UtilPointShopMessages.usage(t, item) }}</PointShopTag>
                    <PointShopTag v-for="tag in tags" :key="tag.key" :tone="tag.tone">{{ tag.text }}</PointShopTag>
                </div>
            </section>

            <!-- 미리보기(필수 — commerce.md 7-3 · 20-1 L6). 닉네임 색: 내 닉네임을 두 바탕에 · 글 작성자 줄. 테두리: 내 이니셜 큰 것·작은 것 -->
            <section v-if="item.kind === 'NICKNAME_COLOR' && colorPair" class="panel-card p-4" :aria-labelledby="`${titleId}-preview`" data-testid="pointshop-preview-color">
                <h2 :id="`${titleId}-preview`" class="m-0 text-sm font-semibold">{{ t('pointShop.previewTitle') }}</h2>
                <div class="mt-3 grid grid-cols-2 gap-2">
                    <!-- 두 바탕은 `UtilColor` 의 카드 바탕(보정 기준)과 같은 값 — 지금 테마와 무관하게 나란히 보인다 -->
                    <div class="rounded-lg border border-surface px-3 py-3" :style="{ background: UtilColor.BG_LIGHT }" data-testid="pointshop-preview-light">
                        <p class="m-0 text-[0.6875rem] opacity-70" :style="{ color: UtilColor.onColor(UtilColor.BG_LIGHT) }">{{ t('pointShop.previewLight') }}</p>
                        <p class="m-0 mt-1 truncate text-base font-bold" :style="{ color: colorPair.light }">{{ nickname }}</p>
                    </div>
                    <div class="rounded-lg border border-surface px-3 py-3" :style="{ background: UtilColor.BG_DARK }" data-testid="pointshop-preview-dark">
                        <p class="m-0 text-[0.6875rem] opacity-70" :style="{ color: UtilColor.onColor(UtilColor.BG_DARK) }">{{ t('pointShop.previewDark') }}</p>
                        <p class="m-0 mt-1 truncate text-base font-bold" :style="{ color: colorPair.dark }">{{ nickname }}</p>
                    </div>
                </div>
                <p v-if="correctedNote" class="m-0 mt-2 text-xs text-muted-color" data-testid="pointshop-preview-corrected"><i class="pi pi-info-circle mr-1 text-[0.6875rem]" aria-hidden="true" />{{ correctedNote }}</p>

                <h3 class="m-0 mt-4 text-xs font-semibold text-muted-color">{{ t('pointShop.previewInPost') }}</h3>
                <div class="mt-2 flex flex-col gap-2 rounded-lg bg-surface-50 px-3 py-3 dark:bg-surface-950">
                    <!-- 글 보기 머리 모양(작은 동그라미 + 굵은 이름 + 등급 뱃지) · 목록·댓글 모양(색만) -->
                    <div class="flex items-center gap-2 text-xs text-muted-color">
                        <ProfileBorderAvatar :initial="initial" :src="myAvatar" size="xs" :border-style="mine?.profileBorder ?? null" />
                        <PostAuthorLabel :author="previewAuthor" preview strong class="text-sm" />
                        <span aria-hidden="true">·</span>
                        <span>{{ t('pointShop.previewJustNow') }}</span>
                    </div>
                    <div class="flex items-center gap-2 text-xs text-muted-color">
                        <PostAuthorLabel :author="previewAuthor" preview />
                        <span aria-hidden="true">·</span>
                        <span>{{ t('pointShop.previewComment') }}</span>
                    </div>
                </div>
            </section>

            <section v-else-if="item.kind === 'PROFILE_BORDER'" class="panel-card p-4" :aria-labelledby="`${titleId}-preview`" data-testid="pointshop-preview-border">
                <h2 :id="`${titleId}-preview`" class="m-0 text-sm font-semibold">{{ t('pointShop.previewTitle') }}</h2>
                <p v-if="borderDef" class="m-0 mt-0.5 text-xs text-muted-color">{{ t('pointShop.borderName', { name: t(borderDef.nameKey) }) }}</p>
                <div class="mt-3 flex flex-wrap items-center gap-x-6 gap-y-4">
                    <div class="flex flex-col items-center gap-1.5">
                        <ProfileBorderAvatar :initial="initial" :src="myAvatar" size="lg" surface="card" :border-style="item.borderStyle" />
                        <span class="text-[0.6875rem] text-muted-color">{{ t('pointShop.previewProfile') }}</span>
                    </div>
                    <div class="flex flex-col items-start gap-1.5">
                        <div class="flex items-center gap-2 text-xs text-muted-color">
                            <ProfileBorderAvatar :initial="initial" :src="myAvatar" size="xs" surface="card" :border-style="item.borderStyle" />
                            <PostAuthorLabel :author="previewAuthor" preview strong class="text-sm" />
                        </div>
                        <span class="text-[0.6875rem] text-muted-color">{{ t('pointShop.previewPostHead') }}</span>
                    </div>
                </div>
                <p class="m-0 mt-3 text-xs leading-relaxed text-muted-color">{{ t('pointShop.borderWhere') }}</p>
            </section>

            <!-- 프로필 아바타 — 내 장착 테두리·닉네임 색과 함께 크게(상점 아바타 탭과 같은 모양) -->
            <section v-else-if="item.kind === 'PROFILE_AVATAR'" class="panel-card p-4" :aria-labelledby="`${titleId}-preview`" data-testid="pointshop-preview-avatar">
                <h2 :id="`${titleId}-preview`" class="m-0 text-sm font-semibold">{{ t('pointShop.previewTitle') }}</h2>
                <div class="mt-3 flex flex-wrap items-center gap-x-6 gap-y-4">
                    <ProfileBorderAvatar :initial="initial" :src="item.avatarUrl" size="xl" surface="card" :border-style="mine?.profileBorder ?? null" />
                    <div class="flex items-center gap-2 text-xs text-muted-color">
                        <ProfileBorderAvatar :initial="initial" :src="item.avatarUrl" size="xs" surface="card" :border-style="mine?.profileBorder ?? null" />
                        <PostAuthorLabel :author="previewAuthor" preview strong class="text-sm" />
                    </div>
                </div>
                <p class="m-0 mt-3 text-xs leading-relaxed text-muted-color">{{ t('avatar.previewNote') }}</p>
            </section>

            <!-- 변경권 — 미리보기가 없어 내용을 다섯 줄로 늘 적는다(계약 「미리보기 없는 품목」) + 내 무료 변경 상태 -->
            <section v-else-if="item.kind === 'NICKNAME_CHANGE_TICKET'" class="panel-card p-4" :aria-labelledby="`${titleId}-about`" data-testid="pointshop-ticket-about">
                <h2 :id="`${titleId}-about`" class="m-0 text-sm font-semibold">{{ t('pointShop.ticketAboutTitle') }}</h2>
                <dl class="m-0 mt-2 flex flex-col gap-2 text-sm">
                    <div v-for="line in ticketLines" :key="line.key" class="grid grid-cols-[5.5rem_1fr] gap-2">
                        <dt class="text-muted-color">{{ line.label }}</dt>
                        <dd class="m-0 leading-relaxed">{{ line.text }}</dd>
                    </div>
                </dl>
                <p v-if="ticketStatus" class="m-0 mt-3 rounded-lg bg-surface-50 px-3 py-2 text-sm dark:bg-surface-950" data-testid="pointshop-ticket-status">{{ ticketStatus }}</p>
            </section>

            <!-- 구매 -->
            <section class="flex flex-col gap-2" data-testid="pointshop-buy">
                <Button
                    type="button"
                    :label="t('pointShop.buy', { points: UtilPoint.points(item.price) })"
                    icon="pi pi-shopping-cart"
                    :disabled="buyDisabled"
                    :aria-describedby="blockLine ? `${titleId}-block` : undefined"
                    class="w-full sm:w-auto sm:self-start"
                    data-testid="pointshop-buy-button"
                    @click="openDialog"
                />
                <p v-if="blockLine" :id="`${titleId}-block`" class="m-0 text-xs text-muted-color" data-testid="pointshop-block-reason">{{ blockLine }}</p>
                <NuxtLink v-if="hasMine" :to="mineLink.to" class="w-fit rounded text-sm text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="pointshop-go-mine">
                    {{ mineLink.label }}
                </NuxtLink>
            </section>
        </template>

        <!-- 구매 확인 — 계약 「구매 확인 대화상자」: 가격 · 지금 N P → 산 뒤 M P · 적립 포인트부터 · 기간 · 고지 동의(필수) · 잔액 부족이면 [구매] 꺼짐 + 이유 -->
        <PointShopPurchaseDialog
            v-if="item"
            v-model:visible="dialogOpen"
            v-model:agreed="agreed"
            :name="localizedName(item.name, item.nameEn)"
            :price="item.price"
            :balance="balance"
            :refund-days="refundDays"
            :period-line="periodLine"
            :buying="buying"
            :error="buyError"
            :agree-error="agreeError"
            :restriction="restriction"
            @buy="purchase"
        />
    </div>
</template>

<script setup lang="ts">
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';
import type { PointShopItemView } from '~/composables/pointshop/api/usePointShopApi.types';
import type { MyProfile } from '~/composables/profile/api/useProfileApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';
import UtilColor from '~/utils/util-color';
import UtilPoint from '~/utils/util-point';
import UtilPointShop from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

/**
 * 포인트 상점 품목 `/point-shop/{id}` — 로그인 필요. 목록 카드에서 온다.
 * - **미리보기 필수**(commerce.md 7-3): 닉네임 색은 내 닉네임을 그 색으로 라이트·다크 두 바탕에 나란히(대비 보정 `UtilColor.pair` — 보정되면 한 줄 안내) + 실제 작성자 줄 모양(등급 뱃지 포함, `PostAuthorLabel preview`).
 *   테두리는 내 이니셜 동그라미에 그 고리(큰 것 = 공개 프로필 머리, 작은 것 = 글 보기 머리). 변경권은 다섯 줄 설명 + 내 남은 무료 변경 여부(`GET /api/me/profile`).
 * - 구매: [구매] → 확인 대화상자(가격 · 지금 N P → 산 뒤 M P · 적립 포인트부터 · 기간제 문구 · 고지 동의 필수 · 잔액 부족이면 꺼짐) → `POST /api/point-shop/purchases`(멱등 키 — 대화상자 한 번에 하나).
 *   성공하면 토스트 + 「장착하러 가기」(색·테두리 → `/me/items`) / 「프로필에서 쓰기」(변경권 → `/settings/profile`).
 *   오류는 코드로 가른다: `403 MEMBER_RESTRICTED` → 기능 제한 안내(`FeatureRestrictionNotice`) · `400 VALIDATION fields.agreeRefundNotice` → 동의 칸 아래 · `429` → 서버 문구 + 기다릴 시간 ·
 *   `409 POINT_SHOP_*`·`POINT_INSUFFICIENT` → 서버 문구 그대로 + 품목을 다시 읽는다 · 그 밖은 서버 문구 그대로.
 * - 잔액·환불 날 수는 목록 API(`balance`·`refundDays`)에서 읽는다 — 상세 응답(`PointShopItemView`)에는 없다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { term } = useSiteTerms();
const route = useRoute();
const router = useRouter();
const api = usePointShopApi();
const profileApi = useProfileApi();
const avatarApi = useAvatarApi();
const { confirm } = useConfirmDialog();
const toast = useAppToast();
const time = useDisplayTime();
const { user } = useAuth();
const { localizedName } = useLocalizedName();
const decoration = useMyDecoration();
const decorationSync = useMyDecorationSync();
const points = usePointSummary();
const titleId = useId();

const item = ref<PointShopItemView | null>(null);
useSeoMeta({ title: () => (item.value ? localizedName(item.value.name, item.value.nameEn) : term('POINT_SHOP')), robots: 'noindex' });

const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));
const id = computed(() => String(route.params.id ?? ''));
const nickname = computed(() => user.value?.nickname ?? '');
const initial = computed(() => UtilPointShop.initial(nickname.value));

const status = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const loadError = ref('');
const balance = ref(0);
const refundDays = ref(7);
/** 변경권 상세의 「무료 변경이 남았나」 — 못 받으면 null(그 줄만 빠진다) */
const profile = ref<MyProfile | null>(null);
let seq = 0;

async function load(quiet = false) {
    const mine = ++seq;
    if (!quiet) {
        status.value = 'loading';
        loadError.value = '';
    }
    try {
        const [detail, list] = await Promise.all([api.get(id.value), api.list()]);
        if (mine !== seq) return;
        item.value = detail;
        balance.value = list.balance;
        refundDays.value = list.refundDays;
        status.value = 'ready';
        if (detail.kind === 'NICKNAME_CHANGE_TICKET') {
            profile.value = await profileApi.me().catch(() => null);
        }
    } catch (e) {
        if (mine !== seq || quiet) return;
        loadError.value = UtilPointShopMessages.error(t, time.value, e);
        status.value = e instanceof ApiError && e.status === 404 ? 'notfound' : 'error';
    }
}

watch(
    () => [user.value?.id ?? null, id.value] as const,
    ([uid]) => {
        if (!uid || businessAccount.value) return;
        void load();
        void decorationSync.ensure();
    },
    { immediate: true },
);
onBeforeUnmount(() => seq++);

const tags = computed(() => (item.value ? UtilPointShopMessages.tags(t, time.value, item.value) : []));

/* ── 미리보기 ── */
/** 지금 내 장착(작성자 표시와 같은 규칙) — 색 미리보기의 작은 동그라미, 테두리 미리보기의 이름 색 */
const mine = computed(() => decoration.mine());
/** 내 아바타 — 테두리·색 미리보기의 동그라미 */
const myAvatar = computed(() => decoration.myAvatarUrl());
const borderDef = computed(() => (item.value?.kind === 'PROFILE_BORDER' ? UtilPointShop.border(item.value.borderStyle) : null));
const colorPair = computed(() => (item.value?.kind === 'NICKNAME_COLOR' ? UtilColor.pair(item.value.color) : null));
const correctedNote = computed(() => {
    const pair = colorPair.value;
    if (!pair) return '';
    if (pair.correctedLight && pair.correctedDark) return t('pointShop.previewCorrectedBoth');
    if (pair.correctedLight) return t('pointShop.previewCorrectedLight');
    if (pair.correctedDark) return t('pointShop.previewCorrectedDark');
    return '';
});
/** 미리보기 작성자 — 내 닉네임·등급 + 이 품목을 장착한 모습 */
const previewAuthor = computed<PostAuthor>(() => {
    const me = user.value;
    const it = item.value;
    return {
        kind: 'MEMBER',
        memberId: me?.id ?? null,
        name: me?.nickname ?? '',
        grade: me?.role === 'USER' ? me.grade : null,
        gradeLabel: me?.gradeLabel ?? null,
        role: 'USER',
        ipMasked: null,
        decoration: {
            nicknameColor: it?.kind === 'NICKNAME_COLOR' ? it.color : (mine.value?.nicknameColor ?? null),
            profileBorder: it?.kind === 'PROFILE_BORDER' ? it.borderStyle : (mine.value?.profileBorder ?? null),
        },
    };
});

/* ── 변경권 설명 ── */
const ticketLines = computed(() => [
    { key: 'what', label: t('pointShop.ticketWhatLabel'), text: t('pointShop.ticketWhat') },
    { key: 'when', label: t('pointShop.ticketWhenLabel'), text: t('pointShop.ticketWhen') },
    { key: 'where', label: t('pointShop.ticketWhereLabel'), text: t('pointShop.ticketWhere') },
    { key: 'period', label: t('pointShop.ticketPeriodLabel'), text: t('pointShop.ticketPeriod') },
    { key: 'refund', label: t('pointShop.ticketRefundLabel'), text: t('pointShop.ticketRefund', { days: refundDays.value }) },
]);
const ticketStatus = computed(() => {
    const change = profile.value?.nicknameChange;
    if (!change) return '';
    if (change.allowed) return t('pointShop.ticketFreeLeft');
    return t('pointShop.ticketFreeUsed', { count: change.ticketCount ?? 0 });
});

/* ── 살 수 있나 ── */
const blockReason = computed(() => (item.value ? UtilPointShop.blockReason(item.value, balance.value) : null));
/** 포인트 모자람은 대화상자에서 계산과 함께 알린다 — [구매]는 켜 둔다 */
const buyDisabled = computed(() => blockReason.value !== null && blockReason.value !== 'INSUFFICIENT');
const blockLine = computed(() => (item.value && blockReason.value ? UtilPointShopMessages.blocked(t, time.value, item.value, blockReason.value, balance.value) : ''));
const insufficient = computed(() => Boolean(item.value) && balance.value < (item.value?.price ?? 0));

const hasMine = computed(() => {
    const m = item.value?.mine;
    return Boolean(m && (m.owned || m.count > 0 || m.periodEndsAt));
});
const mineLink = computed(() => {
    if (item.value?.kind === 'NICKNAME_CHANGE_TICKET') return { to: '/settings/profile', label: t('pointShop.useInProfile') };
    if (item.value?.kind === 'PROFILE_AVATAR') return { to: '/settings/profile', label: t('avatar.useInProfile') };
    return { to: UtilPointShop.MY_ITEMS_PATH, label: t('pointShop.goEquip') };
});

/* ── 구매 확인(`PointShopPurchaseDialog` + `usePointShopPurchase` — 상점 아바타 탭과 같다) ── */
const buyFlow = usePointShopPurchase();
const { open: dialogOpen, agreed, buying, error: buyError, agreeError, restriction } = buyFlow;

const periodLine = computed(() => {
    const it = item.value;
    if (!it || it.usageType !== 'PERIOD') return '';
    const from = UtilPointShop.periodContinuesFrom(it);
    return from
        ? t('pointShop.confirmPeriodContinue', { days: it.durationDays ?? 0, date: UtilPointShopMessages.monthDay(t, time.value, from) })
        : t('pointShop.confirmPeriodFromNow', { days: it.durationDays ?? 0 });
});

function openDialog() {
    buyFlow.start();
}

/** 아바타를 산 뒤 「바로 프로필로 쓰기」 — 고르면 환불할 수 없다(방금 샀으니 한 번 묻는다) */
async function useAvatarNow(avatarId: string) {
    if (!(await confirm({ title: t('avatar.useConfirmTitle'), message: t('avatar.useConfirmText'), confirmLabel: t('avatar.useNow') }))) return;
    try {
        const profile = await avatarApi.select({ avatarId });
        decoration.applyAvatar(profile.avatarUrl);
        toast.success(t('avatar.saved'));
    } catch (e) {
        toast.error(UtilPointShopMessages.error(t, time.value, e), 8000);
    }
}

async function purchase() {
    const it = item.value;
    if (!it) return;
    // 409 — 화면이 본 판매·재고·보유·잔액이 낡았다. 다시 읽어 딱지·단추를 맞춘다(대화상자의 문구는 그대로)
    const result = await buyFlow.buy(it.id, () => void load(true));
    if (!result) return;
    item.value = result.item;
    balance.value = result.balance;
    // 헤더·포인트 화면의 잔액도 맞춘다
    void points.load();
    const name = localizedName(result.item.name, result.item.nameEn);
    const avatarId = result.item.avatarId;
    const action =
        result.item.kind === 'NICKNAME_CHANGE_TICKET'
            ? { label: t('pointShop.useInProfile'), run: () => void router.push('/settings/profile') }
            : result.item.kind === 'PROFILE_AVATAR' && avatarId
              ? { label: t('avatar.useNow'), run: () => void useAvatarNow(avatarId) }
              : { label: t('pointShop.goEquip'), run: () => void router.push(UtilPointShop.MY_ITEMS_PATH) };
    toast.show(t('pointShop.bought', { name }), { tone: 'success', life: 6000, action });
}
</script>
