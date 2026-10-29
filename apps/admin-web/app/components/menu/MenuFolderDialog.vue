<template>
    <!--
        공개 사이트 메뉴의 폴더 추가·수정 — 이름(필수) · 영문명 · 넣을 곳(추가할 때만) · 표시 여부 · 보이는 대상 ·
        누르면 가는 곳(선택: 없음 · 게시판 · 사이트 화면 · 외부 링크, `MenuTargetPicker` — 공개 사이트에서 폴더 이름이 링크가 되고 펼치기·접기는 화살표가 맡는다) ·
        연결이 있으면 상단 탭을 누를 때(이동하고 하위 메뉴도 열기 — 기본 · 이동만) · 접기(안 함/펼친 채 시작/접은 채 시작) · 새 게시판 자동 추가(SIDE 그룹만).
        폴더 연결은 「한 그룹 안 한 번」 규칙을 세지 않아 하위 항목과 같은 대상을 골라도 된다(예: 「소모임」 → 전체 소모임).
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

            <!-- 이름을 누르면 가는 곳(선택) — 없음 · 게시판 · 사이트 화면 · 외부 링크 -->
            <div class="flex flex-col gap-2">
                <span :id="`${formId}-link`" class="text-sm font-medium">{{ t('menuConfig.field.folderLink') }} <span class="text-xs font-normal text-muted-color">{{ t('menuConfig.optional') }}</span></span>
                <SelectButton :model-value="linkKind" :options="linkKinds" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${formId}-link`" class="self-start" @update:model-value="setLinkKind" />
                <p class="m-0 text-xs text-muted-color">{{ form.link ? t('menuConfig.folderLinkHelp') : t('menuConfig.folderLinkNoneHelp') }}</p>
                <MenuTargetPicker
                    v-if="form.link"
                    v-model:target-type="form.link.targetType"
                    v-model:target-id="form.link.targetId"
                    v-model:target-key="form.link.targetKey"
                    v-model:path="form.link.path"
                    v-model:href="form.link.href"
                    v-model:new-tab="form.link.newTab"
                    :ctx="ctx"
                    :errors="pickerErrors"
                />
                <p v-if="linkHint" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ linkHint }}</p>
            </div>

            <!-- 연결이 있을 때 상단 탭을 누르면 — 이동하고 하위 메뉴도 열기(기본) · 이동만 -->
            <div v-if="form.link" class="flex flex-col gap-1.5">
                <span :id="`${formId}-click`" class="text-sm font-medium">{{ t('menuConfig.field.linkClick') }}</span>
                <SelectButton v-model="form.linkClick" :options="linkClicks" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${formId}-click`" class="self-start" />
                <p class="m-0 text-xs text-muted-color">{{ t(`menuConfig.linkClickHelp.${form.linkClick}`) }}</p>
            </div>

            <div class="flex flex-col gap-1.5">
                <span :id="`${formId}-collapse`" class="text-sm font-medium">{{ t('menuConfig.field.collapse') }}</span>
                <SelectButton v-model="form.collapse" :options="collapseOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${formId}-collapse`" class="self-start" />
            </div>

            <MenuDividerField v-if="side" v-model="form.divider" :top-level="topLevel" />

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
import type { MenuContext, MenuEditFolder, MenuProblemField, MenuTargetKind } from '~/utils/util-menu';
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
    /** 고칠 폴더가 최상위인가(구분선은 최상위만) — 추가할 때는 넣을 곳으로 정한다 */
    editTopLevel?: boolean;
}

const props = withDefaults(defineProps<Props>(), { folder: null, parents: () => [], parentKey: '', editTopLevel: true });
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'save', folder: MenuEditFolder, parentKey: string | null): void }>();

const { t } = useI18n();
const formId = useId();

const form = ref<MenuEditFolder | null>(null);
const parentValue = ref('');
const submitted = ref(false);

const side = computed(() => UtilMenu.isSide(props.ctx.placement));
const topLevel = computed(() => (props.folder ? props.editTopLevel : parentValue.value === ''));
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

const linkClicks = computed(() => UtilMenu.LINK_CLICKS.map((value) => ({ value, label: t(`menuConfig.linkClick.${value}`) })));

/** 연결 종류 고르기 — 없음 + `UtilMenu.TARGET_KINDS` */
const linkKinds = computed(() => [{ value: 'NONE', label: t('menuConfig.folderLinkNone') }, ...UtilMenu.TARGET_KINDS.map((def) => ({ value: def.kind, label: t(def.labelKey) }))]);
const linkKind = computed<MenuTargetKind | 'NONE'>(() => (form.value?.link ? (UtilMenu.kindOf(form.value.link.targetType) ?? 'NONE') : 'NONE'));

function setLinkKind(kind: MenuTargetKind | 'NONE' | null) {
    if (!form.value || !kind || kind === linkKind.value) return;
    const def = UtilMenu.TARGET_KINDS.find((entry) => entry.kind === kind);
    form.value.link = def ? (form.value.link ? UtilMenu.retarget(form.value.link, def.initialType) : UtilMenu.newLink(def.initialType)) : null;
}

/** 연결 대상이 방문자에게 안 보이는 상태(꺼진 게시판·판매 꺼짐 …)거나 로그인 필요 화면이면 — 그때는 폴더 이름만 보인다는 안내 */
const linkHint = computed(() => {
    const link = form.value?.link;
    if (!link || Object.keys(UtilMenu.linkProblems(link)).length) return '';
    const status = UtilMenu.itemStatus(link, props.ctx);
    if (status !== 'OK') return t('menuConfig.folderLinkStatus', { status: t(`menuConfig.status.${status}`) });
    return link.targetType === 'BUILTIN' && UtilMenu.builtinOf(link.targetKey)?.loginRequired ? t('menuConfig.folderLinkLogin') : '';
});

const problems = computed(() => (form.value ? UtilMenu.folderProblems(form.value) : {}));

function errorOf(field: MenuProblemField): string {
    const code = problems.value[field];
    return submitted.value && code ? UtilMenuMessages.problem(t, code) : '';
}

const pickerErrors = computed(() => ({ target: errorOf('target'), path: errorOf('path'), href: errorOf('href') }));

function apply() {
    submitted.value = true;
    if (!form.value || Object.keys(problems.value).length) return;
    const link = form.value.link;
    emit(
        'save',
        {
            ...form.value,
            label: form.value.label.trim(),
            nameEn: form.value.nameEn.trim(),
            minGrade: form.value.audience === 'GRADE_MIN' ? form.value.minGrade : null,
            link: link ? { ...link, path: link.path.trim(), href: link.href.trim(), newTab: link.targetType === 'PATH' || link.targetType === 'URL' ? link.newTab : false } : null,
        },
        parentValue.value || null,
    );
    emit('update:visible', false);
}
</script>
