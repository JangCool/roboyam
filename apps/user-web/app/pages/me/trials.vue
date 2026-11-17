<template>
    <!--
        내 체험단 `/me/trials`(trial-group-contract 「사용기 쓰는 곳」·「미반납」·「대여형 파손」·「구현 결정」 9 — 서버 `MyTrial[]`). 내 정보 메뉴 「내 활동」 묶음의 한 줄.
        한 줄(신청 하나): 모집 그림·제목(→ 모집 상세) · 지금 단계 딱지 · (뽑기 전) [신청 취소] · 단계 흐름 —
          신청함(뽑기 기다림) → 뽑힘(발송 기다림) → 배송 중(송장 · [받음] — 7일 안 안 누르면 송장 기준 배송 완료) → 사용기 쓸 차례([쓰러 가기] · 기한 · 남은 날 — 연결된 글만 센다)
          → (대여형) 반납할 차례(반납 기한 · 반납 안내 — 광고주 부담 수거·착불 · 미반납 14일이면 영구 금지) → 끝(약속 지킴).
        파손 신고를 받았으면 그 줄 아래 신고 카드(사진 · 설명 · 이의 기한) + [이의 제기](서버 `canObject` — 받을 때부터 그랬다·택배 파손 등) · 판정이 나면 판정과 메모.
        단추는 서버가 준 `canCancel`·`canReceive`·`canWrite`·`canObject` 로만 보인다(판정은 서버). 로그인 필요.
    -->
    <div class="content-card flex flex-col gap-4" data-testid="my-trials">
        <header class="flex flex-wrap items-center justify-between gap-2">
            <div>
                <h1 class="m-0 text-lg font-bold">{{ t('trial.myTitle') }}</h1>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('trial.myHint') }}</p>
            </div>
            <Button as="router-link" :to="UtilTrial.LIST_PATH" :label="t('trial.title')" icon="pi pi-arrow-right" icon-pos="right" size="small" severity="secondary" variant="outlined" />
        </header>

        <div v-if="state === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-32 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="state === 'error'" :title="t('trial.myLoadFailed')" :description="errorMessage" retry @retry="load" />
        <EmptyState v-else-if="rows.length === 0" icon="pi pi-box" :title="t('trial.myEmpty')" data-testid="my-trials-empty">
            <Button as="router-link" :to="UtilTrial.LIST_PATH" :label="t('trial.title')" />
        </EmptyState>

        <ul v-else class="m-0 flex list-none flex-col gap-3 p-0">
            <li v-for="row in rows" :key="row.applicationId" class="rounded-xl border border-surface p-3" :data-stage="UtilTrial.stageOf(row)" data-testid="my-trial-row">
                <div class="flex items-start gap-3">
                    <img v-if="row.trial.imageUrl" :src="row.trial.imageUrl" alt="" class="size-16 shrink-0 rounded-lg object-cover" loading="lazy" />
                    <span v-else class="grid size-16 shrink-0 place-items-center rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-box" /></span>
                    <div class="min-w-0 flex-1">
                        <p class="m-0 flex flex-wrap items-center gap-1.5">
                            <span :class="[TAG, stageClass(UtilTrial.stageOf(row))]"><span class="relative top-[-0.09em]">{{ t(`trial.stage.${UtilTrial.stageOf(row)}`) }}</span></span>
                            <span :class="[TAG, 'bg-surface-100 text-muted-color dark:bg-surface-800']"><span class="relative top-[-0.09em]">{{ t(`trial.form.${row.trial.form}`) }}</span></span>
                            <span class="text-xs text-muted-color">{{ row.trial.advertiserName }}</span>
                            <Button v-if="row.canCancel" type="button" :label="t('trial.cancelApply')" size="small" severity="danger" variant="text" class="ml-auto" :loading="busyId === row.applicationId" @click="cancel(row)" />
                        </p>
                        <NuxtLink :to="UtilTrial.detailPath(row.trial.id)" class="mt-1 line-clamp-2 block text-sm font-semibold hover:underline">{{ row.trial.title }}</NuxtLink>
                        <p class="m-0 mt-0.5 text-xs tabular-nums text-muted-color">{{ t('trial.appliedAt', { at: time.date(row.appliedAt) }) }} · {{ t(`trial.quota.${row.quota}`) }}</p>
                    </div>
                </div>

                <!-- 단계별 안내·할 일 -->
                <div v-if="row.participation" class="mt-3 flex flex-col gap-2 rounded-lg bg-surface-50 p-3 text-[13px] dark:bg-surface-800/50">
                    <p v-if="row.participation.trackingNo" class="m-0 tabular-nums">{{ t('trial.trackingLine', { carrier: row.participation.carrier ?? '', no: row.participation.trackingNo }) }}</p>

                    <p v-if="UtilTrial.stageOf(row) === 'WAIT_SHIP'" class="m-0 text-muted-color">{{ t('trial.waitShipText') }}</p>
                    <template v-else-if="UtilTrial.stageOf(row) === 'SHIPPED'">
                        <p class="m-0 text-muted-color">{{ t('trial.shippedText') }}</p>
                        <Button v-if="row.participation.canReceive" type="button" :label="t('trial.received')" icon="pi pi-check" size="small" class="self-start" :loading="busyId === row.participation.id" @click="markReceived(row.participation.id)" />
                    </template>
                    <template v-else-if="UtilTrial.stageOf(row) === 'WRITE'">
                        <p v-if="row.participation.reviewDueAt" class="m-0" :class="UtilTrial.overdue(row.participation.reviewDueAt) ? 'text-red-600 dark:text-red-400' : ''">
                            {{
                                UtilTrial.overdue(row.participation.reviewDueAt)
                                    ? t('trial.reviewOverdue', { date: time.date(row.participation.reviewDueAt) })
                                    : t('trial.reviewDue', { date: time.date(row.participation.reviewDueAt), n: UtilTrial.daysLeft(row.participation.reviewDueAt) })
                            }}
                        </p>
                        <p class="m-0 text-xs text-muted-color">{{ t('trial.writeHint', { board: row.participation.writeBoard.name }) }}</p>
                        <Button
                            v-if="row.participation.canWrite"
                            as="router-link"
                            :to="UtilTrial.writeLocation(row.participation.writeBoard.slug, row.participation.id)"
                            :label="t('trial.goWrite')"
                            icon="pi pi-pencil"
                            size="small"
                            class="self-start"
                        />
                    </template>
                    <template v-else-if="UtilTrial.stageOf(row) === 'RETURN'">
                        <p v-if="row.participation.returnDueAt" class="m-0" :class="UtilTrial.overdue(row.participation.returnDueAt) ? 'text-red-600 dark:text-red-400' : ''">
                            {{
                                UtilTrial.overdue(row.participation.returnDueAt)
                                    ? t('trial.returnOverdue', { date: time.date(row.participation.returnDueAt) })
                                    : t('trial.returnDue', { date: time.date(row.participation.returnDueAt), n: UtilTrial.daysLeft(row.participation.returnDueAt) })
                            }}
                        </p>
                        <p class="m-0 text-xs text-muted-color">{{ t('trial.returnGuide') }}</p>
                    </template>
                    <p v-else-if="UtilTrial.stageOf(row) === 'UNRETURNED'" class="m-0 text-red-600 dark:text-red-400">{{ t('trial.unreturnedText') }}</p>
                    <p v-else-if="UtilTrial.stageOf(row) === 'DONE'" class="m-0">{{ row.participation.kept === false ? t('trial.doneLate') : t('trial.doneKept') }}</p>

                    <p v-if="row.participation.reviewPostId" class="m-0 text-xs">
                        <NuxtLink :to="`/posts/${encodeURIComponent(row.participation.reviewPostId)}`" class="text-primary hover:underline">{{ t('trial.linkedReviewOpen') }}</NuxtLink>
                    </p>

                    <!-- 파손 신고 -->
                    <div v-for="report in row.participation.damageReports" :key="report.id" class="mt-1 flex flex-col gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 dark:border-amber-700 dark:bg-amber-950/40">
                        <p class="m-0 flex items-center gap-1.5 font-semibold"><i class="pi pi-exclamation-triangle text-amber-600" aria-hidden="true" />{{ t('trial.damageTitle') }}</p>
                        <p class="m-0 whitespace-pre-line">{{ report.description }}</p>
                        <div v-if="report.photos.length" class="flex flex-wrap gap-2">
                            <a v-for="photo in report.photos" :key="photo.id" :href="photo.url" target="_blank" rel="noopener" class="block">
                                <img :src="photo.url" :alt="t('trial.damagePhotoAlt')" class="size-20 rounded object-cover" loading="lazy" />
                            </a>
                        </div>
                        <template v-if="report.status === 'DECIDED' && report.verdict">
                            <p class="m-0 font-medium">{{ t('trial.verdictLine', { verdict: t(`trial.verdict.${report.verdict}`) }) }}</p>
                            <p class="m-0 text-xs text-muted-color">{{ t(`trial.verdictHelp.${report.verdict}`) }}</p>
                            <p v-if="report.decisionNote" class="m-0 text-xs">{{ t('trial.decisionNote', { note: report.decisionNote }) }}</p>
                        </template>
                        <template v-else-if="report.objection">
                            <p class="m-0 text-xs">{{ t('trial.myObjection', { text: report.objection }) }}</p>
                            <p class="m-0 text-xs text-muted-color">{{ t('trial.objectionWaiting') }}</p>
                        </template>
                        <template v-else-if="report.canObject">
                            <p class="m-0 text-xs text-muted-color">{{ t('trial.objectionDue', { date: time.full(report.objectionDueAt) }) }}</p>
                            <Button type="button" :label="t('trial.objection')" size="small" severity="secondary" variant="outlined" class="self-start" @click="openObjection(report.id)" />
                        </template>
                        <p v-else class="m-0 text-xs text-muted-color">{{ t('trial.objectionClosed') }}</p>
                    </div>
                </div>
            </li>
        </ul>

        <!-- 이의 제기 -->
        <Dialog v-model:visible="objectionOpen" modal :header="t('trial.objectionTitle')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }" :draggable="false" :closable="!objecting">
            <form class="flex flex-col gap-3" novalidate @submit.prevent="submitObjection">
                <p class="m-0 text-sm text-muted-color">{{ t('trial.objectionHint') }}</p>
                <LabeledField :label="t('trial.objectionLabel')" :error="objectionFieldError">
                    <template #aside>
                        <span class="text-xs tabular-nums text-muted-color">{{ objectionText.trim().length }}/{{ UtilTrial.TEXT_MAX }}</span>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="objectionText" rows="4" auto-resize fluid :maxlength="UtilTrial.TEXT_MAX" :disabled="objecting" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>
                <Message v-if="objectionError" severity="error" variant="simple" size="small">{{ objectionError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="objecting" @click="objectionOpen = false" />
                    <Button type="submit" :label="t('trial.objectionSubmit')" :loading="objecting" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { MyTrial } from '~/composables/trial/api/useTrialApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilTrial, { type MyTrialStage } from '~/utils/util-trial';

definePageMeta({ middleware: 'auth', meMenu: true });

const TAG = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const { t } = useI18n();
const api = useTrialApi();
const toast = useAppToast();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();

const rows = ref<MyTrial[]>([]);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');

async function load(): Promise<void> {
    if (!rows.value.length) state.value = 'loading';
    try {
        rows.value = await api.mine();
        state.value = 'ready';
    } catch (e) {
        errorMessage.value = UtilBoardMessages.of(t, e);
        state.value = 'error';
    }
}
void load();

function stageClass(stage: MyTrialStage): string {
    if (stage === 'WRITE' || stage === 'SHIPPED' || stage === 'RETURN') return 'bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200';
    if (stage === 'DONE' || stage === 'WAIT_SHIP') return 'bg-green-100 text-green-900 dark:bg-green-900/40 dark:text-green-200';
    if (stage === 'UNRETURNED') return 'bg-red-100 text-red-900 dark:bg-red-900/40 dark:text-red-200';
    return 'bg-surface-100 text-muted-color dark:bg-surface-800';
}

const busyId = ref<string | null>(null);

/** 신청 취소(뽑기 전 — 서버 `canCancel`) */
async function cancel(row: MyTrial): Promise<void> {
    if (busyId.value) return;
    const ok = await confirm({ title: t('trial.cancelTitle'), message: t('trial.cancelText'), confirmLabel: t('trial.cancelApply'), tone: 'danger' });
    if (!ok) return;
    busyId.value = row.applicationId;
    try {
        await api.cancel(row.trial.id);
        toast.success(t('trial.cancelled'));
        await load();
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        busyId.value = null;
    }
}

async function markReceived(participantId: string): Promise<void> {
    if (busyId.value) return;
    busyId.value = participantId;
    try {
        await api.received(participantId);
        toast.success(t('trial.receivedDone'));
        await load();
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        busyId.value = null;
    }
}

/* ── 이의 제기 ── */
const objectionOpen = ref(false);
const objectionTarget = ref<string | null>(null);
const objectionText = ref('');
const objecting = ref(false);
const objectionError = ref('');
const objectionTried = ref(false);
const objectionServerField = ref('');
const objectionFieldError = computed(() => objectionServerField.value || (objectionTried.value && !objectionText.value.trim() ? t('trial.objectionRequired') : ''));

function openObjection(reportId: string): void {
    objectionTarget.value = reportId;
    objectionText.value = '';
    objectionError.value = '';
    objectionServerField.value = '';
    objectionTried.value = false;
    objectionOpen.value = true;
}

async function submitObjection(): Promise<void> {
    objectionTried.value = true;
    const target = objectionTarget.value;
    if (!target || objecting.value || !objectionText.value.trim()) return;
    objecting.value = true;
    objectionError.value = '';
    objectionServerField.value = '';
    try {
        await api.objection(target, { text: objectionText.value.trim() });
        objectionOpen.value = false;
        toast.success(t('trial.objectionDone'));
        await load();
    } catch (e) {
        if (e instanceof ApiError && e.field('text')) objectionServerField.value = e.field('text') ?? '';
        else objectionError.value = UtilBoardMessages.of(t, e);
    } finally {
        objecting.value = false;
    }
}

useSeoMeta({ title: () => t('trial.myTitle'), robots: 'noindex' });
</script>
