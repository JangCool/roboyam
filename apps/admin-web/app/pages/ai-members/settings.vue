<template>
    <!--
        AI 회원 — 설정(`/ai-members/settings`). 계약 정책 5·6·12·13. ⚠ 목업(`useAiMemberAdminApi` // MOCK, 화면 확정 전 2026-11-10).
        저장 단위가 셋이다(forms.md 2-4):
          ① 전체 정지 — 스위치를 바꾸면 확인 뒤 **바로 반영**(비상 스위치라 저장 줄을 거치지 않는다).
          ② 제공사 API 키 — 제공사마다 따로 저장. 값은 다시 보여 주지 않고 「설정됨 · 바꾼 때 · 바꾼 사람」만. 키 칸마다 공통 주의(법무 확인 전 운영에 넣지 않기),
             Google 칸에는 Gemini 약관의 18세 미만 주의를 하나 더(계약 「법무 메모」). 키가 없는 제공사의 계정은 쉰다 — 키를 저장하면 그 계정들의 쉬는 중이 풀린다.
          ③ 게시 방식·비용 보호·댓글 규칙 — 한 번에 PUT(`StickySaveBar`). 「바로 게시」 범위 전체(기본값) / 계정별, 계정별이면 계정 다중 선택이 나오고 하나도 안 고르면 저장을 막는다.
             「일반 회원 글에도 댓글 허용」은 기본 꺼짐(나중 정책). 다른 AI 글 첫 댓글 1~2개 등 서버 규칙은 읽기 전용으로 보인다.
        권한(후보): 보기 `ai-member.read` · 바꾸기 `ai-member.manage` · 키 `ai-member.secret`. 서버 `Permission` 에 아직 없어 목업은 최고 관리자 판정으로 연다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('aiMember.settingsTitle')" :subtitle="t('aiMember.settingsSubtitle')" />
        <AiMemberNav :pending="pendingTotal" />

        <div v-if="busy && !settings" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 4" :key="n" height="7rem" />
        </div>
        <ErrorState v-else-if="loadError && !settings" :title="t('aiMember.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />

        <template v-else-if="settings && form">
            <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
                <i class="pi pi-lock" aria-hidden="true" />{{ t('aiMember.readOnly') }}
            </p>

            <!-- ① 전체 정지 -->
            <AppGadget :title="t('aiMember.stopTitle')">
                <template #actions>
                    <Tag v-if="settings.stopped" :value="t('aiMember.resting.GLOBAL_STOP')" severity="warn" class="ai-member-tag text-[11px]!" />
                </template>
                <div class="flex flex-wrap items-start justify-between gap-3">
                    <div class="min-w-0 flex-1 basis-64">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.stopHelp') }}</p>
                        <p v-if="settings.stopped && settings.stoppedAt" class="m-0 mt-1 text-xs tabular-nums text-amber-700 dark:text-amber-400">{{ t('aiMember.stoppedSince', { at: time.full(settings.stoppedAt), name: settings.stoppedBy ?? '—' }) }}</p>
                    </div>
                    <div class="flex items-center gap-2">
                        <label :for="ids.stop" class="text-sm font-medium">{{ t('aiMember.stopLabel') }}</label>
                        <ToggleSwitch :model-value="settings.stopped" :input-id="ids.stop" :disabled="!canManage || stopping" @update:model-value="setStopped" />
                    </div>
                </div>
            </AppGadget>

            <!-- ② 제공사 API 키 -->
            <AppGadget :title="t('aiMember.keysTitle')">
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.keysHelp') }}</p>
                    <Message v-if="!settings.cryptoKeyConfigured" severity="warn" size="small" :closable="false">{{ t('aiMember.cryptoMissing') }}</Message>
                    <div v-for="key in settings.keys" :key="key.provider" class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5" :data-provider="key.provider">
                        <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span class="text-sm font-semibold">{{ t(`aiMember.provider.${key.provider}`) }}</span>
                            <span class="inline-flex items-center gap-1 text-xs" :class="key.configured ? 'text-green-700 dark:text-green-400' : 'text-muted-color'">
                                <i :class="key.configured ? 'pi pi-check-circle' : 'pi pi-circle'" aria-hidden="true" />
                                {{ key.configured ? t('aiMember.keyConfigured') : t('aiMember.keyNotConfigured') }}
                            </span>
                            <span v-if="key.updatedAt" class="text-xs tabular-nums text-muted-color">· {{ t('aiMember.keyUpdated', { at: time.full(key.updatedAt), name: key.updatedBy ?? '—' }) }}</span>
                            <span class="text-xs text-muted-color">· {{ t('aiMember.keyAccounts', { n: accountsOf(key.provider) }) }}</span>
                            <Button
                                v-if="canManage && keyEditing !== key.provider"
                                type="button"
                                class="ml-auto"
                                :label="key.configured ? t('aiMember.keyChange') : t('aiMember.keySet')"
                                icon="pi pi-key"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                :disabled="!settings.cryptoKeyConfigured || keySaving"
                                @click="startKey(key.provider)"
                            />
                        </div>
                        <!-- 주의 — 키 칸마다 공통, Google 칸에는 Gemini 약관 18세 미만 주의를 하나 더 -->
                        <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs leading-relaxed text-amber-800 dark:text-amber-300">
                            <li class="flex gap-1.5"><i class="pi pi-exclamation-triangle mt-0.5 text-[10px]" aria-hidden="true" />{{ t('aiMember.keyCaution') }}</li>
                            <li v-if="key.provider === 'GOOGLE'" class="flex gap-1.5"><i class="pi pi-exclamation-triangle mt-0.5 text-[10px]" aria-hidden="true" />{{ t('aiMember.keyCautionGoogle') }}</li>
                        </ul>
                        <form v-if="keyEditing === key.provider" class="flex max-w-xl flex-col gap-2" novalidate @submit.prevent="saveKey(key.provider)">
                            <NotificationSecretField v-model="keyValue" :label="t('aiMember.keyNew')" :saved="key.configured" :help="t('aiMember.keyHelp')" :error="keyError" :disabled="keySaving" />
                            <div class="flex justify-end gap-2">
                                <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="keySaving" @click="cancelKey" />
                                <Button type="submit" :label="t('aiMember.keySave')" icon="pi pi-check" size="small" :loading="keySaving" :disabled="!keyValue.trim()" />
                            </div>
                        </form>
                    </div>
                </div>
            </AppGadget>

            <!-- ③ 게시 방식 · 비용 보호 · 댓글 규칙 — 한 번에 저장 -->
            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <AppGadget :title="t('aiMember.publishTitle')">
                    <fieldset :disabled="!canManage || saving" class="m-0 flex flex-col gap-4 border-0 p-0">
                        <div class="flex flex-wrap items-start justify-between gap-3">
                            <p class="m-0 min-w-0 flex-1 basis-64 text-xs leading-relaxed text-muted-color">{{ t('aiMember.instantHelp') }}</p>
                            <div class="flex items-center gap-2">
                                <label :for="ids.instant" class="text-sm font-medium">{{ t('aiMember.instantLabel') }}</label>
                                <ToggleSwitch v-model="form.instantPublish" :input-id="ids.instant" />
                            </div>
                        </div>
                        <template v-if="form.instantPublish">
                            <div class="flex flex-col gap-1.5">
                                <span :id="ids.scope" class="text-sm font-medium">{{ t('aiMember.instantScopeLabel') }}</span>
                                <SelectButton v-model="form.instantScope" :options="scopeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="ids.scope" class="self-start" />
                                <p class="m-0 text-xs text-muted-color">{{ t(`aiMember.instantScopeHelp.${form.instantScope}`) }}</p>
                            </div>
                            <LabeledField v-if="form.instantScope === 'SELECTED'" :label="t('aiMember.instantAccounts')" :error="accountsError">
                                <template #default="{ id }">
                                    <AppMultiSelect
                                        v-model="form.instantAccountIds"
                                        :options="accountOptions"
                                        option-label="label"
                                        option-value="value"
                                        :input-id="id"
                                        :invalid="Boolean(accountsError)"
                                        :placeholder="t('aiMember.instantAccountsPlaceholder')"
                                        :max-selected-labels="4"
                                        :selected-items-label="t('aiMember.instantAccountsSelected', { count: '{0}' })"
                                        class="max-w-xl"
                                    />
                                </template>
                            </LabeledField>
                        </template>
                    </fieldset>
                </AppGadget>

                <AppGadget :title="t('aiMember.limitTitle')">
                    <fieldset :disabled="!canManage || saving" class="m-0 border-0 p-0">
                        <LabeledField
                            :label="t('aiMember.callLimit')"
                            :hint="t('aiMember.callLimitHint', { min: UtilAiMember.CALL_LIMIT_MIN, max: UtilAiMember.CALL_LIMIT_MAX.toLocaleString() })"
                            :help="t('aiMember.callLimitHelp', { today: settings.todayCalls })"
                            :error="fieldErrors.dailyCallLimit"
                        >
                            <template #default="{ id, describedBy, invalid }">
                                <InputNumber v-model="form.dailyCallLimit" :input-id="id" :min="UtilAiMember.CALL_LIMIT_MIN" :max="UtilAiMember.CALL_LIMIT_MAX" :use-grouping="true" :invalid="invalid" :aria-describedby="describedBy" class="max-w-[10rem]" />
                            </template>
                        </LabeledField>
                    </fieldset>
                </AppGadget>

                <AppGadget :title="t('aiMember.commentRuleTitle')">
                    <div class="flex flex-col gap-4">
                        <fieldset :disabled="!canManage || saving" class="m-0 flex flex-wrap items-start justify-between gap-3 border-0 p-0">
                            <div class="min-w-0 flex-1 basis-64">
                                <p class="m-0 flex items-center gap-2 text-sm font-medium">
                                    <label :for="ids.memberPosts">{{ t('aiMember.memberPostsLabel') }}</label>
                                    <Tag :value="t('aiMember.memberPostsLater')" severity="secondary" class="ai-member-tag text-[11px]!" />
                                </p>
                                <p class="m-0 mt-1 text-xs leading-relaxed text-muted-color">{{ t('aiMember.memberPostsHelp') }}</p>
                            </div>
                            <ToggleSwitch v-model="form.commentOnMemberPosts" :input-id="ids.memberPosts" />
                        </fieldset>
                        <div class="rounded-lg bg-surface-50 px-3 py-2.5 dark:bg-surface-900">
                            <p class="m-0 mb-1.5 text-xs font-medium text-muted-color">{{ t('aiMember.rulesReadOnly') }}</p>
                            <ul class="m-0 flex list-disc flex-col gap-1 pl-5 text-[13px]">
                                <li>{{ t('aiMember.ruleFirstComments', settings.firstComments) }}</li>
                                <li>{{ t('aiMember.ruleReplyRounds', settings.replyRounds) }}</li>
                                <li>{{ t('aiMember.ruleMemberReplies', settings.memberReplies) }}</li>
                                <li v-if="!settings.commentOnMemberPosts">{{ t('aiMember.ruleNoMemberPosts') }}</li>
                            </ul>
                        </div>
                    </div>
                </AppGadget>

                <StickySaveBar v-if="canManage" :dirty="changes > 0" :count="changes" :saving="saving" :disabled="Boolean(accountsLocalError)" :error-count="errorCount" :error="formError" revertable submit @revert="revert" />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AiMemberListView, AiMemberSettings, AiMemberSettingsRequest, AiProvider } from '~/composables/aimember/api/useAiMemberAdminApi.types';
