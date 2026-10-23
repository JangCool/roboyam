<template>
    <!--
        메일 서버 설정(`/settings/mail`) — 하모니카3 `settings/application/mail.vue` 방식이다: 폼 구획 하나 + [저장], 저장된 설정으로 [시험 발송](받는 주소 대화상자 → 결과).
        설치 마법사가 넣은 행을 서버가 그대로 준다(`mail_settings` 하나뿐). 설정이 없으면(설치 때 건너뜀) 빈 폼 + 「아직 메일 서버가 없어요」 안내.
        구획: ① 메일 서버(`mail-server` — 구획 하나 = 저장 하나, 저장 줄은 구획 맨 아래) 설정 이력은 제목 옆 「변경 이력」 단추가 여는 오른쪽 서랍(`SettingHistoryDrawer`)이다.
        - 비밀번호는 서버가 주지 않는다 — 늘 빈 칸, 저장된 것이 있으면 「입력됨」. 비워 저장하면 그대로 둔다. 다만 사용자 이름·서버 주소·보안 방식을 바꾸며 비우면 서버가 `fields.password` 로 거절하므로
          화면이 그 칸에 미리 「다시 입력해 주세요」를 보인다(`UtilMailSettings.validate` — 서버 `sameTarget` 과 같은 규칙). 사용자 이름을 지우면 비밀번호도 지워진다는 안내도 그 칸에.
        - 서버에 암호화 키가 없으면 맨 위 경고 + 비밀번호 칸 잠금(새 비밀번호는 503 `CRYPTO_KEY_MISSING`).
        - 시험 발송은 **저장된 설정으로** 보낸다 — 고친 값이 있거나 설정이 없으면 단추를 잠그고 까닭을 옆에 적는다. 받는 주소 기본값은 로그인한 운영자 이메일(`me.user.email`, 없으면 빈 칸).
          결과는 대화상자 안에: 성공 · 실패(원인 코드별 문구 `mail.reason*`, 모르는 코드는 서버 `detail`). `fields.to` 는 칸 아래, 429 `MAIL_TEST_RATE_LIMITED` 는 남은 초와 함께 한 줄. 실패한 시험도 오류가 아니라 결과다.
        - 오류: 칸 문구(`fields.host|port|security|username|password|fromAddress|senderName|subjectPrefix`)는 그 칸 아래, 칸에 붙지 않는 것(503 등)은 저장 줄 위 한 줄(서버 문구 그대로).
        - 권한: 화면 `site.read`(메뉴가 거른다) · 저장·시험 `site.secret` — 없으면 입력 `readonly`, 저장 줄·시험 단추 없음. 감사·이력은 서버가 남긴다.
        서버 API: `useMailSettingsApi`(`docs/features/mail-settings-contract.md`).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('mail.title')" :subtitle="t('mail.subtitle')">
            <template #actions>
                <Button :label="t('settingHistory.open')" icon="pi pi-history" severity="secondary" outlined size="small" @click="openHistory" />
            </template>
        </PageTitle>

        <Message v-if="!canSecret" severity="info" :closable="false" size="small">{{ t('mail.readOnly') }}</Message>
        <Message v-if="view && !view.cryptoKeyConfigured" severity="warn" :closable="false" size="small">
            <b>{{ t('mail.cryptoMissingTitle') }}</b>
            <span class="mt-0.5 block">{{ t('mail.cryptoMissingText') }}</span>
        </Message>

        <!-- ① 메일 서버 -->
        <AppGadget :title="t('mail.serverTitle')" anchor="mail-server" :changes="changes" :invalid="invalidCount">
            <div v-if="loading && !view" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                <Skeleton height="2.25rem" width="min(24rem, 100%)" />
            </div>

            <ErrorState v-else-if="!view" :title="t('mail.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />

            <form v-else :key="formKey" class="flex max-w-2xl flex-col gap-4" novalidate @submit.prevent="save">
                <Message v-if="!view.configured" severity="info" :closable="false" size="small">
                    <b>{{ t('mail.notConfiguredTitle') }}</b>
                    <span class="mt-0.5 block">{{ t('mail.notConfiguredText') }}</span>
                </Message>
                <p v-else-if="view.updatedAt" class="m-0 text-xs text-muted-color">
                    {{ view.updatedByName ? t('mail.updatedBy', { at: time.full(view.updatedAt), name: view.updatedByName }) : t('mail.updatedAt', { at: time.full(view.updatedAt) }) }}
                </p>

                <div class="grid gap-4 sm:grid-cols-[2fr_1fr]">
                    <LabeledField :label="t('mail.hostLabel')" :error="fieldError('host')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.host" name="host" size="small" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="smtp.example.com" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('mail.portLabel')" :error="fieldError('port')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.port" name="port" size="small" inputmode="numeric" autocomplete="off" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid />
                        </template>
                    </LabeledField>
                </div>

                <div class="flex flex-col gap-1.5">
                    <span :id="securityLabelId" class="text-sm font-medium">{{ t('mail.securityLabel') }}</span>
                    <SelectButton
                        :model-value="form.security"
                        :options="securityOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        size="small"
                        :disabled="readonly"
                        :invalid="Boolean(fieldError('security'))"
                        :aria-labelledby="securityLabelId"
                        class="self-start"
                        @update:model-value="onSecurityChange"
                    />
                    <p v-if="fieldError('security')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('security') }}</p>
                    <p v-else class="m-0 text-xs text-muted-color">{{ t('mail.securityHelp') }}</p>
                </div>

                <div class="grid gap-4 sm:grid-cols-2">
                    <LabeledField :label="t('mail.usernameLabel')" :hint="t('mail.optionalHint')" :help="t('mail.usernameHelp')" :error="fieldError('username')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.username" name="username" size="small" autocomplete="off" autocapitalize="off" spellcheck="false" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                    <NotificationSecretField v-model="form.password" :label="t('mail.passwordLabel')" :saved="view.passwordSet" :help="passwordHelp" :error="fieldError('password')" :disabled="readonly || !view.cryptoKeyConfigured" />
                </div>

                <div class="grid gap-4 sm:grid-cols-2">
                    <LabeledField :label="t('mail.fromLabel')" :help="t('mail.fromHelp')" :error="fieldError('fromAddress')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.fromAddress" name="fromAddress" type="email" size="small" autocomplete="off" placeholder="no-reply@example.com" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('mail.senderNameLabel')" :hint="t('mail.optionalMax', { max: UtilMailSettings.SENDER_NAME_MAX })" :help="t('mail.senderNameHelp')" :error="fieldError('senderName')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.senderName" name="senderName" size="small" autocomplete="off" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                </div>

                <LabeledField :label="t('mail.subjectPrefixLabel')" :hint="t('mail.optionalMax', { max: UtilMailSettings.SUBJECT_PREFIX_MAX })" :help="t('mail.subjectPrefixHelp')" :error="fieldError('subjectPrefix')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.subjectPrefix" name="subjectPrefix" size="small" autocomplete="off" placeholder="[ROBOYAM]" :readonly="readonly" :invalid="invalid" :aria-describedby="describedBy" class="w-full sm:w-64" />
                    </template>
                </LabeledField>

                <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

                <div v-if="canSecret" class="flex flex-wrap items-center gap-2 border-t border-(--line) pt-4">
                    <Button type="button" :label="t('mail.testButton')" icon="pi pi-send" size="small" severity="secondary" variant="outlined" :disabled="!canTest || saving" @click="openTest" />
                    <small v-if="testBlockedReason" class="text-xs text-muted-color">{{ testBlockedReason }}</small>
                    <div class="ml-auto flex flex-wrap items-center gap-2">
                        <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="saving || changes === 0" @click="reset" />
                        <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="changes === 0" />
                    </div>
                </div>
            </form>
        </AppGadget>

        <!-- 설정 이력 — 제목 옆 단추가 오른쪽 서랍으로 연다(최신이 위) -->
        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('mail.historyHint')">
            <SettingHistoryTable
                :items="history.rows.value"
                :total="history.total.value"
                :first="history.first.value"
                :page-size="history.size.value"
                :loading="history.loading.value"
                :loaded="history.loaded.value"
                :error="history.error.value"
                :compact="prefs.density === 'compact'"
                @page="history.onPage"
                @retry="history.load"
            />
        </SettingHistoryDrawer>

        <!-- 시험 발송 — 어디로 보낼지 받는다. 저장된 설정 그대로 실제로 보낸다 -->
        <Dialog v-model:visible="testOpen" modal :header="t('mail.testTitle')" class="w-[28rem] max-w-[calc(100vw-2rem)]">
            <form class="flex flex-col gap-3" novalidate @submit.prevent="runTest">
                <LabeledField :label="t('mail.testToLabel')" :help="t('mail.testHelp')" :error="testToError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="testTo" name="to" type="email" size="small" autocomplete="email" placeholder="me@example.com" :invalid="invalid" :aria-describedby="describedBy" :readonly="testing" fluid />
                    </template>
                </LabeledField>

                <div v-if="testResult" class="flex items-start gap-2 rounded-lg p-3 text-sm" :class="testResult.succeeded ? 'bg-green-50 text-green-800 dark:bg-green-950 dark:text-green-300' : 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300'" :role="testResult.succeeded ? 'status' : 'alert'">
                    <i :class="testResult.succeeded ? 'pi pi-check-circle' : 'pi pi-times-circle'" class="mt-0.5" aria-hidden="true" />
                    <span class="min-w-0">
                        <b class="block">{{ testResult.succeeded ? t('mail.testSucceeded') : t('mail.testFailed') }}</b>
                        <span class="block">{{ testResult.succeeded ? t('mail.testSucceededHint', { to: testResult.to }) : testResult.text }}</span>
                    </span>
                </div>
                <Message v-if="testError" severity="error" variant="simple" size="small">{{ testError }}</Message>

                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="testOpen = false" />
                    <Button type="submit" :label="t('mail.testSend')" icon="pi pi-send" :loading="testing" :disabled="!testTo.trim()" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { MailSecurity, MailSettingsView } from '~/composables/mail/api/useMailSettingsApi.types';
