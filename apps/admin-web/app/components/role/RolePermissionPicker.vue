<template>
    <!--
        권한 고르기 — 후보를 묶음별로 체크한다(운영자 역할은 조회 · 처리 · 설정 · 민감 조치 네 단위, 회원 역할은 이용 · 게시판 운영 두 묶음). 맨 위 「전체 선택」과 묶음마다의 「전체 선택」이 있다(고를 수 있는 것만 담는다).
        **권한 상승 방지**(운영자 역할만): 지금 로그인한 운영자가 가진 권한만 체크할 수 있고 그 밖은 비활성 + 툴팁("내 권한 밖이에요"). 이미 체크된 내 권한 밖의 권한은 그대로 두고 바꿀 수 없다. 회원 역할에는 이 규칙이 없다(정책 제안).
        화면(사이트 기능)이 아직 없는 권한(`featureReady: false`)은 "기능 준비 중" 작은 표시가 붙지만 **체크할 수 있다**(미리 역할을 설계해 둘 수 있게).
        `disabled` 면 전부 잠근다(권한을 줄 권한이 없을 때).
    -->
    <div>
        <div class="mb-2 flex items-center justify-between gap-2">
            <span class="text-xs text-muted-color" aria-live="polite">{{ t('role.permCount', { picked: pickedCount, total: pickableCount }) }}</span>
            <Button type="button" :label="allPicked ? t('role.unpickAll') : t('role.pickAll')" size="small" severity="secondary" variant="text" :disabled="disabled || !selectableCodes.length" @click="toggle(selectableCodes, allPicked)" />
        </div>

        <div v-if="!candidates.length" class="rounded-lg border border-(--line) p-3 text-xs text-muted-color">{{ t('role.permPickType') }}</div>

        <div v-else class="max-h-72 overflow-auto rounded-lg border border-(--line) p-2">
            <section v-for="group in groups" :key="group.key" class="mb-3 last:mb-0" :aria-labelledby="`${idBase}-${group.key}`">
                <div class="mb-1 flex items-center justify-between gap-2">
                    <h4 :id="`${idBase}-${group.key}`" class="flex items-center gap-1.5 text-xs font-semibold">
                        <i :class="group.icon" class="text-[11px]" aria-hidden="true" />
                        {{ t(group.labelKey) }}
                        <span class="font-normal text-muted-color">· {{ t(group.hintKey) }}</span>
                    </h4>
                    <Button
                        type="button"
                        :label="groupAllPicked(group) ? t('role.unpickAll') : t('role.pickAll')"
                        :aria-label="groupAllPicked(group) ? t('role.unpickAllOf', { unit: t(group.labelKey) }) : t('role.pickAllOf', { unit: t(group.labelKey) })"
                        size="small"
                        severity="secondary"
                        variant="text"
                        :disabled="disabled || !groupSelectable(group).length"
                        @click="toggle(groupSelectable(group), groupAllPicked(group))"
                    />
                </div>

                <div v-for="permission in group.items" :key="permission.code" v-tooltip.top="reasonOf(permission) || undefined" class="flex items-start gap-3 rounded-md px-1 py-1.5">
                    <Checkbox :model-value="model.includes(permission.code)" binary :input-id="`${idBase}-${permission.code}`" :disabled="disabled || blocked(permission)" @update:model-value="toggle([permission.code], model.includes(permission.code))" />
                    <label class="min-w-0 flex-1" :class="disabled || blocked(permission) ? 'cursor-not-allowed' : 'cursor-pointer'" :for="`${idBase}-${permission.code}`">
                        <span class="flex flex-wrap items-center gap-1.5">
                            <span class="text-[13px]">{{ t(permission.nameKey) }}</span>
                            <code class="text-[11px] text-muted-color">{{ permission.code }}</code>
                            <Tag v-if="withType" :value="typeName(permission.type)" severity="secondary" class="text-[11px]!" />
                            <span v-if="reasonOf(permission)" class="inline-flex items-center gap-1 text-[11px] text-muted-color"><i class="pi pi-lock text-[10px]" aria-hidden="true" />{{ reasonOf(permission) }}</span>
                            <span v-if="preparing(permission)" class="inline-flex items-center gap-1 text-[11px] text-muted-color"><i class="pi pi-clock text-[10px]" aria-hidden="true" />{{ t('role.permPreparing') }}</span>
                        </span>
                        <span class="mt-0.5 block text-xs leading-relaxed text-muted-color">{{ t(permission.descKey) }}</span>
                    </label>
                </div>
            </section>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilPermissionCatalog, { type PermissionGroupView, type PermissionItem, type RoleKind } from '~/utils/util-permission-catalog';

