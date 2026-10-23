<template>
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('settings.adult') }}</h1>

        <div v-if="phase === 'loading'" class="space-y-3" role="status" :aria-label="t('adult.loading')" data-testid="adult-loading">
            <div class="h-20 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-12 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            <div class="h-12 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="phase === 'error'" :title="t('adult.loadFailed')" :description="loadError" retry data-testid="adult-error" @retry="load" />

        <template v-else-if="status">
            <!-- 지금 상태 — "성인 확인됨 · 1999년생" / "아직 확인하지 않았어요" -->
            <section class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-label="t('adult.stateLabel')" data-testid="adult-state">
                <p class="m-0 flex items-center gap-2 text-base font-semibold">
                    <i :class="[stateIcon, 'text-lg']" aria-hidden="true" />
                    <span data-testid="adult-state-line">{{ stateLine }}</span>
                </p>
                <p v-if="stateNote" class="m-0 mt-1.5 text-sm text-muted-color" data-testid="adult-state-note">{{ stateNote }}</p>
            </section>

            <!-- 출생연도 고르기 + 확인 + 저장. 언제든 다시 고칠 수 있다(자기 입력이라 잠그지 않는다) -->
            <form class="space-y-4" novalidate data-testid="adult-form" @submit.prevent="submit">
                <LabeledField v-slot="{ id, describedBy, invalid }" :label="t('adult.birthLabel')" :error="errors.birthYear">
                    <select
                        :id="id"
                        :value="birthYear ?? ''"
                        class="w-full rounded-lg border bg-surface-0 px-3 py-2.5 text-sm text-color focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-60 dark:bg-surface-900"
                        :class="invalid ? 'border-red-500' : 'border-surface'"
                        :disabled="saving"
                        :aria-invalid="invalid"
                        :aria-describedby="describedBy"
                        data-testid="adult-birth-year"
                        @change="onYear"
                    >
                        <option value="" disabled>{{ t('adult.birthPlaceholder') }}</option>
                        <option v-for="y in years" :key="y" :value="y" :selected="y === birthYear">{{ t('adult.yearOption', { y }) }}</option>
                    </select>
                </LabeledField>

                <div>
                    <div class="flex items-start gap-2.5">
                        <Checkbox :model-value="confirm" binary :input-id="`${uid}-confirm`" :disabled="saving" :invalid="Boolean(errors.confirm)" class="mt-0.5 shrink-0" data-testid="adult-confirm" @update:model-value="onConfirm" />
                        <label :for="`${uid}-confirm`" class="min-w-0 flex-1 cursor-pointer text-sm">{{ t('adult.confirmLabel') }}</label>
                    </div>
                    <p v-if="errors.confirm" class="m-0 mt-1.5 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="adult-confirm-error">{{ errors.confirm }}</p>
                </div>

                <Message v-if="serverMessage" severity="error" variant="simple" data-testid="adult-save-error">{{ serverMessage }}</Message>

                <Button type="submit" :label="saving ? t('common.saving') : t('common.save')" :loading="saving" :disabled="saving" fluid data-testid="adult-save" />
            </form>

            <!-- 안내 — 누가 무엇을 보는지, 입력한 값이 기준이라는 것, 거짓 입력의 책임 -->
            <ul class="m-0 list-none space-y-1.5 rounded-lg bg-surface-100 p-3.5 text-xs leading-relaxed text-muted-color dark:bg-surface-800/60" data-testid="adult-notes">
                <li v-for="line in notes" :key="line" class="flex gap-1.5"><span aria-hidden="true">·</span><span>{{ line }}</span></li>
            </ul>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { AdultStatus } from '~/composables/adult/api/useAdultApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAdult from '~/utils/util-adult';
import type { BirthYearProblem } from '~/utils/util-adult';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 성인 확인 `/settings/adult` — 로그인 필요(community `pages/settings/adult.vue`, 4차 계약 B절). 19금 글을 볼 수 있는 자격을 **출생연도를 직접 입력해** 정한다.
 * community 도 출생연도 **자기 신고** 방식이라 그대로 옮겼다 — 본인인증(커머스 세션 0-4)이 생기면 서버가 같은 칸을 `IDENTITY` 로 채우고 이 화면의 말은 그대로다.
 * 지금 상태 → 출생연도 고르기(올해−14 ~ 올해−100, 최근 해가 위) → 「입력한 출생연도가 사실과 같음을 확인해요」 체크 → 저장 → 안내.
 * 칸 오류(`fields.birthYear`·`fields.confirm`)는 칸 아래, 그 밖의 실패는 폼 안 붉은 줄. 출생연도는 개인정보라 화면 밖(주소·로그·저장소)에 남기지 않는다.
 * 19금 표시의 이름은 서비스 이름 `ADULT_LABEL` 이다. ⚠ 목업 API(`useAdultApi`).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { term } = useSiteTerms();
