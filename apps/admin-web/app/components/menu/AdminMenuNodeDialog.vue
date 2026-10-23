<template>
    <!--
        관리자 사이트 메뉴의 노드 추가·수정 — 종류마다 칸이 다르다.
          · 묶음·하위 메뉴(부모 항목): 이름(기본 이름이 있으면 비워도 된다) · 영문명 · 아이콘 · 표시 여부.
          · 관리자 화면: 화면 고르기(메뉴에 이미 있는 화면은 비활성 + 어느 메뉴에 있는지) · 권한(읽기 전용 — 화면이 정한다) · 이름 덮어쓰기(비우면 화면 이름) · 영문명 · 아이콘 · 표시 여부.
          · 외부 링크: 주소(https:// 또는 /…) · 새 탭(기본 켬) · 이름(필수) · 영문명 · 아이콘 · 권한(선택 — 고르면 그 권한이 있는 운영자에게만) · 표시 여부.
        하위 메뉴 안의 화면은 아이콘이 없다(지금 사이드바와 같다). [적용]은 트리(저장 전)에만 반영한다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="header" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="form" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="apply">
            <div v-if="!node && parents.length > 1" class="flex flex-col gap-1.5">
                <label :for="`${formId}-parent`" class="text-sm font-medium">{{ t('menuConfig.field.parent') }}</label>
                <Select :input-id="`${formId}-parent`" v-model="parentValue" :options="parents" option-label="label" option-value="value" fluid />
            </div>

            <!-- 관리자 화면 -->
            <template v-if="form.kind === 'SCREEN' && form.targetType === 'ADMIN_SCREEN'">
                <div class="flex flex-col gap-1.5">
                    <label :for="`${formId}-screen`" class="text-sm font-medium">{{ t('menuConfig.field.screen') }}</label>
                    <Select :input-id="`${formId}-screen`" v-model="form.targetKey" :options="screenOptions" option-label="label" option-value="value" option-disabled="disabled" filter :invalid="Boolean(errorOf('target'))" :placeholder="t('menuConfig.screenPlaceholder')" fluid @update:model-value="onScreen">
                        <template #option="{ option }">
                            <span class="flex w-full min-w-0 items-center gap-2">
                                <span class="min-w-0 flex-1 truncate text-[13px]">{{ option.label }} <code class="text-[11px] text-muted-color">{{ option.to }}</code></span>
                                <span v-if="option.note" class="shrink-0 text-[11px] text-muted-color">{{ option.note }}</span>
                            </span>
                        </template>
                    </Select>
                    <p v-if="errorOf('target')" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ errorOf('target') }}</p>
                </div>
                <div class="flex flex-wrap items-center gap-2 text-xs text-muted-color">
                    <span>{{ t('menuConfig.field.permission') }}</span>
                    <MenuTag v-if="screenPermission" :value="UtilMenuMessages.adminPermissionLabel(t, screenPermission)" icon="pi pi-key" severity="contrast" />
                    <MenuTag v-else :value="t('menuConfig.permissionNone')" />
                    <span>{{ t('menuConfig.permissionFixed') }}</span>
                </div>
            </template>

            <!-- 외부 링크 -->
            <template v-if="form.kind === 'SCREEN' && form.targetType === 'URL'">
                <LabeledField :label="t('menuConfig.field.href')" :help="t('menuConfig.hrefHelp')" :error="errorOf('href')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.href" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" :placeholder="HREF_PLACEHOLDER" autocomplete="off" autocapitalize="off" spellcheck="false" fluid />
                    </template>
                </LabeledField>
                <div class="flex items-center gap-2">
                    <ToggleSwitch v-model="form.newTab" :input-id="`${formId}-newtab`" />
                    <label :for="`${formId}-newtab`" class="cursor-pointer text-sm">{{ t('menuConfig.field.newTab') }}</label>
                </div>
            </template>

            <LabeledField :label="t('menuConfig.field.label')" :hint="labelRequired ? t('menuConfig.labelHint', { max: UtilAdminMenuConfig.LABEL_MAX }) : t('menuConfig.optional')" :help="defaultName ? t('menuConfig.followHelp', { name: defaultName }) : ''" :error="errorOf('label')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.label" :invalid="invalid" :aria-describedby="describedBy" :aria-required="labelRequired" :placeholder="defaultName" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('menuConfig.field.nameEn')" :hint="t('menuConfig.optional')" :error="errorOf('labelEn')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.labelEn" :invalid="invalid" :aria-describedby="describedBy" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <!-- 아이콘 — 하위 메뉴 안의 화면은 없다 -->
            <div v-if="!subItem" class="flex flex-col gap-1.5">
                <LabeledField :label="t('menuConfig.field.icon')" :hint="t('menuConfig.optional')" :help="t('menuConfig.iconHelp')" :error="errorOf('icon')">
                    <template #default="{ id, describedBy, invalid }">
                        <IconField>
                            <InputIcon :class="form.icon || 'pi pi-image'" />
                            <InputText :id="id" v-model="form.icon" :invalid="invalid" :aria-describedby="describedBy" placeholder="pi pi-home" autocomplete="off" spellcheck="false" fluid />
                        </IconField>
                    </template>
                </LabeledField>
                <div class="flex flex-wrap gap-1" role="group" :aria-label="t('menuConfig.iconPick')">
                    <button
                        v-for="icon in UtilAdminMenuConfig.COMMON_ICONS"
                        :key="icon"
                        type="button"
                        class="grid size-8 place-items-center rounded-md border text-sm focus-visible:outline-2 focus-visible:outline-primary"
                        :class="form.icon === icon ? 'border-primary bg-primary-50 text-primary dark:bg-primary-950/40' : 'border-(--line) text-muted-color hover:bg-surface-100 dark:hover:bg-surface-800'"
                        :aria-label="icon"
                        :aria-pressed="form.icon === icon"
                        @click="form.icon = icon"
                    >
                        <i :class="icon" aria-hidden="true" />
                    </button>
                </div>
            </div>
            <p v-else class="m-0 text-xs text-muted-color">{{ t('menuConfig.subItemNoIcon') }}</p>

            <div v-if="form.kind === 'SCREEN' && form.targetType === 'URL'" class="flex flex-col gap-1.5">
                <label :for="`${formId}-perm`" class="text-sm font-medium">{{ t('menuConfig.field.permission') }} <small class="ml-1 text-xs font-normal text-muted-color">{{ t('menuConfig.optional') }}</small></label>
                <Select :input-id="`${formId}-perm`" v-model="form.permission" :options="permissionOptions" option-label="label" option-value="value" filter show-clear :placeholder="t('menuConfig.permissionAll')" fluid />
                <p class="m-0 text-xs text-muted-color">{{ t('menuConfig.urlPermissionHelp') }}</p>
            </div>

            <div class="flex items-center gap-2">
                <ToggleSwitch v-model="form.visible" :input-id="`${formId}-visible`" />
                <label :for="`${formId}-visible`" class="cursor-pointer text-sm">{{ t('menuConfig.field.visible') }}</label>
            </div>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('menuConfig.apply')" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { AdminMenuMode } from '~/utils/util-admin-menu';
