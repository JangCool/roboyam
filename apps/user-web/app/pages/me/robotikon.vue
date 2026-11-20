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
                                <PointShopTag v-else-if="pack.reward" tone="success" data-testid="my-emoticons-reward">{{ t('emoticon.rewardTag') }}</PointShopTag>
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


        <!--
            내 짤(my-jjal-contract) — 나만 쓰는 보관함. 격자(내가 정한 순서 — 고르기 창도 같은 순서) + n / 50, 올리기 · 순서 바꾸기 · 고르기 → 빼기(확인 창).
            순서 바꾸기: PC 는 칸을 끌어 옮기기(네이티브 drag), 휴대폰·키보드는 칸마다 앞으로·뒤로 단추 → [저장](전체 ID 한 번) · [취소]. 그동안 올리기·고르기·빼기는 숨긴다.
            내려간 짤은 빨간 칸 + 안내 한 줄(순서 자리는 있다). 꽉 찼을 때 토스트 「정리하기」가 여기로 온다
        -->
        <section :id="UtilJjal.MY_SECTION_ID" class="panel-card scroll-mt-20" :aria-labelledby="`${uid}-jjals`" data-testid="my-jjals">
            <header class="flex flex-wrap items-center justify-between gap-2 px-4 pt-4">
                <h2 :id="`${uid}-jjals`" class="m-0 flex items-center gap-2 text-sm font-semibold">
                    {{ t('jjal.myTitle') }}
                    <span v-if="jjalList" class="text-xs font-normal tabular-nums text-muted-color" data-testid="my-jjals-count">{{ t('jjal.count', { count: jjalList.count, limit: jjalList.limit }) }}</span>
                </h2>
                <div class="flex flex-wrap items-center gap-1">
                    <template v-if="ordering">
                        <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="savingOrder" data-testid="my-jjals-order-cancel" @click="cancelOrdering" />
                        <Button type="button" :label="t('common.save')" icon="pi pi-check" size="small" :disabled="!orderChanged" :loading="savingOrder" data-testid="my-jjals-order-save" @click="saveOrdering" />
                    </template>
                    <template v-else-if="selecting">
                        <span class="text-xs tabular-nums text-muted-color">{{ t('jjal.mySelected', { count: selected.length }) }}</span>
                        <Button type="button" :label="t('jjal.myRemove')" icon="pi pi-trash" size="small" severity="danger" :disabled="selected.length === 0" :loading="removingJjals" data-testid="my-jjals-remove" @click="removeSelected" />
                        <Button type="button" :label="t('jjal.mySelectDone')" size="small" severity="secondary" variant="text" :disabled="removingJjals" @click="stopSelecting" />
                    </template>
                    <template v-else>
                        <Button type="button" :label="t('jjal.upload')" icon="pi pi-upload" size="small" severity="secondary" variant="outlined" data-testid="my-jjals-upload" @click="jjalUi.openUpload()" />
                        <Button type="button" :label="t('jjal.myReorder')" icon="pi pi-sort" size="small" severity="secondary" variant="text" :disabled="jjalItems.length < 2" data-testid="my-jjals-reorder" @click="startOrdering" />
                        <Button type="button" :label="t('jjal.mySelect')" size="small" severity="secondary" variant="text" :disabled="jjalItems.length === 0" data-testid="my-jjals-select" @click="selecting = true" />
                    </template>
                </div>
            </header>
            <div v-if="jjalList === undefined && !jjalError" class="grid grid-cols-[repeat(auto-fill,minmax(5rem,1fr))] gap-2 p-4" role="status" :aria-label="t('jjal.loading')">
                <div v-for="n in 6" :key="n" class="aspect-square animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            </div>
            <p v-else-if="jjalError && !jjalList" class="m-0 flex flex-wrap items-center gap-2 px-4 py-3 text-sm text-muted-color" role="alert">
                {{ t('jjal.loadFailed') }}
                <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="myJjals.reload()" />
            </p>
            <div v-else-if="jjalItems.length === 0" class="px-4 py-3" data-testid="my-jjals-empty">
                <p class="m-0 text-sm font-medium">{{ t('jjal.myEmpty') }}</p>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('jjal.myEmptyText') }}</p>
            </div>
            <template v-else>
                <p
                    v-if="jjalBlocked > 0"
                    class="mx-4 mt-3 mb-0 flex items-start gap-1.5 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs leading-relaxed text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"
                    data-testid="my-jjals-blocked"
                >
                    <i class="pi pi-eye-slash mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('jjal.blockedNote', { count: jjalBlocked }) }}
                </p>
                <p v-if="ordering" class="mx-4 mt-3 mb-0 flex items-start gap-1.5 text-xs leading-relaxed text-muted-color" data-testid="my-jjals-order-hint">
                    <i class="pi pi-info-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('jjal.myReorderHint') }}
                </p>
                <!-- 순서 바꾸기 — 칸 전체를 끌어 옮긴다(PC). 놓을 자리는 칸 왼쪽·오른쪽 반으로 앞·뒤를 정하고 세로 막대로 보인다 -->
                <ul v-if="ordering" class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(5rem,1fr))] gap-2 p-4" data-testid="my-jjals-order-grid">
                    <li
                        v-for="(item, i) in orderDraft"
                        :key="item.id"
                        class="relative flex flex-col gap-1"
                        draggable="true"
                        @dragstart="(e: DragEvent) => onOrderDragStart(e, item.id)"
                        @dragover="(e: DragEvent) => onOrderDragOver(e, item.id)"
                        @drop="(e: DragEvent) => onOrderDrop(e, item.id)"
                        @dragend="resetOrderDrag"
                    >
                        <span v-if="orderMark?.id === item.id" class="pointer-events-none absolute inset-y-0 z-10 w-0.5 rounded bg-primary" :class="orderMark.after ? '-right-[5px]' : '-left-[5px]'" aria-hidden="true" />
                        <div
                            class="relative grid aspect-square w-full cursor-grab place-items-center overflow-hidden rounded-lg border p-1 active:cursor-grabbing"
                            :class="[item.blocked ? 'border-red-300 bg-red-50 dark:border-red-800 dark:bg-red-950/40' : 'border-surface bg-surface-0 dark:bg-surface-900', orderDragId === item.id ? 'opacity-40' : '']"
                            data-testid="my-jjals-order-item"
                        >
                            <img v-if="!item.blocked" :src="item.url" alt="" class="pointer-events-none size-full object-contain" draggable="false" loading="lazy" decoding="async" />
                            <span v-else class="flex flex-col items-center gap-1 text-[0.6875rem] font-semibold text-red-600 dark:text-red-400">
                                <i class="pi pi-eye-slash text-sm" aria-hidden="true" />{{ t('jjal.blockedCell') }}
                            </span>
                            <span class="absolute left-1 top-1 rounded bg-surface-900/70 px-1 text-[0.625rem] font-semibold tabular-nums leading-4 text-white" aria-hidden="true">{{ i + 1 }}</span>
                        </div>
                        <div class="flex justify-between gap-1">
                            <Button
                                type="button"
                                icon="pi pi-arrow-left"
                                size="small"
                                severity="secondary"
                                variant="text"
                                class="size-7!"
                                :aria-label="t('jjal.moveEarlier', { n: i + 1 })"
                                :disabled="i === 0 || savingOrder"
                                :data-order-move="`${item.id}-earlier`"
                                @click="moveOrder(i, i - 1, `${item.id}-earlier`)"
                            />
                            <Button
                                type="button"
                                icon="pi pi-arrow-right"
                                size="small"
                                severity="secondary"
                                variant="text"
                                class="size-7!"
                                :aria-label="t('jjal.moveLater', { n: i + 1 })"
                                :disabled="i === orderDraft.length - 1 || savingOrder"
                                :data-order-move="`${item.id}-later`"
                                @click="moveOrder(i, i + 1, `${item.id}-later`)"
                            />
                        </div>
                    </li>
                </ul>
                <p class="sr-only" role="status" aria-live="polite">{{ orderLive }}</p>
                <ul v-if="!ordering" class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(5rem,1fr))] gap-2 p-4" data-testid="my-jjals-grid">
                    <li v-for="(item, i) in jjalItems" :key="item.id">
                        <button
                            type="button"
                            class="relative grid aspect-square w-full place-items-center overflow-hidden rounded-lg border p-1 focus-visible:outline-2 focus-visible:outline-primary"
                            :class="[
                                item.blocked ? 'border-red-300 bg-red-50 dark:border-red-800 dark:bg-red-950/40' : 'border-surface bg-surface-0 dark:bg-surface-900',
                                selecting && selected.includes(item.id) ? 'ring-2 ring-primary' : '',
                            ]"
                            :aria-label="jjalCellLabel(item, i)"
                            :aria-pressed="selecting ? selected.includes(item.id) : undefined"
                            :disabled="removingJjals"
                            :data-testid="item.blocked ? 'my-jjals-item-blocked' : 'my-jjals-item'"
                            @click="toggleJjal(item.id)"
                        >
                            <img v-if="!item.blocked" :src="item.url" alt="" class="size-full object-contain" loading="lazy" decoding="async" />
                            <span v-else class="flex flex-col items-center gap-1 text-[0.6875rem] font-semibold text-red-600 dark:text-red-400">
                                <i class="pi pi-eye-slash text-sm" aria-hidden="true" />{{ t('jjal.blockedCell') }}
                            </span>
                            <span
                                v-if="selecting"
                                class="absolute right-1 top-1 grid size-5 place-items-center rounded-full border"
                                :class="selected.includes(item.id) ? 'border-primary bg-primary text-primary-contrast' : 'border-surface bg-surface-0/90 dark:bg-surface-900/90'"
                                aria-hidden="true"
                            >
                                <i v-if="selected.includes(item.id)" class="pi pi-check text-[0.5625rem]" />
                            </span>
                        </button>
                    </li>
                </ul>
            </template>
            <p class="m-0 border-t border-surface px-4 py-3 text-xs leading-relaxed text-muted-color">{{ t('jjal.pickerNote') }}</p>
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
import type { MyJjalItem } from '~/composables/jjal/api/useJjalApi.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilJjal from '~/utils/util-jjal';
import UtilJjalMessages from '~/utils/util-jjal-messages';
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
 * - 내 짤(my-jjal-contract — 목업): 격자(내가 정한 순서) + `n / 50` · 올리기(처음이면 안내 창) · 순서 바꾸기(끌어 옮기기 + 앞으로·뒤로 단추 → 저장·취소, `409` 면 다시 받기) ·
 *   고르기 → 빼기(확인 창 — 내 보관함 줄만 빠진다) · 내려간 짤은 빨간 칸 + 안내. 상태는 고르기 창과 같다(`useMyJjals`).
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
    void myJjals.reload();
    void loadPurchases();
    void commerce.ensureLoaded();
    void publicApi
        .rules()
        .then((r) => (refundDays.value = r.refundDays))
        .catch(() => undefined);
});