/** 고른 권한 코드들 */
const model = defineModel<string[]>({ required: true });

interface Props {
    /** 고를 수 있는 권한 후보(유형이 정한다) */
    candidates: PermissionItem[];
    /** 역할의 축 — 묶음 모양과 권한 상승 방지 적용 여부가 갈린다 */
    kind?: RoleKind;
    /** 전부 잠근다 */
    disabled?: boolean;
    /** 권한마다 속한 유형 이름도 보인다(종합 관리처럼 유형이 섞일 때) */
    showType?: boolean;
}

const props = withDefaults(defineProps<Props>(), { kind: 'OPERATOR', disabled: false, showType: false });

const { t } = useI18n();
const idBase = useId();
const { can } = useAdminPermission();

/** 권한마다 속한 권한 묶음(세부 유형) 이름을 보일까 — 부르는 쪽이 켜거나, 운영자 권한 후보가 여러 묶음에 걸칠 때(영역 유형·종합) 자동으로 켠다 */
const withType = computed(() => props.showType || (props.kind === 'OPERATOR' && new Set(props.candidates.map((p) => p.type)).size > 1));

const groups = computed<PermissionGroupView[]>(() => UtilPermissionCatalog.viewGroups(props.kind, props.candidates.map((p) => p.code)));

/** 내 권한 밖인가 — 권한 상승 방지(운영자 역할만) */
function outside(permission: PermissionItem): boolean {
    return props.kind === 'OPERATOR' && !can(permission.code);
}

/** 화면(기능)이 아직 없는가 — 표시만 붙고 막지 않는다 */
function preparing(permission: PermissionItem): boolean {
    return permission.featureReady === false;
}

function blocked(permission: PermissionItem): boolean {
    return outside(permission);
}

/** 못 고르는 이유(툴팁·글자) — 없으면 빈 글자 */
function reasonOf(permission: PermissionItem): string {
    return outside(permission) ? t('role.outsideOfMine') : '';
}

function typeName(typeId: string): string {
    const type = UtilPermissionCatalog.typeOf(typeId);
    return type ? t(type.nameKey) : typeId;
}

const pickableCount = computed(() => props.candidates.length);
const pickedCount = computed(() => props.candidates.filter((p) => model.value.includes(p.code)).length);
const selectableCodes = computed(() => props.candidates.filter((p) => !blocked(p)).map((p) => p.code));
const allPicked = computed(() => selectableCodes.value.length > 0 && selectableCodes.value.every((code) => model.value.includes(code)));

function groupSelectable(group: PermissionGroupView): string[] {
    return group.items.filter((p) => !blocked(p)).map((p) => p.code);
}

function groupAllPicked(group: PermissionGroupView): boolean {
    const codes = groupSelectable(group);
    return codes.length > 0 && codes.every((code) => model.value.includes(code));
}

/** `codes` 를 모두 켜거나(`off` 가 false) 모두 끈다. 못 고르는 것에서 이미 체크된 것은 건드리지 않는다 */
function toggle(codes: string[], off: boolean): void {
    model.value = off ? model.value.filter((code) => !codes.includes(code)) : [...new Set([...model.value, ...codes])];
}
</script>
