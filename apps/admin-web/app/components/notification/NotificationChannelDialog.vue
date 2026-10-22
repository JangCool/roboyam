<template>
    <!--
        알림 도구 추가 · 수정 — 종류를 고르면 종류별 입력칸이 나온다(hamonica3 `NotificationConfigForm` 과 같은 칸).
          · Slack: 봇 토큰(비밀) + 채널.  · Mattermost: 연결 방식 둘 — 웹훅 주소(비밀) 하나, 또는 서버 주소 + 봇 토큰(비밀) + 채널.
          · 이메일: 칸이 없다 — 설정 > 메일 서버 설정으로 보낸다(도구 전용 SMTP 는 두지 않는다).  · 브라우저 알림: 받을 것이 없다(각자 내 정보에서 켠다).
          · 카카오톡 · SMS: 선택지에 「준비 중」으로 흐리게만 보이고 고를 수 없다(업체 계약 전이라 새로 만들 수 없다).
        종류는 만든 뒤 바꿀 수 없다(바꾸면 담긴 값이 전부 뜻을 잃는다). 기본 제공 도구(서랍·이메일·브라우저)는 이름·설명을 못 바꾸고 사용 여부만 바뀐다.
        **비밀 값은 화면에 되돌아오지 않는다**: 저장된 칸은 「입력됨」이고, 비워 두면 저장된 값을 그대로 쓴다(`NotificationSecretField`). 비밀은 이 다이얼로그 메모리에만 두고 닫으면 비운다.
        입력은 화면에서 먼저 검사한다(`UtilNotificationChannel.validate` — 서버가 같은 규칙으로 다시 본다). 서버 호출은 부르는 쪽(페이지)이 하고 여기는 `submit` 으로 값을 내보낸다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="editing ? t('notification.channelDialogEdit') : t('notification.channelDialogCreate')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate autocomplete="off" @submit.prevent="submit">
            <!-- 종류 — 만든 뒤에는 바꿀 수 없다 -->
            <LabeledField :label="t('notification.channelField.type')" :help="editing ? t('notification.channelTypeLocked') : ''" :error="fieldErrors.type">
                <template #default="{ id, describedBy, invalid }">
                    <Select
                        v-model="type"
                        :options="typeOptions"
                        option-label="label"
                        option-value="value"
                        option-disabled="disabled"
                        :input-id="id"
                        :disabled="busy || editing"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                        @change="onTypeChange"
                    >
                        <template #value="{ value }">
                            <span v-if="value" class="flex items-center gap-2">
                                <i :class="UtilNotificationChannel.icon(value)" aria-hidden="true" />
                                {{ UtilNotificationChannelMessages.typeLabel(t, value) }}
                            </span>
                        </template>
                        <template #option="{ option }">
                            <span class="flex w-full items-center gap-2" :class="option.disabled ? 'opacity-55' : ''">
                                <i :class="UtilNotificationChannel.icon(option.value)" aria-hidden="true" />
                                <span class="min-w-0 flex-1">{{ option.label }}</span>
                                <span v-if="option.note" class="text-xs text-muted-color">{{ option.note }}</span>
                            </span>
                        </template>
                    </Select>
                </template>
            </LabeledField>

            <LabeledField :label="t('notification.channelField.name')" :hint="t('notification.channelNameHint', { max: UtilNotificationChannel.NAME_MAX })" :help="builtIn ? t('notification.channelBuiltInLocked') : ''" :error="problemText('name') || fieldErrors.name">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.name" name="channelName" :disabled="busy" :readonly="builtIn" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <LabeledField v-if="!builtIn" :label="t('notification.channelField.description')" :hint="t('notification.channelDescHint')" :error="problemText('description') || fieldErrors.description">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.description" name="channelDescription" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <!-- 종류별 입력칸 -->
            <div v-if="hasDetail" class="flex flex-col gap-4 border-t border-(--line) pt-4">
                <h3 class="m-0 text-sm font-medium">{{ t('notification.channelDetailTitle') }}</h3>

                <template v-if="type === 'MATTERMOST'">
                    <div class="flex flex-col gap-1.5">
                        <span :id="modeLabelId" class="text-sm font-medium">{{ t('notification.channelField.mode') }}</span>
                        <SelectButton v-model="mode" :options="modeOptions" option-label="label" option-value="value" :allow-empty="false" :disabled="busy" size="small" :aria-labelledby="modeLabelId" @update:model-value="onModeChange" />
                    </div>
                </template>

                <template v-for="key in fieldKeys" :key="key">
                    <NotificationSecretField v-if="UtilNotificationChannel.isSecret(key)" v-model="secrets[key]" :label="UtilNotificationChannelMessages.fieldLabel(t, key, type)" :saved="isSaved(key)" :placeholder="placeholderOf(key)" :disabled="busy" :error="problemText(key) || fieldErrors[key]" />
                    <LabeledField v-else :label="UtilNotificationChannelMessages.fieldLabel(t, key, type)":help="helpOf(key)" :error="problemText(key) || fieldErrors[key]">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="config[key as 'channel' | 'serverUrl']" :name="key" :placeholder="placeholderOf(key)" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" fluid />
                        </template>
                    </LabeledField>
                </template>
            </div>

            <!-- 칸이 없는 종류 — 왜 칸이 없는지 말한다 -->
            <Message v-else-if="type === 'EMAIL'" severity="secondary" size="small" :closable="false">{{ t('notification.channelEmailNote') }}</Message>
            <Message v-else-if="type === 'BROWSER'" severity="secondary" size="small" :closable="false">{{ t('notification.channelBrowserNote') }}</Message>
            <Message v-else-if="type === 'DRAWER'" severity="secondary" size="small" :closable="false">{{ t('notification.channelDrawerNote') }}</Message>

            <!-- 사용 — 서랍은 늘 켜져 있다 -->
            <div class="flex items-start justify-between gap-4 border-t border-(--line) pt-4">
                <div class="min-w-0">
                    <label :for="enabledId" class="text-sm font-medium">{{ t('notification.channelField.enabled') }}</label>
                    <p class="m-0 mt-0.5 text-xs text-muted-color">{{ type === 'DRAWER' ? t('notification.channelDrawerAlwaysOn') : t('notification.channelEnabledHint') }}</p>
                </div>
                <ToggleSwitch v-model="form.enabled" class="shrink-0" :input-id="enabledId" :disabled="busy || type === 'DRAWER'" />
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="editing ? t('common.save') : t('common.add')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { MattermostMode, NotificationChannelConfig, NotificationChannelSaveRequest, NotificationChannelSecrets, NotificationChannelType, NotificationChannelView, NotificationSecretKey } from '~/composables/notification/api/useNotificationChannelApi.types';
import UtilNotificationChannel, { type ChannelFieldKey } from '~/utils/util-notification-channel';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';

