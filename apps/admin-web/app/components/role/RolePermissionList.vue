<template>
    <!--
        역할이 가진 권한을 묶음별로 보여 주는 읽기 전용 목록. **가진 권한만** 보인다(안 가진 것까지 늘어놓으면 무엇을 가졌는지가 묻힌다) — 고치는 것은 「수정」 다이얼로그다.
        운영자 역할은 조회 · 처리 · 설정 · 민감 조치 네 단위, 회원 역할은 이용 · 게시판 운영 두 묶음이다(`kind`).
        `showType` 은 종합 관리 역할처럼 여러 유형의 권한이 섞일 때 권한마다 속한 유형 이름을 함께 보인다. `compact` 는 이름만 한 줄로(요약).
    -->
    <div class="flex flex-col" :class="compact ? 'gap-3' : 'gap-4'">
        <section v-for="group in groups" :key="group.key" :aria-labelledby="`${idBase}-${group.key}`">
            <h3 :id="`${idBase}-${group.key}`" class="mb-1.5 flex items-center gap-1.5 text-xs font-semibold">
                <i :class="group.icon" class="text-[11px]" aria-hidden="true" />
                {{ t(group.labelKey) }}
                <span class="font-normal text-muted-color tabular-nums">{{ group.items.length }}</span>
                <span v-if="!compact" class="font-normal text-muted-color">· {{ t(group.hintKey) }}</span>
            </h3>
            <ul class="m-0 flex list-none flex-col p-0">
                <li v-for="permission in group.items" :key="permission.code" class="flex items-start gap-2.5 rounded-md px-1" :class="compact ? 'py-0.5' : 'py-1.5'">
                    <i class="pi pi-check mt-0.5 shrink-0 text-[11px] text-primary" aria-hidden="true" />
                    <div class="min-w-0 flex-1">
                        <span class="flex flex-wrap items-center gap-1.5">
                            <span class="text-[13px] font-medium">{{ t(permission.nameKey) }}</span>
                            <code v-if="!compact" class="text-[11px] text-muted-color">{{ permission.code }}</code>
                            <Tag v-if="withType" :value="typeName(permission.type)" severity="secondary" class="text-[11px]!" />
                        </span>
                        <span v-if="!compact" class="mt-0.5 block text-xs leading-relaxed text-muted-color">{{ t(permission.descKey) }}</span>
                    </div>
                </li>
            </ul>
        </section>
    </div>
</template>

<script setup lang="ts">
import UtilPermissionCatalog, { type RoleKind } from '~/utils/util-permission-catalog';

interface Props {
    /** 가진 권한 코드들 */
    codes: string[];
    /** 역할의 축 — 운영자 역할은 4단위, 회원 역할은 이용·게시판 운영 묶음 */
    kind?: RoleKind;
    showType?: boolean;
    /** 코드·설명 없이 이름만 */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { kind: 'OPERATOR', showType: false, compact: false });

const { t } = useI18n();
const idBase = useId();

const groups = computed(() => UtilPermissionCatalog.viewGroups(props.kind, props.codes));
/** 권한마다 속한 권한 묶음(세부 유형) 이름을 보일까 — 부르는 쪽이 켜거나, 운영자 권한이 여러 묶음에 걸칠 때 자동으로 켠다(compact 에서는 끈다) */
const withType = computed(() => !props.compact && (props.showType || (props.kind === 'OPERATOR' && new Set(groups.value.flatMap((g) => g.items.map((p) => p.type))).size > 1)));

function typeName(typeId: string): string {
    const type = UtilPermissionCatalog.typeOf(typeId);
    return type ? t(type.nameKey) : typeId;
}
</script>
