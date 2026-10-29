<template>
    <div class="mx-auto flex max-w-3xl flex-col gap-4">
        <h1 class="text-lg font-bold">{{ term('POINT_SHOP') }}</h1>

        <!-- 사업자 전용 계정(H-2) — 포인트가 없다 -->
        <EmptyState v-if="businessAccount" :title="t('point.businessTitle')" :description="t('point.businessText')" icon="pi pi-wallet" data-testid="pointshop-business" />

        <template v-else>
            <!-- 내 포인트 · 내 아이템 — 포인트는 그 탭 응답의 `balance`(품목 목록 또는 아바타 카탈로그 — 충전을 열기 전이라 하나, D13) -->
            <section class="flex flex-wrap items-center justify-between gap-3 panel-card p-4" :aria-label="t('pointShop.myPoints')" data-testid="pointshop-balance">
                <div>
                    <p class="m-0 text-xs font-semibold text-muted-color">{{ t('pointShop.myPoints') }}</p>
                    <p v-if="balance !== null" class="m-0 mt-0.5 text-2xl font-bold tabular-nums">{{ UtilPoint.points(balance) }}</p>
                    <div v-else class="mt-1 h-8 w-28 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
                <Button as="router-link" :to="UtilPointShop.MY_ITEMS_PATH" :label="t('pointShop.myItems')" icon="pi pi-box" size="small" severity="secondary" variant="outlined" data-testid="pointshop-my-items" />
            </section>

            <!-- 종류 거르기 — 주소의 `?kind=` 가 상태다 -->
            <SelectButton
                :model-value="kind"
                :options="kindOptions"
                option-label="label"
                option-value="value"
                :allow-empty="false"
                size="small"
                class="flex-wrap"
                :aria-label="t('pointShop.kindFilter')"
                data-testid="pointshop-kind"
                @update:model-value="setKind"
            />

            <div v-if="status === 'loading'" class="grid grid-cols-2 gap-3 sm:grid-cols-3" role="status" :aria-label="t('pointShop.loading')" data-testid="pointshop-loading">
                <div v-for="n in 6" :key="n" class="h-48 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>
            <ErrorState v-else-if="status === 'error'" :title="t('pointShop.loadFailed')" :description="loadError" retry data-testid="pointshop-error" @retry="load" />

            <!-- 아바타 탭 — 유료 306종(`GET /api/avatars`). 카테고리 칩 + 격자, 누르면 위에 크게 미리보기(내 닉네임·장착 테두리와 함께) + 구매 -->
            <template v-else-if="kind === 'PROFILE_AVATAR'">
                <EmptyState v-if="!catalog || shopItems.length === 0" :title="t('pointShop.emptyFiltered')" :description="t('pointShop.emptyText')" icon="pi pi-user" data-testid="pointshop-avatar-empty" />
                <template v-else>
                    <section
                        v-if="picked"
                        class="flex flex-wrap items-center gap-4 panel-card p-4"
                        :aria-labelledby="`${uid}-picked`"
                        data-testid="pointshop-avatar-preview"
                    >
                        <ProfileBorderAvatar :initial="initial" :src="picked.url" :alt="picked.name" size="xl" surface="card" :border-style="mine?.profileBorder ?? null" />
                        <div class="min-w-0 flex-1">
                            <h2 :id="`${uid}-picked`" class="m-0 text-base font-bold">{{ picked.name }}</h2>
                            <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t(UtilAvatar.categoryKey(picked.category)) }}</p>
                            <div class="mt-2 flex items-center gap-2 text-xs text-muted-color">
                                <ProfileBorderAvatar :initial="initial" :src="picked.url" size="xs" surface="card" :border-style="mine?.profileBorder ?? null" />
                                <PostAuthorLabel :author="previewAuthor" preview strong class="text-sm" />
                            </div>
                            <div class="mt-2 flex flex-wrap items-center gap-1.5">
                                <span class="mr-1 text-lg font-bold tabular-nums text-primary">{{ UtilPoint.points(picked.price ?? 0) }}</span>
                                <PointShopTag>{{ t('pointShop.usagePermanent') }}</PointShopTag>
                                <PointShopTag v-if="picked.selected" tone="primary">{{ t('avatar.inUse') }}</PointShopTag>
                                <PointShopTag v-else-if="picked.owned" tone="success">{{ t('pointShop.owned') }}</PointShopTag>
                                <PointShopTag v-else-if="!picked.forSale">{{ t('avatar.notForSale') }}</PointShopTag>
                            </div>
                        </div>
                        <div class="flex w-full flex-col gap-1.5 sm:w-auto">
                            <Button
                                v-if="picked.owned && !picked.selected"
                                type="button"
                                :label="t('avatar.useNow')"
                                icon="pi pi-user"
                                :loading="selecting"
                                data-testid="pointshop-avatar-use"
                                @click="useAvatar(picked)"
                            />
                            <Button
                                v-else-if="!picked.owned"
                                type="button"
                                :label="t('pointShop.buy', { points: UtilPoint.points(picked.price ?? 0) })"
                                icon="pi pi-shopping-cart"
                                :disabled="!picked.forSale"
                                data-testid="pointshop-avatar-buy"
                                @click="buyFlow.start()"
                            />
                            <p v-if="!picked.owned && !picked.forSale" class="m-0 text-xs text-muted-color">{{ t('avatar.notForSaleText') }}</p>
                        </div>
                    </section>
                    <p v-else class="m-0 text-sm text-muted-color">{{ t('avatar.shopPickHint') }}</p>

                    <AvatarCategoryChips v-model="avatarCategory" :categories="shopCategories" :label="t('avatar.categoriesLabel')" testid="pointshop-avatar-categories" />
                    <AvatarGrid :cells="shopCells" :label="t(UtilAvatar.categoryKey(avatarCategory))" testid="pointshop-avatar-grid" @choose="pickedId = $event" />
                </template>
            </template>

            <EmptyState
                v-else-if="!data || data.items.length === 0"
                :title="t(kind === 'ALL' ? 'pointShop.empty' : 'pointShop.emptyFiltered')"
                :description="t('pointShop.emptyText')"
                icon="pi pi-shopping-bag"
                data-testid="pointshop-empty"
            />
            <ul v-else class="m-0 grid list-none grid-cols-2 gap-3 p-0 sm:grid-cols-3" data-testid="pointshop-items">
                <li v-for="item in data.items" :key="item.id">
                    <NuxtLink
                        :to="UtilPointShop.itemPath(item.id)"
                        class="flex h-full flex-col gap-2 panel-card p-3 hover:border-primary focus-visible:outline-2 focus-visible:outline-primary"
                        :class="item.saleState === 'SOLD_OUT' ? 'opacity-70' : ''"
                        data-testid="pointshop-item"
                    >
                        <PointShopItemThumb :kind="item.kind" :color="item.color" :border-style="item.borderStyle" :my-avatar-src="myAvatar" :nickname="nickname" />
                        <p class="m-0 line-clamp-2 text-sm font-semibold leading-snug">{{ localizedName(item.name, item.nameEn) }}</p>
                        <p class="m-0 text-sm font-bold tabular-nums text-primary">{{ UtilPoint.points(item.price) }}</p>
                        <div class="mt-auto flex flex-wrap items-center gap-1">
                            <PointShopTag>{{ UtilPointShopMessages.usage(t, item) }}</PointShopTag>
                            <PointShopTag v-for="tag in UtilPointShopMessages.tags(t, time, item)" :key="tag.key" :tone="tag.tone">{{ tag.text }}</PointShopTag>
                        </div>
                    </NuxtLink>
                </li>
            </ul>

            <p class="text-xs leading-relaxed text-muted-color">{{ t('pointShop.listNote', { days: refundDays }) }}</p>
        </template>

        <PointShopPurchaseDialog
            v-if="picked"
            v-model:visible="buyOpen"
            v-model:agreed="buyAgreed"
            :name="picked.name"
            :price="picked.price ?? 0"
            :balance="catalog?.balance ?? 0"
            :refund-days="refundDays"
            :buying="buying"
            :error="buyError"
            :agree-error="buyAgreeError"
            :restriction="buyRestriction"
            @buy="purchaseAvatar"
        >
            <template #preview>
                <ProfileBorderAvatar :initial="initial" :src="picked.url" size="md" surface="card" :border-style="mine?.profileBorder ?? null" />
            </template>
        </PointShopPurchaseDialog>
    </div>
