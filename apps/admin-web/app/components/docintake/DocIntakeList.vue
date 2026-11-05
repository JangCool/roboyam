<template>
    <!--
        종이 접수 줄 목록(인쇄본·팩스 — 계약서 사본·사업자등록증 사본이 같이 쓴다). 한 줄: 방식 아이콘 · 방식 · 쪽수 · 처리 Tag(계속 보관 / 스캔 후 폐기 예정 / 폐기함) ·
        접수일 · 접수자 · 보관 위치 · 메모, 폐기한 줄은 폐기일·폐기자·사유·스캔본, 잘못 기록은 줄을 그은 채 사유와 함께 남는다.
        줄 단추: [처리 바꾸기]·[폐기 처리](`handleable`) · [잘못 기록](`editable`) — 잘못 기록·폐기함 줄에는 없다. 서버는 부르지 않는다 — 동작은 emit.
    -->
    <ul class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0" data-testid="doc-intake-list">
        <li v-for="row in intakes" :key="row.id" class="flex flex-wrap items-start gap-x-3 gap-y-1.5 px-3 py-2 text-[13px]" :class="row.voidedAt ? 'opacity-60' : ''">
            <i :class="UtilDocIntake.channelIcon(row.channel)" class="mt-0.5 text-muted-color" aria-hidden="true" />
            <div class="flex min-w-0 flex-1 flex-col gap-0.5">
                <span class="inline-flex min-w-0 flex-wrap items-center gap-1.5">
                    <span :class="row.voidedAt ? 'line-through' : 'font-medium'">{{ t(`docIntake.channel.${row.channel}`) }}</span>
                    <span v-if="row.pages" class="text-muted-color" :class="row.voidedAt ? 'line-through' : ''">· {{ t('docIntake.pagesN', { n: row.pages }) }}</span>
                    <Tag v-if="row.voidedAt" :value="t('docIntake.voidedTag')" severity="danger" />
                    <Tag v-else :value="t(`docIntake.handling.${row.handling}`)" :severity="UtilDocIntake.handlingSeverity(row.handling)" />
                    <Tag v-if="UtilDocIntake.disposeOverdue(row, disposeDueAt, now)" :value="t('docIntake.overdue')" severity="danger" icon="pi pi-exclamation-triangle" />
                </span>
                <span class="text-[11px] tabular-nums text-muted-color">
                    {{ t('docIntake.receivedLine', { date: time.serviceDate(row.receivedOn), who: row.receivedBy }) }}<template v-if="row.storageLocation"> · {{ t('docIntake.storageLine', { where: row.storageLocation }) }}</template>
                </span>
                <span v-if="row.memo" class="text-xs [overflow-wrap:anywhere]">{{ row.memo }}</span>
                <span v-if="row.handling === 'DISPOSED'" class="text-xs text-muted-color [overflow-wrap:anywhere]">
                    {{ t('docIntake.disposedLine', { date: row.disposedOn ? time.serviceDate(row.disposedOn) : '—', who: row.disposedBy ?? '—', reason: row.disposeReason ?? '' }) }}<template v-if="row.scanFileId"> · {{ t('docIntake.scanLine', { name: scanName(row.scanFileId) }) }}</template>
                </span>
                <span v-if="row.voidedAt" class="text-xs text-muted-color [overflow-wrap:anywhere]">{{ t('docIntake.voidedLine', { who: row.voidedBy ?? '—', at: time.shortDateTime(row.voidedAt), reason: row.voidReason ?? '' }) }}</span>
            </div>
            <div v-if="UtilDocIntake.open(row) && (handleable || editable)" class="ml-auto flex flex-wrap justify-end gap-1">
                <Button v-if="handleable" type="button" :label="t('docIntake.act.handling')" icon="pi pi-sync" size="small" severity="secondary" variant="text" :disabled="busy" @click="emit('handling', row.id)" />
                <Button v-if="handleable" type="button" :label="t('docIntake.act.dispose')" icon="pi pi-trash" size="small" severity="danger" variant="text" :disabled="busy" @click="emit('dispose', row.id)" />
                <Button v-if="editable" type="button" :label="t('docIntake.act.void')" icon="pi pi-times" size="small" severity="secondary" variant="text" :disabled="busy" @click="emit('void', row.id)" />
            </div>
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { DocIntake } from '~/composables/docintake/api/useDocIntakeApi.types';
import type { DocScanFile } from '~/composables/docintake/useDocIntake.types';
import UtilDocIntake from '~/utils/util-doc-intake';

interface Props {
    intakes: DocIntake[];
    /** 스캔본 이름 찾기 */
    files?: DocScanFile[];
    /** [잘못 기록] */
    editable?: boolean;
    /** [처리 바꾸기]·[폐기 처리] */
    handleable?: boolean;
    busy?: boolean;
    /** 폐기 기한(ISO) — 지났는데 보관·폐기 예정인 줄에 「폐기 기한 지남」 */
    disposeDueAt?: string | null;
    now?: number;
}

const props = withDefaults(defineProps<Props>(), { files: () => [], editable: false, handleable: false, busy: false, disposeDueAt: null, now: () => Date.now() });
const emit = defineEmits<{ handling: [id: string]; dispose: [id: string]; void: [id: string] }>();

const { t } = useI18n();
const time = useDisplayTime();

function scanName(id: string): string {
    return props.files.find((f) => f.id === id)?.label ?? t('docIntake.scanUnknown');
}
</script>