interface Props {
    visible: boolean;
    /** 주면 수정, 없으면(null) 추가 */
    channel?: NotificationChannelView | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 칸마다 준 오류(`fields`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { channel: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [payload: NotificationChannelSaveRequest] }>();

const { t } = useI18n();
const formId = useId();
const enabledId = useId();
const modeLabelId = useId();

const editing = computed(() => props.channel !== null);
const builtIn = computed(() => props.channel?.builtIn === true);

const type = ref<NotificationChannelType>('SLACK');
const form = reactive({ name: '', description: '', enabled: true });
/** 비밀이 아닌 칸 — 쓰는 칸만 값이 있다 */
const config = reactive<NotificationChannelConfig>({});
/** 새로 입력한 비밀 — 그 화면 메모리에만 두고 닫으면 비운다 */
const secrets = reactive<Required<NotificationChannelSecrets>>({ botToken: '', webhookUrl: '' });
const mode = ref<MattermostMode>('webhook');
const submitted = ref(false);

/** 서버에 이미 저장된 비밀 칸 — 고칠 때만 있다 */
const savedSecrets = computed<NotificationSecretKey[]>(() => props.channel?.secretKeys ?? []);

/** 열 때마다 채운다 — 추가는 비우고(Slack 으로 시작) 수정은 그 도구의 값으로. 닫을 때 비밀을 비운다 */
watch(
    () => props.visible,
    (open) => {
        secrets.botToken = '';
        secrets.webhookUrl = '';
        if (!open) return;
        submitted.value = false;
        const channel = props.channel;
        type.value = channel?.type ?? 'SLACK';
        form.name = channel ? UtilNotificationChannelMessages.displayName(t, channel) : '';
        form.description = channel?.description ?? '';
        form.enabled = channel?.enabled ?? true;
        config.channel = channel?.config.channel ?? '';
        config.serverUrl = channel?.config.serverUrl ?? '';
        mode.value = channel?.config.mattermostMode ?? 'webhook';
    },
    { immediate: true },
);

/** 종류 선택지 — 만들 수 없는 종류(기본 제공 · 준비 중)는 흐리게 보이고 고를 수 없다. 수정할 때는 지금 종류가 있어야 이름이 보인다. ⚠ `computed` 다 — 글자가 처음 읽은 순간의 언어로 굳지 않게 */
const typeOptions = computed(() =>
    UtilNotificationChannel.TYPE_ORDER.map((value) => {
        const creatable = UtilNotificationChannel.isCreatable(value);
        const note = UtilNotificationChannel.isComingSoon(value) ? t('notification.channelTypeComingSoon') : creatable ? '' : t('notification.channelTypeBuiltIn');
        return { value, label: UtilNotificationChannelMessages.typeLabel(t, value), disabled: !editing.value && !creatable, note: editing.value ? '' : note };
    }),
);

const modeOptions = computed(() => [
    { value: 'webhook' as MattermostMode, label: t('notification.channelModeWebhook') },
    { value: 'bot' as MattermostMode, label: t('notification.channelModeBot') },
]);

const fieldKeys = computed<ChannelFieldKey[]>(() => UtilNotificationChannel.fieldsOf(type.value, mode.value));
const hasDetail = computed(() => fieldKeys.value.length > 0 || type.value === 'MATTERMOST');

/** 종류를 바꾸면 쓰던 칸을 비운다 — 남아 있으면 안 보이는 채로 딸려 간다 */
function onTypeChange(): void {
    config.channel = '';
    config.serverUrl = '';
    secrets.botToken = '';
    secrets.webhookUrl = '';
    submitted.value = false;
}

/** 연결 방식을 바꾸면 **반대 방식의 칸을 비운다** — 서버 주소가 남은 채 웹훅으로 저장되면 무엇으로 보내는지 화면과 저장값이 어긋난다 */
function onModeChange(next: MattermostMode): void {
    if (next === 'webhook') {
        config.serverUrl = '';
        config.channel = '';
        secrets.botToken = '';
    } else {
        secrets.webhookUrl = '';
    }
}

const isSaved = (key: NotificationSecretKey): boolean => savedSecrets.value.includes(key);

function placeholderOf(key: ChannelFieldKey): string {
    if (key === 'botToken') return type.value === 'SLACK' ? 'xoxb-…' : '';
    if (key === 'channel') return type.value === 'SLACK' ? '#ops-alerts' : 'a1b2c3d4e5f6g7h8i9j0k1l2m3';
    if (key === 'serverUrl') return 'https://chat.example.com';
    if (key === 'webhookUrl') return 'https://chat.example.com/hooks/…';
    return '';
}

function helpOf(key: ChannelFieldKey): string {
    if (key === 'channel') return type.value === 'SLACK' ? t('notification.channelHelpSlackChannel') : t('notification.channelHelpMattermostChannel');
    return '';
}

/** 지금 입력의 검사 — 보낼 때 한 번 눌러 본 뒤부터 칸 아래에 보인다 */
const problems = computed(() =>
    UtilNotificationChannel.validate({
        type: type.value,
        name: form.name,
        description: form.description,
        config: { ...config, mattermostMode: type.value === 'MATTERMOST' ? mode.value : undefined },
        secrets: { ...secrets },
        savedSecrets: savedSecrets.value,
    }),
);

function problemText(key: ChannelFieldKey): string {
    const problem = submitted.value ? problems.value[key] : undefined;
    return problem ? UtilNotificationChannelMessages.problem(t, key, problem, type.value) : '';
}

function submit(): void {
    submitted.value = true;
    // 기본 제공 도구는 이름·설명·칸이 없어 사용 여부만 바뀐다
    if (!builtIn.value && Object.keys(problems.value).length > 0) return;
    const used = fieldKeys.value;
    const nextConfig: NotificationChannelConfig = {};
    if (type.value === 'MATTERMOST') nextConfig.mattermostMode = mode.value;
    if (used.includes('channel')) nextConfig.channel = (config.channel ?? '').trim();
    if (used.includes('serverUrl')) nextConfig.serverUrl = (config.serverUrl ?? '').trim();
    // 입력한 비밀만 담는다 — 비워 두면 키를 보내지 않아 서버가 저장된 값을 지킨다
    const nextSecrets: NotificationChannelSecrets = {};
    for (const key of used.filter(UtilNotificationChannel.isSecret)) {
        const value = (secrets[key] ?? '').trim();
        if (value) nextSecrets[key] = value;
    }
    emit('submit', {
        type: type.value,
        name: form.name.trim(),
        description: form.description.trim() || null,
        enabled: type.value === 'DRAWER' ? true : form.enabled,
        config: nextConfig,
        secrets: nextSecrets,
    });
}
</script>