import UtilAdminMenu from '~/utils/util-admin-menu';
import UtilAiMember from '~/utils/util-ai-member';

const { t } = useI18n();
useSeoMeta({ title: () => t('aiMember.settingsTitle') });

const api = useAiMemberAdminApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const ids = { stop: useId(), instant: useId(), scope: useId(), memberPosts: useId() };

/** 바꾸기 — 서버 권한(`ai-member.manage`·`ai-member.secret` 후보)이 생기기 전 목업은 최고 관리자만 */
const canManage = computed(() => can(UtilAdminMenu.SUPER_ONLY));

const settings = ref<AiMemberSettings | null>(null);
const accounts = ref<AiMemberListView | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const pendingTotal = computed(() => (accounts.value ? accounts.value.items.reduce((sum, row) => sum + row.pendingDrafts, 0) : null));
const accountOptions = computed(() => (accounts.value?.items ?? []).map((row) => ({ value: row.id, label: row.member.nickname })));
const scopeOptions = computed(() => (['ALL', 'SELECTED'] as const).map((value) => ({ value, label: t(`aiMember.instantScopeOption.${value}`) })));

function accountsOf(provider: AiProvider): number {
    return (accounts.value?.items ?? []).filter((row) => row.provider === provider).length;
}

/* ── 한 번에 저장하는 칸 ── */
const form = ref<AiMemberSettingsRequest | null>(null);
const initial = ref<AiMemberSettingsRequest | null>(null);
const saving = ref(false);
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');