</template>

<script setup lang="ts">
import type { AvatarGridCell } from '~/components/avatar/AvatarGrid.types';
import type { AvatarCatalog, AvatarItem } from '~/composables/avatar/api/useAvatarApi.types';
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';
import type { PointShopItemList } from '~/composables/pointshop/api/usePointShopApi.types';
import type { PointShopKindFilter } from '~/utils/util-point-shop';
import UtilAvatar from '~/utils/util-avatar';
import UtilBusiness from '~/utils/util-business';
import UtilPoint from '~/utils/util-point';
import UtilPointShop from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

/**
 * 포인트 상점 `/point-shop?kind=` — 로그인 필요. 헤더 프로필 메뉴·햄버거의 「포인트 상점」(서비스 용어 `POINT_SHOP`)이 온다. 공동구매(주문 내역)와 따로다.
 * 위에 내 포인트와 내 아이템 링크, 종류 거르기(전체·닉네임 색·프로필 테두리·아바타·변경권 — 주소 `?kind=`).
 * - 아바타 밖의 탭: 품목 카드(미리보기 조각·이름·가격·사용 방식·판매 상태·보유) — 누르면 상세(`/point-shop/{id}` — 미리보기·구매). `GET /api/point-shop/items`.
 * - 아바타 탭: 응답이 다르다(`GET /api/avatars` — 유료 306종만 보인다). 카테고리 칩 + 격자(보유·사용 중·판매 중단 표시), 누르면 위에 크게 미리보기(내 닉네임·장착 테두리와 함께) +
 *   구매 확인(상세와 같은 대화상자 `PointShopPurchaseDialog`) → 성공 뒤 「바로 프로필로 쓰기」(고르면 환불할 수 없어 한 번 묻는다 — `PUT /api/me/profile/avatar`).
 * 계약 `docs/features/commerce-stage1-contract.md` 「1-2 화면 목업」. 서버 연결됨(`usePointShopApi`·`useAvatarApi`). 구매 뒤 헤더·포인트 화면 잔액도 다시 받는다(`usePointSummary`).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { term } = useSiteTerms();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => term('POINT_SHOP'), description: () => t('pointShop.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const route = useRoute();
const router = useRouter();
const api = usePointShopApi();
const avatarApi = useAvatarApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const { user } = useAuth();
const { localizedName } = useLocalizedName();
const decoration = useMyDecoration();
const decorationSync = useMyDecorationSync();
const points = usePointSummary();

const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));
const nickname = computed(() => user.value?.nickname ?? '');
const initial = computed(() => UtilPointShop.initial(nickname.value));
/** 내 아바타·장식 — 미리보기 */
const myAvatar = computed(() => decoration.myAvatarUrl());
const mine = computed(() => decoration.mine());

