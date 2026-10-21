<template>
    <!--
        역할 추가 · 수정 — 순서: (운영자 역할만) 영역 → 세부 유형 → 아이디 → 이름 → 설명 → 권한.
          · 운영자 역할: **영역(대분류) → 세부 유형(24개)** 을 차례로 고른다. 역할 하나는 세부 유형 하나에 속하고, 고른 유형의 권한만 아래 체크 목록에 후보로 나온다. 내가 가진 권한만 체크할 수 있다(권한 상승 방지).
            종합 관리 유형은 유형 제한 없이 모든 권한 중에서 고르고 최고 관리자만 고른다·만든다.
          · 회원 역할: 사용자 정의 유형에만 만든다(유형은 정해져 있어 알려 주기만 한다). 사이트 전체 능력(이용) 권한이 후보다(준비 중 표시가 붙는다). 권한 상승 방지는 적용하지 않는다.
        수정에서는 유형을 바꿀 수 없다(글자로 보여 준다). 내장 회원 역할(등급·회원 유형·게시판 단위)은 이름·설명을 잠그고 권한만 고친다.
        서버 호출은 부르는 쪽(페이지)이 하고 여기는 `submit` 으로 값을 내보낸다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="editing ? t('role.dialogEdit') : t('role.dialogCreate')" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <!-- 어느 유형에 속하는 역할인지 — 수정이거나 회원 역할이면 고르는 것이 아니라 알려 주는 것이다 -->
            <div v-if="fixedType" class="flex flex-wrap items-center gap-2 text-xs text-muted-color">
                {{ t('role.fieldRoleType') }}
                <Tag v-if="fixedAreaName" :value="fixedAreaName" severity="secondary" class="text-[11px]!" />
                <Tag :value="fixedTypeName" severity="secondary" class="text-[11px]!" />
            </div>

            <!-- 영역 → 세부 유형 — 운영자 역할을 만들 때만 -->
            <template v-else>
                <LabeledField :label="t('role.fieldArea')" :error="areaProblemText">
                    <template #default="{ id, describedBy, invalid }">
                        <Select
                            v-model="areaId"
                            :options="areaOptions"
                            option-label="label"
                            option-value="value"
                            option-disabled="disabled"
                            :input-id="id"
                            :placeholder="t('role.areaPlaceholder')"
                            :disabled="busy"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            fluid
                            @change="onAreaChange"
                        />
                    </template>
                </LabeledField>
                <LabeledField :label="t('role.fieldType')" :help="typeHelp" :error="typeProblemText || fieldErrors.roleTypeId">
                    <template #default="{ id, describedBy, invalid }">
                        <Select
                            v-model="typeId"
                            :options="typeOptions"
                            option-label="label"
                            option-value="value"
                            option-disabled="disabled"
                            :input-id="id"
                            :placeholder="t('role.typePlaceholder')"
                            :disabled="busy || !areaId"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            fluid
                            @change="onTypeChange"
                        />
                    </template>
                </LabeledField>
            </template>

            <!-- 역할 아이디 — 만들 때만. ROLE_ 는 자동으로 붙는다 -->
            <LabeledField v-if="!editing" :label="t('role.fieldId')" :help="t('role.idHelp')" :error="idProblemText || fieldErrors.roleId">
                <template #default="{ id, describedBy, invalid }">
                    <InputGroup>
                        <InputGroupAddon class="text-[13px]">{{ UtilRole.ID_PREFIX }}</InputGroupAddon>
                        <InputText :id="id" :model-value="idSuffix" name="roleId" :placeholder="t('role.idPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" @input="onIdInput" />
                    </InputGroup>
                </template>
            </LabeledField>
            <div v-else class="text-xs text-muted-color">
                {{ t('role.fieldId') }} <code class="ml-1 text-[13px] text-(--p-text-color)">{{ role?.roleId }}</code>
            </div>

            <LabeledField :label="t('role.fieldName')" :help="identityLocked ? t('role.memberBuiltinLocked') : ''" :error="nameProblemText || fieldErrors.roleName">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.roleName" name="roleName" :placeholder="t('role.namePlaceholder')" :disabled="busy" :readonly="identityLocked" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('role.fieldDescription')" :error="descProblemText || fieldErrors.description">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.description" name="description" :disabled="busy" :readonly="identityLocked" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <!-- 권한 — 고른 유형의 후보만 -->
            <div class="border-t border-(--line) pt-4">
                <h3 class="mb-2 text-sm font-medium">{{ t('role.permTitle') }}</h3>
                <Message v-if="!grantAllowed" severity="warn" size="small" variant="simple" :closable="false" class="mb-2">{{ t('role.permNoGrant') }}</Message>
                <RolePermissionPicker v-model="picked" :candidates="candidates" :kind="axis" :disabled="busy || !grantAllowed" :show-type="axis === 'OPERATOR' && UtilRole.isGeneralType(typeId)" />
                <p v-if="!editing" class="mt-1.5 text-xs text-muted-color">{{ t('role.permLater') }}</p>
            </div>

            <!-- 저장하면 보이는 메뉴 — 아직 저장하지 않은 선택으로 계산한다(운영자 역할만. 회원 역할은 관리자 앱 메뉴를 보지 않는다) -->
            <details v-if="axis === 'OPERATOR'" class="rounded-lg border border-(--line) px-3 py-2">
                <summary class="cursor-pointer text-sm font-medium">{{ t('role.menuDraftTitle') }}</summary>
                <div class="pt-3">
                    <RoleMenuPreview :permissions="picked" compact />
                </div>
            </details>
            <p v-else class="text-xs leading-relaxed text-muted-color">{{ t('role.memberNoMenu') }}</p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="editing ? t('common.save') : t('common.add')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { RoleEditSubmit } from '~/components/role/RoleEditDialog.types';