import type { MailSettingsField, MailSettingsFieldErrors, MailSettingsForm } from '~/utils/util-mail-settings';
import UtilMailSettings from '~/utils/util-mail-settings';
import UtilMailSettingsMessages from '~/utils/util-mail-settings-messages';
import UtilValidation from '~/utils/util-validation';

const { t } = useI18n();
useSeoMeta({ title: () => t('mail.title') });

const api = useMailSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { user } = useAdminAuth();
const { confirm } = useConfirmDialog();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();

/** 저장·시험 발송에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canSecret = computed(() => can('site.secret'));

const securityLabelId = useId();

/* ── ① 메일 서버 ── */

const view = ref<MailSettingsView | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
/** 서버 값이 새로 오면 폼을 다시 세운다(`forms.md` 2-1) */
const formKey = ref(0);
const form = reactive<MailSettingsForm>({ host: '', port: '', security: 'STARTTLS', username: '', password: '', fromAddress: '', senderName: '', subjectPrefix: '' });
/** 저장을 눌러 본 적이 있는가 — 그 전에는 「비어 있음」을 빨갛게 보이지 않는다 */
const tried = ref(false);
/** 서버가 거절한 칸 문구 — 그 칸을 고치면 지운다 */
const serverErrors = ref<MailSettingsFieldErrors>({});
/** 칸에 붙지 않는 저장 오류(서버 문구 그대로) */
const formError = ref('');