/* ── 내 짤 ── */
const myJjals = useMyJjals();
const jjalUi = useJjalUi();
const { list: jjalList, error: jjalError } = myJjals;
/** 내 짤 칸 — 내가 정한 순서 그대로(서버 차례), 내려간 짤도 자리가 있다 */
const jjalItems = computed(() => jjalList.value?.items ?? []);
const jjalBlocked = computed(() => UtilJjal.blockedCount(jjalItems.value));
const selecting = ref(false);
const selected = ref<string[]>([]);
const removingJjals = ref(false);

/** 칸의 읽는 이름 — 차례 · 올린/담은 짤 · (내려간 짤) */
function jjalCellLabel(item: MyJjalItem, index: number): string {
    const parts = [t('jjal.myItemAria', { n: index + 1 }), t(item.source === 'UPLOAD' ? 'jjal.mySourceUpload' : 'jjal.mySourceCollect')];
    if (item.blocked) parts.push(t('jjal.blockedCell'));
    return parts.join(' · ');
}

/** 칸을 누르면 고르기 — 고르는 중이 아니면 고르기를 켜고 그 칸을 고른다 */
function toggleJjal(id: string): void {
    if (!selecting.value) selecting.value = true;
    selected.value = selected.value.includes(id) ? selected.value.filter((v) => v !== id) : [...selected.value, id];
}