import type { AdminMenuEditNode, AdminMenuProblemField } from '~/utils/util-admin-menu-config';
import UtilAdminMenuConfig from '~/utils/util-admin-menu-config';
import UtilMenuMessages from '~/utils/util-menu-messages';
import UtilMenuTree from '~/utils/util-menu-tree';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';

interface Props {
    visible: boolean;
    /** 고칠 노드 — null 이면 `blank` 로 새 노드 */
    node?: AdminMenuEditNode | null;
    /** 새 노드의 빈 모양(종류·연결 유형) */
    blank?: AdminMenuEditNode | null;
    /** 하위 메뉴 안의 화면(아이콘 없음) */
    subItem?: boolean;
    /** 넣을 곳 선택지(추가할 때만, 값 `''` 은 최상위) */
    parents?: { value: string; label: string }[];
    parentKey?: string;
    /** 메뉴에 이미 있는 화면(두 모드) */
    used: Map<string, AdminMenuMode>;
}

const props = withDefaults(defineProps<Props>(), { node: null, blank: null, subItem: false, parents: () => [], parentKey: '' });
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'save', node: AdminMenuEditNode, parentKey: string | null): void }>();

const { t } = useI18n();
const formId = useId();
/** 외부 링크 칸의 자리표시 — 템플릿의 정적 속성에 두 빗금을 쓰면 타입 검사가 그 뒤를 주석으로 읽는다 */
const HREF_PLACEHOLDER = 'https://';

