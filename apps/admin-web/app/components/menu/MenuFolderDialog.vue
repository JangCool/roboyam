<template>
    <!--
        공개 사이트 메뉴의 폴더 추가·수정 — 이름(필수) · 영문명 · 넣을 곳(추가할 때만) · 표시 여부 · 보이는 대상 · 접기(안 함/펼친 채 시작/접은 채 시작) · 새 게시판 자동 추가(SIDE 그룹만).
        [적용]은 트리(저장 전)에만 반영한다 — 서버 저장은 화면 아래 저장 줄이 한 번에 한다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="folder ? t('menuConfig.folderEditTitle') : t('menuConfig.folderAddTitle')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="form" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="apply">
            <div v-if="!folder" class="flex flex-col gap-1.5">
                <label :for="`${formId}-parent`" class="text-sm font-medium">{{ t('menuConfig.field.parent') }}</label>
                <Select :input-id="`${formId}-parent`" v-model="parentValue" :options="parents" option-label="label" option-value="value" fluid />
            </div>

            <LabeledField :label="t('menuConfig.field.label')" :hint="t('menuConfig.labelHint', { max: UtilMenu.LIMITS.labelMax })" :error="errorOf('label')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.label" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('menuConfig.field.nameEn')" :hint="t('menuConfig.optional')" :error="errorOf('nameEn')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.nameEn" :invalid="invalid" :aria-describedby="describedBy" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <div class="flex items-center gap-2">
                <ToggleSwitch v-model="form.visible" :input-id="`${formId}-visible`" />
                <label :for="`${formId}-visible`" class="cursor-pointer text-sm">{{ t('menuConfig.field.visible') }}</label>
            </div>

            <MenuAudienceField v-model:audience="form.audience" v-model:min-grade="form.minGrade" :grades="ctx.grades" :grade-error="errorOf('minGrade')" :help="t('menuConfig.folderAudienceHelp')" />

            <div class="flex flex-col gap-1.5">
                <span :id="`${formId}-collapse`" class="text-sm font-medium">{{ t('menuConfig.field.collapse') }}</span>
                <SelectButton v-model="form.collapse" :options="collapseOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${formId}-collapse`" class="self-start" />
            </div>

            <div v-if="side" class="flex flex-col gap-1">
                <div class="flex items-center gap-2">
                    <ToggleSwitch v-model="form.autoAdd" :input-id="`${formId}-auto`" />
                    <label :for="`${formId}-auto`" class="cursor-pointer text-sm">{{ t('menuConfig.field.autoAdd') }}</label>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('menuConfig.autoAddHelp') }}</p>
            </div>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('menuConfig.apply')" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { MenuContext, MenuEditFolder, MenuProblemField } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';
import UtilMenuMessages from '~/utils/util-menu-messages';
import UtilMenuTree from '~/utils/util-menu-tree';

interface Props {
    visible: boolean;
    /** 고칠 폴더 — null 이면 새 폴더 */
    folder?: MenuEditFolder | null;
    /** 넣을 곳 선택지(추가할 때만) — 값 `''` 은 최상위 */
    parents?: { value: string; label: string }[];
    /** 처음 고른 넣을 곳(`''` 은 최상위) */
    parentKey?: string;
    ctx: MenuContext;
}

const props = withDefaults(defineProps<Props>(), { folder: null, parents: () => [], parentKey: '' });
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'save', folder: MenuEditFolder, parentKey: string | null): void }>();

const { t } = useI18n();
const formId = useId();

const form = ref<MenuEditFolder | null>(null);
const parentValue = ref('');
const submitted = ref(false);

const side = computed(() => UtilMenu.isSide(props.ctx.placement));
const collapseOptions = computed(() => UtilMenu.COLLAPSE_MODES.map((value) => ({ value, label: t(`menuConfig.collapse.${value}`) })));

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        form.value = props.folder ? UtilMenuTree.clone(props.folder) : UtilMenu.newFolder();
        parentValue.value = props.parentKey;
        submitted.value = false;
    },
    { immediate: true },
);

const problems = computed(() => (form.value ? UtilMenu.folderProblems(form.value) : {}));

function errorOf(field: MenuProblemField): string {
    const code = problems.value[field];
    return submitted.value && code ? UtilMenuMessages.problem(t, code) : '';
}

function apply() {
    submitted.value = true;
    if (!form.value || Object.keys(problems.value).length) return;
    emit('save', { ...form.value, label: form.value.label.trim(), nameEn: form.value.nameEn.trim(), minGrade: form.value.audience === 'GRADE_MIN' ? form.value.minGrade : null }, parentValue.value || null);
    emit('update:visible', false);
}
</script>