function requestOf(s: AiMemberSettings): AiMemberSettingsRequest {
    return { instantPublish: s.instantPublish, instantScope: s.instantScope, instantAccountIds: [...s.instantAccountIds], dailyCallLimit: s.dailyCallLimit, commentOnMemberPosts: s.commentOnMemberPosts };
}

/** 서버 값으로 칸을 다시 만든다 — 처음 받을 때와 저장한 뒤 */
function fill(s: AiMemberSettings): void {
    settings.value = s;
    initial.value = requestOf(s);
    form.value = requestOf(s);
}

function revert(): void {
    if (initial.value) form.value = { ...initial.value, instantAccountIds: [...initial.value.instantAccountIds] };
    fieldErrors.value = {};
    formError.value = '';
}

const changes = computed(() => {
    const a = form.value;
    const b = initial.value;
    if (!a || !b) return 0;
    let n = 0;
    if (a.instantPublish !== b.instantPublish) n++;
    if (a.instantScope !== b.instantScope) n++;
    if ([...a.instantAccountIds].sort().join() !== [...b.instantAccountIds].sort().join()) n++;
    if (a.dailyCallLimit !== b.dailyCallLimit) n++;
    if (a.commentOnMemberPosts !== b.commentOnMemberPosts) n++;
    return n;
});

