<template>
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('settings.nicknameImage') }}</h1>

        <div v-if="status === 'loading'" class="space-y-3" role="status" :aria-label="t('nicknameImage.loading')" data-testid="nickname-image-loading">
            <div class="h-12 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('nicknameImage.loadFailed')" :description="loadError" retry data-testid="nickname-image-error" @retry="load()" />

        <template v-else-if="state && layout">
            <p class="m-0 text-sm text-muted-color" data-testid="nickname-image-intro">{{ t('nicknameImage.intro') }}</p>

            <!-- 신청을 막는 까닭 — 이메일 인증 전·정지 중. 조건이 바뀌면 할 수 있는 일이라 단추를 감추지 않고 막은 채 이유를 보인다 -->
            <div v-if="blockedCopy" role="status" class="rounded-lg bg-amber-50 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="nickname-image-blocked">
                <p class="m-0 font-semibold">{{ blockedCopy.title }}</p>
                <p class="m-0 mt-0.5">{{ blockedCopy.text }}</p>
                <NuxtLink v-if="blocked === 'EMAIL_NOT_VERIFIED'" to="/verify-email" class="mt-1 inline-block font-semibold underline">{{ t('nicknameImage.verifyLink') }}</NuxtLink>
            </div>

            <!-- 자격 미달 — 단골 미만(현재·필요 등급 + 등급 안내) · 정지 중. 적용 중인 이미지는 등급이 내려가도 유지된다 -->
            <section v-if="layout.ineligible" :aria-labelledby="`${uid}-ineligible`" data-testid="nickname-image-ineligible">
                <div class="rounded-lg bg-surface-100 px-3 py-2.5 text-sm leading-relaxed dark:bg-surface-800/60" role="status">
                    <h2 :id="`${uid}-ineligible`" class="m-0 text-sm font-semibold">{{ ineligibleCopy.title }}</h2>
                    <p class="m-0 mt-0.5 text-muted-color">{{ ineligibleCopy.text }}</p>
                </div>
                <div v-if="state.ineligibleReason !== 'SUSPENDED'" class="mt-3 flex items-center gap-3 rounded-xl border border-surface bg-surface-0 p-4 text-sm dark:bg-surface-900" data-testid="nickname-image-grades">
                    <p class="m-0 flex items-center gap-1.5"><span class="text-xs text-muted-color">{{ t('nicknameImage.myGrade') }}</span><GradeBadge :grade="state.grade.code as Grade" :label="state.grade.label" /></p>
                    <i class="pi pi-arrow-right text-xs text-muted-color" aria-hidden="true" />
                    <p class="m-0 flex items-center gap-1.5">
                        <span class="text-xs text-muted-color">{{ t('nicknameImage.requiredGrade') }}</span><GradeBadge :grade="state.requiredGrade.code as Grade" :label="state.requiredGrade.label" />
                    </p>
                </div>
                <div class="mt-3 space-y-2">
                    <Button type="button" :label="t('nicknameImage.cannotApply')" fluid disabled data-testid="nickname-image-disabled" />
                    <Button v-if="state.ineligibleReason !== 'SUSPENDED'" as="router-link" to="/me/grade" :label="t('nicknameImage.gradeLink')" severity="secondary" variant="outlined" fluid data-testid="nickname-image-grade-link" />
                </div>
            </section>

            <!-- 승인 대기 — 신청한 이미지 · 운영자 확인 후 적용 · 신청 취소 -->
            <section v-if="layout.request === 'PENDING' && state.request" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-pending`" data-testid="nickname-image-pending">
                <div class="flex items-center justify-between gap-2">
                    <h2 :id="`${uid}-pending`" class="m-0 text-sm font-semibold">
                        <span class="inline-flex items-center justify-center rounded bg-amber-100 px-1.5 py-1 text-xs leading-none text-amber-900 dark:bg-amber-900/40 dark:text-amber-200"
                            ><span class="relative top-[-0.09em]">{{ t('nicknameImage.pendingTag') }}</span></span
                        >
                    </h2>
                    <small class="text-xs text-muted-color">{{ t('nicknameImage.requestedOn', { date: time.date(state.request.createdAt) }) }}</small>
                </div>
                <NicknameImageFrame :src="state.request.url" :alt="t('nicknameImage.altRequested')" class="mt-3" />
                <p class="m-0 mt-3 text-sm">{{ t('nicknameImage.pendingText') }}</p>
                <p class="m-0 mt-1 text-xs text-muted-color">{{ layout.current ? t('nicknameImage.pendingKeepNote') : t('nicknameImage.pendingPlainNote') }}</p>
                <Button
                    type="button"
                    :label="t('nicknameImage.cancelRequest')"
                    severity="secondary"
                    variant="outlined"
                    class="mt-3"
                    :loading="busy === 'cancel'"
                    :disabled="busy !== null"
                    data-testid="nickname-image-cancel"
                    @click="cancelRequest"
                />
            </section>

            <!-- 반려 — 사유 · 다시 신청할 수 있는 때 -->
            <section v-else-if="layout.request === 'REJECTED' && state.request" :aria-labelledby="`${uid}-rejected`" data-testid="nickname-image-rejected">
                <div class="rounded-lg bg-red-50 px-3 py-2.5 text-sm leading-relaxed text-red-900 dark:bg-red-950/40 dark:text-red-200" role="status">{{ t('nicknameImage.rejectedBanner') }}</div>
                <div class="mt-3 rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                    <div class="flex items-center justify-between gap-2">
                        <h2 :id="`${uid}-rejected`" class="m-0 text-sm font-semibold">
                            <span class="inline-flex items-center justify-center rounded bg-red-100 px-1.5 py-1 text-xs leading-none text-red-900 dark:bg-red-900/40 dark:text-red-200"
                                ><span class="relative top-[-0.09em]">{{ t('nicknameImage.rejectedTag') }}</span></span
                            >
                        </h2>
                        <small class="text-xs text-muted-color">{{ t('nicknameImage.requestedOn', { date: time.date(state.request.createdAt) }) }}</small>
                    </div>
                    <NicknameImageFrame :src="state.request.url" :alt="t('nicknameImage.altRejected')" class="mt-3" />
                    <p class="m-0 mt-3 text-xs font-semibold text-muted-color">{{ t('nicknameImage.rejectReasonLabel') }}</p>
                    <p class="m-0 mt-1 whitespace-pre-line break-words rounded-lg bg-surface-100 px-3 py-2 text-sm dark:bg-surface-800" data-testid="nickname-image-reject-reason">
                        {{ state.request.rejectReason?.trim() || t('nicknameImage.rejectReasonEmpty') }}
                    </p>
                    <p class="m-0 mt-3 text-xs text-muted-color" data-testid="nickname-image-reapply-wait">{{ UtilNicknameImageMessages.reapplyWait(t, layout.waitSeconds, state.request.canReapplyAt, time) }}</p>
                    <Button
                        v-if="layout.startable && !layout.current"
                        type="button"
                        :label="t('nicknameImage.reapply')"
                        class="mt-3"
                        :disabled="layout.waitSeconds > 0 || blocked !== null"
                        :title="blockedCopy?.title"
                        data-testid="nickname-image-reapply"
                        @click="opened = true"
                    />
                </div>
            </section>

            <!-- 적용 중 — 미리보기 · 이미지 바꾸기(승인 전까지 이전 이미지 유지) · 해제 -->
            <section v-if="layout.current && state.current" :aria-labelledby="`${uid}-current`" data-testid="nickname-image-current">
                <div class="rounded-lg bg-green-50 px-3 py-2.5 text-sm leading-relaxed text-green-900 dark:bg-green-950/40 dark:text-green-200" role="status">
                    <h2 :id="`${uid}-current`" class="m-0 text-sm font-semibold">{{ t('nicknameImage.currentBanner') }}</h2>
                </div>
                <NicknameImageFrame :src="state.current.url" :alt="t('nicknameImage.altCurrent')" class="mt-3" />
                <section class="mt-3 rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-preview`" data-testid="nickname-image-preview">
                    <h3 :id="`${uid}-preview`" class="m-0 text-xs font-semibold text-muted-color">{{ t('nicknameImage.previewTitle') }}</h3>
                    <p class="m-0 mt-3 text-xs text-muted-color">{{ t('nicknameImage.previewList') }}</p>
                    <div class="mt-1 rounded-lg border border-surface px-3 py-2.5">
                        <p class="m-0 text-sm leading-snug">{{ t('nicknameImage.previewSamplePost') }} <span class="ml-1 font-semibold text-primary">[21]</span></p>
                        <p class="m-0 mt-1 flex items-center gap-1 text-xs text-muted-color">
                            <img :src="state.current.still || state.current.url" :alt="user?.nickname ?? ''" class="h-4 w-16 object-contain" />
                            <GradeBadge v-if="user" :grade="user.grade" :label="user.gradeLabel" />
                            <span class="shrink-0">· {{ t('nicknameImage.previewMeta') }}</span>
                        </p>
                    </div>
                    <p class="m-0 mt-3 text-xs text-muted-color">{{ t('nicknameImage.previewComment') }}</p>
                    <div class="mt-1 rounded-lg border border-surface px-3 py-2.5">
                        <p class="m-0 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                            <img :src="state.current.url" :alt="user?.nickname ?? ''" class="h-5 w-20 object-contain" />
                            <GradeBadge v-if="user" :grade="user.grade" :label="user.gradeLabel" />
                        </p>
                        <p class="m-0 mt-1 text-sm">{{ t('nicknameImage.previewSampleComment') }}</p>
                    </div>
                </section>
                <p class="m-0 mt-2 px-1 text-xs text-muted-color">{{ t('nicknameImage.nicknameKept') }}</p>
                <div class="mt-3 flex gap-2">
                    <Button
                        v-if="layout.startable"
                        type="button"
                        :label="t('nicknameImage.change')"
                        severity="secondary"
                        variant="outlined"
                        class="flex-1"
                        :disabled="layout.waitSeconds > 0 || blocked !== null"
                        :title="blockedCopy?.title"
                        data-testid="nickname-image-change"
                        @click="opened = true"
                    />
                    <Button
                        type="button"
                        :label="t('nicknameImage.remove')"
                        severity="danger"
                        variant="outlined"
                        class="flex-1"
                        :loading="busy === 'remove'"
                        :disabled="busy !== null || blocked !== null"
                        :title="blockedCopy?.title"
                        data-testid="nickname-image-remove"
                        @click="removeCurrent"
                    />
                </div>
                <p v-if="layout.request !== 'PENDING'" class="m-0 mt-2 px-1 text-xs text-muted-color">{{ t('nicknameImage.currentChangeNote') }}</p>
            </section>

            <!-- 신청 — 파일 고르기 · 미리보기(4:1 틀) · 안내 · 올리기. 서버 응답을 받은 뒤에야 「승인 대기」가 된다 -->
            <section v-if="layout.form" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-form`" data-testid="nickname-image-form">
                <h2 :id="`${uid}-form`" class="m-0 text-sm font-semibold">{{ layout.current ? t('nicknameImage.change') : t('nicknameImage.formTitle') }}</h2>

                <ul class="m-0 mt-2 list-none space-y-0.5 p-0 text-xs leading-relaxed text-muted-color" data-testid="nickname-image-rules">
                    <li v-for="line in rules" :key="line">{{ line }}</li>
                    <li>{{ t('nicknameImage.flashNote') }}</li>
                    <li>{{ t('nicknameImage.reapplyRule', { wait: UtilNicknameImageMessages.hoursLabel(t, reapplyHours) }) }}</li>
                </ul>

                <input ref="fileInput" type="file" class="sr-only" :accept="UtilNicknameImage.ACCEPT" tabindex="-1" aria-hidden="true" data-testid="nickname-image-file" @change="onFilePicked" />

                <button
                    v-if="!file"
                    type="button"
                    class="mt-3 grid aspect-[4/1] w-full max-w-60 place-items-center rounded-md border border-dashed border-surface-400 text-sm text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50 dark:hover:bg-surface-800"
                    :disabled="formLocked"
                    :title="blockedCopy?.title"
                    data-testid="nickname-image-pick"
                    @click="fileInput?.click()"
                >
                    <span class="flex items-center gap-1.5"><i class="pi pi-upload" aria-hidden="true" />{{ t('nicknameImage.pick') }}</span>
                </button>

                <div v-else-if="fileProblem" class="mt-3" data-testid="nickname-image-problem">
                    <p class="m-0 flex items-center justify-between gap-2 rounded-lg border border-red-300 px-3 py-2 text-sm dark:border-red-800">
                        <span class="min-w-0 truncate">{{ file.name }}</span>
                        <span class="shrink-0 text-xs text-muted-color">{{ UtilNicknameImage.formatBytes(file.size) }}</span>
                    </p>
                    <p role="alert" class="m-0 mt-1.5 text-xs text-red-600 dark:text-red-400" data-testid="nickname-image-file-error">{{ fileProblem }}</p>
                    <Button type="button" :label="t('nicknameImage.pickAgain')" severity="secondary" variant="outlined" size="small" class="mt-2" :disabled="formLocked" data-testid="nickname-image-repick" @click="fileInput?.click()" />
                </div>

                <div v-else class="mt-3" data-testid="nickname-image-picked">
                    <NicknameImageFrame v-if="previewUrl" :src="previewUrl" :alt="t('nicknameImage.altPicked')" />
                    <div v-else class="aspect-[4/1] w-full max-w-60 animate-pulse rounded-md bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('nicknameImage.reading')" />
                    <p class="m-0 mt-1.5 text-xs text-muted-color">{{ file.name }} · {{ UtilNicknameImage.formatBytes(file.size) }}</p>
                    <p v-if="isGif" class="m-0 mt-2 text-xs text-muted-color" data-testid="nickname-image-gif-note">{{ t('nicknameImage.gifNote') }}</p>
                    <Button type="button" :label="t('nicknameImage.pickAgain')" severity="secondary" variant="outlined" size="small" class="mt-2" :disabled="formLocked" data-testid="nickname-image-repick" @click="fileInput?.click()" />
                </div>

                <div class="mt-4 flex items-start gap-2.5">
                    <Checkbox v-model="consent" binary :input-id="consentId" :disabled="formLocked" data-testid="nickname-image-consent" />
                    <label :for="consentId" class="cursor-pointer text-sm leading-snug">{{ t('nicknameImage.consent') }}</label>
                </div>
                <p class="m-0 mt-2 text-xs text-muted-color">{{ t('nicknameImage.nicknameKept') }}</p>

                <div v-if="busy === 'apply'" class="mt-3 w-full max-w-60" role="status" data-testid="nickname-image-progress">
                    <div class="h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700">
                        <div class="h-full rounded-full bg-primary transition-[width]" :style="{ width: `${Math.round(progress * 100)}%` }" />
                    </div>
                    <p class="m-0 mt-1 text-xs text-muted-color">{{ progress < 1 ? t('nicknameImage.uploading', { n: Math.round(progress * 100) }) : t('nicknameImage.processing') }}</p>
                </div>

                <p v-if="serverError" role="alert" class="m-0 mt-3 text-sm text-red-600 dark:text-red-400" data-testid="nickname-image-server-error">{{ serverError }}</p>
                <p v-if="layout.waitSeconds > 0" class="m-0 mt-3 text-xs text-muted-color" data-testid="nickname-image-form-wait">
                    {{ UtilNicknameImageMessages.reapplyWait(t, layout.waitSeconds, state.request?.canReapplyAt ?? state.reapplyAt, time) }}
                </p>

                <div class="mt-4 flex gap-2">
                    <Button v-if="opened" type="button" :label="t('common.close')" severity="secondary" variant="outlined" class="flex-1" :disabled="busy !== null" data-testid="nickname-image-close" @click="closeForm" />
                    <Button type="button" :label="t('nicknameImage.submit')" class="flex-1" :loading="busy === 'apply'" :disabled="!canSubmit" :title="blockedCopy?.title" data-testid="nickname-image-submit" @click="submit" />
                </div>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { Grade } from '~/composables/auth/api/useAuthApi.types';
import type { MyNicknameImage } from '~/composables/profile/api/useNicknameImageApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilNicknameImage from '~/utils/util-nickname-image';
import UtilNicknameImageMessages from '~/utils/util-nickname-image-messages';
import type { NicknameImageBlockedReason } from '~/utils/util-nickname-image-messages';

/**
 * 닉네임 이미지 `/settings/nickname-image` — 로그인 필요(community `pages/settings/nickname-image.vue`, `docs/api-nickname-image.md`).
 * 글자 닉네임 자리에 이미지를 보이게 하는 신청 화면 — **단골 이상**이 올리고 **운영자 심사 뒤** 적용된다(심사 대기는 운영자 알림 `NICKNAME_IMAGE_PENDING`, 관리자 심사 화면은 이번 범위 밖).
 * 한 화면이 서버 상태 하나로 그려진다(`UtilNicknameImage.layout`): 자격 미달 · 신청(파일 고르기 → 형식·용량·4:1 화면 검사 → 미리보기 → 동의 → 신청하기) · 승인 대기(취소) ·
 * 반려(사유 · 재신청까지 남은 시간) · 적용 중(미리보기 · 바꾸기 · 해제). 올린 뒤에는 서버 응답을 받고 상태를 다시 읽는다(낙관적으로 바꾸지 않는다).
 * community 대비 뺀 것: 아이템샵 변경권(로보얌에 아이템샵이 없다)·닉네임 이미지 신고, 작성자 표시의 이미지(서버 응답에 칸이 없다 — 미리보기만).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('settings.nicknameImage'), description: () => t('nicknameImage.seo', { site: siteLabel.value }), robots: 'noindex' });

const time = useDisplayTime();
const values = useDisplayValues();
const api = useNicknameImageApi();
const { user, emailVerified } = useAuth();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const uid = useId();
const consentId = `${uid}-consent`;

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const state = ref<MyNicknameImage | null>(null);
const opened = ref(false);
const busy = ref<'apply' | 'cancel' | 'remove' | null>(null);

const file = ref<File | null>(null);
const previewUrl = ref<string | null>(null);
const fileProblem = ref('');
const serverError = ref('');
const consent = ref(false);
const progress = ref(0);
const fileInput = ref<HTMLInputElement | null>(null);
const serverBlocked = ref<NicknameImageBlockedReason | null>(null);

let pickSeq = 0;
let alive = true;
let uploadController: AbortController | null = null;

const blocked = computed<NicknameImageBlockedReason | null>(() => (user.value && !emailVerified.value ? 'EMAIL_NOT_VERIFIED' : serverBlocked.value));
const blockedCopy = computed(() => (blocked.value ? UtilNicknameImageMessages.blocked(t, blocked.value) : null));
const formLocked = computed(() => busy.value !== null || blocked.value !== null);

/* ── 재신청까지 남은 시간 — 시각이 지나면 단추가 켜진다 ── */
const reapplyEnds = ref(0);
const { remaining } = useCountdown(reapplyEnds);

const layout = computed(() => {
    if (!state.value) return null;
    void remaining.value;
    return UtilNicknameImage.layout(state.value, opened.value, Date.now());
});

const ineligibleCopy = computed(() => (state.value ? UtilNicknameImageMessages.ineligible(t, state.value.ineligibleReason, state.value.grade, state.value.requiredGrade) : { title: '', text: '' }));
const rules = computed(() => (state.value ? UtilNicknameImageMessages.rules(t, state.value.limits) : []));
const reapplyHours = computed(() => UtilNicknameImage.reapplyHoursOf(state.value?.limits?.rejectedReapplyHours));
const isGif = computed(() => (file.value ? UtilNicknameImage.isGif(file.value) : false));

const canSubmit = computed(
    () => Boolean(file.value && previewUrl.value) && !fileProblem.value && consent.value && blocked.value === null && busy.value === null && (layout.value?.form ?? false) && (layout.value?.waitSeconds ?? 0) === 0,
);

/* ── 서버 상태 ── */
let request = 0;
async function load(silent = false): Promise<void> {
    const mine = ++request;
    if (!silent) status.value = 'loading';
    try {
        const value = await api.status();
        if (mine !== request) return;
        state.value = value;
        const until = value.request?.status === 'REJECTED' ? value.request.canReapplyAt : (value.reapplyAt ?? null);
        const at = until ? Date.parse(until) : Number.NaN;
        reapplyEnds.value = Number.isNaN(at) ? 0 : at;
        status.value = 'ready';
    } catch (e) {
        if (mine !== request) return;
        loadError.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}
void load();

/* ── 파일 고르기 ── */
function measure(url: string): Promise<{ width: number; height: number } | null> {
    return new Promise((resolve) => {
        const image = new Image();
        image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight });
        image.onerror = () => resolve(null);
        image.src = url;
    });
}

function revokePreview() {
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = null;
}

function clearFile() {
    pickSeq++;
    revokePreview();
    file.value = null;
    fileProblem.value = '';
}

async function onFilePicked(event: Event) {
    const input = event.target as HTMLInputElement;
    const picked = input.files?.[0];
    input.value = '';
    if (!picked || !state.value || formLocked.value) return;
    clearFile();
    serverError.value = '';
    const mine = pickSeq;
    file.value = picked;
    const limits = state.value.limits;
    const problem = UtilNicknameImage.precheck(picked, limits);
    if (problem) {
        fileProblem.value = UtilNicknameImageMessages.problem(t, problem, { size: picked.size, limits });
        return;
    }
    const url = URL.createObjectURL(picked);
    const size = await measure(url);
    if (mine !== pickSeq || !alive) {
        URL.revokeObjectURL(url);
        return;
    }
    if (!size || !UtilNicknameImage.isRatioOk(size.width, size.height, limits)) {
        URL.revokeObjectURL(url);
        fileProblem.value = size ? UtilNicknameImageMessages.problem(t, 'RATIO', { width: size.width, height: size.height, limits }) : UtilNicknameImageMessages.problem(t, 'UNREADABLE');
        return;
    }
    previewUrl.value = url;
}

/* ── 신청 · 취소 · 해제 ── */
async function onFailed(e: unknown): Promise<string> {
    const outcome = UtilNicknameImage.outcomeOf(e);
    const reason = UtilNicknameImage.blockedReasonOf(outcome);
    if (reason) serverBlocked.value = reason;
    const error = e instanceof ApiError ? e : null;
    const text = UtilNicknameImageMessages.failure(t, outcome, {
        message: error?.message,
        fileField: error?.field('file'),
        retryAfterSeconds: error?.retryAfterSeconds,
        maxBytes: state.value?.limits.maxBytes,
        reapplyHours: reapplyHours.value,
        language: values.value.language,
    });
    if (UtilNicknameImage.needsReload(outcome)) await load(true);
    return text;
}

async function submit() {
    const picked = file.value;
    if (!picked || !canSubmit.value) return;
    if (!(await confirm({ title: t('nicknameImage.applyConfirmTitle'), message: t('nicknameImage.applyConfirmMessage'), confirmLabel: t('nicknameImage.submit') }))) return;
    if (busy.value !== null || !alive) return;
    busy.value = 'apply';
    progress.value = 0;
    serverError.value = '';
    uploadController = new AbortController();
    try {
        await api.apply(picked, { signal: uploadController.signal, onProgress: (fraction) => (progress.value = fraction) });
    } catch (e) {
        if (!alive) return;
        busy.value = null;
        serverError.value = await onFailed(e);
        return;
    }
    if (!alive) return;
    busy.value = null;
    toast.success(t('nicknameImage.toastApplied'));
    clearFile();
    consent.value = false;
    opened.value = false;
    await load(true);
}

async function cancelRequest() {
    if (busy.value !== null) return;
    busy.value = 'cancel';
    try {
        await api.cancelRequest();
    } catch (e) {
        if (!alive) return;
        busy.value = null;
        toast.error(await onFailed(e), 8000);
        return;
    }
    if (!alive) return;
    busy.value = null;
    toast.success(t('nicknameImage.toastCanceled'));
    await load(true);
}

async function removeCurrent() {
    if (busy.value !== null || !user.value) return;
    const ok = await confirm({
        title: t('nicknameImage.removeConfirmTitle'),
        message: t('nicknameImage.removeConfirmMessage', { nickname: user.value.nickname }),
        confirmLabel: t('nicknameImage.removeConfirmLabel'),
        tone: 'danger',
    });
    if (!ok) return;
    busy.value = 'remove';
    try {
        await api.remove();
    } catch (e) {
        if (!alive) return;
        busy.value = null;
        toast.error(await onFailed(e), 8000);
        return;
    }
    if (!alive) return;
    busy.value = null;
    toast.success(t('nicknameImage.toastRemoved'));
    opened.value = false;
    await load(true);
}

function closeForm() {
    if (busy.value !== null) return;
    clearFile();
    consent.value = false;
    serverError.value = '';
    opened.value = false;
}

onBeforeUnmount(() => {
    alive = false;
    request++;
    uploadController?.abort();
    revokePreview();
});
</script>
