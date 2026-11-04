<template>
    <!--
        가젯 추가 창 — 업무 묶음(처리할 일 · 커뮤니티·신고 · 방문 분석 · 회원 · 핫딜·광고·사업자 · 공구·결제 · 포인트 · 시스템·운영 · 개인)별 카드(아이콘 · 이름 · 설명 · 필요 권한).
        **권한이 없는 종류는 목록에 안 보인다**(묶음이 비면 묶음도). 같은 종류는 메모·바로가기만 여러 개, 나머지는 하나 — 이미 있으면 「추가됨」으로 막는다.
        「통계 수집」 항목이 있어야 데이터가 생기는 종류는 카드에 그 항목을 적는다(꺼져 있으면 넣은 뒤 가젯이 「수집이 꺼져 있어요」).
        위에 서버 부담 원칙을 한 줄로 알린다. 고르면 `add`(창은 부모가 닫는다).
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('dashboard.addTitle')" :style="{ width: 'min(44rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <p class="m-0 mb-4 text-sm leading-relaxed text-muted-color">{{ t('dashboard.addLead') }}</p>
        <p v-if="full" class="m-0 mb-4 text-sm text-amber-700 dark:text-amber-400">{{ t('dashboard.addFull', { n: UtilDashboardLayout.MAX_GADGETS }) }}</p>
        <div class="flex flex-col gap-5">
            <section v-for="group in groups" :key="group.id" class="flex flex-col gap-2" :aria-labelledby="`${baseId}-${group.id}`">
                <h3 :id="`${baseId}-${group.id}`" class="m-0 text-[13px] font-semibold">{{ t(`dashboard.group.${group.id}`) }}</h3>
                <ul class="m-0 grid list-none grid-cols-1 gap-2 p-0 sm:grid-cols-2">
                    <li v-for="spec in group.kinds" :key="spec.type">
                        <button
                            type="button"
                            class="flex h-full w-full items-start gap-3 rounded-lg border border-(--line) bg-transparent p-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary enabled:hover:border-primary enabled:hover:bg-surface-50 disabled:cursor-not-allowed disabled:opacity-60 dark:enabled:hover:bg-surface-800"
                            :disabled="!UtilDashboardLayout.addable(gadgets, spec.type)"
                            @click="emit('add', spec.type)"
                        >
                            <span class="grid size-8 shrink-0 place-items-center rounded-md bg-primary-50 text-primary dark:bg-primary-950"><i :class="spec.icon" aria-hidden="true" /></span>
                            <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                                <span class="flex items-center gap-2">
                                    <span class="text-sm font-medium">{{ t(`dashboard.kind.${spec.type}`) }}</span>
                                    <span v-if="added(spec.type)" class="inline-flex items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none text-muted-color dark:bg-surface-800">{{ t('dashboard.added') }}</span>
                                    <span v-else-if="spec.multiple" class="text-[11px] text-muted-color">{{ t('dashboard.addMultiple') }}</span>
                                </span>
                                <span class="text-xs leading-relaxed text-muted-color">{{ t(`dashboard.kindDesc.${spec.type}`) }}</span>
                                <span class="text-[11px] text-muted-color">{{ permissionText(spec.permissions) }}</span>
                                <span v-if="spec.collect" class="text-[11px] text-muted-color">{{ t('dashboard.collectNeeded', { item: t(`analytics.label.${spec.collect}`) }) }}</span>
                            </span>
                        </button>
                    </li>
                </ul>
            </section>
        </div>
    </Dialog>
</template>

<script setup lang="ts">
import type { DashboardGadgetItem, DashboardGadgetType } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardLayout, { DASHBOARD_GROUPS } from '~/utils/util-dashboard-layout';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';

interface Props {
    visible: boolean;
    /** 지금 구성(편집 중) — 「추가됨」·더 넣을 수 있나 */
    gadgets: DashboardGadgetItem[];
}

const props = withDefaults(defineProps<Props>(), {});

const emit = defineEmits<{
    (e: 'update:visible', value: boolean): void;
    (e: 'add', type: DashboardGadgetType): void;
}>();

const { t } = useI18n();
const { can } = useAdminPermission();
const baseId = useId();

/** 묶음별 쓸 수 있는 종류(빈 묶음은 뺀다) */
const groups = computed(() =>
    DASHBOARD_GROUPS.map((id) => ({ id, kinds: UtilDashboardLayout.KINDS.filter((k) => k.group === id && UtilDashboardLayout.allowed(k.type, can)) })).filter((g) => g.kinds.length > 0),
);

const full = computed(() => props.gadgets.length >= UtilDashboardLayout.MAX_GADGETS);

function added(type: DashboardGadgetType): boolean {
    return !UtilDashboardLayout.spec(type).multiple && props.gadgets.some((g) => g.type === type);
}

/** 필요 권한 글자 — 없으면 「누구나」, 여럿이면 「… 중 하나」 */
function permissionText(codes: readonly string[]): string {
    if (!codes.length) return t('dashboard.permissionNone');
    const names = codes.map((code) => {
        const def = UtilPermissionCatalog.PERMISSIONS.find((p) => p.code === code);
        return def ? t(def.nameKey) : code;
    });
    return names.length === 1 ? t('dashboard.permissionOne', { name: names[0] }) : t('dashboard.permissionAny', { names: names.join(', ') });
}
</script>
