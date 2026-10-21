<template>
    <!--
        운영자 상세 — 표에서 행을 고르면 여는 PrimeVue `Drawer`(알림·화면 설정 서랍과 같이 설정의 "서랍 위치"를 따른다). 안의 구성은 탭 둘: ① 기본 정보(이름·이메일·역할·계정 기간) ② 계정 상태(승인·잠금 해제·만료 아님·사용).
        기본 정보의 저장·초기화는 하단 고정 푸터에 있다(그 탭에서만, 변경이 없으면 비활성). 계정 상태는 탭 안의 「상태 저장」을 쓴다. 최고 관리자(루트)는 읽기 전용이다.
        **표를 가리지 않는다(`modal=false`)** — 서랍이 열린 채로 다른 행을 누르면 내용이 그 운영자로 바뀐다. 대신 서랍·표 행·떠 있는 창(확인 대화상자·고르개 목록) 밖을 누르면 닫히도록 여기서 바깥 누르기를 듣는다. Esc 도 닫는다.
        닫기 요청(Esc·바깥·X)은 스스로 닫지 않고 `update:visible(false)` 로 알린다 — 저장하지 않은 변경이 있으면 부모가 확인을 묻고 닫는다. 변경 여부는 `update:dirty` 로 알린다.
        서버 호출은 부르는 쪽(페이지)이 하고 여기는 `save-info`·`save-status` 로 값을 내보낸다. 저장 뒤 부모가 새 `operator` 를 내려 주면 폼이 그 값으로 다시 채워진다.
    -->
    <Drawer
        :visible="visible"
        :modal="false"
        :dismissable="false"
        :position="prefs.drawerPosition"
        :aria-labelledby="titleId"
        :style="{ width: '30rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => emit('update:visible', v)"
        @show="onShow"
        @hide="onHide"
    >
        <template #header>
            <div v-if="operator" class="flex min-w-0 flex-1 flex-col gap-1">
                <h2 :id="titleId" class="truncate text-base font-semibold">{{ operator.operatorName }}</h2>
                <div class="flex flex-wrap items-center gap-1.5">
                    <Tag v-if="operator.isSuper" :value="t('operator.superTag')" icon="pi pi-shield" severity="warn" />
                    <Tag v-for="key in UtilOperator.statusKeys(operator)" :key="key" :value="UtilOperatorMessages.statusLabel(t, key)" :icon="UtilOperator.statusIcon(key)" :severity="UtilOperator.statusSeverity(key)" />
                </div>
            </div>
            <h2 v-else :id="titleId" class="text-base font-semibold">{{ t('operator.detailTitle') }}</h2>
        </template>

        <div v-if="operator" class="flex flex-col gap-1">
            <p class="break-all text-xs text-muted-color">{{ operator.email }}</p>
            <Tabs v-model:value="tab">
                <TabList>
                    <Tab value="basic">{{ t('operator.tabBasic') }}</Tab>
                    <Tab value="status">{{ t('operator.tabStatus') }}</Tab>
                </TabList>
                <TabPanels class="px-0!">
                    <TabPanel value="basic">
                        <OperatorInfoForm
                            ref="infoForm"
                            :operator="operator"
                            :role-options="roleOptions"
                            :can-manage="canManage"
                            :can-grant="canGrant"
                            :saving="infoSaving"
                            :error="infoError"
                            :field-errors="infoFieldErrors"
                            hide-actions
                            @save="(body: OperatorUpdateRequest) => emit('save-info', body)"
                            @update:dirty="(v: boolean) => (infoDirty = v)"
                        />
                    </TabPanel>
                    <TabPanel value="status">
                        <OperatorStatusForm :operator="operator" :can-manage="canManage" :can-lock="canLock" :saving="statusSaving" :error="statusError" @save="(body: OperatorStatusRequest) => emit('save-status', body)" @update:dirty="(v: boolean) => (statusDirty = v)" />
                    </TabPanel>
                </TabPanels>
            </Tabs>
        </div>

        <!-- 하단 고정 푸터 — 기본 정보 탭에서만, 수정할 수 있는 운영자에게만 -->
        <template v-if="operator && tab === 'basic' && canManage && !operator.isSuper" #footer>
            <div class="flex w-full justify-end gap-2 border-t border-(--line) pt-3">
                <Button type="button" :label="t('common.reset')" size="small" severity="secondary" variant="text" :disabled="infoSaving || !infoDirty" @click="infoForm?.reset()" />
                <Button type="button" :label="t('common.save')" size="small" :loading="infoSaving" :disabled="!infoDirty" @click="infoForm?.submit()" />
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { OperatorRoleOption } from '~/components/operator/OperatorInfoForm.types';
import type { OperatorStatusRequest, OperatorUpdateRequest, OperatorView } from '~/composables/operator/api/useOperatorApi.types';
import UtilOperator from '~/utils/util-operator';
import UtilOperatorMessages from '~/utils/util-operator-messages';

