<template>
    <div class="mx-auto max-w-xl">
        <h1 class="mb-3 text-lg font-bold">{{ headerTitle }}</h1>

        <!-- 불러오는 중 -->
        <div v-if="status === 'loading'" role="status" :aria-label="t('sanction.loading')" data-testid="appeal-loading">
            <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="mt-4 h-4 w-1/4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="mt-2 h-36 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <!-- 불러오지 못했다 -->
        <ErrorState v-else-if="status === 'error'" :title="t('sanction.loadError')" :description="errorMessage" retry data-testid="appeal-error" @retry="load()" />

        <template v-else-if="screen">
            <!-- 없는 제재 / 이미 무효가 된 제재 -->
            <ErrorState v-if="screen === 'MISSING'" code="404" :title="t('sanction.missingTitle')" :description="t('sanction.missingText')" data-testid="appeal-missing">
                <Button as="router-link" :to="UtilSanction.PATH" :label="t('sanction.backToHistory')" />
            </ErrorState>
            <ErrorState v-else-if="screen === 'VOIDED'" :title="t('sanction.voidedTitle')" :description="t('sanction.voidedText')" data-testid="appeal-voided">
                <Button as="router-link" :to="UtilSanction.PATH" :label="t('sanction.backToHistory')" />
            </ErrorState>

            <!-- 접수 완료 -->
            <section v-else-if="screen === 'DONE'" :aria-label="t('sanction.doneTitle')" data-testid="appeal-done">
                <StatusPanel icon="pi pi-check" tone="success" :title="t('sanction.doneTitle')">{{ t('sanction.doneText') }}</StatusPanel>
                <SanctionSummary class="mt-4" :rows="resultRows" />
                <Button as="router-link" :to="UtilSanction.PATH" :label="t('common.confirm')" size="large" fluid class="mt-4" data-testid="appeal-done-confirm" />
            </section>

            <!-- 이미 접수함(검토 중) -->
            <section v-else-if="screen === 'PENDING' && sanction" :aria-label="t('sanction.alreadyTitle')" data-testid="appeal-pending">
                <SanctionNotice :title="t('sanction.alreadyTitle')" :text="t('sanction.alreadyText')" />
                <SanctionSummary class="mt-3" :rows="resultRows" />
                <div v-if="sanction.appeal" class="mt-4">
                    <p class="m-0 text-sm font-medium">{{ t('sanction.sentReasonLabel') }}</p>
                    <p class="mb-0 mt-1.5 whitespace-pre-line break-words rounded-xl border border-surface bg-surface-100 p-3 text-sm leading-relaxed [overflow-wrap:anywhere] dark:bg-surface-800/60" data-testid="appeal-sent-reason">
                        {{ sanction.appeal.reason }}
                    </p>
                </div>
                <p class="mb-0 mt-3 text-xs text-muted-color">{{ t('sanction.appealOnce') }}</p>
                <Button as="router-link" :to="UtilSanction.PATH" :label="t('sanction.backToHistory')" size="large" severity="secondary" variant="outlined" fluid class="mt-4" />
            </section>

            <!-- 답변 도착 — 인용 / 기각 (기각된 뒤에는 폼을 다시 열지 않는다) -->
            <section v-else-if="(screen === 'ACCEPTED' || screen === 'REJECTED') && sanction" :aria-label="answeredTitle" :data-screen="screen" data-testid="appeal-answered">
                <StatusPanel :icon="screen === 'ACCEPTED' ? 'pi pi-check' : 'pi pi-times'" :tone="screen === 'ACCEPTED' ? 'success' : 'error'" :title="answeredTitle">
                    {{ screen === 'ACCEPTED' ? t('sanction.acceptedText') : t('sanction.rejectedText') }}
                </StatusPanel>
                <SanctionSummary class="mt-4" :rows="resultRows" />
                <div v-if="sanction.appeal?.answer" class="mt-3 rounded-r-lg border-l-4 border-surface-300 bg-surface-100 p-3 dark:border-surface-600 dark:bg-surface-800/60" data-testid="appeal-answer">
                    <p class="m-0 text-xs font-medium text-muted-color">{{ t('sanction.answerLabel') }}</p>
                    <p class="mb-0 mt-1 whitespace-pre-line break-words text-sm leading-relaxed [overflow-wrap:anywhere]">{{ sanction.appeal.answer }}</p>
                </div>
                <p v-if="screen === 'REJECTED'" class="mb-0 mt-3 text-xs text-muted-color">{{ t('sanction.rejectedNote') }}</p>
                <Button
                    as="router-link"
                    :to="screen === 'ACCEPTED' ? '/' : UtilSanction.PATH"
                    :label="screen === 'ACCEPTED' ? t('sanction.home') : t('common.confirm')"
                    size="large"
                    fluid
                    class="mt-4"
                    data-testid="appeal-answered-confirm"
                />
            </section>

            <!-- 접수 폼 -->
            <form v-else-if="sanction" novalidate :aria-label="t('sanction.headerAppeal')" data-testid="appeal-form" @submit.prevent="onSubmit">
                <SanctionSummary :rows="targetRows" />

                <div class="mt-4">
                    <LabeledField :label="t('sanction.reasonLabel')" :hint="t('sanction.reasonRequired')" :error="reasonMessage">
                        <template #aside>
                            <span class="text-xs tabular-nums" :class="problem === 'TOO_LONG' ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'" data-testid="appeal-count">
                                {{ t('sanction.reasonCounter', { n: length, max: UtilSanction.APPEAL_REASON_MAX }) }}
                            </span>
                        </template>
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea
                                :id="id"
                                v-model="reason"
                                rows="6"
                                auto-resize
                                fluid
                                :placeholder="t('sanction.reasonPlaceholder')"
                                :disabled="sending"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                data-testid="appeal-reason"
                                @blur="touch"
                            />
                        </template>
                    </LabeledField>
                </div>

                <SanctionNotice class="mt-3" :text="`${t('sanction.appealInfo')} ${t('sanction.appealOnce')}`" data-testid="appeal-info" />

                <!-- 보내지 못했다 — 쓴 사유는 그대로 남아 있다 -->
                <SanctionNotice v-if="failure" class="mt-3" tone="red" :title="failure.title" :text="failure.text" data-testid="appeal-failure" />

                <Button
                    type="submit"
                    class="mt-4"
                    size="large"
                    fluid
                    :label="sending ? t('sanction.submitting') : failure ? t('common.retry') : t('sanction.submit')"
                    :loading="sending"
                    :disabled="!canSubmit"
                    data-testid="appeal-submit"
                />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/**
 * 이의제기 — `/sanction/appeal?sanctionId=…`(community `pages/sanction/appeal.vue`, `docs/api-sanction.md` 2절). 정지·영구 정지 중에도 쓴다.
 * 제재의 상태에 따라 바뀐다 — 접수 폼(제재 요약 + 사유 10~500자 · 글자 수 · 접수) → 접수 완료 / 이미 접수함(검토 중) / 답변 도착(인용·기각, 운영자 답변) /
 * 이미 무효가 된 제재(`SANCTION_NOT_APPEALABLE`) / 없는 제재. **제재 하나에 1번**이라 기각된 뒤에도 폼을 다시 열지 않는다.
 * 흐름은 `useAppealForm`, 문구는 `UtilSanctionMessages`, 계산은 `UtilSanction`. ⚠ 목업 API(`useSanctionApi`).
 * 원본과 달라진 점: 게시판 한정 제재(`?kind=board`)의 이의제기는 로보얌에 없어 뺐다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('sanction.appealSeoTitle'), description: () => t('sanction.appealSeoDescription', { site: siteLabel.value }), robots: 'noindex' });

const route = useRoute();
const time = useDisplayTime();
const { status, errorMessage, sanction, screen, reason, sending, submitted, failure, length, problem, reasonMessage, canSubmit, load, cancel, touch, submit } = useAppealForm(
    () => route.query.sanctionId,
);

const headerTitle = computed(() => UtilSanctionMessages.appealHeaderTitle(t, screen.value ?? 'FORM'));
const answeredTitle = computed(() => (screen.value === 'ACCEPTED' ? t('sanction.acceptedTitle') : t('sanction.rejectedTitle')));
const targetRows = computed(() => (sanction.value ? UtilSanctionMessages.appealTargetRows(t, sanction.value, time.value) : []));
const resultRows = computed(() => (screen.value ? UtilSanctionMessages.appealRows(t, screen.value, sanction.value, time.value, submitted.value?.no ?? null) : []));

function onSubmit() {
    void submit();
}

void load();
onBeforeUnmount(cancel);
</script>
