<template>
    <!--
        체험단 모집 상세 `/trials/{id}`(trial-group-contract 「광고 표시」·「모집 · 신청 · 뽑기」·「구현 결정」 9 — 서버 `TrialPublicDetail`).
        위: 「광고」 딱지 + 광고주 이름 · 대표 그림 · 제목 · 요약 표(형태 · 제품값 · 인원(리뷰어 몫/일반 몫) · 신청 기간 · 리뷰어 먼저 신청(금·은 시각) · 사용기 기한 · (대여형) 반납 기한 · 보내는 쪽 · 사용기 게시판) · 설명.
        신청 칸(`mine` — 비회원이면 null): 신청할 수 있으면 [신청하기](대화상자 — 배송지 관리의 배송지 고르기 · 서버가 준 동의 항목 체크) · 이미 신청했으면 상태 + [신청 취소](`canCancel`) ·
          못 하면 까닭(`blockedReason` — 본인인증·AI/운영팀 계정·신청 금지·창 전·마감). 대여형은 청구 한도(`damageChargeLimit`)와 파손 동의 문구를 계약 그대로 보인다.
        진행 중·끝난 모집은 아래에 연결 사용기 모아 보기. 신청 실패는 서버 문구 그대로(판정은 서버).
    -->
    <div class="content-card flex flex-col gap-4" data-testid="trial-detail">
        <NuxtLink :to="UtilTrial.LIST_PATH" class="self-start text-xs font-semibold text-primary hover:underline">{{ t('trial.backToList') }}</NuxtLink>

        <div v-if="state === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <div class="aspect-[16/9] animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-6 w-2/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="h-32 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="state === 'notfound'" code="404" :title="t('trial.notFoundTitle')" :description="t('trial.notFoundText')">
            <Button as="router-link" :to="UtilTrial.LIST_PATH" :label="t('trial.backToList')" />
        </ErrorState>
        <ErrorState v-else-if="state === 'error' || !detail" :title="t('trial.loadFailed')" :description="errorMessage" retry @retry="load" />

        <template v-else>
            <article class="flex flex-col gap-4" :aria-labelledby="titleId">
                <div class="relative overflow-hidden rounded-xl bg-surface-100 dark:bg-surface-800">
                    <img v-if="card!.imageUrl" :src="card!.imageUrl" alt="" class="aspect-[16/9] w-full object-cover" />
                    <span v-else class="grid aspect-[16/9] place-items-center text-muted-color" aria-hidden="true"><i class="pi pi-box text-4xl" /></span>
                </div>
                <div class="flex flex-col gap-1">
                    <p class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
                        <span class="inline-flex items-center justify-center rounded bg-amber-500 px-1.5 py-1 text-[0.6875rem] font-bold leading-none text-white"><span class="relative top-[-0.09em]">{{ t('trial.adBadge') }}</span></span>
                        {{ t('trial.byAdvertiser', { name: card!.advertiserName }) }}
                    </p>
                    <h1 :id="titleId" class="m-0 text-lg font-bold">{{ card!.title }}</h1>
                    <p class="m-0 text-sm text-muted-color">{{ card!.productName }}</p>
                </div>

                <dl class="m-0 grid grid-cols-[7.5rem_minmax(0,1fr)] gap-x-3 gap-y-2 rounded-xl border border-surface p-3 text-sm" data-testid="trial-summary">
                    <dt class="text-muted-color">{{ t('trial.fieldForm') }}</dt>
                    <dd class="m-0">{{ UtilTrialMessages.formLine(t, card!.form) }}</dd>
                    <dt class="text-muted-color">{{ t('trial.fieldPrice') }}</dt>
                    <dd class="m-0 tabular-nums">{{ UtilTrialMessages.won(t, card!.productPrice, locale) }}</dd>
                    <dt class="text-muted-color">{{ t('trial.fieldRecruit') }}</dt>
                    <dd class="m-0">{{ UtilTrialMessages.quotaLine(t, card!.recruitCount, detail.reviewerShare) }}</dd>
                    <dt class="text-muted-color">{{ t('trial.fieldPeriod') }}</dt>
                    <dd class="m-0 tabular-nums">{{ t('trial.periodLine', { from: time.full(card!.applyStartAt), to: time.full(card!.applyEndAt) }) }}</dd>
                    <dt class="text-muted-color">{{ t('trial.fieldEarly') }}</dt>
                    <dd class="m-0 flex flex-col gap-0.5 text-[13px]">
                        <span v-if="detail.reviewerGoldHours > 0">{{ t('trial.earlyGold', { h: detail.reviewerGoldHours, at: time.full(card!.goldOpenAt) }) }}</span>
                        <span v-if="detail.reviewerSilverHours > 0">{{ t('trial.earlySilver', { h: detail.reviewerSilverHours, at: time.full(card!.silverOpenAt) }) }}</span>
                        <span class="text-muted-color">{{ t('trial.earlyBronze') }}</span>
                    </dd>
                    <dt class="text-muted-color">{{ t('trial.fieldReviewDue') }}</dt>
                    <dd class="m-0">{{ t('trial.reviewDueLine', { n: detail.reviewDays }) }}</dd>
                    <template v-if="card!.form === 'LEND' && detail.returnDays">
                        <dt class="text-muted-color">{{ t('trial.fieldReturnDue') }}</dt>
                        <dd class="m-0">{{ t('trial.returnDueLine', { n: detail.returnDays }) }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ t('trial.fieldShipper') }}</dt>
                    <dd class="m-0">{{ t(`trial.shipper.${detail.shipper}`) }}</dd>
                    <dt class="text-muted-color">{{ t('trial.fieldBoard') }}</dt>
                    <dd class="m-0">
                        <NuxtLink :to="`/boards/${encodeURIComponent(card!.reviewBoard.slug)}`" class="text-primary hover:underline">{{ card!.reviewBoard.name }}</NuxtLink>
                    </dd>
                </dl>

                <!-- 대여형 — 청구 한도(제품값)·정상 마모 안내 -->
                <Message v-if="card!.form === 'LEND'" severity="warn" :closable="false" size="small" data-testid="trial-lend-notice">
                    <p class="m-0">{{ t('trial.lendNotice', { price: UtilTrialMessages.won(t, detail.damageChargeLimit ?? card!.productPrice, locale) }) }}</p>
                    <p class="m-0 mt-1">{{ t('trial.consentText.DAMAGE_CHARGE') }}</p>
                </Message>

                <p v-if="detail.description" class="m-0 whitespace-pre-line text-[0.9375rem] leading-relaxed">{{ detail.description }}</p>
            </article>

            <!-- 신청 -->
            <section class="panel-card flex flex-col gap-3 rounded-xl border border-surface p-4" :aria-label="t('trial.applySection')" data-testid="trial-apply">
                <template v-if="!mine">
                    <Message severity="secondary" :closable="false" size="small">{{ t('trial.blockedLogin') }}</Message>
                    <Button type="button" :label="t('common.login')" size="small" class="self-start" @click="prompt('view')" />
                </template>
                <template v-else-if="mine.applicationId && mine.applicationStatus">
                    <p class="m-0 flex items-center gap-2 text-sm font-semibold"><i class="pi pi-check-circle text-green-600 dark:text-green-400" aria-hidden="true" />{{ t(`trial.applicationStatus.${mine.applicationStatus}`) }}</p>
                    <p class="m-0 text-xs text-muted-color">{{ t(`trial.quota.${mine.quota}`) }}</p>
                    <div class="flex flex-wrap gap-2">
                        <Button as="router-link" :to="UtilTrial.MY_PATH" :label="t('trial.myTitle')" size="small" severity="secondary" variant="outlined" />
                        <Button v-if="mine.canCancel" type="button" :label="t('trial.cancelApply')" size="small" severity="danger" variant="text" :loading="cancelling" data-testid="trial-cancel" @click="cancel" />
                    </div>
                </template>
                <template v-else-if="mine.canApply">
                    <p class="m-0 text-xs text-muted-color">{{ mine.reviewerTier ? t('trial.reviewerHint', { tier: t(`trial.tier.${mine.reviewerTier}`) }) : t('trial.generalHint') }}</p>
                    <Button type="button" :label="t('trial.apply')" icon="pi pi-send" class="self-start" data-testid="trial-apply-open" @click="openApply" />
                </template>
                <template v-else-if="mine.blockedReason">
                    <Message severity="secondary" :closable="false" size="small" data-testid="trial-blocked">{{ UtilTrialMessages.blocked(t, mine.blockedReason, mine.applyOpenAt, time.full) }}</Message>
                    <Button v-if="mine.blockedReason === 'IDENTITY_REQUIRED'" as="router-link" to="/settings/identity" :label="t('trial.goIdentity')" size="small" class="self-start" />
                </template>
            </section>

            <!-- 사용기 모아 보기 — 진행 중·끝난 모집 -->
            <section v-if="card!.status === 'RUNNING' || card!.status === 'CLOSED'" class="flex flex-col gap-2" :aria-labelledby="reviewsId" data-testid="trial-reviews">
                <h2 :id="reviewsId" class="m-0 text-base font-bold">{{ t('trial.reviewsTitle', { n: detail.reviews.length }) }}</h2>
                <EmptyState v-if="detail.reviews.length === 0" icon="pi pi-pencil" :title="t('trial.reviewsEmpty')" />
                <ul v-else class="m-0 list-none p-0">
                    <li v-for="r in detail.reviews" :key="r.postId" class="border-b border-surface last:border-b-0">
                        <NuxtLink :to="`/posts/${encodeURIComponent(r.postId)}`" class="flex flex-col gap-0.5 py-2.5 hover:bg-surface-50 dark:hover:bg-surface-800/40">
                            <span class="line-clamp-1 text-sm font-medium">{{ r.title }}</span>
                            <span class="flex gap-2 text-xs tabular-nums text-muted-color">
                                <span>{{ r.author.nickname ?? '' }}</span>
                                <span>{{ time.date(r.createdAt) }}</span>
                            </span>
                        </NuxtLink>
                    </li>
                </ul>
            </section>
        </template>

        <!-- 신청 대화상자 — 배송지 고르기 · 동의 -->
        <Dialog v-model:visible="applyOpen" modal :header="t('trial.applyTitle')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" :draggable="false" :closable="!applying">
            <form v-if="detail" class="flex flex-col gap-4" novalidate data-testid="trial-apply-form" @submit.prevent="submitApply">
                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                    <legend class="mb-1 text-sm font-medium">{{ t('trial.addressLegend') }}</legend>
                    <div v-if="addressState === 'loading'" class="h-16 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('common.loading')" />
                    <Message v-else-if="addressState === 'error'" severity="error" variant="simple" size="small">{{ addressError }}</Message>
                    <div v-else-if="addresses.length === 0" class="flex flex-col items-start gap-2 rounded-lg border border-dashed border-surface p-3 text-sm">
                        <span>{{ t('trial.addressNone') }}</span>
                        <Button as="router-link" to="/settings/addresses" :label="t('trial.addressManage')" size="small" severity="secondary" variant="outlined" />
                    </div>
                    <template v-else>
                        <label v-for="a in addresses" :key="a.id" class="flex cursor-pointer items-start gap-2 rounded-lg border p-2.5 text-sm" :class="addressId === a.id ? 'border-primary bg-primary/5' : 'border-surface'">
                            <RadioButton v-model="addressId" :value="a.id" :input-id="`${baseId}-addr-${a.id}`" name="trial-address" class="mt-0.5" />
                            <span class="min-w-0">
                                <span class="flex items-center gap-1.5 font-medium">{{ a.label || a.recipient }}<Tag v-if="a.isDefault" :value="t('trial.addressDefault')" severity="secondary" class="text-[0.625rem]" /></span>
                                <span class="block text-xs text-muted-color">{{ a.recipient }} · {{ a.phone }}</span>
                                <span class="block text-xs text-muted-color">({{ a.postcode }}) {{ a.address1 }} {{ a.address2 }}</span>
                            </span>
                        </label>
                        <NuxtLink to="/settings/addresses" class="self-start text-xs text-primary hover:underline">{{ t('trial.addressManage') }}</NuxtLink>
                    </template>
                    <small v-if="fieldErrors.addressId" class="text-xs text-red-600 dark:text-red-400">{{ fieldErrors.addressId }}</small>
                </fieldset>

                <fieldset v-if="detail.consents.length" class="m-0 flex flex-col gap-2 border-0 p-0">
                    <legend class="mb-1 text-sm font-medium">{{ t('trial.consentLegend') }}</legend>
                    <label v-for="c in detail.consents" :key="c" class="flex cursor-pointer items-start gap-2 text-[13px] leading-relaxed">
                        <Checkbox v-model="consents" :value="c" :input-id="`${baseId}-consent-${c}`" class="mt-0.5" />
                        <span>{{ consentText(c) }}</span>
                    </label>
                    <small v-if="fieldErrors.consents || (tried && !consentsOk)" class="text-xs text-red-600 dark:text-red-400">{{ fieldErrors.consents || t('trial.consentRequired') }}</small>
                </fieldset>
                <p v-else class="m-0 text-xs text-muted-color">{{ t('trial.consentNone') }}</p>

                <Message v-if="applyError" severity="error" variant="simple" size="small">{{ applyError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="applying" @click="applyOpen = false" />
                    <Button type="submit" :label="t('trial.applySubmit')" :loading="applying" :disabled="addresses.length === 0" data-testid="trial-apply-submit" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ShippingAddress } from '~/composables/address/api/useAddressBookApi.types';
import type { TrialConsent, TrialPublicDetail } from '~/composables/trial/api/useTrialApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilTrial from '~/utils/util-trial';
import UtilTrialMessages from '~/utils/util-trial-messages';

const { t, locale } = useI18n();
const route = useRoute();
const api = useTrialApi();
const addressApi = useAddressBookApi();
const toast = useAppToast();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const { user } = useAuth();
const baseId = useId();
const titleId = useId();
const reviewsId = useId();

const id = computed(() => String(route.params.id ?? ''));
const detail = ref<TrialPublicDetail | null>(null);
const card = computed(() => detail.value?.card ?? null);
const mine = computed(() => detail.value?.mine ?? null);
const state = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const errorMessage = ref('');

let seq = 0;
async function load(): Promise<void> {
    const mine2 = ++seq;
    state.value = 'loading';
    try {
        const res = await api.detail(id.value);
        if (mine2 !== seq) return;
        detail.value = res;
        state.value = 'ready';
    } catch (e) {
        if (mine2 !== seq) return;
        if (e instanceof ApiError && e.status === 404) {
            state.value = 'notfound';
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        state.value = 'error';
    }
}
watch(id, () => void load(), { immediate: true });

/* ── 신청 ── */
const applyOpen = ref(false);
const addresses = ref<ShippingAddress[]>([]);
const addressState = ref<'loading' | 'ready' | 'error'>('loading');
const addressError = ref('');
const addressId = ref<string | null>(null);
const consents = ref<TrialConsent[]>([]);
const tried = ref(false);
const applying = ref(false);
const applyError = ref('');
const fieldErrors = ref<Record<string, string>>({});

const consentsOk = computed(() => (detail.value?.consents ?? []).every((c) => consents.value.includes(c)));

function consentText(c: TrialConsent): string {
    const d = detail.value;
    if (!d) return '';
    const name = d.card.advertiserName;
    if (c === 'PROVIDE_CONTACT') return t('trial.consentText.PROVIDE_CONTACT', { name });
    if (c === 'UNRETURNED_CONTACT') return t('trial.consentText.UNRETURNED_CONTACT', { name });
    if (c === 'PRODUCT_CHARGE') return t('trial.consentText.PRODUCT_CHARGE', { name, price: UtilTrialMessages.won(t, d.damageChargeLimit ?? d.card.productPrice, locale.value) });
    return t('trial.consentText.DAMAGE_CHARGE');
}

async function openApply(): Promise<void> {
    if (!user.value) return prompt('view');
    applyOpen.value = true;
    tried.value = false;
    applyError.value = '';
    fieldErrors.value = {};
    consents.value = [];
    addressState.value = 'loading';
    try {
        const res = await addressApi.list();
        addresses.value = res.items;
        addressId.value = res.items.find((a) => a.isDefault)?.id ?? res.items[0]?.id ?? null;
        addressState.value = 'ready';
    } catch (e) {
        addressError.value = UtilBoardMessages.of(t, e);
        addressState.value = 'error';
    }
}

async function submitApply(): Promise<void> {
    tried.value = true;
    const d = detail.value;
    if (!d || applying.value) return;
    if (!addressId.value) {
        fieldErrors.value = { addressId: t('trial.addressRequired') };
        return;
    }
    if (!consentsOk.value) return;
    applying.value = true;
    applyError.value = '';
    fieldErrors.value = {};
    try {
        const next = await api.apply(d.card.id, { addressId: addressId.value, consents: consents.value.slice() });
        detail.value = { ...d, mine: next };
        applyOpen.value = false;
        toast.success(t('trial.applied'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') prompt('view');
        else if (e instanceof ApiError && e.fields && (e.fields.addressId || e.fields.consents)) fieldErrors.value = { ...e.fields };
        else applyError.value = UtilBoardMessages.of(t, e);
    } finally {
        applying.value = false;
    }
}

/* ── 신청 취소 ── */
const cancelling = ref(false);
async function cancel(): Promise<void> {
    const d = detail.value;
    if (!d || cancelling.value) return;
    const ok = await confirm({ title: t('trial.cancelTitle'), message: t('trial.cancelText'), confirmLabel: t('trial.cancelApply'), tone: 'danger' });
    if (!ok) return;
    cancelling.value = true;
    try {
        await api.cancel(d.card.id);
        toast.success(t('trial.cancelled'));
        await load();
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        cancelling.value = false;
    }
}

useSeoMeta({ title: () => (detail.value ? `${detail.value.card.title} · ${t('trial.title')}` : t('trial.title')) });
</script>