const kind = computed<PointShopKindFilter>(() => UtilPointShop.parseKind(route.query.kind));
const kindOptions = computed(() => [
    { value: 'ALL', label: t('common.all') },
    { value: 'NICKNAME_COLOR', label: t('pointShop.kindNicknameColor') },
    { value: 'PROFILE_BORDER', label: t('pointShop.kindProfileBorder') },
    { value: 'PROFILE_AVATAR', label: t('pointShop.kindAvatar') },
    { value: 'NICKNAME_CHANGE_TICKET', label: t('pointShop.kindTicket') },
]);

function setKind(value: PointShopKindFilter) {
    void router.replace({ query: { ...route.query, kind: value === 'ALL' ? undefined : value } });
}

const data = ref<PointShopItemList | null>(null);
const catalog = ref<AvatarCatalog | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
/* 아바타 탭의 상태 — 지금 보는 카테고리 · 미리보기로 고른 아바타 */
const avatarCategory = ref('');
const pickedId = ref<string | null>(null);
const shopItems = computed(() => (catalog.value ? UtilAvatar.forShop(catalog.value.items) : []));
const shopCategories = computed(() => (catalog.value ? UtilAvatar.categoriesOf(catalog.value.categories, shopItems.value) : []));

const loadError = ref('');
let seq = 0;

const balance = computed(() => (kind.value === 'PROFILE_AVATAR' ? (catalog.value?.balance ?? null) : (data.value?.balance ?? null)));
const refundDays = computed(() => (kind.value === 'PROFILE_AVATAR' ? catalog.value?.refundDays : data.value?.refundDays) ?? 7);

