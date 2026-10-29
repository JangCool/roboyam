<template>
    <!--
        공개 사이트 메뉴의 항목 추가·수정 시트. 추가는 ① 연결 종류 고르기(준비 중 유형은 흐린 줄로만) → ② 대상 고르기 + 칸, 수정은 ②에서 시작한다.
        종류는 게시판 · 사이트 화면(목록에서 고르기 `BUILTIN` / 주소 직접 입력 `PATH` — 화면에서만 하나로 합쳤다) · 외부 링크(`UtilMenu.TARGET_KINDS`).
        수정할 때도 [종류 바꾸기]로 ①에 돌아가 다른 종류를 고를 수 있다 — 대상 칸만 비우고 이름·보이는 대상·탭·구분선·설명은 그대로 둔다(`UtilMenu.retarget`). 같은 종류를 다시 고르면 고친 값 그대로 ②로 돌아간다.
        대상 고르기는 `MenuTargetPicker`(이 그룹에 이미 있는 게시판·앱 화면은 비활성 + 「○○ 폴더에 있음」).
        칸: 이름(게시판·목록의 사이트 화면은 비우면 대상 이름을 따라간다) · 영문명 · 넣을 곳(추가만) · 표시 여부 · 보이는 대상 · 모바일 탭(SIDE 그룹만) · 구분선 · 설명.
        로그인이 필요한 앱 화면을 고르면 보이는 대상을 「로그인」으로 바꿔 준다(모두·비로그인으로는 저장할 수 없다).
        목록에서 고른 화면을 「주소 직접 입력」으로 바꾸면 이름이 필수가 되므로, 이름이 비었으면 그 화면 이름을 채운다. [적용]은 트리(저장 전)에만 반영한다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="header" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <!-- ① 연결 종류 -->
        <div v-if="step === 'TYPE'" class="flex flex-col gap-2">
            <p class="m-0 text-sm text-muted-color">{{ t('menuConfig.pickType') }}</p>
            <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                <li v-for="def in UtilMenu.TARGET_KINDS" :key="def.kind">
                    <button
                        type="button"
                        class="flex w-full items-start gap-3 rounded-lg border px-3 py-2.5 text-left hover:bg-surface-50 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                        :class="currentKind === def.kind ? 'border-primary' : 'border-(--line)'"
                        :aria-current="currentKind === def.kind ? 'true' : undefined"
                        @click="pickKind(def.kind)"
                    >
                        <i :class="def.icon" class="mt-0.5 text-sm text-primary" aria-hidden="true" />
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t(def.labelKey) }}</span>
                            <span class="block text-xs text-muted-color">{{ t(def.descKey) }}</span>
                        </span>
                        <MenuTag v-if="currentKind === def.kind" :value="t('menuConfig.currentKind')" />
                    </button>
                </li>
                <li v-for="def in UtilMenu.SOON_TYPES" :key="def.type" class="flex items-start gap-3 rounded-lg border border-dashed border-(--line) px-3 py-2.5 opacity-55" aria-disabled="true">
                    <i :class="def.icon" class="mt-0.5 text-sm text-muted-color" aria-hidden="true" />
                    <span class="min-w-0 flex-1">
                        <span class="flex items-center gap-2 text-sm font-medium">{{ t(def.labelKey) }} <MenuTag :value="t('menuConfig.soon')" /></span>
                        <span class="block text-xs text-muted-color">{{ t(def.descKey) }}</span>
                    </span>
                </li>
            </ul>
            <p v-if="item" class="m-0 text-xs text-muted-color">{{ t('menuConfig.changeKindHelp') }}</p>
        </div>

        <!-- ② 대상 + 칸 -->
        <form v-else-if="form" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="apply">
            <div class="flex items-center gap-2">
                <i :class="typeDef?.icon" class="text-sm text-primary" aria-hidden="true" />
                <span class="text-sm font-medium">{{ typeDef ? t(typeDef.labelKey) : form.targetType }}</span>
                <Button type="button" :label="t('menuConfig.changeType')" size="small" severity="secondary" variant="text" class="ml-auto" @click="step = 'TYPE'" />
            </div>

            <MenuTargetPicker
                v-model:target-type="form.targetType"
                v-model:target-id="form.targetId"
                v-model:target-key="form.targetKey"
                v-model:path="form.path"
                v-model:href="form.href"
                v-model:new-tab="form.newTab"
                :ctx="ctx"
                :linked-note="linkedNote"
                :errors="pickerErrors"
                @builtin="pickBuiltin"
            />

            <div v-if="!item" class="flex flex-col gap-1.5">
                <label :for="`${formId}-parent`" class="text-sm font-medium">{{ t('menuConfig.field.parent') }}</label>
                <Select :input-id="`${formId}-parent`" v-model="parentValue" :options="parents" option-label="label" option-value="value" fluid />
            </div>

            <LabeledField :label="t('menuConfig.field.label')" :hint="needsLabel ? t('menuConfig.labelHint', { max: UtilMenu.LIMITS.labelMax }) : t('menuConfig.optional')" :help="followHelp" :error="errorOf('label')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.label" :invalid="invalid" :aria-describedby="describedBy" :aria-required="needsLabel" :placeholder="targetName" autocomplete="off" fluid />
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

            <MenuAudienceField v-model:audience="form.audience" v-model:min-grade="form.minGrade" :grades="ctx.grades" :audience-error="errorOf('audience')" :grade-error="errorOf('minGrade')" :help="audienceNote" />

            <div v-if="side" class="flex flex-col gap-1">
                <div class="flex items-center gap-2">
                    <ToggleSwitch v-model="form.mobileTab" :input-id="`${formId}-tab`" :disabled="tabsFull && !form.mobileTab" />
                    <label :for="`${formId}-tab`" class="cursor-pointer text-sm">{{ t('menuConfig.field.mobileTab') }}</label>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('menuConfig.mobileTabHelp', { n: tabCount + (form.mobileTab ? 1 : 0), max: UtilMenu.LIMITS.mobileTabs }) }}</p>
            </div>

            <MenuDividerField v-if="side" v-model="form.divider" :top-level="topLevel" />

            <!-- 설명 — 상단 메뉴 둘째 줄(최상위 항목만, 하위에서는 비활성) -->
            <LabeledField v-if="side" :label="t('menuConfig.field.description')" :hint="t('menuConfig.itemDescCount', { n: form.description.length, max: UtilMenu.LIMITS.descriptionMax })" :help="topLevel ? t('menuConfig.itemDescHelp') : t('menuConfig.itemDescTopOnly')">
                <template #default="{ id, describedBy }">
                    <InputText :id="id" v-model="form.description" :maxlength="UtilMenu.LIMITS.descriptionMax" :disabled="!topLevel" :aria-describedby="describedBy" :placeholder="t('menuConfig.itemDescPlaceholder')" autocomplete="off" fluid />
                </template>
            </LabeledField>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="emit('update:visible', false)" />
            <Button v-if="step === 'FORM'" type="submit" :form="formId" :label="t('menuConfig.apply')" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { MenuContext, MenuEditItem, MenuLinkedTarget, MenuProblemField, MenuTargetKind } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';
