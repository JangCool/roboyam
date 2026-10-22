<template>
    <!--
        공개 사이트 메뉴의 항목 추가·수정 시트. 추가는 ① 연결 유형 고르기(준비 중 유형은 흐린 줄로만) → ② 대상 고르기 + 칸, 수정은 ②에서 시작한다(유형은 그대로).
        대상 고르기 모양은 유형 표(`UtilMenu.TARGET_TYPES` 의 `picker`)가 정한다:
          · 게시판 목록(이 그룹에 이미 있는 게시판은 비활성 + 「○○ 폴더에 있음」, 꺼진 게시판은 딱지) · 앱 화면 목록(카테고리별, 그룹 위치에 맞는 카테고리가 위) · 페이지 주소 입력 · 외부 링크 입력.
        칸: 이름(게시판·앱 화면은 비우면 대상 이름을 따라간다) · 영문명 · 넣을 곳(추가만) · 표시 여부 · 보이는 대상 · 모바일 탭(SIDE 그룹만) · 새 탭에서 열기(페이지·외부 링크만).
        로그인이 필요한 앱 화면을 고르면 보이는 대상을 「로그인」으로 바꿔 준다(모두·비로그인으로는 저장할 수 없다). [적용]은 트리(저장 전)에만 반영한다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="header" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <!-- ① 연결 유형 -->
        <div v-if="step === 'TYPE'" class="flex flex-col gap-2">
            <p class="m-0 text-sm text-muted-color">{{ t('menuConfig.pickType') }}</p>
            <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                <li v-for="def in UtilMenu.TARGET_TYPES" :key="def.type">
                    <button type="button" class="flex w-full items-start gap-3 rounded-lg border border-(--line) px-3 py-2.5 text-left hover:bg-surface-50 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800" @click="pickType(def.type)">
                        <i :class="def.icon" class="mt-0.5 text-sm text-primary" aria-hidden="true" />
                        <span class="min-w-0">
                            <span class="block text-sm font-medium">{{ t(def.labelKey) }}</span>
                            <span class="block text-xs text-muted-color">{{ t(def.descKey) }}</span>
                        </span>
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
        </div>

        <!-- ② 대상 + 칸 -->
        <form v-else-if="form" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="apply">
            <div class="flex items-center gap-2">
                <i :class="typeDef?.icon" class="text-sm text-primary" aria-hidden="true" />
                <span class="text-sm font-medium">{{ typeDef ? t(typeDef.labelKey) : form.targetType }}</span>
                <Button v-if="!item" type="button" :label="t('menuConfig.changeType')" size="small" severity="secondary" variant="text" class="ml-auto" @click="step = 'TYPE'" />
            </div>

            <!-- 게시판 -->
            <div v-if="typeDef?.picker === 'BOARD_SELECT'" class="flex flex-col gap-1.5">
                <span :id="`${formId}-target`" class="text-sm font-medium">{{ t('menuConfig.field.board') }}</span>
                <Listbox v-model="form.targetId" :options="boardOptions" option-label="label" option-value="value" option-disabled="disabled" filter :filter-fields="['label', 'slug']" :filter-placeholder="t('menuConfig.boardSearch')" scroll-height="15rem" :invalid="Boolean(errorOf('target'))" :aria-labelledby="`${formId}-target`" :empty-message="t('menuConfig.boardEmpty')">
                    <template #option="{ option }">
                        <span class="flex w-full min-w-0 items-center gap-2">
                            <span class="min-w-0 flex-1 truncate text-[13px]">{{ option.label }} <code class="text-[11px] text-muted-color">/boards/{{ option.slug }}</code></span>
                            <MenuTag v-if="!option.active" :value="t('menuConfig.boardInactive')" severity="warn" />
                            <span v-if="option.note" class="shrink-0 text-[11px] text-muted-color">{{ option.note }}</span>
                        </span>
                    </template>
                </Listbox>
                <p v-if="errorOf('target')" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ errorOf('target') }}</p>
            </div>

            <!-- 앱 화면 -->
            <div v-else-if="typeDef?.picker === 'BUILTIN_SELECT'" class="flex flex-col gap-1.5">
                <span :id="`${formId}-target`" class="text-sm font-medium">{{ t('menuConfig.field.builtin') }}</span>
                <Listbox
                    :model-value="form.targetKey"
                    :options="builtinGroups"
                    option-label="label"
                    option-value="value"
                    option-disabled="disabled"
                    option-group-label="label"
                    option-group-children="items"
                    filter
                    :filter-placeholder="t('menuConfig.builtinSearch')"
                    scroll-height="15rem"
                    :invalid="Boolean(errorOf('target'))"
                    :aria-labelledby="`${formId}-target`"
                    @update:model-value="pickBuiltin"
                >
                    <template #optiongroup="{ option }">
                        <span class="text-xs font-semibold text-muted-color">{{ option.label }}</span>
                    </template>
                    <template #option="{ option }">
                        <span class="flex w-full min-w-0 items-center gap-2">
                            <span class="min-w-0 flex-1 truncate text-[13px]">{{ option.label }} <code class="text-[11px] text-muted-color">{{ option.path }}</code></span>
                            <MenuTag v-if="option.login" :value="t('menuConfig.loginRequired')" icon="pi pi-lock" />
                            <span v-if="option.note" class="shrink-0 text-[11px] text-muted-color">{{ option.note }}</span>
                        </span>
                    </template>
                </Listbox>
                <p v-if="errorOf('target')" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ errorOf('target') }}</p>
            </div>

            <!-- 페이지 주소 -->
            <LabeledField v-else-if="typeDef?.picker === 'PATH_INPUT'" :label="t('menuConfig.field.path')" :help="t('menuConfig.pathHelp')" :error="errorOf('path')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.path" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" placeholder="/boards/free" autocomplete="off" autocapitalize="off" spellcheck="false" fluid />
                </template>
            </LabeledField>

            <!-- 외부 링크 -->
            <LabeledField v-else-if="typeDef?.picker === 'URL_INPUT'" :label="t('menuConfig.field.href')" :help="t('menuConfig.hrefHelp')" :error="errorOf('href')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.href" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" :placeholder="HREF_PLACEHOLDER" autocomplete="off" autocapitalize="off" spellcheck="false" fluid />
                </template>
            </LabeledField>

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

            <div v-if="form.targetType === 'PATH' || form.targetType === 'URL'" class="flex items-center gap-2">
                <ToggleSwitch v-model="form.newTab" :input-id="`${formId}-newtab`" />
                <label :for="`${formId}-newtab`" class="cursor-pointer text-sm">{{ t('menuConfig.field.newTab') }}</label>
            </div>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="emit('update:visible', false)" />
            <Button v-if="step === 'FORM'" type="submit" :form="formId" :label="t('menuConfig.apply')" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { MenuKnownTargetType } from '~/composables/menu/api/useMenuApi.types';
