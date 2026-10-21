<template>
    <!--
        운영자 계정 상태 — 승인 · 잠금 해제 · 만료 아님 · 사용 네 스위치(hamonica3 방식, 정책 6번). **켠 쪽이 "쓸 수 있는" 방향**이다.
        기간 때문에 막힌 것은 여기서 못 푼다(만료 표시를 꺼도 기간이 그대로면 로그인이 다시 막는다). 최고 관리자(루트)는 바꿀 수 없다.
        저장은 부르는 쪽(페이지)이 한다 — 여기는 `save` 로 바뀐 칸만 내보낸다.
    -->
    <form class="flex flex-col gap-3 pt-3" novalidate @submit.prevent="submit">
        <p class="text-xs leading-relaxed text-muted-color">{{ t('operator.loginRule') }}</p>

        <Message v-if="operator.isSuper" severity="info" size="small" variant="simple" :closable="false">
            {{ t('operator.superNotice') }} {{ t('operator.lastSuperNote') }}
        </Message>

        <div v-for="row in rows" :key="row.key" class="flex items-start justify-between gap-3 rounded-lg border border-(--line) px-3 py-2">
            <div class="min-w-0">
                <label :for="row.id" class="text-sm">{{ row.label }}</label>
                <div :id="`${row.id}-help`" class="mt-0.5 text-xs leading-relaxed text-muted-color">{{ row.help }}</div>
                <div v-if="!operator.isSuper && !row.allowed" class="mt-0.5 flex items-center gap-1 text-xs text-muted-color">
                    <i class="pi pi-lock text-[10px]" aria-hidden="true" />{{ t('operator.needPermission', { permission: row.permissionName }) }}
                </div>
            </div>
            <ToggleSwitch v-model="state[row.key]" :input-id="row.id" :disabled="!row.allowed || operator.isSuper || saving" :aria-describedby="`${row.id}-help`" />
        </div>

        <!-- 기간 때문에 막힌 것은 여기서 못 푼다 — 그 사실을 말해 준다 -->
        <Message v-if="operator.periodOver" severity="warn" size="small" variant="simple" :closable="false">
            <strong>{{ t('operator.expiredNotice') }}</strong> {{ t('operator.expiredNoticeTail') }}
        </Message>
        <p v-if="!operator.isSuper" class="text-xs leading-relaxed text-muted-color">{{ t('operator.lastSuperNote') }}</p>

        <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>

        <div v-if="!operator.isSuper" class="flex gap-2 border-t border-(--line) pt-4">
            <Button type="submit" :label="t('operator.saveStatus')" size="small" :loading="saving" :disabled="!dirty" />
            <Button type="button" :label="t('common.reset')" size="small" severity="secondary" variant="text" :disabled="saving || !dirty" @click="fill" />
        </div>
    </form>
</template>

<script setup lang="ts">
import type { OperatorStatusRequest, OperatorView } from '~/composables/operator/api/useOperatorApi.types';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';

interface Props {
    operator: OperatorView;
    /** 승인·만료·사용을 바꿀 수 있는가(`operator.manage`) */
    canManage?: boolean;
    /** 잠금·해제를 할 수 있는가(`operator.lock`, 민감 조치) */
    canLock?: boolean;
    saving?: boolean;
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { canManage: false, canLock: false, saving: false, error: '' });
const emit = defineEmits<{ save: [payload: OperatorStatusRequest]; 'update:dirty': [dirty: boolean] }>();

const { t } = useI18n();
const idBase = useId();

type StatusKey = 'approved' | 'unlocked' | 'notExpired' | 'enabled';

/** 스위치 값 — 켠 쪽이 쓸 수 있는 방향이라 `locked`·`expired` 는 뒤집어 든다 */
const state = reactive<Record<StatusKey, boolean>>({ approved: false, unlocked: true, notExpired: true, enabled: true });

function permissionName(code: string): string {
    const def = UtilPermissionCatalog.permissionOf(code);
    return def ? t(def.nameKey) : code;
}

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const rows = computed(() => [
    { key: 'approved' as const, id: `${idBase}-approved`, label: t('operator.status.approval'), help: t('operator.status.approvalHint'), allowed: props.canManage, permissionName: permissionName('operator.manage') },
    { key: 'unlocked' as const, id: `${idBase}-unlocked`, label: t('operator.status.unlocked'), help: t('operator.status.unlockedHint'), allowed: props.canLock, permissionName: permissionName('operator.lock') },
    { key: 'notExpired' as const, id: `${idBase}-notExpired`, label: t('operator.status.notExpired'), help: t('operator.status.notExpiredHint'), allowed: props.canManage, permissionName: permissionName('operator.manage') },
    { key: 'enabled' as const, id: `${idBase}-enabled`, label: t('operator.status.inUse'), help: t('operator.status.inUseHint'), allowed: props.canManage, permissionName: permissionName('operator.manage') },
]);

function fill(): void {
    state.approved = props.operator.approved;
    state.unlocked = !props.operator.locked;
    state.notExpired = !props.operator.expired;
    state.enabled = props.operator.enabled;
}
watch(() => props.operator, fill, { immediate: true });

const dirty = computed(
    () => state.approved !== props.operator.approved || state.unlocked === props.operator.locked || state.notExpired === props.operator.expired || state.enabled !== props.operator.enabled,
);

// 저장하지 않은 변경 여부를 부모(서랍의 닫기 보호)에 알린다
watch(dirty, (value) => emit('update:dirty', value), { immediate: true });

/** 바뀐 칸만 보낸다 */
function submit(): void {
    const body: OperatorStatusRequest = {};
    if (state.approved !== props.operator.approved) body.approved = state.approved;
    if (state.unlocked === props.operator.locked) body.locked = !state.unlocked;
    if (state.notExpired === props.operator.expired) body.expired = !state.notExpired;
    if (state.enabled !== props.operator.enabled) body.enabled = state.enabled;
    emit('save', body);
}
</script>