import UtilMenuMessages from '~/utils/util-menu-messages';
import UtilMenuTree from '~/utils/util-menu-tree';

interface Props {
    visible: boolean;
    /** 고칠 항목 — null 이면 새 항목 */
    item?: MenuEditItem | null;
    /** 넣을 곳 선택지(추가할 때만) — 값 `''` 은 최상위 */
    parents?: { value: string; label: string }[];
    /** 처음 고른 넣을 곳 */
    parentKey?: string;
    ctx: MenuContext;
    /** 이 그룹에 이미 연결된 게시판·앱 화면(`UtilMenu.linkedTargets`) */
    linked: Map<string, MenuLinkedTarget>;
    /** 이 항목을 뺀 모바일 탭 수 */
    tabCount?: number;
    /** 고칠 항목이 최상위인가(구분선은 최상위만) — 추가할 때는 넣을 곳으로 정한다 */
    editTopLevel?: boolean;
}

const props = withDefaults(defineProps<Props>(), { item: null, parents: () => [], parentKey: '', tabCount: 0, editTopLevel: true });
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'save', item: MenuEditItem, parentKey: string | null): void }>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const formId = useId();

const step = ref<'TYPE' | 'FORM'>('TYPE');
const form = ref<MenuEditItem | null>(null);
const parentValue = ref('');
const submitted = ref(false);
/** 로그인 필요 화면을 골라 보이는 대상을 바꿔 줬다 */
const audienceChanged = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        form.value = props.item ? UtilMenuTree.clone(props.item) : null;
        step.value = props.item ? 'FORM' : 'TYPE';
        parentValue.value = props.parentKey;
        submitted.value = false;
        audienceChanged.value = false;
    },
    { immediate: true },
);