import type { RoleView } from '~/composables/role/api/useRoleApi.types';
import type { RoleAxis, RoleTypeView } from '~/composables/role/api/useRoleTypeApi.types';
import UtilPermissionCatalog, { type PermissionItem } from '~/utils/util-permission-catalog';
import UtilRole from '~/utils/util-role';

interface Props {
    visible: boolean;
    /** 역할의 그룹 — 운영자 역할이면 영역 → 세부 유형을 고르고, 회원 역할이면 유형이 정해져 있다 */
    axis: RoleAxis;
    /** 이 그룹의 역할 유형들(운영자: 세부 유형 전부, 회원: 4종) */
    roleTypes: RoleTypeView[];
    /** 추가할 때 미리 고를 역할 유형 */
    initialTypeId?: string;
    /** 추가할 때 미리 고를 영역(운영자) — `initialTypeId` 가 있으면 그 유형의 영역이 우선한다 */
    initialAreaId?: string;
    /** 주면 수정, 없으면(null) 추가 */
    role?: RoleView | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류 */
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { initialTypeId: '', initialAreaId: '', role: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [payload: RoleEditSubmit] }>();

const { t } = useI18n();
const formId = useId();
const { isSuper, can } = useAdminPermission();

const editing = computed(() => props.role !== null);

const idSuffix = ref('');
const form = reactive({ roleName: '', description: '' });
/** 운영자 역할을 만들 때 고른 영역 · 세부 유형(역할 유형 id) */
const areaId = ref('');
const typeId = ref('');
const picked = ref<string[]>([]);
const submitted = ref(false);

/** 만들 수 있는 유형들 — 운영자는 세부 유형 전부, 회원은 사용자 정의뿐 */
const creatableTypes = computed(() => props.roleTypes.filter((type) => type.axis === props.axis && type.creatable));
/** 영역에서 고를 수 있는 유형이 하나뿐이면 그것을 바로 고른다(소모임·핫딜·종합) */
function singleTypeOf(area: string): string {
    const types = creatableTypes.value.filter((type) => type.area === area);
    return types.length === 1 ? (types[0]?.roleTypeId ?? '') : '';
}

/** 열 때마다 채운다 — 추가는 비우고(미리 고를 유형이 있으면 채우고) 수정은 그 역할의 값으로 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        submitted.value = false;
        idSuffix.value = '';
        form.roleName = props.role?.roleName ?? '';
        form.description = props.role?.description ?? '';
        picked.value = [...(props.role?.permissionCodes ?? [])];
        if (props.role) {
            typeId.value = props.role.roleTypeId;
            areaId.value = props.roleTypes.find((type) => type.roleTypeId === props.role?.roleTypeId)?.area ?? '';
            return;
        }
        const preset = creatableTypes.value.find((type) => type.roleTypeId === props.initialTypeId);
        if (props.axis === 'MEMBER') {
            areaId.value = '';
            typeId.value = preset?.roleTypeId ?? creatableTypes.value[0]?.roleTypeId ?? '';
            return;
        }
        areaId.value = preset?.area ?? props.initialAreaId;
        typeId.value = preset?.roleTypeId ?? (areaId.value ? singleTypeOf(areaId.value) : '');
        // 종합 관리는 최고 관리자만 — 미리 골라 둔 값이 그것이면 비운다
        if (UtilRole.isGeneralType(typeId.value) && !isSuper.value) {
            areaId.value = '';
            typeId.value = '';
        }
    },
    { immediate: true },
);

/** 권한을 줄 수 있는가 — 운영자 역할은 `role.grant`(권한 상승), 회원 역할은 역할을 고칠 수 있으면 된다(`role.manage`, 이 다이얼로그가 이미 요구한다) */
const grantAllowed = computed(() => (props.axis === 'MEMBER' ? true : can('role.grant')));

/** 내장 회원 역할(등급·회원 유형·게시판 단위)은 이름·설명을 잠근다 — 회원 화면이 이 이름을 그대로 쓴다. 권한만 고친다 */
const identityLocked = computed(() => editing.value && props.role?.roleKind === 'MEMBER' && props.role.builtin);

/** 유형을 고르지 않고 알려 주기만 하는 경우의 유형 — 수정, 또는 회원 역할(유형이 정해져 있다) */
const fixedType = computed(() => (editing.value || props.axis === 'MEMBER' ? (props.roleTypes.find((type) => type.roleTypeId === typeId.value) ?? null) : null));
const fixedTypeName = computed(() => (fixedType.value ? UtilRole.typeLabel(t, fixedType.value) : ''));
const fixedAreaName = computed(() => (fixedType.value?.area ? t(`permission.area.${fixedType.value.area}`) : ''));

const candidates = computed<PermissionItem[]>(() => {
    if (props.axis === 'MEMBER') return UtilPermissionCatalog.memberCandidatesOf(props.role?.scope ?? 'SITE', props.role?.roleId);
    return typeId.value ? UtilRole.operatorCandidatesOf(typeId.value) : [];
});

/** 아이디 뒷부분은 쓰는 대로 규칙에 맞게 다듬는다(소문자→대문자, 허용하지 않는 글자는 뺀다). 값이 그대로면 화면이 안 바뀌므로 입력 칸에도 직접 되돌려 쓴다 */
function onIdInput(event: Event): void {
    const el = event.target as HTMLInputElement;
    idSuffix.value = UtilRole.normalizeSuffix(el.value);
    el.value = idSuffix.value;
}

/** 영역을 바꾸면 세부 유형을 다시 고르게 하고(하나뿐이면 바로 고른다) 고른 권한을 버린다 */
function onAreaChange(): void {
    typeId.value = singleTypeOf(areaId.value);
    picked.value = [];
}

/** 세부 유형을 바꾸면 고른 권한을 버린다 — 남겨 두면 안 보이는 채로 딸려 가서 서버가 거절한다 */
function onTypeChange(): void {
    picked.value = [];
}

// ⚠ `computed` 다 — 옵션 목록의 글자가 처음 읽은 순간의 언어로 굳지 않게. 종합 관리는 최고 관리자만 고를 수 있다
const areaOptions = computed(() => {
    const areas = new Set(creatableTypes.value.map((type) => type.area));
    return UtilPermissionCatalog.AREAS.filter((area) => areas.has(area.id)).map((area) => {
        const superOnly = area.id === UtilPermissionCatalog.GENERAL_TYPE && !isSuper.value;
        return { value: area.id, label: superOnly ? `${t(area.nameKey)} · ${t('role.superOnly')}` : t(area.nameKey), disabled: superOnly };
    });
});

const typeOptions = computed(() =>
    creatableTypes.value
        .filter((type) => type.area === areaId.value)
        .map((type) => {
            const superOnly = UtilRole.isGeneralType(type.roleTypeId) && !isSuper.value;
            return { value: type.roleTypeId, label: superOnly ? `${UtilRole.typeLabel(t, type)} · ${t('role.superOnly')}` : UtilRole.typeLabel(t, type), disabled: superOnly };
        }),
);

const typeHelp = computed(() => (UtilRole.isGeneralType(typeId.value) ? t('role.typeGeneralHint') : t('role.typeHelpCreate')));

const idProblemText = computed(() => {
    if (!submitted.value || editing.value) return '';
    const problem = UtilRole.idProblem(idSuffix.value);
    return problem === 'required' ? t('role.idRequired') : problem === 'tooLong' ? t('role.idTooLong', { max: UtilRole.ID_MAX }) : '';
});
const nameProblemText = computed(() => {
    if (!submitted.value) return '';
    const name = form.roleName.trim();
    if (!name) return t('role.nameRequired');
    return name.length > UtilRole.NAME_MAX ? t('role.nameTooLong', { max: UtilRole.NAME_MAX }) : '';
});
const descProblemText = computed(() => (submitted.value && form.description.length > UtilRole.DESC_MAX ? t('role.descTooLong', { max: UtilRole.DESC_MAX }) : ''));
/** 운영자 역할을 만들 때만 영역·세부 유형을 골라야 한다 */
const needsPick = computed(() => !editing.value && props.axis === 'OPERATOR');
const areaProblemText = computed(() => (submitted.value && needsPick.value && !areaId.value ? t('role.areaRequired') : ''));
const typeProblemText = computed(() => (submitted.value && needsPick.value && areaId.value && !typeId.value ? t('role.typeRequired') : ''));

function submit(): void {
    submitted.value = true;
    if (idProblemText.value || nameProblemText.value || descProblemText.value || areaProblemText.value || typeProblemText.value || !typeId.value) return;
    const before = props.role?.permissionCodes ?? [];
    const changed = picked.value.length !== before.length || picked.value.some((code) => !before.includes(code));
    emit('submit', {
        roleId: editing.value ? (props.role?.roleId ?? '') : UtilRole.fullId(idSuffix.value),
        roleTypeId: typeId.value,
        roleName: form.roleName.trim(),
        description: form.description.trim(),
        permissionCodes: [...picked.value],
        // 권한을 줄 수 없으면 권한은 건드리지 않는다
        permissionsChanged: grantAllowed.value && changed,
    });
}
</script>
