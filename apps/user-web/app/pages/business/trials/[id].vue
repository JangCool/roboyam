<template>
    <!--
        사업자 센터 › 체험단 모집 상세 `/business/trials/{id}`(trial-group-contract 「구현 결정」 8 — 광고주, 서버 `BusinessTrialDetail { trial, applicants }`).
        위: 상태·형태·보내는 쪽·제목·인원·기간·제품값. 아래 두 구획:
          ① 신청자 — 닉네임 · 리뷰어 단계 · 몫 · 사용기 수 · 평균 추천 · 약속 지킨 비율 · [후보 추천] 켬/끔(최종 선정은 운영자 — 회원 개인정보는 없다).
          ② 뽑힌 사람 — 보내는 쪽이 광고주면 받을 곳(동의 범위 — 반납 확인(제공형은 받음) 뒤 파기) · [송장 넣기](발송 전 사진 필수 — 없으면 처리 안 됨) · 받음 · 사용기 ·
             (대여형) [반납 받음](반납 사진) · [파손 신고](사진 + 설명 — 회원 7일 이의 뒤 운영자 판정). 보내는 쪽이 운영자면 주소 없이 진행 상황만(발송은 운영자가 한다).
        광고 결제(V242 「유료 결제 연결」 — `trial.payment`): 머리 아래 구획 — 상태 · 금액 · 기한/결제 시각 + 상태별 안내.
          온라인 결제 대기이고 `payOnline` 이면 [결제하기](신청 건 `/business/applications/{id}?pay=1` — 그 화면이 결제창을 연다), 그 밖 신청 건이 있으면 [신청 건 보기](입금 안내 등).
          `shippingAllowed` false 면 [송장 넣기]를 막고 안내(서버 409 `TRIAL_PAYMENT_PENDING` — 문구를 보이고 다시 읽는다). 운영자가 취소한 줄은 「운영자가 취소했어요」 + 단추 없음.
          결제 방식(V244 `payment.timing`): 제목 옆 딱지(선결제 · 뽑기 뒤 결제) + 선결제면 「모집 인원 기준 금액 · 덜 모여도 차액 환불 없음」, 상태 안내도 결제 방식마다(`notePrepaid`).
        공개 전 선결제 모집(`status` DRAFT — 계약 「후속 고치기」, 서버 `TrialPayments.advertiserSeesDraft`): 딱지 「공개 전」, 신청자·뽑힌 사람 구획과 신청 수를 숨기고(서버도 빈 배열) 결제 구획만.
          공개 전 안내(결제를 마치면 운영팀이 연다 · 결제 끝이면 「운영팀이 열면 신청을 받아요」), 결제 기한 만료·반려면 「운영팀에 결제 다시 요청을 부탁하세요」.
        서버 오류는 대화상자 안(칸 오류는 칸 아래)·목록 한 줄 실패는 토스트.
    -->
    <div class="content-card flex flex-col gap-4" data-testid="business-trial-detail">
        <NuxtLink :to="UtilTrial.BIZ_PATH" class="self-start text-xs font-semibold text-primary hover:underline">{{ t('trial.bizTitle') }}</NuxtLink>

        <div v-if="state === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <EmptyState v-else-if="state === 'blocked'" icon="pi pi-lock" :title="t('trial.bizBlockedTitle')" :description="t('trial.bizBlockedText')" data-testid="business-trial-blocked">
            <NuxtLink :to="UtilBusiness.CENTER_PATH" class="text-sm font-semibold text-primary hover:underline">{{ t('business.home.title') }}</NuxtLink>
        </EmptyState>
        <ErrorState v-else-if="state === 'error' || !view" :title="t('trial.loadFailed')" :description="errorMessage" retry @retry="load" />

        <template v-else>
            <header class="flex flex-col gap-1">
                <p class="m-0 flex flex-wrap items-center gap-1.5">
                    <span :class="[TAG, 'bg-surface-100 dark:bg-surface-800']"><span class="relative top-[-0.09em]">{{ t(`trial.status.${trial!.status}`) }}</span></span>
                    <span :class="[TAG, 'bg-surface-100 text-muted-color dark:bg-surface-800']"><span class="relative top-[-0.09em]">{{ t(`trial.form.${trial!.form}`) }}</span></span>
                    <span class="text-xs text-muted-color">{{ t(`trial.shipper.${trial!.shipper}`) }}</span>
                </p>
                <h1 class="m-0 text-lg font-bold">{{ trial!.title }}</h1>
                <p class="m-0 flex flex-wrap gap-x-3 text-xs tabular-nums text-muted-color">
                    <span>{{ trial!.productName }} · {{ UtilTrialMessages.won(t, trial!.productPrice, locale) }}</span>
                    <span v-if="!isDraft">{{ t('trial.bizApplicants', { n: view.applicants.items.length, total: trial!.recruitCount }) }}</span>
                    <span>{{ t('trial.periodLine', { from: time.full(trial!.applyStartAt), to: time.full(trial!.applyEndAt) }) }}</span>
                </p>
            </header>

            <!-- 광고 결제(V242) -->
            <section class="flex flex-col gap-2 rounded-xl border p-3 text-sm" :class="paymentBorder" :aria-labelledby="paymentId" data-testid="business-trial-payment" :data-status="trial!.payment.status">
                <h2 :id="paymentId" class="m-0 flex flex-wrap items-center gap-1.5 text-base font-bold">
                    {{ t('trial.bizPayment.title') }}
                    <span :class="[TAG, paymentTone]"><span class="relative top-[-0.09em]">{{ t(`trial.bizPayment.status.${trial!.payment.status}`) }}</span></span>
                    <span :class="[TAG, 'bg-surface-100 text-muted-color dark:bg-surface-800']" data-testid="business-trial-payment-timing"><span class="relative top-[-0.09em]">{{ t(`trial.bizPayment.timing.${trial!.payment.timing}`) }}</span></span>
                </h2>
                <p v-if="trial!.payment.timing === 'PREPAID'" class="m-0 text-xs">{{ t('trial.bizPayment.prepaidRule', { n: trial!.recruitCount }) }}</p>
                <p v-if="trial!.payment.required" class="m-0 flex flex-wrap gap-x-3 text-xs tabular-nums">
                    <span class="font-semibold">{{ t('trial.bizPayment.amount', { total: won(trial!.payment.total), supply: won(trial!.payment.supply), vat: won(trial!.payment.vat) }) }}</span>
                    <span v-if="trial!.payment.paidAt" class="text-muted-color">{{ t('trial.bizPayment.paidAt', { at: time.full(trial!.payment.paidAt) }) }}</span>
                    <span v-else-if="trial!.payment.deadlineAt" class="text-muted-color">{{ t('trial.bizPayment.deadline', { at: time.full(trial!.payment.deadlineAt) }) }}</span>
                </p>
                <p class="m-0 text-xs text-muted-color">{{ trial!.payment.timing === 'PREPAID' ? t(`trial.bizPayment.notePrepaid.${trial!.payment.status}`) : t(`trial.bizPayment.note.${trial!.payment.status}`) }}</p>
                <!-- 공개 전 선결제 모집 — 결제 건이 닫혔으면(만료·반려) 운영팀에 다시 요청, 아니면 공개 전 안내 -->
                <template v-if="isDraft">
                    <Message v-if="draftClosed" severity="warn" size="small" :closable="false" data-testid="business-trial-draft-request-again">{{ t('trial.bizPayment.draftRequestAgain') }}</Message>
                    <Message v-else severity="info" size="small" :closable="false" data-testid="business-trial-draft-notice">
                        {{ trial!.payment.status === 'PAID' ? t('trial.bizPayment.draftPaid') : t('trial.bizPayment.draftNotice') }}
                    </Message>
                </template>
                <div v-if="trial!.payment.bizApplicationId && trial!.payment.status !== 'NOT_REQUIRED'" class="flex flex-wrap gap-2">
                    <Button
                        v-if="trial!.payment.payOnline"
                        as="router-link"
                        :to="{ path: UtilBusiness.applicationPath(trial!.payment.bizApplicationId), query: { pay: '1' } }"
                        :label="t('trial.bizPayment.pay')"
                        icon="pi pi-credit-card"
                        size="small"
                        data-testid="business-trial-pay"
                    />
                    <Button
                        v-else
                        as="router-link"
                        :to="UtilBusiness.applicationPath(trial!.payment.bizApplicationId)"
                        :label="t('trial.bizPayment.openApplication')"
                        icon="pi pi-file"
                        size="small"
                        severity="secondary"
                        variant="outlined"
                    />
                </div>
            </section>

            <!-- ① 신청자 -->
            <section v-if="!isDraft && view.applicants.items.length" class="flex flex-col gap-2" :aria-labelledby="applicantsId">
                <h2 :id="applicantsId" class="m-0 text-base font-bold">{{ t('trial.bizApplicantsTitle', { n: view.applicants.items.length }) }}</h2>
                <p class="m-0 text-xs text-muted-color">{{ t('trial.bizRecommendHint') }} {{ t('trial.bizSlots', { reviewer: view.applicants.reviewerSlots, general: view.applicants.generalSlots }) }}</p>
                <div class="overflow-x-auto rounded-xl border border-surface">
                    <table class="w-full min-w-[36rem] border-collapse text-sm">
                        <thead>
                            <tr class="border-b border-surface text-left text-xs text-muted-color">
                                <th scope="col" class="p-2 font-medium">{{ t('trial.colMember') }}</th>
                                <th scope="col" class="p-2 font-medium">{{ t('trial.colQuota') }}</th>
                                <th scope="col" class="p-2 text-right font-medium">{{ t('trial.colReviews') }}</th>
                                <th scope="col" class="p-2 text-right font-medium">{{ t('trial.colAvgLikes') }}</th>
                                <th scope="col" class="p-2 text-right font-medium">{{ t('trial.colKept') }}</th>
                                <th scope="col" class="p-2 font-medium">{{ t('trial.colRecommend') }}</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="a in view.applicants.items" :key="a.id" class="border-b border-surface last:border-b-0">
                                <td class="p-2">
                                    <span class="font-medium">{{ a.member.nickname ?? '' }}</span>
                                    <span v-if="a.reviewerTier" class="ml-1 text-xs text-muted-color">{{ t('trial.reviewerTierShort', { tier: t(`trial.tier.${a.reviewerTier}`) }) }}</span>
                                </td>
                                <td class="p-2 text-xs">{{ t(`trial.quota.${a.quota}`) }}</td>
                                <td class="p-2 text-right tabular-nums">{{ a.stats.reviewPosts }}</td>
                                <td class="p-2 text-right tabular-nums">{{ a.stats.averageLikes.toFixed(1) }}</td>
                                <td class="p-2 text-right tabular-nums">{{ a.stats.keptRate === null ? '—' : `${a.stats.keptRate}%` }}</td>
                                <td class="p-2">
                                    <ToggleButton
                                        :model-value="a.recommended"
                                        :on-label="t('trial.recommended')"
                                        :off-label="t('trial.recommend')"
                                        on-icon="pi pi-star-fill"
                                        off-icon="pi pi-star"
                                        size="small"
                                        :disabled="busy || a.status !== 'APPLIED'"
                                        @update:model-value="(v: boolean) => toggleRecommend(a.id, v)"
                                    />
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </section>

            <!-- ② 뽑힌 사람(공개 전이면 없음) -->
            <section v-if="!isDraft" class="flex flex-col gap-2" :aria-labelledby="participantsId">
                <h2 :id="participantsId" class="m-0 text-base font-bold">{{ t('trial.bizParticipantsTitle', { n: trial!.participants.length }) }}</h2>
                <p v-if="trial!.shipper === 'OPERATOR'" class="m-0 text-xs text-muted-color">{{ t('trial.bizOperatorShips') }}</p>
                <Message v-else-if="!trial!.payment.shippingAllowed" severity="warn" size="small" :closable="false" data-testid="business-trial-shipping-blocked">{{ t('trial.bizPayment.shippingBlocked') }}</Message>
                <EmptyState v-if="trial!.participants.length === 0" icon="pi pi-users" :title="t('trial.bizNoParticipants')" />
                <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
                    <li v-for="p in trial!.participants" :key="p.id" class="rounded-xl border border-surface p-3 text-sm">
                        <p class="m-0 flex flex-wrap items-center gap-2">
                            <span class="font-semibold">{{ p.member.nickname ?? '' }}</span>
                            <span class="text-xs text-muted-color">{{ t(`trial.quota.${p.quota}`) }}</span>
                        </p>
                        <!-- 받을 곳(동의 범위 안 — 서버가 볼 수 있을 때만 채운다) -->
                        <div v-if="trial!.shipper === 'ADVERTISER' || p.recipient" class="mt-2 rounded-lg bg-surface-50 p-2.5 text-xs dark:bg-surface-800/50">
                            <p v-if="p.addressPurged" class="m-0 text-muted-color">{{ t('trial.bizAddressPurged') }}</p>
                            <template v-else-if="p.recipient">
                                <p class="m-0">{{ p.recipient }} · {{ p.phone }}</p>
                                <p v-if="p.address1" class="m-0">({{ p.postcode }}) {{ p.address1 }} {{ p.address2 }}</p>
                                <p v-if="p.memo" class="m-0 text-muted-color">{{ t('trial.bizMemo', { memo: p.memo }) }}</p>
                                <p class="m-0 mt-1 text-muted-color">{{ t('trial.bizAddressNotice') }}</p>
                            </template>
                        </div>
                        <ul class="m-0 mt-2 flex list-none flex-col gap-1 p-0 text-xs">
                            <li v-if="p.cancelledAt" class="font-medium text-red-700 dark:text-red-400" data-testid="business-trial-participant-cancelled">{{ t('trial.bizCancelledLine', { at: time.date(p.cancelledAt) }) }}</li>
                            <li v-else>{{ p.trackingNo ? t('trial.trackingLine', { carrier: p.carrier ?? '', no: p.trackingNo }) : t('trial.bizNotShipped') }}</li>
                            <li v-if="p.receivedAt">{{ t('trial.bizReceivedAt', { at: time.date(p.receivedAt) }) }}</li>
                            <li v-if="p.reviewPost">
                                <NuxtLink :to="`/posts/${encodeURIComponent(p.reviewPost.id)}`" class="text-primary hover:underline">{{ p.reviewPost.name }}</NuxtLink>
                            </li>
                            <li v-if="trial!.form === 'LEND' && p.returnDueAt">{{ p.returnedAt ? t('trial.bizReturnedAt', { at: time.date(p.returnedAt) }) : t('trial.bizReturnDue', { date: time.date(p.returnDueAt) }) }}</li>
                            <li v-for="d in damagesOf(p.id)" :key="d.id" class="text-amber-700 dark:text-amber-400">
                                {{ t('trial.bizDamageLine', { at: time.date(d.createdAt), status: d.verdict ? t(`trial.verdict.${d.verdict}`) : t(`trial.damageStatus.${d.status}`) }) }}
                            </li>
                        </ul>
                        <div v-if="p.photos.length" class="mt-2 flex flex-wrap gap-1.5">
                            <TrialPhotoImage v-for="photo in p.photos" :key="photo.id" :url="photo.url" :purged="photo.purged" zoomable />
                        </div>
                        <div v-if="trial!.shipper === 'ADVERTISER' && !p.cancelledAt" class="mt-2 flex flex-wrap gap-2">
                            <Button v-if="!p.shippedAt" type="button" :label="t('trial.bizShip')" icon="pi pi-truck" size="small" :disabled="!trial!.payment.shippingAllowed" @click="openAction('ship', p)" />
                            <Button v-if="trial!.form === 'LEND' && p.receivedAt && !p.returnedAt" type="button" :label="t('trial.bizReturned')" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" @click="openAction('returned', p)" />
                            <Button v-if="trial!.form === 'LEND' && p.returnedAt" type="button" :label="t('trial.bizDamage')" icon="pi pi-exclamation-triangle" size="small" severity="warn" variant="outlined" @click="openAction('damage', p)" />
                        </div>
                    </li>
                </ul>
            </section>
        </template>

        <!-- 송장 · 반납 받음 · 파손 신고 -->
        <Dialog v-model:visible="actionOpen" modal :header="t(`trial.bizActionTitle.${actionKind}`)" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" :draggable="false" :closable="!acting">
            <form v-if="actionTarget" class="flex flex-col gap-3" novalidate @submit.prevent="submitAction">
                <p class="m-0 text-sm">{{ t('trial.bizActionFor', { name: actionTarget.member.nickname ?? '' }) }}</p>
                <template v-if="actionKind === 'ship'">
                    <LabeledField :label="t('trial.carrier')" :error="fieldErrors.carrier ?? ''">
                        <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="carrier" maxlength="30" fluid :invalid="invalid" :aria-describedby="describedBy" :disabled="acting" /></template>
                    </LabeledField>
                    <LabeledField :label="t('trial.trackingNo')" :error="fieldErrors.trackingNo ?? (tried && !trackingNo.trim() ? t('trial.trackingRequired') : '')">
                        <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="trackingNo" maxlength="40" fluid inputmode="numeric" :invalid="invalid" :aria-describedby="describedBy" :disabled="acting" /></template>
                    </LabeledField>
                </template>
                <LabeledField v-if="actionKind === 'damage'" :label="t('trial.damageDescription')" :error="fieldErrors.description ?? (tried && !description.trim() ? t('trial.damageDescriptionRequired') : '')">
                    <template #default="{ id, describedBy, invalid }"><Textarea :id="id" v-model="description" rows="3" auto-resize fluid :maxlength="UtilTrial.TEXT_MAX" :invalid="invalid" :aria-describedby="describedBy" :disabled="acting" /></template>
                </LabeledField>

                <!-- 사진(발송 전 필수 · 반납 · 파손 필수) -->
                <div class="flex flex-col gap-1.5">
                    <span class="text-sm font-medium">{{ t(`trial.photoLabel.${actionKind}`) }}<span v-if="actionKind !== 'returned'" class="ml-1 text-xs text-red-600 dark:text-red-400">{{ t('trial.sourceRequiredMark') }}</span></span>
                    <div class="flex flex-wrap items-center gap-2">
                        <TrialPhotoImage v-for="photo in photos" :key="photo.id" :url="photo.url" box-class="size-16" />
                        <input ref="fileInput" type="file" class="sr-only" :accept="UtilTrial.PHOTO_ACCEPT" :aria-label="t('trial.photoAdd')" :disabled="acting || uploading" @change="onPhoto" />
                        <Button type="button" :label="t('trial.photoAdd')" icon="pi pi-camera" size="small" severity="secondary" variant="outlined" :loading="uploading" :disabled="acting" @click="fileInput?.click()" />
                    </div>
                    <small v-if="photoError" class="text-xs text-red-600 dark:text-red-400">{{ photoError }}</small>
                </div>

                <Message v-if="actionError" severity="error" variant="simple" size="small">{{ actionError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="acting" @click="actionOpen = false" />
                    <Button type="submit" :label="t('common.save')" :loading="acting" :disabled="uploading" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { BizTrialParticipant, BizTrialUploaded, BizTrialView } from '~/composables/business/api/useBusinessTrialApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBusiness from '~/utils/util-business';
import UtilTrial from '~/utils/util-trial';
import UtilTrialMessages from '~/utils/util-trial-messages';

definePageMeta({ middleware: 'auth', meMenu: true });

type ActionKind = 'ship' | 'returned' | 'damage';
const TAG = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const { t, locale } = useI18n();
const route = useRoute();
const api = useBusinessTrialApi();
const toast = useAppToast();
const time = useDisplayTime();
const applicantsId = useId();
const participantsId = useId();
const paymentId = useId();
const won = (n: number) => UtilTrialMessages.won(t, n, locale.value);

const id = computed(() => String(route.params.id ?? ''));
const view = ref<BizTrialView | null>(null);
const trial = computed(() => view.value?.trial ?? null);
/** `blocked` = 광고주 자격 없음(403 `TRIAL_ADVERTISER_BLOCKED` — 사업자 승인·광고 서비스 정지·광고주 보관, 점검 D5) */
const state = ref<'loading' | 'ready' | 'error' | 'blocked'>('loading');
const errorMessage = ref('');
const busy = ref(false);

async function load(): Promise<void> {
    if (!view.value) state.value = 'loading';
    try {
        view.value = await api.detail(id.value);
        state.value = 'ready';
    } catch (e) {
        if (UtilTrial.isAdvertiserBlocked(e)) {
            view.value = null;
            state.value = 'blocked';
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        state.value = 'error';
    }
}
watch(id, () => void load(), { immediate: true });

/** 공개 전 선결제 모집(서버 `TrialPayments.advertiserSeesDraft` — 신청자·뽑힌 사람은 빈 배열, 처리 API 는 404) */
const isDraft = computed(() => trial.value?.status === 'DRAFT');
/** 공개 전인데 결제 건이 닫혔다(결제 기한 만료 · 반려) — 운영팀의 「결제 다시 요청」을 기다린다 */
const draftClosed = computed(() => isDraft.value && (trial.value?.payment.status === 'EXPIRED' || trial.value?.payment.status === 'REJECTED'));

/** 결제 상태 딱지 · 테두리 색 — 결제 완료 초록 · 대기 주황 · 취소 빨강 · 그 밖 흐림 */
const paymentKind = computed<'ok' | 'wait' | 'bad' | 'none'>(() => {
    const s = trial.value?.payment.status;
    if (s === 'PAID') return 'ok';
    if (s === 'AWAITING_QUOTE_REQUEST' || s === 'AWAITING_APPROVAL' || s === 'AWAITING_PAYMENT' || s === 'AWAITING_DEPOSIT') return 'wait';
    return s === 'CANCELED' || s === 'EXPIRED' || s === 'REJECTED' ? 'bad' : 'none';
});
const paymentTone = computed(() =>
    paymentKind.value === 'ok'
        ? 'bg-green-100 text-green-900 dark:bg-green-900/40 dark:text-green-200'
        : paymentKind.value === 'wait'
          ? 'bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200'
          : paymentKind.value === 'bad'
            ? 'bg-red-100 text-red-900 dark:bg-red-900/40 dark:text-red-200'
            : 'bg-surface-100 text-muted-color dark:bg-surface-800',
);
const paymentBorder = computed(() => (paymentKind.value === 'wait' ? 'border-amber-300 dark:border-amber-700' : 'border-surface'));

function damagesOf(participantId: string) {
    return trial.value?.damageReports.filter((d) => d.participantId === participantId) ?? [];
}

async function toggleRecommend(applicationId: string, on: boolean): Promise<void> {
    const v = view.value;
    if (!v || busy.value) return;
    busy.value = true;
    try {
        await api.recommend(applicationId, on);
        view.value = { ...v, applicants: { ...v.applicants, items: v.applicants.items.map((a) => (a.id === applicationId ? { ...a, recommended: on } : a)) } };
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        busy.value = false;
    }
}

/* ── 송장 · 반납 받음 · 파손 신고 ── */
const actionOpen = ref(false);
const actionKind = ref<ActionKind>('ship');
const actionTarget = ref<BizTrialParticipant | null>(null);
const carrier = ref('');
const trackingNo = ref('');
const description = ref('');
const photos = ref<BizTrialUploaded[]>([]);
const tried = ref(false);
const acting = ref(false);
const uploading = ref(false);
const actionError = ref('');
const photoError = ref('');
const fieldErrors = ref<Record<string, string>>({});
const fileInput = ref<HTMLInputElement | null>(null);

function openAction(kind: ActionKind, p: BizTrialParticipant): void {
    actionKind.value = kind;
    actionTarget.value = p;
    carrier.value = '';
    trackingNo.value = '';
    description.value = '';
    photos.value = [];
    tried.value = false;
    actionError.value = '';
    photoError.value = '';
    fieldErrors.value = {};
    actionOpen.value = true;
}

async function onPhoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file || !trial.value) return;
    const problem = UtilTrial.photoProblem(file);
    if (problem) {
        photoError.value = problem === 'type' ? t('trial.photoTypeError') : t('trial.photoSizeError');
        return;
    }
    photoError.value = '';
    uploading.value = true;
    try {
        const kind = actionKind.value === 'ship' ? 'PRE_SHIP' : actionKind.value === 'returned' ? 'RETURN' : 'DAMAGE';
        photos.value = [...photos.value, await api.uploadPhoto(trial.value.id, kind, file)];
    } catch (e) {
        photoError.value = UtilBoardMessages.of(t, e);
    } finally {
        uploading.value = false;
    }
}

async function submitAction(): Promise<void> {
    tried.value = true;
    const p = actionTarget.value;
    if (!p || acting.value) return;
    const photoIds = photos.value.map((x) => x.id);
    if (actionKind.value === 'ship' && (!trackingNo.value.trim() || photoIds.length === 0)) {
        if (photoIds.length === 0) photoError.value = t('trial.photoRequiredPreShip');
        return;
    }
    if (actionKind.value === 'damage' && (!description.value.trim() || photoIds.length === 0)) {
        if (photoIds.length === 0) photoError.value = t('trial.photoRequiredDamage');
        return;
    }
    acting.value = true;
    actionError.value = '';
    fieldErrors.value = {};
    try {
        if (actionKind.value === 'ship') await api.shipment(p.id, { carrier: carrier.value.trim(), trackingNo: trackingNo.value.trim(), photoIds });
        else if (actionKind.value === 'returned') await api.returned(p.id, { photoIds });
        else await api.damage(p.id, { description: description.value.trim(), photoIds });
        actionOpen.value = false;
        toast.success(t(`trial.bizActionDone.${actionKind.value}`));
        await load();
    } catch (e) {
        if (e instanceof ApiError && e.fields && Object.keys(e.fields).length) {
            fieldErrors.value = { ...e.fields };
            if (e.fields.photoIds) photoError.value = e.fields.photoIds;
        } else actionError.value = UtilBoardMessages.of(t, e);
        // 결제 전 송장(409 TRIAL_PAYMENT_PENDING) · 취소된 줄(409 TRIAL_PARTICIPANT_CANCELLED) — 문구를 보이고 결제 상태·줄을 다시 읽는다
        if (e instanceof ApiError && e.status === 409 && (e.code === 'TRIAL_PAYMENT_PENDING' || e.code === 'TRIAL_PARTICIPANT_CANCELLED' || e.code === 'TRIAL_NOT_RETURNED')) void load();
    } finally {
        acting.value = false;
    }
}

useSeoMeta({ title: () => (trial.value ? `${trial.value.title} · ${t('trial.bizTitle')}` : t('trial.bizTitle')), robots: 'noindex' });
</script>