// 목록에서 고른 화면을 「주소 직접 입력」으로 바꾸면 이름이 필수가 된다 — 비었으면 그 화면 이름을 채운다
watch(
    () => form.value?.targetType,
    (type, before) => {
        const value = form.value;
        if (!value || type !== 'PATH' || before !== 'BUILTIN' || value.label.trim()) return;
        const def = UtilMenu.builtinOf(value.targetKey);
        if (def) value.label = t(def.labelKey);
    },
);

const header = computed(() => (props.item ? t('menuConfig.itemEditTitle') : t('menuConfig.itemAddTitle')));
const side = computed(() => UtilMenu.isSide(props.ctx.placement));
const topLevel = computed(() => (props.item ? props.editTopLevel : parentValue.value === ''));
const typeDef = computed(() => (form.value ? UtilMenu.typeOf(form.value.targetType) : undefined));
const currentKind = computed(() => (form.value ? UtilMenu.kindOf(form.value.targetType) : undefined));
const needsLabel = computed(() => form.value?.targetType === 'PATH' || form.value?.targetType === 'URL');
const tabsFull = computed(() => props.tabCount >= UtilMenu.LIMITS.mobileTabs);
const targetName = computed(() => (form.value ? UtilMenuMessages.targetName(t, form.value, props.ctx, localizedName) : ''));
const followHelp = computed(() => (needsLabel.value ? '' : targetName.value ? t('menuConfig.followHelp', { name: targetName.value }) : t('menuConfig.followHelpEmpty')));
const audienceNote = computed(() => (audienceChanged.value ? t('menuConfig.audienceAuto') : ''));

/** 종류 고르기 — 같은 종류면 고친 값 그대로, 다른 종류면 대상 칸만 비운다(이미 만든 항목도 이름·보이는 대상 등은 그대로) */
function pickKind(kind: MenuTargetKind) {
    const def = UtilMenu.TARGET_KINDS.find((entry) => entry.kind === kind);
    if (!def) return;
    if (!form.value) form.value = UtilMenu.newItem(def.initialType);
    else if (UtilMenu.kindOf(form.value.targetType) !== kind) form.value = UtilMenu.retarget(form.value, def.initialType);
    step.value = 'FORM';
    submitted.value = false;
}

/** 이미 연결된 대상의 안내 — 「○○ 폴더에 있음」 · 「맨 위에 있음」. 이 항목 자신이면 없음 */
function linkedNote(id: string): string {
    const hit = props.linked.get(id);
    if (!hit || hit.itemKey === form.value?.key) return '';
    return hit.folderLabel ? t('menuConfig.inFolder', { name: hit.folderLabel }) : t('menuConfig.inTop');
}

function pickBuiltin(key: string | null) {
    if (!form.value) return;
    const def = UtilMenu.builtinOf(key);
    if (def?.loginRequired && !UtilMenu.isLoginAudience(form.value.audience)) {
        form.value.audience = 'MEMBER';
        audienceChanged.value = true;
    }
}

const problems = computed<Partial<Record<MenuProblemField, string>>>(() => {
    if (!form.value) return {};
    const out: Partial<Record<MenuProblemField, string>> = {};
    for (const [field, code] of Object.entries(UtilMenu.itemProblems(form.value, props.ctx.placement))) out[field as MenuProblemField] = UtilMenuMessages.problem(t, code);
    const id = UtilMenu.linkId(form.value);
    if (id && linkedNote(id)) out.target = UtilMenuMessages.problem(t, 'TARGET_DUPLICATE');
    return out;
});

function errorOf(field: MenuProblemField): string {
    return submitted.value ? (problems.value[field] ?? '') : '';
}

const pickerErrors = computed(() => ({ target: errorOf('target'), path: errorOf('path'), href: errorOf('href') }));

function apply() {
    submitted.value = true;
    if (!form.value || Object.keys(problems.value).length) return;
    const value = form.value;
    emit(
        'save',
        {
            ...value,
            label: value.label.trim(),
            nameEn: value.nameEn.trim(),
            path: value.path.trim(),
            href: value.href.trim(),
            description: value.description.trim(),
            minGrade: value.audience === 'GRADE_MIN' ? value.minGrade : null,
            mobileTab: side.value && value.mobileTab,
            newTab: value.targetType === 'PATH' || value.targetType === 'URL' ? value.newTab : false,
        },
        parentValue.value || null,
    );
    emit('update:visible', false);
}
</script>