import type { MenuContext, MenuEditItem, MenuLinkedTarget, MenuProblemField } from '~/utils/util-menu';
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
}

const props = withDefaults(defineProps<Props>(), { item: null, parents: () => [], parentKey: '', tabCount: 0 });
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'save', item: MenuEditItem, parentKey: string | null): void }>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const formId = useId();
/** 외부 링크 칸의 자리표시 — 템플릿의 정적 속성에 두 빗금을 쓰면 타입 검사가 그 뒤를 주석으로 읽는다 */
const HREF_PLACEHOLDER = 'https://';

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

const header = computed(() => (props.item ? t('menuConfig.itemEditTitle') : t('menuConfig.itemAddTitle')));
const side = computed(() => UtilMenu.isSide(props.ctx.placement));
const typeDef = computed(() => (form.value ? UtilMenu.typeOf(form.value.targetType) : undefined));
const needsLabel = computed(() => form.value?.targetType === 'PATH' || form.value?.targetType === 'URL');
const tabsFull = computed(() => props.tabCount >= UtilMenu.LIMITS.mobileTabs);
const targetName = computed(() => (form.value ? UtilMenuMessages.targetName(t, form.value, props.ctx, localizedName) : ''));
const followHelp = computed(() => (needsLabel.value ? '' : targetName.value ? t('menuConfig.followHelp', { name: targetName.value }) : t('menuConfig.followHelpEmpty')));
const audienceNote = computed(() => (audienceChanged.value ? t('menuConfig.audienceAuto') : ''));

function pickType(type: MenuKnownTargetType) {
    const keep = form.value && form.value.targetType === type ? form.value : null;
    form.value = keep ?? UtilMenu.newItem(type);
    step.value = 'FORM';
    submitted.value = false;
}

/** 이미 연결된 대상의 안내 — 「○○ 폴더에 있음」 · 「맨 위에 있음」. 이 항목 자신이면 없음 */
function linkedNote(id: string): string {
    const hit = props.linked.get(id);
    if (!hit || hit.itemKey === form.value?.key) return '';
    return hit.folderLabel ? t('menuConfig.inFolder', { name: hit.folderLabel }) : t('menuConfig.inTop');
}

const boardOptions = computed(() =>
    props.ctx.boards.map((board) => {
        const note = linkedNote(`BOARD:${board.id}`);
        return { value: board.id, label: localizedName(board.name, board.nameEn), slug: board.slug.toLowerCase(), active: board.active, note, disabled: note !== '' };
    }),
);

const builtinGroups = computed(() =>
    UtilMenu.builtinCategoryOrder(props.ctx.placement).map((category) => ({
        label: t(`menuConfig.builtinCategory.${category}`),
        items: UtilMenu.BUILTINS.filter((def) => def.category === category).map((def) => {
            const note = linkedNote(`BUILTIN:${def.key}`);
            return { value: def.key, label: t(def.labelKey), path: def.path, login: def.loginRequired, note, disabled: note !== '' };
        }),
    })),
);

function pickBuiltin(key: string | null) {
    if (!form.value) return;
    form.value.targetKey = key;
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
            minGrade: value.audience === 'GRADE_MIN' ? value.minGrade : null,
            mobileTab: side.value && value.mobileTab,
            newTab: value.targetType === 'PATH' || value.targetType === 'URL' ? value.newTab : false,
        },
        parentValue.value || null,
    );
    emit('update:visible', false);
}
</script>