const form = ref<AdminMenuEditNode | null>(null);
const parentValue = ref('');
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        const source = props.node ?? props.blank;
        form.value = source ? UtilMenuTree.clone(source) : null;
        parentValue.value = props.parentKey;
        submitted.value = false;
    },
    { immediate: true },
);

const header = computed(() => {
    const f = form.value;
    if (!f) return '';
    const kind = f.kind === 'SCREEN' ? t(`menuConfig.adminType.${f.targetType ?? 'ADMIN_SCREEN'}`) : t(`menuConfig.adminKind.${f.kind}`);
    return props.node ? t('menuConfig.adminEditTitle', { kind }) : t('menuConfig.adminAddTitle', { kind });
});

const labelRequired = computed(() => (form.value ? (form.value.kind === 'SCREEN' ? form.value.targetType === 'URL' : form.value.labelKey === null) : false));
const defaultName = computed(() => {
    const key = form.value ? UtilAdminMenuConfig.defaultLabelKey(form.value) : null;
    return key ? t(key) : '';
});
const screenPermission = computed(() => (form.value ? UtilAdminMenuConfig.permissionOf(form.value) : null));

const screenOptions = computed(() =>
    UtilAdminMenuConfig.screens().map((screen) => {
        const usedIn = props.used.get(screen.key);
        const mine = props.node?.targetKey === screen.key;
        const note = usedIn && !mine ? t('menuConfig.inMode', { mode: t(`menuConfig.mode.${usedIn}`) }) : '';
        return { value: screen.key, label: t(screen.labelKey), to: screen.to, note, disabled: note !== '' };
    }),
);

const permissionOptions = computed(() => UtilPermissionCatalog.PERMISSIONS.map((p) => ({ value: p.code, label: `${t(p.nameKey)} (${p.code})` })));

/** 화면을 고르면 아이콘이 비어 있을 때 메뉴 정의의 아이콘을 넣어 준다 */
function onScreen(key: string) {
    const screen = UtilAdminMenuConfig.screenOf(key);
    if (form.value && !props.subItem && !form.value.icon && screen?.icon) form.value.icon = screen.icon;
}

const problems = computed(() => {
    if (!form.value) return {};
    const probe = { ...form.value, icon: props.subItem ? '' : form.value.icon };
    const rows = UtilAdminMenuConfig.validate({ normal: [probe], settings: [] }).rows[probe.key] ?? {};
    const out: Partial<Record<AdminMenuProblemField, string>> = {};
    for (const [field, code] of Object.entries(rows)) out[field as AdminMenuProblemField] = UtilMenuMessages.problem(t, code);
    if (probe.kind === 'SCREEN' && probe.targetType === 'ADMIN_SCREEN' && probe.targetKey && props.used.has(probe.targetKey) && props.node?.targetKey !== probe.targetKey) {
        out.target = UtilMenuMessages.problem(t, 'TARGET_DUPLICATE');
    }
    return out;
});

function errorOf(field: AdminMenuProblemField): string {
    return submitted.value ? (problems.value[field] ?? '') : '';
}

function apply() {
    submitted.value = true;
    if (!form.value || Object.keys(problems.value).length) return;
    const f = form.value;
    emit('save', { ...f, label: f.label.trim(), labelEn: f.labelEn.trim(), icon: props.subItem ? '' : f.icon.trim(), href: f.href.trim() }, parentValue.value || null);
    emit('update:visible', false);
}

</script>
