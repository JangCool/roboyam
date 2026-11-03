<template>
    <StatusPanel icon="pi pi-check" tone="success" :title="t('withdrawal.doneTitle')" data-testid="withdrawn">
        {{ t('withdrawal.doneText') }}

        <template #actions>
            <!-- 남긴 항목·기간 -->
            <section class="rounded-lg bg-surface-100 p-3 text-left text-xs leading-relaxed dark:bg-surface-800" data-testid="withdrawn-remains">
                <h2 class="mb-1 text-xs font-semibold text-color">{{ t('withdrawal.doneRemainsTitle') }}</h2>
                <ul class="m-0 flex list-disc flex-col gap-0.5 pl-4">
                    <li>{{ t('withdrawal.doneRemainPosts') }}</li>
                    <li>{{ t('withdrawal.doneRemainTrades', { years: retentionYears }) }}</li>
                    <li v-if="result?.hasPaymentHistory">{{ t('withdrawal.doneRemainContact', { years: retentionYears }) }}</li>
                    <li v-if="result?.rejoinAvailableAt">{{ t('withdrawal.doneRemainHashes', { date: time.date(result.rejoinAvailableAt) }) }}</li>
                </ul>
            </section>

            <!-- 고객센터 — 탈퇴 뒤 반품·환불·하자, 글 블라인드 요청 -->
            <section class="rounded-lg bg-surface-100 p-3 text-left text-xs leading-relaxed dark:bg-surface-800" data-testid="withdrawn-contact">
                <h2 class="mb-1 text-xs font-semibold text-color">{{ t('withdrawal.doneContactTitle') }}</h2>
                <p class="m-0">{{ t('withdrawal.doneContactText') }}</p>
                <p v-if="result?.supportEmail" class="m-0 mt-1">
                    {{ t('withdrawal.doneContactMail') }}:
                    <a :href="`mailto:${result.supportEmail}`" class="break-all text-primary hover:underline" data-testid="withdrawn-support-mail">{{ result.supportEmail }}</a>
                </p>
            </section>

            <!-- 다시 가입할 수 있는 날 -->
            <section class="rounded-lg bg-surface-100 p-3 text-left text-xs leading-relaxed dark:bg-surface-800" data-testid="withdrawn-rejoin">
                <h2 class="mb-1 text-xs font-semibold text-color">{{ t('withdrawal.doneRejoinTitle') }}</h2>
                <p class="m-0">{{ result?.rejoinAvailableAt ? t('withdrawal.doneRejoin', { date: time.date(result.rejoinAvailableAt) }) : t('withdrawal.doneRejoinUnknown') }}</p>
            </section>

            <Button as="router-link" to="/" :label="t('withdrawal.doneHome')" variant="outlined" severity="secondary" fluid size="large" data-testid="withdrawn-home" />
        </template>
    </StatusPanel>
</template>

<script setup lang="ts">
/**
 * 탈퇴 완료 `/withdrawn` — **로그인 불필요**(세션은 이미 끝났다). 탈퇴 화면이 즉시 완료 응답을 받으면 `useWithdrawalDone` 에 넣고 여기로 온다.
 * 남긴 항목·기간(글·댓글, 거래 기록 N년, 결제 이력이 있으면 연락용 이메일, 재가입 제한 해시), 고객센터 연락 방법(탈퇴 뒤 반품·환불·하자·글 블라인드 요청), 다시 가입할 수 있는 날.
 * 새로고침·직접 열면 결과가 없어 일반 안내(기간 5년·30일 뒤 재가입)로 그린다 — 같은 내용은 탈퇴 완료 메일에도 간다(분석 문서 5-4).
 */
definePageMeta({ layout: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('withdrawal.doneSeoTitle'), description: () => t('withdrawal.doneSeoDescription'), robots: 'noindex' });

const time = useDisplayTime();
const { result } = useWithdrawalDone();

/** 거래 기록·연락용 이메일 보관 기간(년) — 결과가 없으면 정본(`commerce.md` 17-3)의 5년 */
const retentionYears = computed(() => result.value?.retentionYears ?? 5);
</script>
