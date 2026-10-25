<template>
    <!-- 새 쪽지 `/messages/new?to=` — 받는 사람 · 내용 · 오늘 남은 한도 · 보내기. 모바일은 안쪽 화면, 데스크톱은 쪽지함 오른쪽 칸 -->
    <section class="flex min-h-0 flex-1 flex-col bg-surface-0 dark:bg-surface-900" :aria-label="t('dm.newLabel')" data-testid="dm-new-page">
        <header class="flex h-12 shrink-0 items-center gap-1 border-b border-surface px-2">
            <button
                type="button"
                class="flex size-10 shrink-0 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary lg:hidden dark:hover:bg-surface-800"
                :aria-label="t('common.back')"
                data-testid="dm-new-back"
                @click="goBack"
            >
                <i class="pi pi-chevron-left" aria-hidden="true" />
            </button>
            <h1 class="m-0 min-w-0 flex-1 text-center text-sm font-semibold lg:px-3 lg:text-left">{{ t('dm.newLabel') }}</h1>
            <span class="size-10 shrink-0 lg:hidden" aria-hidden="true" />
        </header>

        <div class="min-h-0 flex-1 overflow-y-auto">
            <div v-if="phase === 'loading'" class="mx-auto w-full max-w-xl space-y-3 p-4" role="status" :aria-label="t('dm.newLoading')" data-testid="dm-new-loading">
                <div class="h-11 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                <div class="h-32 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                <div class="h-16 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            </div>

            <ErrorState v-else-if="phase === 'error'" :title="t('dm.newLoadFailed')" :description="loadError" retry data-testid="dm-new-error" @retry="loadMe" />

            <form v-else class="mx-auto w-full max-w-xl space-y-4 p-4" novalidate :aria-label="t('dm.newFormLabel')" @submit.prevent="onSubmit">
                <!-- 못 보내는 이유 — 받는 사람을 고르기 전에는 나 때문인 이유만, 고른 뒤에는 서버가 그 사람에게 보낼 수 있나를 알려 준다 -->
                <DmReasonBanner v-if="blockReason" :reason="blockReason" :available-from="availableFrom" :quota="quota" :min-days="minDays" />

                <div>
                    <p class="mb-1.5 text-sm font-semibold">{{ t('dm.recipientLabel') }}</p>
                    <DmRecipientPicker :selected="recipient" :loading="recipientLoading" :search="searchRecipients" @select="onSelect" @clear="onClear" />
                    <p v-if="lost" class="mt-1.5 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="dm-new-lost">{{ lost }}</p>
                    <p v-if="showRecipientError" class="mt-1.5 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="dm-new-recipient-error">{{ t('dm.recipientRequired') }}</p>
                    <p v-if="checking" class="mt-1.5 text-xs text-muted-color" role="status">{{ t('dm.checking') }}</p>
                    <p v-else-if="checkError" class="mt-1.5 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="dm-new-check-error">
                        {{ checkError }}
                        <button type="button" class="ml-1 underline focus-visible:outline-2 focus-visible:outline-primary" @click="retryCheck">{{ t('common.retry') }}</button>
                    </p>
                    <p v-if="existingThread" class="mt-1.5 text-xs text-muted-color" data-testid="dm-new-existing">
                        {{ t('dm.recipientExisting') }} ·
                        <NuxtLink :to="UtilDm.threadPath(existingThread)" class="underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('dm.openThread') }}</NuxtLink>
                    </p>
                </div>

                <div>
                    <label :for="bodyId" class="mb-1.5 block text-sm font-semibold">{{ t('dm.bodyLabel') }}</label>
                    <Textarea
                        :id="bodyId"
                        v-model="body"
                        name="dm-body"
                        rows="6"
                        fluid
                        class="resize-none"
                        :disabled="locked"
                        :placeholder="locked ? lockText : t('dm.bodyPlaceholder')"
                        :invalid="Boolean(bodyError) || contactWarn"
                        :aria-describedby="bodyHintId"
                        data-testid="dm-new-body"
                    />
                    <div :id="bodyHintId" class="mt-1 flex items-start justify-between gap-2 text-xs">
                        <span class="text-red-600 dark:text-red-400" :role="bodyError ? 'alert' : undefined" data-testid="dm-new-body-error">{{ bodyError }}</span>
                        <span class="shrink-0 tabular-nums" :class="count > max ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'" data-testid="dm-new-counter">{{
                            t('dm.counter', { count, max })
                        }}</span>
                    </div>
                </div>

                <DmNotice v-if="contactWarn" tone="warn" :title="t('dm.contactWarnTitle')" :text="t('dm.contactWarnText')" live data-testid="dm-new-contact-warn" />

                <DmQuotaStat v-if="quota" :quota="quota" />

                <p class="m-0 text-xs text-muted-color" data-testid="dm-new-help">{{ help }}</p>

                <DmNotice v-if="notice" :tone="notice.tone" :title="notice.title" :text="notice.text" :link="notice.link" live dismissible data-testid="dm-new-notice" @dismiss="clearNotice" />

                <Button type="submit" :label="t('dm.send')" fluid :loading="sending" :disabled="!canSubmit" data-testid="dm-new-send" />
            </form>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { DmCanSend, DmPartner, DmReason, MyDm } from '~/composables/dm/api/useDmApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilDm from '~/utils/util-dm';