interface Props {
    /** 보이는 운영자. 저장하면 부모가 새 값으로 바꿔 내려 준다 */
    operator: OperatorView | null;
    visible: boolean;
    roleOptions?: OperatorRoleOption[];
    /** 이름·기간·승인·만료·사용을 바꿀 수 있는가(`operator.manage`) */
    canManage?: boolean;
    /** 잠금·해제를 할 수 있는가(`operator.lock`) */
    canLock?: boolean;
    /** 역할을 바꿀 수 있는가(`role.grant`) */
    canGrant?: boolean;
    infoSaving?: boolean;
    infoError?: string;
    infoFieldErrors?: Record<string, string>;
    statusSaving?: boolean;
    statusError?: string;
}

const props = withDefaults(defineProps<Props>(), {
    roleOptions: () => [],
    canManage: false,
    canLock: false,
    canGrant: false,
    infoSaving: false,
    infoError: '',
    infoFieldErrors: () => ({}),
    statusSaving: false,
    statusError: '',
});
const emit = defineEmits<{
    'update:visible': [visible: boolean];
    /** 저장하지 않은 변경이 있나(기본 정보·계정 상태 중 하나라도) */
    'update:dirty': [dirty: boolean];
    'save-info': [body: OperatorUpdateRequest];
    'save-status': [body: OperatorStatusRequest];
    /** 서랍이 닫혔다(전환이 끝났다) — 부모가 눌렀던 행으로 초점을 돌려준다 */
    hide: [];
}>();

const { t } = useI18n();
// ⚠ 알림·화면 설정 서랍과 같은 규칙 — 열리는 쪽은 설정의 "서랍 위치"를 따른다
const { prefs } = useLayoutPrefs();
const titleId = useId();

const tab = ref('basic');
const infoDirty = ref(false);
const statusDirty = ref(false);
const infoForm = ref<{ submit: () => void; reset: () => void; focusFirst: () => void } | null>(null);

watch([infoDirty, statusDirty], () => emit('update:dirty', infoDirty.value || statusDirty.value), { immediate: true });

// 다른 운영자로 바뀌면 기본 정보 탭부터 본다
watch(
    () => props.operator?.operatorId,
    () => (tab.value = 'basic'),
);

/** 닫히면 폼이 사라지므로 변경 표시도 비운다 */
function onHide(): void {
    infoDirty.value = false;
    statusDirty.value = false;
    emit('hide');
}

/** 열릴 때 첫 입력칸(이름)에 초점 — 닫을 때 눌렀던 행으로 돌려주는 것은 부모 몫이다 */
function onShow(): void {
    tab.value = 'basic';
    void nextTick(() => infoForm.value?.focusFirst());
}

/**
 * 바깥 누르기 — 서랍·표 행·떠 있는 창(확인 대화상자, 고르개·날짜 목록, 토스트) 안이면 무시하고, 그 밖이면 닫기를 요청한다.
 * 표 행은 부모가 "다른 운영자 보기"로 처리한다.
 */
const INSIDE = '.p-drawer, tbody tr, .p-dialog, .p-overlay, .p-select-overlay, .p-multiselect-overlay, .p-datepicker-panel, .p-toast, [role="alertdialog"], [role="listbox"]';
function onOutsidePointer(event: PointerEvent): void {
    const target = event.target as Element | null;
    if (!target || target.closest(INSIDE)) return;
    emit('update:visible', false);
}

watch(
    () => props.visible,
    (open) => {
        if (open) document.addEventListener('pointerdown', onOutsidePointer, true);
        else document.removeEventListener('pointerdown', onOutsidePointer, true);
    },
    { immediate: true },
);
onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutsidePointer, true));
</script>