const readonly = computed(() => !canSecret.value || saving.value);
const securityOptions = computed(() => UtilMailSettings.SECURITIES.map((value) => ({ value, label: UtilMailSettingsMessages.securityLabel(t, value) })));

const problems = computed(() => (view.value ? UtilMailSettings.validate(form, view.value) : {}));
/** 칸 아래 문구 — 서버 문구가 먼저, 그다음 화면 검사(「비어 있음」은 저장을 눌러 본 뒤에만, 비밀번호 다시 입력은 바로) */
function fieldError(field: MailSettingsField): string {
    const server = serverErrors.value[field];
    if (server) return server;
    const problem = problems.value[field];
    if (!problem || !canSecret.value) return '';
    if ((problem === 'REQUIRED' || problem === 'PAIR') && !tried.value) return '';
    return UtilMailSettingsMessages.problem(t, field, problem);
}
const FIELDS: readonly MailSettingsField[] = ['host', 'port', 'security', 'username', 'password', 'fromAddress', 'senderName', 'subjectPrefix'];
const invalidCount = computed(() => FIELDS.filter((field) => Boolean(fieldError(field))).length);
const changes = computed(() => (view.value ? UtilMailSettings.changes(form, view.value) : 0));

/** 비밀번호 칸 아래 안내 — 사용자 이름을 지우면 같이 지워진다 · 암호화 키가 없다 · 저장된 것을 그대로 쓴다 */
const passwordHelp = computed(() => {
    if (!view.value) return '';
    if (UtilMailSettings.clearsPassword(form, view.value)) return t('mail.passwordWillClear');
    if (!view.value.cryptoKeyConfigured) return t('mail.passwordLocked');
    return view.value.passwordSet ? t('mail.passwordKeep') : t('mail.passwordNew');
});

