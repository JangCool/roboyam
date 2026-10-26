<template>
    <div class="mx-auto flex max-w-2xl flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-lg font-bold">{{ t('pointShop.myItems') }}</h1>
            <Button as="router-link" :to="UtilPointShop.PATH" :label="term('POINT_SHOP')" icon="pi pi-shopping-bag" size="small" severity="secondary" variant="outlined" data-testid="myitems-shop" />
        </div>

        <EmptyState v-if="businessAccount" :title="t('point.businessTitle')" :description="t('point.businessText')" icon="pi pi-wallet" />

        <div v-else-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('pointShop.myItemsLoading')" data-testid="myitems-loading">
            <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 3" :key="n" class="h-16 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error'" :title="t('pointShop.myItemsLoadFailed')" :description="loadError" retry data-testid="myitems-error" @retry="load()" />
        <EmptyState v-else-if="items.length === 0" :title="t('pointShop.myItemsEmpty')" :description="t('pointShop.myItemsEmptyText')" icon="pi pi-box" data-testid="myitems-empty">
            <Button as="router-link" :to="UtilPointShop.PATH" :label="term('POINT_SHOP')" />
        </EmptyState>

        <template v-else>
            <!-- 정지 중 — 장착·해제·아바타 고르기를 막는다(서버 `403 ACCOUNT_RESTRICTED`, 프로필 수정과 같다). 목록은 그대로 보인다. 풀리면 할 수 있는 일이라 단추는 감추지 않고 끈다 -->
            <div v-if="restricted" role="status" class="rounded-lg bg-amber-50 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="myitems-restricted">
                <p class="m-0 font-semibold">{{ t('pointShop.restrictedTitle') }}</p>
                <p class="m-0 mt-0.5">{{ t('pointShop.restrictedText') }}</p>
                <NuxtLink :to="UtilSanction.PATH" class="mt-1 inline-block font-semibold underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('profile.restrictedLink') }}</NuxtLink>
            </div>

            <!-- 지금 장착 — 자리마다 하나(닉네임 색 · 프로필 테두리) -->
            <section class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-equipped`" data-testid="myitems-equipped">
                <h2 :id="`${uid}-equipped`" class="m-0 text-sm font-semibold">{{ t('pointShop.equippedTitle') }}</h2>
                <div class="mt-3 grid gap-3 sm:grid-cols-2">
                    <div v-for="slot in slots" :key="slot.key" class="flex items-center gap-3 rounded-lg bg-surface-50 p-3 dark:bg-surface-950" :data-testid="`myitems-slot-${slot.key}`">
                        <ProfileBorderAvatar :initial="initial" :src="myAvatar" size="md" :border-style="slot.key === 'PROFILE_BORDER' ? (slot.row?.borderStyle ?? null) : null" />
                        <div class="min-w-0 flex-1">
                            <p class="m-0 text-xs text-muted-color">{{ slot.label }}</p>
                            <p v-if="slot.row" class="m-0 mt-0.5 truncate text-sm font-semibold" :style="slot.key === 'NICKNAME_COLOR' ? { color: UtilColor.cssPair(slot.row.color) ?? undefined } : undefined">
                                {{ slot.key === 'NICKNAME_COLOR' ? nickname : localizedName(slot.row.name, slot.row.nameEn) }}
                            </p>
                            <p v-else class="m-0 mt-0.5 text-sm text-muted-color">{{ t('pointShop.slotEmpty') }}</p>
                            <p v-if="slot.row && slot.key === 'NICKNAME_COLOR'" class="m-0 truncate text-xs text-muted-color">{{ localizedName(slot.row.name, slot.row.nameEn) }}</p>
                        </div>
                        <Button v-if="slot.row" type="button" :label="t('pointShop.unequip')" size="small" severity="secondary" variant="outlined" :loading="busyId === `slot:${slot.key}`" :disabled="busy || restricted" @click="unequip(slot.key)" />
                    </div>
                </div>
                <p class="m-0 mt-3 text-xs leading-relaxed text-muted-color">{{ t('pointShop.equippedNote', { days: refundDays }) }}</p>
            </section>

            <!-- 자리별 보유 목록 -->
            <section v-for="group in wearGroups" :key="group.key" class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900" :aria-labelledby="`${uid}-${group.key}`" :data-testid="`myitems-group-${group.key}`">
                <h2 :id="`${uid}-${group.key}`" class="m-0 px-4 pt-4 text-sm font-semibold">{{ group.title }}</h2>
                <ul class="m-0 mt-2 list-none p-0">
                    <li v-for="row in group.rows" :key="row.id" class="flex flex-wrap items-center gap-3 border-t border-surface px-4 py-3" :class="row.expired ? 'opacity-60' : ''" data-testid="myitems-row">
                        <PointShopItemThumb :kind="row.kind" :color="row.color" :border-style="row.borderStyle" :avatar-src="row.avatarUrl" :my-avatar-src="myAvatar" :nickname="nickname" compact />
                        <div class="min-w-0 flex-1">
                            <p class="m-0 flex flex-wrap items-center gap-1.5 text-sm font-semibold">
                                <span class="min-w-0 break-words">{{ localizedName(row.name, row.nameEn) }}</span>
                                <PointShopTag v-if="row.equipped" tone="primary">{{ t('pointShop.equippedTag') }}</PointShopTag>
                                <PointShopTag>{{ UtilPointShopMessages.usage(t, row) }}</PointShopTag>
                                <PointShopTag v-if="row.usageType === 'PERIOD'" :tone="row.expired ? 'neutral' : 'warn'">{{ UtilPointShopMessages.period(t, row) }}</PointShopTag>
                            </p>
                            <p class="m-0 mt-0.5 text-xs text-muted-color">{{ rowMeta(row) }}</p>
                            <p v-if="requestLine(row)" class="m-0 mt-1 flex flex-wrap items-center gap-1.5 text-xs" data-testid="myitems-refund-request">
                                <PointShopTag :tone="UtilPointShop.refundPending(row) ? 'warn' : 'danger'">{{ requestLine(row)!.tag }}</PointShopTag>
                                <span v-if="requestLine(row)!.text" class="min-w-0 break-words text-muted-color">{{ requestLine(row)!.text }}</span>
                            </p>
                        </div>
                        <div class="flex shrink-0 gap-2 max-sm:w-full max-sm:justify-end">
                            <Button v-if="row.refundable" type="button" :label="refundLabel" size="small" severity="secondary" variant="text" :loading="busyId === `refund:${row.id}`" :disabled="busy" @click="refund(row)" />
                            <Button
                                v-if="row.equipped"
                                type="button"
                                :label="t('pointShop.unequip')"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                :loading="busyId === `slot:${slotKey(row)}`"
                                :disabled="busy || restricted"
                                @click="unequip(slotKey(row)!)"
                            />
                            <Button v-else-if="!row.expired && slotKey(row)" type="button" :label="t('pointShop.equip')" size="small" :loading="busyId === `equip:${row.id}`" :disabled="busy || restricted || UtilPointShop.refundPending(row)" @click="equip(row)" />
                        </div>
                    </li>
                </ul>
            </section>

            <!-- 프로필 아바타 — 산 것(쓰는 중이 위). 아바타는 장착 칸이 아니라 프로필 그림이라 「프로필로 쓰기」(고르면 환불할 수 없다) -->
            <section v-if="grouped.avatars.length > 0" class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900" :aria-labelledby="`${uid}-avatar`" data-testid="myitems-group-avatar">
                <h2 :id="`${uid}-avatar`" class="m-0 px-4 pt-4 text-sm font-semibold">{{ t('pointShop.kindAvatar') }}</h2>
                <ul class="m-0 mt-2 list-none p-0">
                    <li v-for="row in grouped.avatars" :key="row.id" class="flex flex-wrap items-center gap-3 border-t border-surface px-4 py-3" data-testid="myitems-avatar-row">
                        <PointShopItemThumb kind="PROFILE_AVATAR" :avatar-src="row.avatarUrl" :nickname="nickname" compact />
                        <div class="min-w-0 flex-1">
                            <p class="m-0 flex flex-wrap items-center gap-1.5 text-sm font-semibold">
                                <span class="min-w-0 break-words">{{ localizedName(row.name, row.nameEn) }}</span>
                                <PointShopTag v-if="row.inUse" tone="primary">{{ t('avatar.inUse') }}</PointShopTag>
                                <PointShopTag>{{ UtilPointShopMessages.usage(t, row) }}</PointShopTag>
                            </p>
                            <p class="m-0 mt-0.5 text-xs text-muted-color">{{ rowMeta(row) }}</p>
                            <p v-if="requestLine(row)" class="m-0 mt-1 flex flex-wrap items-center gap-1.5 text-xs" data-testid="myitems-refund-request">
                                <PointShopTag :tone="UtilPointShop.refundPending(row) ? 'warn' : 'danger'">{{ requestLine(row)!.tag }}</PointShopTag>
                                <span v-if="requestLine(row)!.text" class="min-w-0 break-words text-muted-color">{{ requestLine(row)!.text }}</span>
                            </p>
                        </div>
                        <div class="flex shrink-0 gap-2 max-sm:w-full max-sm:justify-end">
                            <Button v-if="row.refundable" type="button" :label="refundLabel" size="small" severity="secondary" variant="text" :loading="busyId === `refund:${row.id}`" :disabled="busy" @click="refund(row)" />
                            <Button v-if="!row.inUse" type="button" :label="t('avatar.useInProfileShort')" size="small" :loading="busyId === `avatar:${row.id}`" :disabled="busy || restricted || UtilPointShop.refundPending(row)" @click="useAvatar(row)" />
                        </div>
                    </li>
                </ul>
            </section>

            <!-- 변경권 — 품목별 한 카드(장수), 펼치면 장마다 구매일·상태·환불 -->
            <section v-for="group in grouped.tickets" :key="group.itemId" class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900" data-testid="myitems-tickets">
                <button
                    type="button"
                    class="flex w-full items-center gap-3 rounded-xl p-4 text-left focus-visible:outline-2 focus-visible:outline-primary"
                    :aria-expanded="openTickets.has(group.itemId)"
                    :aria-controls="`${uid}-t-${group.itemId}`"
                    @click="toggleTicket(group.itemId)"
                >
                    <PointShopItemThumb kind="NICKNAME_CHANGE_TICKET" compact />
                    <span class="min-w-0 flex-1">
                        <span class="block text-sm font-semibold">{{ localizedName(group.name, group.nameEn) }}</span>
                        <span class="block text-xs text-muted-color">{{ t('pointShop.ticketCount', { count: group.ownedCount }) }}</span>
                    </span>
                    <i class="pi text-muted-color" :class="openTickets.has(group.itemId) ? 'pi-angle-up' : 'pi-angle-down'" aria-hidden="true" />
                </button>
                <ul v-show="openTickets.has(group.itemId)" :id="`${uid}-t-${group.itemId}`" class="m-0 list-none p-0">
                    <li v-for="row in group.rows" :key="row.id" class="flex flex-wrap items-center gap-3 border-t border-surface px-4 py-3" :class="row.status === 'USED' ? 'opacity-60' : ''" data-testid="myitems-ticket-row">
                        <div class="min-w-0 flex-1 text-sm">
                            <p class="m-0 flex items-center gap-1.5">
                                {{ t('pointShop.boughtOn', { date: time.date(row.purchasedAt) }) }}
                                <PointShopTag v-if="!UtilPointShop.refundPending(row)" :tone="row.status === 'USED' ? 'neutral' : 'success'">{{ row.status === 'USED' ? t('pointShop.ticketUsed') : t('pointShop.ticketOwned') }}</PointShopTag>
                            </p>
                            <p class="m-0 mt-0.5 text-xs text-muted-color">{{ rowMeta(row) }}</p>
                            <p v-if="requestLine(row)" class="m-0 mt-1 flex flex-wrap items-center gap-1.5 text-xs" data-testid="myitems-refund-request">
                                <PointShopTag :tone="UtilPointShop.refundPending(row) ? 'warn' : 'danger'">{{ requestLine(row)!.tag }}</PointShopTag>
                                <span v-if="requestLine(row)!.text" class="min-w-0 break-words text-muted-color">{{ requestLine(row)!.text }}</span>
                            </p>
                        </div>
                        <Button v-if="row.refundable" type="button" :label="refundLabel" size="small" severity="secondary" variant="text" :loading="busyId === `refund:${row.id}`" :disabled="busy" @click="refund(row)" />
                    </li>
                </ul>
                <div class="border-t border-surface px-4 py-2.5">
                    <NuxtLink to="/settings/profile" class="text-sm text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('pointShop.useInProfile') }}</NuxtLink>
                </div>
            </section>

            <!-- 환불됨 — 접힌 구획 -->
            <details v-if="grouped.revoked.length > 0" class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900" data-testid="myitems-revoked">
                <summary class="cursor-pointer rounded-xl px-4 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-primary">{{ t('pointShop.revokedTitle', { count: grouped.revoked.length }) }}</summary>
                <ul class="m-0 list-none p-0">
                    <li v-for="row in grouped.revoked" :key="row.id" class="flex items-center gap-3 border-t border-surface px-4 py-3 text-sm text-muted-color">
                        <span class="min-w-0 flex-1 break-words">{{ localizedName(row.name, row.nameEn) }}</span>
                        <span class="shrink-0 text-xs">{{ t('pointShop.boughtOn', { date: time.date(row.purchasedAt) }) }} · {{ UtilPoint.points(row.pricePaid) }}</span>
                    </li>
                </ul>
            </details>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { EquipSlot, MyItemView } from '~/composables/pointshop/api/useMyItemsApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';
import UtilColor from '~/utils/util-color';
import UtilPoint from '~/utils/util-point';
import UtilPointShop from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';
import UtilSanction from '~/utils/util-sanction';

/**
 * 내 아이템 `/me/items` — 로그인 필요. 상세의 「장착하러 가기」·목록의 「내 아이템」이 온다.
 * - 지금 장착(닉네임 색·프로필 테두리 — 자리마다 하나, 해제) · 자리별 보유 목록(장착·해제, 기간제 「N일 남음」·끝나면 흐리게 「기간 끝」) · 변경권은 품목별 장수 카드(펼치면 장마다 구매일·환불) · 「환불됨」 접힌 구획.
 * - 프로필 아바타 구획: 산 아바타(쓰는 중이 위) · 「프로필로 쓰기」(`useAvatarApi().select` — 환불할 수 있는 것만 한 번 묻는다) · 환불. 고르면 프로필·글 보기 머리의 내 동그라미가 바로 바뀐다.
 * - 정지 중(서버 `refundNeedsApproval`)이면 [환불] 대신 [환불 신청] — 202 면 「환불을 신청했어요」. 대기 줄은 「환불 신청함」(장착·고르기 끔), 거절 줄은 사유. 계약 「1-2 정지 중 환불 신청」.
 * - 장착 전 확인은 환불할 수 있는 것만 묻는다(「장착하면 이 아이템은 환불할 수 없어요」). 환불은 확인 대화상자(돌려받는 포인트·기한, 장착하면 환불 못 한다는 안내) 뒤 `POST …/refund`(멱등 키).
 * - 한 줄 동작 실패는 토스트(사유 8초). `409 POINT_SHOP_REFUND_NOT_ALLOWED`·`USER_ITEM_EXPIRED` 는 목록을 다시 읽는다. 장착·해제 결과는 내 장식 겹치기(`useMyDecoration`)에 바로 넣는다 — 글·댓글·프로필의 내 이름이 바로 바뀐다.
 * 계약 `docs/features/commerce-stage1-contract.md` 「1-2 화면 목업」. 서버 연결됨(`useMyItemsApi`·`useAvatarApi`). 환불 뒤 헤더·포인트 화면 잔액도 다시 받는다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { term } = useSiteTerms();
useSeoMeta({ title: () => t('pointShop.myItems'), robots: 'noindex' });

const uid = useId();
const api = useMyItemsApi();
const avatarApi = useAvatarApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const { user, restriction } = useAuth();
const { localizedName } = useLocalizedName();
const decoration = useMyDecoration();
const decorationSync = useMyDecorationSync();
const points = usePointSummary();

const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));
/** 장착·해제·아바타 고르기를 서버가 정지 중이라고 막았다(로그인 정보가 아직 정상이어도) */
const restrictedByServer = ref(false);
/** 정지·영구 정지 중 — 장착·해제·아바타 고르기를 끈다(환불은 그대로) */
const restricted = computed(() => restrictedByServer.value || UtilSanction.isRestricted(restriction.value));
const nickname = computed(() => user.value?.nickname ?? '');
const initial = computed(() => UtilPointShop.initial(nickname.value));
/** 내 아바타(방금 고른 것 포함) */
const myAvatar = computed(() => decoration.myAvatarUrl());

const items = ref<MyItemView[]>([]);
const refundDays = ref(7);
/** 정지 중이라 환불이 운영자 수락을 거친다(서버 `refundNeedsApproval`) — [환불] 대신 [환불 신청] */
const refundNeedsApproval = ref(false);
const refundLabel = computed(() => (refundNeedsApproval.value ? t('pointShop.refundRequest') : t('pointShop.refund')));

/** 환불 신청 한 줄 — 대기 중 · 거절됨(사유). 신청한 적 없거나 수락됐으면(회수 구획) null */
function requestLine(row: MyItemView): { tag: string; text: string } | null {
    const request = row.refundRequest;
    if (!request || row.status === 'REVOKED') return null;
    if (request.status === 'PENDING') return { tag: t('pointShop.refundRequestPending'), text: t('pointShop.refundRequestPendingText') };
    if (request.status === 'REJECTED') return { tag: t('pointShop.refundRequestRejected'), text: request.rejectReason ? t('pointShop.refundRequestRejectedReason', { reason: request.rejectReason }) : t('pointShop.refundRequestRejectedPlain') };
    return null;
}
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
let seq = 0;

/** 받는다 — `quiet` 이면 받아 둔 목록을 그대로 보이면서 바꾼다(동작 뒤 다시 읽기) */
async function load(quiet = false) {
    const mine = ++seq;
    if (!quiet) {
        status.value = 'loading';
        loadError.value = '';
    }
    try {
        const result = await api.list();
        if (mine !== seq) return;
        items.value = result.items;
        refundDays.value = result.refundDays;
        refundNeedsApproval.value = result.refundNeedsApproval === true;
        status.value = 'ready';
        syncDecoration();
    } catch (e) {
        if (mine !== seq) return;
        if (quiet) {
            toast.error(UtilPointShopMessages.error(t, time.value, e), 8000);
            return;
        }
        loadError.value = UtilPointShopMessages.error(t, time.value, e);
        status.value = 'error';
    }
}

/** 목록에서 읽은 장착을 내 장식 겹치기에 넣는다(다른 탭·기기에서 바꾼 것까지 맞춘다) */
function syncDecoration() {
    const color = items.value.find((r) => r.equipped && r.kind === 'NICKNAME_COLOR');
    const border = items.value.find((r) => r.equipped && r.kind === 'PROFILE_BORDER');
    decoration.applyEquipped({
        nicknameColor: color?.color ? { userItemId: color.id, itemId: color.itemId, color: color.color, expiresAt: color.expiresAt } : null,
        profileBorder: border?.borderStyle ? { userItemId: border.id, itemId: border.itemId, borderStyle: border.borderStyle, expiresAt: border.expiresAt } : null,
    });
}

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

const grouped = computed(() => UtilPointShop.group(items.value));
const wearGroups = computed(() =>
    [
        { key: 'color', title: t('pointShop.kindNicknameColor'), rows: grouped.value.colors },
        { key: 'border', title: t('pointShop.kindProfileBorder'), rows: grouped.value.borders },
    ].filter((g) => g.rows.length > 0),
);
const slots = computed<{ key: EquipSlot; label: string; row: MyItemView | null }[]>(() => [
    { key: 'NICKNAME_COLOR', label: t('pointShop.kindNicknameColor'), row: grouped.value.colors.find((r) => r.equipped) ?? null },
    { key: 'PROFILE_BORDER', label: t('pointShop.kindProfileBorder'), row: grouped.value.borders.find((r) => r.equipped) ?? null },
]);

const slotKey = (row: MyItemView): EquipSlot | null => UtilPointShop.slotOf(row.kind);

/** 줄 아래 한 줄 — 구매일 · 환불 기한(환불 가능하면) 또는 「사용함 · 환불 불가」 */
function rowMeta(row: MyItemView): string {
    const parts = [t('pointShop.boughtOn', { date: time.value.date(row.purchasedAt) }), UtilPoint.points(row.pricePaid)];
    if (row.refundable && row.refundUntil) parts.push(t('pointShop.refundUntil', { date: time.value.shortDateTime(row.refundUntil) }));
    else if (row.firstUsedAt) parts.push(t('pointShop.usedNoRefund'));
    // 신청 대기·정지 중 거절은 아래 신청 줄이 까닭을 보인다(기한이 지난 것이 아니다)
    else if (row.status === 'OWNED' && !row.refundRequest) parts.push(t('pointShop.refundExpired'));
    return parts.join(' · ');
}

const openTickets = ref(new Set<string>());
function toggleTicket(itemId: string) {
    const next = new Set(openTickets.value);
    if (next.has(itemId)) next.delete(itemId);
    else next.add(itemId);
    openTickets.value = next;
}

/* ── 동작 ── */
const busyId = ref('');
const busy = computed(() => busyId.value !== '');

function actionFailed(e: unknown) {
    if (e instanceof ApiError && e.code === 'ACCOUNT_RESTRICTED') restrictedByServer.value = true;
    toast.error(UtilPointShopMessages.error(t, time.value, e), 8000);
    // 화면이 본 상태가 낡았다(기간이 끝났거나 이미 썼거나 기한이 지남) — 다시 읽는다
    if (e instanceof ApiError && (e.status === 409 || e.status === 404)) void load(true);
}

async function equip(row: MyItemView) {
    const slot = slotKey(row);
    if (!slot || busy.value) return;
    // 환불할 수 있는 것만 묻는다 — 장착하면 「사용함」이 남아 환불할 수 없다
    if (row.refundable) {
        const ok = await confirm({ title: t('pointShop.equipConfirmTitle'), message: t('pointShop.equipConfirmText'), confirmLabel: t('pointShop.equip') });
        if (!ok) return;
    }
    busyId.value = `equip:${row.id}`;
    try {
        const equipped = await api.equip(slot, row.id);
        decoration.applyEquipped(equipped);
        toast.success(t('pointShop.equipped', { name: localizedName(row.name, row.nameEn) }));
        await load(true);
    } catch (e) {
        actionFailed(e);
    } finally {
        busyId.value = '';
    }
}

async function unequip(slot: EquipSlot) {
    if (busy.value) return;
    busyId.value = `slot:${slot}`;
    try {
        const equipped = await api.unequip(slot);
        decoration.applyEquipped(equipped);
        toast.success(t('pointShop.unequipped'));
        await load(true);
    } catch (e) {
        actionFailed(e);
    } finally {
        busyId.value = '';
    }
}

/** 산 아바타를 프로필로 쓴다 — 환불할 수 있는 것만 한 번 묻는다(고르면 「사용함」이 남는다). `PUT /api/me/profile/avatar` */
async function useAvatar(row: MyItemView) {
    if (busy.value || !row.avatarId) return;
    if (row.refundable && !(await confirm({ title: t('avatar.useConfirmTitle'), message: t('avatar.useConfirmText'), confirmLabel: t('avatar.useNow') }))) return;
    busyId.value = `avatar:${row.id}`;
    try {
        const profile = await avatarApi.select({ avatarId: row.avatarId });
        decoration.applyAvatar(profile.avatarUrl);
        toast.success(t('avatar.saved'));
        await load(true);
    } catch (e) {
        actionFailed(e);
    } finally {
        busyId.value = '';
    }
}

async function refund(row: MyItemView) {
    if (busy.value || !row.refundable) return;
    const approval = refundNeedsApproval.value;
    const ok = await confirm({
        title: approval ? t('pointShop.refundRequestConfirmTitle') : t('pointShop.refundConfirmTitle'),
        message: t(approval ? 'pointShop.refundRequestConfirmText' : 'pointShop.refundConfirmText', {
            name: localizedName(row.name, row.nameEn),
            points: UtilPoint.points(row.pricePaid),
            date: row.refundUntil ? time.value.shortDateTime(row.refundUntil) : '',
        }),
        confirmLabel: refundLabel.value,
        tone: 'danger',
    });
    if (!ok) return;
    busyId.value = `refund:${row.id}`;
    try {
        const result = await api.refund(row.id);
        if (UtilPointShop.isRefundRequested(result)) {
            // 정지 중 — 환불 신청(202). 운영자가 수락하면 환불되고 알림이 온다
            toast.success(t('pointShop.refundRequested'));
        } else {
            // 헤더·포인트 화면의 잔액도 맞춘다
            void points.load();
            toast.success(t('pointShop.refunded', { points: UtilPoint.points(result.refundedPoints), balance: UtilPoint.points(result.balance) }));
        }
        await load(true);
    } catch (e) {
        actionFailed(e);
    } finally {
        busyId.value = '';
    }
}
</script>