function stopSelecting(): void {
    selecting.value = false;
    selected.value = [];
}

/* ── 내 짤 순서 바꾸기 ── */
const ordering = ref(false);
/** 옮기는 중인 순서(저장 전) — 취소하면 버린다 */
const orderDraft = ref<MyJjalItem[]>([]);
const savingOrder = ref(false);
const orderLive = ref('');
const orderDragId = ref<string | null>(null);
/** 놓을 자리 — 그 칸의 앞(after=false)·뒤 */
const orderMark = ref<{ id: string; after: boolean } | null>(null);
const orderChanged = computed(
    () =>
        !UtilJjal.sameOrder(
            orderDraft.value.map((i) => i.id),
            jjalItems.value.map((i) => i.id),
        ),
);

function startOrdering(): void {
    stopSelecting();
    orderDraft.value = [...jjalItems.value];
    orderLive.value = '';
    ordering.value = true;
}

function cancelOrdering(): void {
    ordering.value = false;
    orderDraft.value = [];
    resetOrderDrag();
}

/**
 * 한 칸 옮긴다(앞으로·뒤로 단추·끌어 놓기 공통). 단추로 옮겼으면 옮긴 칸의 같은 단추에 초점을 둔다(키보드로 이어서 옮기게).
 * @param from - 옮길 칸의 차례.
 * @param to - 옮긴 뒤 차례.
 * @param focusKey - 옮긴 뒤 초점을 줄 단추(`data-order-move`).
 */