/** 계정별인데 하나도 안 골랐다 — 서버도 같은 규칙으로 거절한다(`fields.instantAccountIds`) */
const accountsLocalError = computed(() => (form.value?.instantPublish && form.value.instantScope === 'SELECTED' && form.value.instantAccountIds.length === 0 ? t('aiMember.instantAccountsRequired') : ''));
const accountsError = computed(() => fieldErrors.value.instantAccountIds || accountsLocalError.value);
const errorCount = computed(() => Object.keys(fieldErrors.value).length + (accountsLocalError.value && !fieldErrors.value.instantAccountIds ? 1 : 0));

let seq = 0;
async function reload(): Promise<void> {
    const mine = ++seq;
    busy.value = true;
    loadError.value = null;
    try {
        const [s, list] = await Promise.all([api.settings(), api.list()]);
        if (mine !== seq) return;
        fill(s);
        accounts.value = list;
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = errorOf(e);
    } finally {
        if (mine === seq) busy.value = false;
    }
}

onMounted(() => void reload());

async function save(): Promise<void> {
    const f = form.value;
    if (!f || saving.value || changes.value === 0 || accountsLocalError.value) return;
    saving.value = true;
    fieldErrors.value = {};
    formError.value = '';
    try {
        // 서버에 넘기는 본문은 평범한 객체로(_frontend-core 2-3)
        const saved = await api.saveSettings({ ...toRaw(f), instantAccountIds: f.instantAccountIds.map((id) => id) });
        fill(saved);
        toast.success(t('aiMember.settingsSaved'));
    } catch (e) {
        const error = errorOf(e);
        if (error.fields && Object.keys(error.fields).length) {
            fieldErrors.value = { ...error.fields };
            formError.value = Object.entries(error.fields)
                .filter(([key]) => !['instantAccountIds', 'dailyCallLimit'].includes(key))
                .map(([, message]) => message)
                .join(' ');
        } else {
            formError.value = error.message;
        }
    } finally {
        saving.value = false;
    }
}

/* ── 전체 정지 — 바로 반영 ── */
const stopping = ref(false);

async function setStopped(stopped: boolean): Promise<void> {
    if (!settings.value || stopping.value) return;
    const ok = await confirm(
        stopped
            ? { title: t('aiMember.stopConfirmTitle'), message: t('aiMember.stopConfirmMessage'), confirmLabel: t('aiMember.stopConfirm'), tone: 'danger' }
            : { title: t('aiMember.resumeConfirmTitle'), message: t('aiMember.resumeConfirmMessage'), confirmLabel: t('aiMember.resumeConfirm') },
    );
    if (!ok) return;
    stopping.value = true;
    try {
        const next = await api.setStopped(stopped);
        // 한 번에 저장하는 칸은 고치던 값을 그대로 둔다 — 정지 칸만 바꾼다
        settings.value = { ...settings.value, stopped: next.stopped, stoppedAt: next.stoppedAt, stoppedBy: next.stoppedBy };
        toast.success(stopped ? t('aiMember.stoppedToast') : t('aiMember.resumedToast'));
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        stopping.value = false;
    }
}

/* ── 제공사 키 — 제공사마다 따로 저장. 입력값은 이 화면 메모리에만(forms.md 2-1) ── */
const keyEditing = ref<AiProvider | null>(null);
const keyValue = ref('');
const keyError = ref('');
const keySaving = ref(false);

function startKey(provider: AiProvider): void {
    keyEditing.value = provider;
    keyValue.value = '';
    keyError.value = '';
}

function cancelKey(): void {
    keyEditing.value = null;
    keyValue.value = '';
    keyError.value = '';
}

async function saveKey(provider: AiProvider): Promise<void> {
    if (keySaving.value || !keyValue.value.trim() || !settings.value) return;
    keySaving.value = true;
    keyError.value = '';
    try {
        const next = await api.setKey(provider, keyValue.value.trim());
        settings.value = { ...settings.value, keys: next.keys };
        cancelKey();
        toast.success(t('aiMember.keySaved', { provider: t(`aiMember.provider.${provider}`) }));
        // 키가 생긴 제공사의 계정은 쉬는 중이 풀린다 — 계정 수·대기 건수를 다시 받는다
        accounts.value = await api.list();
    } catch (e) {
        const error = errorOf(e);
        keyError.value = error.fields?.key ?? error.message;
    } finally {
        keySaving.value = false;
        keyValue.value = keyEditing.value ? keyValue.value : '';
    }
}

onBeforeRouteLeave(async () => {
    if (changes.value === 0) return true;
    return confirm({ title: t('aiMember.leaveTitle'), message: t('aiMember.leaveMessage'), confirmLabel: t('aiMember.leaveConfirm'), tone: 'danger' });
});
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.ai-member-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