import UtilDmContact from '~/utils/util-dm-contact';
import UtilDmMessages from '~/utils/util-dm-messages';
import UtilDmRules from '~/utils/util-dm-rules';

/**
 * 새 쪽지 — `/messages/new`(받는 사람을 직접 찾는다) · `/messages/new?to={회원ID}`(프로필의 「쪽지 보내기」가 미리 고른다). 로그인 필요(community `pages/messages/new.vue`).
 *  - 처음에 내 쪽지 상태(`me`)로 보낼 자격·한도·규칙을 읽는다. 나 때문에 못 보내면(가입 7일 전·정지·인증 전·오늘 20통) 받는 사람을 고르기 전부터 배너로 알린다.
 *  - 받는 사람을 고르면 `canSend` 로 그 사람에게 보낼 수 있나·새 상대인가·링크를 보낼 수 있나를 읽는다(한도를 쓰지 않는다).
 *  - 보내면 대화방으로 옮기고(`replace`) 「쪽지를 보냈어요 · 오늘 남은 N통 · 새 상대 M명」 토스트. 거절은 입력을 그대로 두고 입력창 위 안내로 알린다.
 */
definePageMeta({ middleware: 'auth', shell: 'inner' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('dm.newLabel'), description: () => t('dm.newSeo', { site: siteLabel.value }), robots: 'noindex,nofollow' });

const route = useRoute();
const router = useRouter();
const api = useDmApi();
const profileApi = useUserProfileApi();
const inbox = useDmInbox();
const toast = useAppToast();
const time = useDisplayTime();
const { notice, cooling, clear: clearNotice, rejected, failedLocally } = useDmNotice();

const uid = useId();
const bodyId = `${uid}-body`;
const bodyHintId = `${uid}-body-hint`;

/* ── 내 상태 ── */
const phase = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const me = ref<MyDm | null>(null);

async function loadMe() {
    phase.value = 'loading';
    loadError.value = '';
    try {
        me.value = await api.me();
        phase.value = 'ready';
    } catch (e) {
        loadError.value = UtilDmMessages.errorOf(t, e);
        phase.value = 'error';
    }
}

const minDays = computed(() => me.value?.rules.minAccountAgeDays ?? UtilDmRules.MIN_ACCOUNT_AGE_DAYS);
const max = computed(() => me.value?.rules.bodyMax ?? UtilDmRules.BODY_MAX);

/* ── 받는 사람 ── */
const recipient = ref<DmPartner | null>(null);
const recipientLoading = ref(false);
const check = ref<DmCanSend | null>(null);
const checking = ref(false);
const checkError = ref('');
const lost = ref('');
let checkSeq = 0;

const searchRecipients = (q: string, signal: AbortSignal) => api.recipients(q, signal);

function dropRecipient() {
    checkSeq++;
    recipient.value = null;
    check.value = null;
    checking.value = false;
    checkError.value = '';
    recipientLoading.value = false;
    lost.value = t('dm.recipientLost');
}

async function runCheck(id: string) {
    const mine = ++checkSeq;
    checking.value = true;
    checkError.value = '';
    check.value = null;
    try {
        const res = await api.canSend(id);
        if (mine !== checkSeq) return;
        check.value = res;
    } catch (e) {
        if (mine !== checkSeq) return;
        if (UtilDmMessages.isProfileMissing(e)) dropRecipient();
        else checkError.value = UtilDmMessages.errorOf(t, e);
    } finally {
        if (mine === checkSeq) checking.value = false;
    }
}

function retryCheck() {
    if (recipient.value?.id) void runCheck(recipient.value.id);
}

