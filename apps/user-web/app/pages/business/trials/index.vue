<template>
    <!--
        사업자 센터 › 체험단 `/business/trials`(trial-group-contract 「구현 결정」 8 — 광고주 화면). 운영자가 만든 그 광고주의 모집 목록(광고주 = 사업자 신원으로 이어진 광고주).
        한 줄: 상태 딱지 · 형태 · 보내는 쪽 · 제목 · 신청 수 / 인원 · 뽑힌 인원 · 신청 기간 → 누르면 모집 상세(신청자 보고 후보 추천 · 발송·반납·파손 신고).
        모집을 새로 만드는 것은 운영자(관리자 「광고 › 체험단」)라 여기에는 [만들기]가 없다 — 안내 한 줄.
        공개 전 선결제 모집(`status` DRAFT — 서버 `TrialPayments.advertiserSeesDraft`)도 줄로 온다: 딱지 「공개 전」 + 결제 딱지(결제할 차례 주황 · 기한 지남·반려 빨강).
    -->
    <div class="content-card flex flex-col gap-4" data-testid="business-trials">
        <header class="flex flex-wrap items-center justify-between gap-2">
            <div>
                <NuxtLink :to="UtilBusiness.CENTER_PATH" class="text-xs font-semibold text-primary hover:underline">{{ t('business.home.title') }}</NuxtLink>
                <h1 class="m-0 mt-0.5 text-lg font-bold">{{ t('trial.bizTitle') }}</h1>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('trial.bizHint') }}</p>
            </div>
        </header>

        <div v-if="state === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-20 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <EmptyState v-else-if="state === 'blocked'" icon="pi pi-lock" :title="t('trial.bizBlockedTitle')" :description="t('trial.bizBlockedText')" data-testid="business-trials-blocked" />
        <ErrorState v-else-if="state === 'error'" :title="t('trial.loadFailed')" :description="errorMessage" retry @retry="load" />
        <EmptyState v-else-if="items.length === 0" icon="pi pi-box" :title="t('trial.bizEmpty')" :description="t('trial.bizEmptyText')" />
        <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
            <li v-for="item in items" :key="item.id">
                <NuxtLink :to="UtilTrial.bizDetailPath(item.id)" class="panel-card flex flex-col gap-1.5 rounded-xl border border-surface p-3 hover:border-primary" :data-testid="`business-trial-${item.id}`">
                    <p class="m-0 flex flex-wrap items-center gap-1.5">
                        <span :class="[TAG, 'bg-surface-100 dark:bg-surface-800']"><span class="chip-label">{{ t(`trial.status.${item.status}`) }}</span></span>
                        <span :class="[TAG, 'bg-surface-100 text-muted-color dark:bg-surface-800']"><span class="chip-label">{{ t(`trial.form.${item.form}`) }}</span></span>
                        <span class="text-xs text-muted-color">{{ t(`trial.shipper.${item.shipper}`) }}</span>
                        <!-- 광고 결제 — 결제할 차례(승인·결제·입금 대기)만 눈에 띄게(V242) -->
                        <span v-if="item.paymentStatus === 'AWAITING_PAYMENT' || item.paymentStatus === 'AWAITING_DEPOSIT' || item.paymentStatus === 'AWAITING_APPROVAL'" :class="[TAG, 'bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200']">
                            <span class="chip-label">{{ t(`trial.bizPayment.status.${item.paymentStatus}`) }}</span>
                        </span>
                        <!-- 공개 전 선결제 모집의 닫힌 결제 건(결제 기한 지남 · 반려 — 운영팀에 다시 요청) -->
                        <span v-else-if="item.status === 'DRAFT' && (item.paymentStatus === 'EXPIRED' || item.paymentStatus === 'REJECTED')" :class="[TAG, 'bg-red-100 text-red-900 dark:bg-red-900/40 dark:text-red-200']">
                            <span class="chip-label">{{ t(`trial.bizPayment.status.${item.paymentStatus}`) }}</span>
                        </span>
                    </p>
                    <span class="line-clamp-1 text-sm font-semibold">{{ item.title }}</span>
                    <span class="flex flex-wrap gap-x-3 text-xs tabular-nums text-muted-color">
                        <span v-if="item.status !== 'DRAFT'">{{ t('trial.bizApplicants', { n: item.applicantCount, total: item.recruitCount }) }}</span>
                        <span v-if="item.selectedCount !== null">{{ t('trial.bizSelected', { n: item.selectedCount }) }}</span>
                        <span>{{ t('trial.periodLine', { from: time.date(item.applyStartAt), to: time.date(item.applyEndAt) }) }}</span>
                    </span>
                </NuxtLink>
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import type { BizTrialRow } from '~/composables/business/api/useBusinessTrialApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBusiness from '~/utils/util-business';
import UtilTrial from '~/utils/util-trial';

definePageMeta({ middleware: 'auth', meMenu: true });

const TAG = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const { t } = useI18n();
const api = useBusinessTrialApi();
const time = useDisplayTime();

const items = ref<BizTrialRow[]>([]);
/** `blocked` = 광고주 자격 없음(403 `TRIAL_ADVERTISER_BLOCKED`, 점검 D5) */
const state = ref<'loading' | 'ready' | 'error' | 'blocked'>('loading');
const errorMessage = ref('');

async function load(): Promise<void> {
    state.value = 'loading';
    try {
        items.value = await api.list();
        state.value = 'ready';
    } catch (e) {
        if (UtilTrial.isAdvertiserBlocked(e)) {
            state.value = 'blocked';
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        state.value = 'error';
    }
}
void load();

useSeoMeta({ title: () => t('trial.bizTitle'), robots: 'noindex' });
</script>