useSeoMeta({ title: () => t('settings.adult'), description: () => t('adult.seo', { label: term('ADULT_LABEL') }), robots: 'noindex' });

const api = useAdultApi();
const toast = useAppToast();
const uid = useId();

const phase = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const status = ref<AdultStatus | null>(null);

const years = computed(() => (status.value ? UtilAdult.years(status.value) : []));
const view = computed(() => UtilAdult.viewOf(status.value));
const stateLine = computed(() => {
    const s = status.value;
    if (view.value === 'CONFIRMED') return s?.birthYear != null ? t('adult.stateConfirmedYear', { y: s.birthYear }) : t('adult.stateConfirmed');
    if (view.value === 'MINOR' && s?.birthYear != null) return t('adult.stateMinor', { y: s.birthYear });
    return t('adult.stateNone');
});
const stateNote = computed(() => (view.value === 'MINOR' && status.value?.birthYear != null ? t('adult.stateMinorNote', { y: UtilAdult.adultYear(status.value.birthYear), label: term('ADULT_LABEL') }) : ''));
const notes = computed(() => [t('adult.note1', { label: term('ADULT_LABEL') }), t('adult.note2', { age: UtilAdult.ADULT_AGE }), t('adult.note3')]);
const stateIcon = computed(() => {
    if (view.value === 'CONFIRMED') return 'pi pi-check-circle text-green-600 dark:text-green-400';
    if (view.value === 'MINOR') return 'pi pi-info-circle text-amber-600 dark:text-amber-400';
    return 'pi pi-minus-circle text-muted-color';
});

/* ── 입력 ── */
const birthYear = ref<number | null>(null);
const confirm = ref(false);
const errors = reactive({ birthYear: '', confirm: '' });
const serverMessage = ref('');
const saving = ref(false);
/** 고르기 칸을 직접 만졌는가 — 만졌으면 서버 값이 와도 덮지 않는다 */
let touched = false;

function birthProblemText(problem: BirthYearProblem | null): string {
    if (problem === 'REQUIRED') return t('adult.birthRequired');
    if (problem === 'TOO_YOUNG') return t('adult.birthTooYoung', { age: UtilAdult.MIN_AGE });
    if (problem === 'OUT_OF_RANGE') return t('adult.birthOutOfRange');
    return '';
}

function onYear(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    birthYear.value = value === '' ? null : Number(value);
    touched = true;
    errors.birthYear = '';
    serverMessage.value = '';
}

function onConfirm(value: boolean) {
    confirm.value = value;
    errors.confirm = '';
    serverMessage.value = '';
}

async function load() {
    phase.value = 'loading';
    loadError.value = '';
    try {
        status.value = UtilAdult.sanitize(await api.get());
        if (!touched) birthYear.value = status.value.birthYear;
        phase.value = 'ready';
    } catch (e) {
        loadError.value = UtilBoardMessages.of(t, e);
        phase.value = 'error';
    }
}
void load();

async function submit() {
    if (saving.value || !status.value) return;
    serverMessage.value = '';
    errors.birthYear = birthProblemText(UtilAdult.birthYearProblem(birthYear.value, status.value));
    errors.confirm = confirm.value ? '' : t('adult.confirmRequired');
    if (errors.birthYear || errors.confirm || birthYear.value === null) return;
    saving.value = true;
    try {
        const saved = UtilAdult.sanitize(await api.update({ birthYear: birthYear.value, confirm: confirm.value }));
        status.value = saved;
        confirm.value = false; // 확인 체크는 저장할 때마다 다시 한다
        touched = false;
        birthYear.value = saved.birthYear;
        toast.success(saved.adult ? t('adult.savedAdult') : t('adult.saved'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields) {
            errors.birthYear = e.field('birthYear') ?? '';
            errors.confirm = e.field('confirm') ?? '';
            if (!errors.birthYear && !errors.confirm) serverMessage.value = t('adult.saveFailed', { reason: e.message });
        } else {
            serverMessage.value = t('adult.saveFailed', { reason: UtilBoardMessages.of(t, e) });
        }
    } finally {
        saving.value = false;
    }
}
</script>