// 칸을 고치면 그 칸의 서버 문구를 걷는다
for (const field of FIELDS) {
    watch(
        () => form[field],
        () => {
            if (serverErrors.value[field]) serverErrors.value = { ...serverErrors.value, [field]: undefined };
        },
    );
}

function fill(res: MailSettingsView): void {
    view.value = res;
    Object.assign(form, UtilMailSettings.initialForm(res));
    tried.value = false;
    serverErrors.value = {};
    formError.value = '';
    formKey.value += 1;
}

function reset(): void {
    if (view.value) fill(view.value);
}

function onSecurityChange(next: MailSecurity | null): void {
    if (!next) return;
    form.port = UtilMailSettings.portAfterSecurityChange(form.port, next);
    form.security = next;
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        fill(await api.load());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function save(): Promise<void> {
    if (saving.value || !canSecret.value || !view.value || changes.value === 0) return;
    tried.value = true;
    if (Object.keys(problems.value).length > 0) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        fill(await api.save(UtilMailSettings.toRequest(form)));
        toast.success(t('mail.saved'));
        if (history.loaded.value) void history.reloadFirst();
    } catch (e) {
        const error = toApiError(e);
        const found: MailSettingsFieldErrors = {};
        for (const field of FIELDS) {
            const message = error.field(field);
            if (message) found[field] = message;
        }
        serverErrors.value = found;
        if (error.code === 'CRYPTO_KEY_MISSING' && view.value) view.value = { ...view.value, cryptoKeyConfigured: false };
        // 칸에 붙지 않는 오류만 저장 줄 위 한 줄 — 서버 문구 그대로
        if (Object.keys(found).length === 0) formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

/* ── 시험 발송 ── */

const testOpen = ref(false);
const testTo = ref('');
const testing = ref(false);
const testToError = ref('');
const testError = ref('');
const testResult = ref<{ succeeded: boolean; text: string; to: string } | null>(null);

/** 시험은 저장된 설정으로 보낸다 — 설정이 없거나 고친 값이 있으면 못 한다 */
const canTest = computed(() => Boolean(view.value?.configured) && changes.value === 0);
const testBlockedReason = computed(() => {
    if (!view.value?.configured) return t('mail.testNeedsConfig');
    if (changes.value > 0) return t('mail.testNeedsSave');
    return '';
});

watch(testTo, () => (testToError.value = ''));

function openTest(): void {
    if (!canTest.value) return;
    testTo.value = user.value?.email ?? '';
    testToError.value = '';
    testError.value = '';
    testResult.value = null;
    testOpen.value = true;
}

async function runTest(): Promise<void> {
    if (testing.value) return;
    const to = testTo.value.trim();
    const problem = UtilValidation.email(to);
    if (problem) {
        testToError.value = t(problem === 'REQUIRED' ? 'mail.testToRequired' : 'mail.testToFormat');
        return;
    }
    testing.value = true;
    testError.value = '';
    testResult.value = null;
    try {
        const result = await api.test(to);
        testResult.value = { succeeded: result.succeeded, text: result.succeeded ? '' : UtilMailSettingsMessages.testFailure(t, result), to };
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('to');
        if (field) testToError.value = field;
        else if (error.code === 'MAIL_TEST_RATE_LIMITED') testError.value = t('mail.testRateLimited', { seconds: error.retryAfterSeconds ?? 60 });
        else testError.value = error.message;
    } finally {
        testing.value = false;
    }
}

/* ── ② 설정 이력 ── */

const history = useSettingHistory((page, size) => api.history(page, size));
/** 이력 서랍 — 처음 열 때 받는다(화면을 열 때마다 이력을 부르지 않는다) */
const historyOpen = ref(false);
function openHistory(): void {
    historyOpen.value = true;
    if (!history.loaded.value && !history.loading.value) void history.load();
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (changes.value === 0) return true;
    return confirm({ title: t('mail.leaveTitle'), message: t('mail.leaveMessage'), confirmLabel: t('mail.leaveConfirm'), tone: 'danger' });
});

onMounted(() => {
    void load();
});
</script>
