<template>
    <!--
        인쇄본·팩스 접수 칸 — 파일 사본 목록 아래에 놓는다(계약서: 신청 건 입금 패널 ①·운영자 건 심사 대기·결제 기록·광고 서랍 결제 탭 / 등록증: 사업자 서랍 ③).
        작은 제목 + 종이 접수 줄(`DocIntakeList`) + [접수 기록](`editable`) + 대화상자 넷(접수 기록 · 처리 바꾸기 · 폐기 처리 · 잘못 기록 — 사유 5~200자는 `BizReasonDialog`).
        `editable` = 새 접수 기록·잘못 기록(기존 업로드 허용 상태에서만), `handleable` = 처리 바꾸기·폐기 처리(확정 뒤에도 — 종이는 남아 있으니까). 둘 다 권한이 있을 때만.
        값·대화상자 상태는 부모의 `useDocIntake().host` 한 값으로 받고 동작은 `intake` 이벤트 하나로 올린다. 서버는 부르지 않는다.
    -->
    <div v-if="host?.bundle && (rows.length || editable)" class="flex flex-col gap-2" data-testid="doc-intake-section">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h5 class="m-0 inline-flex items-center gap-1.5 text-[12px] font-semibold text-muted-color">
                <i class="pi pi-inbox text-[11px]" aria-hidden="true" />{{ t('docIntake.title') }}
                <span v-if="rows.length" class="font-normal tabular-nums">{{ activeCount }}</span>
            </h5>
            <Button v-if="editable" type="button" :label="t('docIntake.add')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="host.busy" data-testid="doc-intake-add" @click="open({ kind: 'create' })" />
        </div>
        <DocIntakeList
            v-if="rows.length"
            :intakes="rows"
            :files="files"
            :editable="editable"
            :handleable="handleable"
            :busy="host.busy"
            :dispose-due-at="disposeDueAt"
            @handling="(id) => open({ kind: 'handling', id })"
            @dispose="(id) => open({ kind: 'dispose', id })"
            @void="(id) => open({ kind: 'void', id })"
        />
        <p v-else class="m-0 text-xs text-muted-color">{{ t(`docIntake.addHelp.${doc}`) }}</p>

        <DocIntakeCreateDialog
            :visible="host.dialog?.kind === 'create'"
            :doc="doc"
            :receiver="host.receiver"
            :busy="host.busy"
            :error="host.error"
            :field-errors="host.fieldErrors"
            @update:visible="onVisible"
            @submit-paper="(body) => emit('intake', { type: 'create', body })"
            @submit-file="(file) => emit('intake', { type: 'createFile', file })"
        />
        <DocIntakeHandlingDialog
            :visible="host.dialog?.kind === 'handling'"
            :intake="target"
            :busy="host.busy"
            :error="host.error"
            :field-errors="host.fieldErrors"
            @update:visible="onVisible"
            @submit="(body) => target && emit('intake', { type: 'handling', id: target.id, body })"
        />
        <DocIntakeDisposeDialog
            :visible="host.dialog?.kind === 'dispose'"
            :doc="doc"
            :intake="target"
            :files="files"
            :busy="host.busy"
            :error="host.error"
            :field-errors="host.fieldErrors"
            @update:visible="onVisible"
            @submit="(body) => target && emit('intake', { type: 'dispose', id: target.id, body })"
        />
        <BizReasonDialog
            :visible="host.dialog?.kind === 'void'"
            :title="t('docIntake.void.title')"
            :description="t('docIntake.void.body')"
            :confirm-label="t('docIntake.void.submit')"
            tone="danger"
            :busy="host.busy"
            :error="host.error"
            :field-error="host.fieldErrors.reason ?? ''"
            @update:visible="onVisible"
            @submit="(reason: string) => target && emit('intake', { type: 'void', id: target.id, reason })"
        />
    </div>
</template>

<script setup lang="ts">
import type { DocIntakeDoc } from '~/composables/docintake/api/useDocIntakeApi.types';
import type { DocIntakeDialog, DocIntakeEvent, DocIntakeHost, DocScanFile } from '~/composables/docintake/useDocIntake.types';
import UtilDocIntake from '~/utils/util-doc-intake';

interface Props {
    doc: DocIntakeDoc;
    /** 부모의 `useDocIntake().host` — 받는 중이면 null(칸을 그리지 않는다) */
    host?: DocIntakeHost | null;
    /** 파일 사본(스캔본 후보·이름 찾기) */
    files?: DocScanFile[];
    /** 새 접수 기록·잘못 기록 */
    editable?: boolean;
    /** 처리 바꾸기·폐기 처리 */
    handleable?: boolean;
    /** 폐기 기한(ISO, 등록증 반려·탈퇴 90일) */
    disposeDueAt?: string | null;
}

const props = withDefaults(defineProps<Props>(), { host: null, files: () => [], editable: false, handleable: false, disposeDueAt: null });
const emit = defineEmits<{ intake: [event: DocIntakeEvent] }>();

const { t } = useI18n();

const rows = computed(() => props.host?.bundle?.intakes ?? []);
const activeCount = computed(() => UtilDocIntake.activeIntakes(rows.value).length);
/** 대화상자가 가리키는 줄 */
const target = computed(() => {
    const d = props.host?.dialog;
    return d && d.kind !== 'create' ? (rows.value.find((r) => r.id === d.id) ?? null) : null;
});

function open(dialog: DocIntakeDialog): void {
    emit('intake', { type: 'dialog', dialog });
}

function onVisible(visible: boolean): void {
    if (!visible) emit('intake', { type: 'dialog', dialog: null });
}
</script>
