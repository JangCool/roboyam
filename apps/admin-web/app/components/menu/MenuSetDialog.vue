<template>
    <!--
        메뉴 그룹 만들기·고치기 — 코드(만든 뒤 바꿀 수 없음) · 이름 · 영문명 · 위치/용도 · 사용 여부 · 설명. 기본 그룹은 위치도 바꿀 수 없다.
        저장은 부르는 쪽이 서버에 바로 한다(트리 저장과 별개) — 서버 입력 오류(`fieldErrors`)는 칸 아래, 그 밖의 실패(`error`)는 폼 아래에 선다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="set ? t('menuConfig.setEditTitle') : t('menuConfig.setAddTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <LabeledField :label="t('menuConfig.field.setKey')" :hint="t('menuConfig.setKeyHint', { min: L.keyMin, max: L.keyMax })" :help="set ? t('menuConfig.setKeyLocked') : t('menuConfig.setKeyHelp')" :error="errorOf('key')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.key" :readonly="Boolean(set)" :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" autocapitalize="off" spellcheck="false" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('menuConfig.field.setName')" :hint="t('menuConfig.labelHint', { max: L.nameMax })" :error="errorOf('name')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.name" :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('menuConfig.field.nameEn')" :hint="t('menuConfig.optional')" :error="errorOf('nameEn')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.nameEn" :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <div class="flex flex-col gap-1.5">
                <span :id="`${formId}-placement`" class="text-sm font-medium">{{ t('menuConfig.field.placement') }}</span>
                <div class="flex flex-col gap-1.5" role="radiogroup" :aria-labelledby="`${formId}-placement`">
                    <label v-for="value in UtilMenu.PLACEMENTS" :key="value" class="flex cursor-pointer items-start gap-2 rounded-md border border-(--line) px-3 py-2" :class="placementLocked && form.placement !== value ? 'opacity-50' : ''">
                        <RadioButton v-model="form.placement" :value="value" :disabled="saving || placementLocked" :input-id="`${formId}-p-${value}`" class="mt-0.5" />
                        <span class="min-w-0">
                            <span class="block text-[13px] font-medium">{{ t(`menuConfig.placement.${value}`) }}</span>
                            <span class="block text-[11px] text-muted-color">{{ t(`menuConfig.placementDesc.${value}`) }}</span>
                        </span>
                    </label>
                </div>
                <p v-if="placementLocked" class="m-0 text-xs text-muted-color">{{ t('menuConfig.placementLocked') }}</p>
                <p v-if="errorOf('placement')" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ errorOf('placement') }}</p>
            </div>

            <div class="flex items-center gap-2">
                <ToggleSwitch v-model="form.enabled" :input-id="`${formId}-enabled`" :disabled="saving" />
                <label :for="`${formId}-enabled`" class="cursor-pointer text-sm">{{ t('menuConfig.field.setEnabled') }}</label>
            </div>

            <LabeledField :label="t('menuConfig.field.description')" :hint="t('menuConfig.descHint', { max: L.descMax })" :error="errorOf('description')">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="form.description" rows="2" auto-resize :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="set ? t('common.save') : t('menuConfig.setCreate')" :loading="saving" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { MenuSet, MenuSetCreateRequest } from '~/composables/menu/api/useMenuApi.types';
import UtilMenu from '~/utils/util-menu';

interface Props {
    visible: boolean;
    /** 고칠 그룹 — null 이면 새 그룹 */
    set?: MenuSet | null;
    /** 이미 있는 코드(중복 미리 보기) */
    takenKeys?: string[];
    saving?: boolean;
    /** 서버 입력 오류(칸 이름 → 문구) */
    fieldErrors?: Record<string, string>;
    /** 칸에 붙지 않는 실패 */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { set: null, takenKeys: () => [], saving: false, fieldErrors: () => ({}), error: '' });
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'submit', body: MenuSetCreateRequest): void }>();

const { t } = useI18n();
const formId = useId();
const L = UtilMenu.SET_LIMITS;

const form = reactive({ key: '', name: '', nameEn: '', placement: 'CUSTOM' as MenuSetCreateRequest['placement'], enabled: true, description: '' });
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        const s = props.set;
        Object.assign(form, s ? { key: s.key, name: s.name, nameEn: s.nameEn ?? '', placement: s.placement, enabled: s.enabled, description: s.description ?? '' } : { key: '', name: '', nameEn: '', placement: 'CUSTOM', enabled: true, description: '' });
        submitted.value = false;
    },
    { immediate: true },
);

const placementLocked = computed(() => props.set?.system === true);

/** 화면이 미리 보는 문제 — 코드 형식·중복, 이름 길이 */
const problems = computed<Record<string, string>>(() => {
    const out: Record<string, string> = {};
    if (!props.set) {
        const key = UtilMenu.setKeyProblem(form.key, props.takenKeys);
        if (key) out.key = t(`menuConfig.setProblem.${key}`, { min: L.keyMin, max: L.keyMax });
    }
    const name = form.name.trim();
    if (!name) out.name = t('menuConfig.problem.LABEL_REQUIRED');
    else if ([...name].length > L.nameMax) out.name = t('menuConfig.problem.LABEL_LENGTH', { max: L.nameMax });
    if (form.nameEn.trim().length > L.nameEnMax) out.nameEn = t('menuConfig.problem.NAME_EN_LENGTH', { maxEn: L.nameEnMax });
    if (form.description.length > L.descMax) out.description = t('menuConfig.descTooLong', { max: L.descMax });
    return out;
});

function errorOf(field: string): string {
    const local = problems.value[field];
    if (local && (submitted.value || field === 'key')) return form.key || submitted.value ? local : '';
    return props.fieldErrors[field] ?? '';
}

function submit() {
    submitted.value = true;
    if (props.saving || Object.keys(problems.value).length) return;
    emit('submit', {
        key: form.key.trim(),
        name: form.name.trim(),
        nameEn: form.nameEn.trim() || null,
        placement: form.placement,
        enabled: form.enabled,
        description: form.description.trim() || null,
    });
}
</script>
