<template>
    <!--
        사업자 센터 › 체험단 모집 상세 `/business/trials/{id}`(trial-group-contract 「구현 결정」 8 — 광고주, 서버 `BusinessTrialDetail { trial, applicants }`).
        위: 상태·형태·보내는 쪽·제목·인원·기간·제품값. 아래 두 구획:
          ① 신청자 — 닉네임 · 리뷰어 단계 · 몫 · 사용기 수 · 평균 추천 · 약속 지킨 비율 · [후보 추천] 켬/끔(최종 선정은 운영자 — 회원 개인정보는 없다).
          ② 뽑힌 사람 — 보내는 쪽이 광고주면 받을 곳(동의 범위 — 반납 확인(제공형은 받음) 뒤 파기) · [송장 넣기](발송 전 사진 필수 — 없으면 처리 안 됨) · 받음 · 사용기 ·
             (대여형) [반납 받음](반납 사진) · [파손 신고](사진 + 설명 — 회원 7일 이의 뒤 운영자 판정). 보내는 쪽이 운영자면 주소 없이 진행 상황만(발송은 운영자가 한다).
        서버 오류는 대화상자 안(칸 오류는 칸 아래)·목록 한 줄 실패는 토스트.
    -->
    <div class="content-card flex flex-col gap-4" data-testid="business-trial-detail">
        <NuxtLink :to="UtilTrial.BIZ_PATH" class="self-start text-xs font-semibold text-primary hover:underline">{{ t('trial.bizTitle') }}</NuxtLink>

        <div v-if="state === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
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
                    <span>{{ t('trial.bizApplicants', { n: view.applicants.items.length, total: trial!.recruitCount }) }}</span>
                    <span>{{ t('trial.periodLine', { from: time.full(trial!.applyStartAt), to: time.full(trial!.applyEndAt) }) }}</span>
                </p>
            </header>

            <!-- ① 신청자 -->
            <section v-if="view.applicants.items.length" class="flex flex-col gap-2" :aria-labelledby="applicantsId">
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

            <!-- ② 뽑힌 사람 -->
            <section class="flex flex-col gap-2" :aria-labelledby="participantsId">
                <h2 :id="participantsId" class="m-0 text-base font-bold">{{ t('trial.bizParticipantsTitle', { n: trial!.participants.length }) }}</h2>
                <p v-if="trial!.shipper === 'OPERATOR'" class="m-0 text-xs text-muted-color">{{ t('trial.bizOperatorShips') }}</p>
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
                            <li>{{ p.trackingNo ? t('trial.trackingLine', { carrier: p.carrier ?? '', no: p.trackingNo }) : t('trial.bizNotShipped') }}</li>
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
                            <a v-for="photo in p.photos" :key="photo.id" :href="photo.url" target="_blank" rel="noopener"><img :src="photo.url" alt="" class="size-14 rounded object-cover" loading="lazy" /></a>
                        </div>
                        <div v-if="trial!.shipper === 'ADVERTISER'" class="mt-2 flex flex-wrap gap-2">
                            <Button v-if="!p.shippedAt" type="button" :label="t('trial.bizShip')" icon="pi pi-truck" size="small" @click="openAction('ship', p)" />
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
                        <img v-for="photo in photos" :key="photo.id" :src="photo.url" alt="" class="size-16 rounded object-cover" />
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

const id = computed(() => String(route.params.id ?? ''));
const view = ref<BizTrialView | null>(null);
const trial = computed(() => view.value?.trial ?? null);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');
const busy = ref(false);

async function load(): Promise<void> {
    if (!view.value) state.value = 'loading';
    try {
        view.value = await api.detail(id.value);
        state.value = 'ready';
    } catch (e) {
        errorMessage.value = UtilBoardMessages.of(t, e);
        state.value = 'error';
    }
}
watch(id, () => void load(), { immediate: true });

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
    } finally {
        acting.value = false;
    }
}

useSeoMeta({ title: () => (trial.value ? `${trial.value.title} · ${t('trial.bizTitle')}` : t('trial.bizTitle')), robots: 'noindex' });
</script>
