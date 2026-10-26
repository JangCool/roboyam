<template>
    <!--
        포트원 설정의 카드 하나(`/settings/portone`) — 비밀값 칸 묶음 하나를 한 번에 저장한다(지금은 「본인인증」, 2a 단계에 「결제」 카드가 같은 부품으로 더해진다).
        구획 하나가 `<form>` 하나다 — **카드마다 따로 저장**(저장 줄은 카드 맨 아래, `forms.md` 2-4). 서버는 부르지 않고 이벤트로 알린다(`save`·`remove`).
        - 값은 **절대 보이지 않는다** — 칸마다 「설정됨 · 마지막 변경 시각 · 변경한 사람」 / 「설정 안 됨」만. 바꾸려면 새 값을 입력(비밀번호형)하고 저장, 비워 둔 칸은 보내지 않는다(그대로).
        - [지우기]는 설정된 칸만 — 부모가 확인을 묻는다. 서버 암호화 키가 없으면 입력·저장을 잠근다(지우기는 된다).
        - 읽기 전용(`editable` false — `site.secret` 없음): 입력·단추를 그리지 않고 상태만 보인다.
        - 입력값은 이 카드의 메모리에만 있고(`forms.md` 2-1) 저장에 성공하면(`fields` 가 새 배열로 바뀌면) 비운다. 실패하면 남겨 다시 시도할 수 있다.
    -->
    <form novalidate @submit.prevent="submit">
        <AppGadget :title="title" :anchor="anchor" :changes="changes" :invalid="invalidCount">
            <template #actions>
                <Tag class="portone-badge shrink-0" :value="ready ? t('portone.badgeReady') : t('portone.badgeIncomplete')" :severity="ready ? 'success' : 'warn'" />
            </template>

            <div class="flex flex-col gap-5">
                <p v-if="description" class="m-0 text-xs leading-relaxed text-muted-color">{{ description }}</p>

                <div v-for="field in fields" :key="field.key" class="flex max-w-xl flex-col gap-1.5" :data-field="field.key">
                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <span class="text-sm font-medium">{{ t(`portone.field.${field.key}`) }}</span>
                        <span class="inline-flex items-center gap-1 text-xs" :class="field.configured ? 'text-green-700 dark:text-green-400' : 'text-muted-color'">
                            <i :class="field.configured ? 'pi pi-check-circle' : 'pi pi-circle'" aria-hidden="true" />
                            {{ field.configured ? t('portone.configured') : t('portone.notConfigured') }}
                        </span>
                        <span v-if="field.updatedAt" class="text-xs text-muted-color tabular-nums">
                            · {{ field.updatedBy ? t('portone.updatedBy', { at: time.full(field.updatedAt), name: field.updatedBy }) : t('portone.updatedAt', { at: time.full(field.updatedAt) }) }}
                        </span>
                        <Button
                            v-if="editable && field.configured"
                            type="button"
                            class="ml-auto"
                            :label="t('portone.remove')"
                            icon="pi pi-trash"
                            size="small"
                            severity="danger"
                            variant="text"
                            :loading="removingKey === field.key"
                            :disabled="busy"
                            :aria-label="t('portone.removeAria', { name: t(`portone.field.${field.key}`) })"
                            @click="emit('remove', field.key)"
                        />
                    </div>
                    <NotificationSecretField
                        v-if="editable"
                        v-model="form[field.key]!"
                        :label="field.configured ? t('portone.newValue') : t('portone.value')"
                        :saved="false"
                        :help="field.configured ? t('portone.keepHint') : t(`portone.fieldHelp.${field.key}`)"
                        :error="errors[field.key]"
                        :disabled="formLocked"
                    />
                </div>

                <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

                <div v-if="editable" class="flex flex-wrap items-center justify-end gap-2">
                    <span class="mr-auto text-xs text-muted-color">{{ changes ? t('portone.changes', { count: changes }) : t('portone.noChanges') }}</span>
                    <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="busy || changes === 0" @click="reset" />
                    <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="formLocked || changes === 0" />
                </div>
            </div>
        </AppGadget>
    </form>
</template>

<script setup lang="ts">
/** 칸 하나의 상태 — 값은 없다(`PortoneFieldState` 와 같은 모양, 2a 의 결제 칸도 받게 키는 글자) */
interface PortoneSecretCardField {
    key: string;
    configured: boolean;
    updatedAt: string | null;
    updatedBy: string | null;
}

interface Props {
    title: string;
    /** 우측 구역 내비 주소 */
    anchor?: string;
    description?: string;
    fields: PortoneSecretCardField[];
    /** 저장·지우기 권한(`site.secret`) */
    editable?: boolean;
    /** false 면 서버에 암호화 키가 없어 저장할 수 없다 */
    cryptoKeyConfigured?: boolean;
    saving?: boolean;
    /** 지우는 중인 칸 */
    removingKey?: string | null;
    /** 다른 일이 진행 중이라 잠근다 */
    locked?: boolean;
    /** 서버가 거절한 칸 문구 — 새 객체가 올 때마다 칸 아래에 보인다 */
    serverErrors?: Record<string, string>;
    formError?: string;
}

const props = withDefaults(defineProps<Props>(), {
    anchor: undefined,
    description: '',
    editable: false,
    cryptoKeyConfigured: true,
    saving: false,
    removingKey: null,
    locked: false,
    serverErrors: undefined,
    formError: '',
});

const emit = defineEmits<{ save: [body: Record<string, string>]; remove: [key: string]; changes: [count: number] }>();

const { t } = useI18n();
const time = useDisplayTime();

const form = reactive<Record<string, string>>({});
const errors = reactive<Record<string, string | undefined>>({});

const ready = computed(() => props.fields.every((f) => f.configured));
const changes = computed(() => props.fields.filter((f) => (form[f.key] ?? '').trim().length > 0).length);
const invalidCount = computed(() => Object.values(errors).filter(Boolean).length);
const busy = computed(() => props.saving || props.removingKey !== null || props.locked);
const formLocked = computed(() => busy.value || !props.cryptoKeyConfigured);

watch(changes, (count) => emit('changes', count), { immediate: true });

function reset(): void {
    for (const f of props.fields) {
        form[f.key] = '';
        errors[f.key] = undefined;
    }
}
reset();

// 상태가 새로 오면(저장 응답·지운 뒤 새로 받음) 입력을 비운다 — 실패하면 그대로라 입력한 값이 남는다
watch(() => props.fields, reset);

watch(
    () => ({ ...form }),
    (now, before) => {
        for (const key of Object.keys(now)) if (now[key] !== before?.[key]) errors[key] = undefined;
    },
);

watch(
    () => props.serverErrors,
    (found) => {
        if (!found) return;
        for (const f of props.fields) errors[f.key] = found[f.key];
    },
);

function submit(): void {
    if (!props.editable || formLocked.value || changes.value === 0) return;
    const body: Record<string, string> = {};
    for (const f of props.fields) {
        const value = (form[f.key] ?? '').trim();
        if (!value) continue;
        if (/\s/.test(value)) errors[f.key] = t('portone.noSpaces');
        else body[f.key] = value;
    }
    if (Object.values(errors).some(Boolean)) return;
    emit('save', body);
}
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.portone-badge :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