/** 주소의 `?to=` — 이름·등급은 공개 프로필에서, 보낼 수 있나는 쪽지 API 에서 한꺼번에 */
async function preselect(id: string) {
    const mine = ++checkSeq;
    lost.value = '';
    recipientLoading.value = true;
    checking.value = true;
    checkError.value = '';
    check.value = null;
    recipient.value = null;
    const [profile, state] = await Promise.allSettled([profileApi.profile(id), api.canSend(id)]);
    if (mine !== checkSeq) return;
    recipientLoading.value = false;
    checking.value = false;
    if (profile.status === 'rejected') {
        lost.value = profile.reason instanceof ApiError && profile.reason.code === 'PROFILE_NOT_FOUND' ? t('dm.recipientLost') : UtilDmMessages.errorOf(t, profile.reason);
        return;
    }
    const p = profile.value;
    recipient.value = { id: p.id, nickname: p.nickname, grade: p.grade, gradeLabel: p.gradeLabel, role: p.role, withdrawn: false, memo: p.memo ?? null };
    if (state.status === 'fulfilled') check.value = state.value;
    else if (UtilDmMessages.isProfileMissing(state.reason)) dropRecipient();
    else checkError.value = UtilDmMessages.errorOf(t, state.reason);
}

const toId = computed(() => UtilDmRules.parseTo(route.query.to));
watch(
    toId,
    (id) => {
        if (id) void preselect(id);
    },
    { immediate: true },
);

async function onSelect(user: DmPartner) {
    if (!user.id) return;
    recipient.value = user;
    lost.value = '';
    clearNotice();
    await runCheck(user.id);
    await nextTick();
    if (!locked.value) document.getElementById(bodyId)?.focus();
}

function onClear() {
    checkSeq++;
    recipient.value = null;
    check.value = null;
    checking.value = false;
    checkError.value = '';
    clearNotice();
    // 주소에 남은 `?to=` 도 지운다 — 새로 고쳐도 같은 사람이 다시 골라지지 않게
    if (route.query.to !== undefined) void router.replace({ path: route.path });
}

/* ── 내용 ── */
const body = ref('');
const fieldError = ref('');
watch(body, () => (fieldError.value = ''));

const count = computed(() => UtilDmRules.count(body.value));
const problem = computed(() => UtilDmRules.bodyProblem(body.value, max.value));
/** 비었음은 오류로 보이지 않는다(아직 안 쓴 것) */
const bodyError = computed(() => fieldError.value || (problem.value && problem.value !== 'EMPTY' ? UtilDmMessages.bodyProblem(t, problem.value, max.value) : ''));

/* ── 못 보내는 이유 ── */
const blockReason = computed<DmReason | null>(() => (recipient.value ? (check.value?.reason ?? null) : (me.value?.sender.reason ?? null)));
const availableFrom = computed(() => (recipient.value ? (check.value?.availableFrom ?? null) : (me.value?.sender.availableFrom ?? null)));
const quota = computed(() => check.value?.quota ?? me.value?.quota ?? null);
const locked = computed(() => blockReason.value !== null);
const lockText = computed(() =>
    blockReason.value ? UtilDmMessages.reason(t, blockReason.value, { quota: quota.value, availableFrom: availableFrom.value, minDays: minDays.value, time: time.value }).lock : '',
);

const contactWarn = computed(() => Boolean(recipient.value && check.value && !check.value.contactAllowed && UtilDmContact.has(body.value)));
const existingThread = computed(() => check.value?.threadId ?? null);
const showRecipientError = computed(() => !recipient.value && !recipientLoading.value && body.value.trim() !== '');
const help = computed(() => (recipient.value && check.value ? UtilDmMessages.recipientHelp(t, check.value.newPartner) : t('dm.newHelp')));

/* ── 보내기 ── */
const sending = ref(false);
const canSubmit = computed(() => Boolean(recipient.value?.id && check.value?.canSend) && !locked.value && problem.value === null && !contactWarn.value && !sending.value && !cooling.value);

async function onSubmit() {
    const to = recipient.value;
    if (!canSubmit.value || !to?.id) return;
    sending.value = true;
    clearNotice();
    fieldError.value = '';
    try {
        const res = await api.send(to.id, UtilDmRules.normalize(body.value));
        toast.show(UtilDmMessages.sentToast(t, res.quota, res.created));
        inbox.touch(res.threadId, to, res.message);
        await router.replace(UtilDm.threadPath(res.threadId));
    } catch (e) {
        if (UtilDmMessages.isLocalFailure(e)) {
            failedLocally(e);
        } else if (UtilDmMessages.isProfileMissing(e)) {
            dropRecipient();
        } else {
            if (e instanceof ApiError && e.code === 'VALIDATION' && e.field('body')) fieldError.value = e.field('body') ?? '';
            else rejected(e);
            if (UtilDmMessages.changesState(e)) {
                void loadMeQuietly();
                if (to.id) void runCheck(to.id);
            }
        }
    } finally {
        sending.value = false;
    }
}

async function loadMeQuietly() {
    try {
        me.value = await api.me();
    } catch {
        /* 이미 보이는 값을 그대로 둔다 */
    }
}

function goBack() {
    if (typeof window !== 'undefined' && window.history.state?.back) router.back();
    else void router.push(UtilDm.INBOX_PATH);
}

void loadMe();
</script>