function moveOrder(from: number, to: number, focusKey: string | null = null): void {
    if (to < 0 || to >= orderDraft.value.length || from === to) return;
    orderDraft.value = UtilJjal.move(orderDraft.value, from, to);
    orderLive.value = t('jjal.movedTo', { n: to + 1 });
    if (focusKey) {
        void nextTick(() => {
            const find = (key: string) => document.querySelector<HTMLButtonElement>(`[data-order-move="${key}"]`);
            // 끝에 닿아 그 단추가 꺼졌으면 반대쪽 단추로
            const other = focusKey.endsWith('-earlier') ? focusKey.replace(/-earlier$/, '-later') : focusKey.replace(/-later$/, '-earlier');
            const el = find(focusKey);
            (el && !el.disabled ? el : find(other))?.focus();
        });
    }
}

function onOrderDragStart(event: DragEvent, id: string): void {
    orderDragId.value = id;
    orderMark.value = null;
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', id);
    }
}

function onOrderDragOver(event: DragEvent, id: string): void {
    if (orderDragId.value === null) return;
    event.preventDefault();
    if (id === orderDragId.value) {
        orderMark.value = null;
        if (event.dataTransfer) event.dataTransfer.dropEffect = 'none';
        return;
    }
    // 격자라 칸의 왼쪽 반 = 앞, 오른쪽 반 = 뒤
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const after = (event.clientX - box.left) / Math.max(box.width, 1) >= 0.5;
    if (orderMark.value?.id !== id || orderMark.value.after !== after) orderMark.value = { id, after };
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
}

function onOrderDrop(event: DragEvent, id: string): void {
    event.preventDefault();
    const from = orderDragId.value;
    const mark = orderMark.value;
    resetOrderDrag();
    if (from === null || !mark || mark.id !== id) return;
    const fromIndex = orderDraft.value.findIndex((i) => i.id === from);
    // 끈 칸을 뺀 차례에서 놓은 칸의 앞·뒤 자리
    const rest = orderDraft.value.filter((i) => i.id !== from);
    const to = rest.findIndex((i) => i.id === id) + (mark.after ? 1 : 0);
    moveOrder(fromIndex, to);
}

function resetOrderDrag(): void {
    orderDragId.value = null;
    orderMark.value = null;
}

/** [저장] — 전체 ID 를 새 순서로 한 번. 보관함과 맞지 않으면(`409`) 알리고 목록을 다시 받는다 */
async function saveOrdering(): Promise<void> {
    if (savingOrder.value) return;
    if (!orderChanged.value) {
        cancelOrdering();
        return;
    }
    savingOrder.value = true;
    try {
        await myJjals.saveOrder(orderDraft.value.map((i) => i.id));
        toast.success(t('jjal.orderSaved'));
        cancelOrdering();
    } catch (e) {
        toast.error(UtilJjalMessages.error(t, e), 6000);
        if (e instanceof ApiError && e.status === 409) {
            cancelOrdering();
            void myJjals.reload();
        }
    } finally {
        savingOrder.value = false;
    }
}

/** 고른 짤을 내 보관함에서 뺀다(확인 창) — 이미 쓴 글·댓글의 짤은 그대로 */
async function removeSelected(): Promise<void> {
    const ids = [...selected.value];
    if (ids.length === 0 || removingJjals.value) return;
    const ok = await confirm({ title: t('jjal.removeConfirmTitle', { count: ids.length }), message: t('jjal.removeConfirmText'), confirmLabel: t('jjal.myRemove'), tone: 'danger' });
    if (!ok) return;
    removingJjals.value = true;
    const done: string[] = [];
    try {
        for (const id of ids) {
            await myJjals.remove(id);
            done.push(id);
        }
        toast.success(t('jjal.removed', { count: done.length }));
        stopSelecting();
    } catch (e) {
        if (done.length > 0) toast.success(t('jjal.removed', { count: done.length }));
        toast.error(UtilJjalMessages.error(t, e), 6000);
        selected.value = selected.value.filter((id) => !done.includes(id));
    } finally {
        removingJjals.value = false;
    }
}

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