/** 지금 탭을 받는다 — 아바타 탭은 카탈로그, 나머지는 품목 목록. `quiet` 면 받아 둔 것을 그대로 보이면서 바꾼다 */
async function load(quiet = false) {
    const mineSeq = ++seq;
    if (!quiet) {
        status.value = 'loading';
        loadError.value = '';
    }
    try {
        if (kind.value === 'PROFILE_AVATAR') {
            const result = await avatarApi.catalog();
            if (mineSeq !== seq) return;
            catalog.value = result;
            if (!shopCategories.value.some((c) => c.id === avatarCategory.value)) avatarCategory.value = shopCategories.value[0]?.id ?? '';
        } else {
            const result = await api.list({ kind: kind.value === 'ALL' ? undefined : kind.value });
            if (mineSeq !== seq) return;
            data.value = result;
        }
        status.value = 'ready';
    } catch (e) {
        if (mineSeq !== seq) return;
        if (quiet) {
            toast.error(UtilPointShopMessages.error(t, time.value, e), 8000);
            return;
        }
        loadError.value = UtilPointShopMessages.error(t, time.value, e);
        status.value = 'error';
    }
}

watch(kind, () => void load());
watch(
    () => user.value?.id ?? null,
    (id) => {
        if (!id || businessAccount.value) return;
        void load();
        void decorationSync.ensure();
    },
    { immediate: true },
);
onBeforeUnmount(() => seq++);

/* ── 아바타 탭 ── */
const picked = computed<AvatarItem | null>(() => shopItems.value.find((a) => a.id === pickedId.value) ?? null);
const shopCells = computed<AvatarGridCell[]>(() =>
    shopItems.value
        .filter((a) => a.category === avatarCategory.value)
        .map((a) => {
            const caption = a.selected ? t('avatar.inUse') : a.owned ? t('pointShop.owned') : !a.forSale ? t('avatar.notForSale') : UtilPoint.points(a.price ?? 0);
            return { id: a.id, name: a.name, url: a.url, active: a.id === pickedId.value, caption, dim: !a.owned && !a.forSale, ariaLabel: `${a.name} · ${caption}` };
        }),
);

/** 미리보기 작성자 — 내 닉네임·등급·장착 색 */
const previewAuthor = computed<PostAuthor>(() => {
    const me = user.value;
    return {
        kind: 'MEMBER',
        memberId: me?.id ?? null,
        name: me?.nickname ?? '',
        grade: me?.role === 'USER' ? me.grade : null,
        gradeLabel: me?.gradeLabel ?? null,
        role: 'USER',
        ipMasked: null,
        decoration: { nicknameColor: mine.value?.nicknameColor ?? null, profileBorder: mine.value?.profileBorder ?? null },
    };
});

const buyFlow = usePointShopPurchase();
const { open: buyOpen, agreed: buyAgreed, buying, error: buyError, agreeError: buyAgreeError, restriction: buyRestriction } = buyFlow;

async function purchaseAvatar() {
    const avatar = picked.value;
    if (!avatar?.itemId) return;
    const result = await buyFlow.buy(avatar.itemId, () => void load(true));
    if (!result) return;
    // 헤더·포인트 화면의 잔액도 맞춘다
    void points.load();
    await load(true);
    toast.show(t('pointShop.bought', { name: avatar.name }), { tone: 'success', life: 6000, action: { label: t('avatar.useNow'), run: () => void useAvatar(avatar) } });
}

/** 「바로 프로필로 쓰기」 — 환불할 수 있는 것(안 썼고 기한 안)은 고르면 환불할 수 없어 한 번 묻는다 */
const selecting = ref(false);
async function useAvatar(avatar: AvatarItem) {
    if (selecting.value) return;
    const refundable = catalog.value?.items.find((a) => a.id === avatar.id)?.refundable ?? avatar.refundable;
    if (refundable && !(await confirm({ title: t('avatar.useConfirmTitle'), message: t('avatar.useConfirmText'), confirmLabel: t('avatar.useNow') }))) return;
    selecting.value = true;
    try {
        const profile = await avatarApi.select({ avatarId: avatar.id });
        decoration.applyAvatar(profile.avatarUrl);
        toast.success(t('avatar.saved'));
        await load(true);
    } catch (e) {
        toast.error(UtilPointShopMessages.error(t, time.value, e), 8000);
    } finally {
        selecting.value = false;
    }
}
</script>
