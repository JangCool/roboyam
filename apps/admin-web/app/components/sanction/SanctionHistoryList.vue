<template>
    <!--
        회원 제재 이력 — 최근 것이 위. 줄마다 번호 · 단계 · 상태 · 이의제기 상태(있으면), 사유(글자로만), 기간 · 부여자 · 근거 신고 사건(`/reports?report=`), 해제·무효 기록.
        회원 상세 제재 구획·제재 서랍·이의제기 서랍이 같은 모양으로 쓴다. 진행 중 정지의 [해제]는 `canLift` 가 켤 때만(기간 정지 `sanction.manage`, 영구 정지 `sanction.ban`) — 누르면 `lift` 로 알린다.
        `currentId` 줄(지금 보는 제재)은 테두리를 강조한다. `linkSanction` 이면 번호가 제재 화면 서랍(`/sanctions?sanction=`)으로 가는 링크다.
    -->
    <p v-if="!lines.length" class="m-0 text-[13px] text-muted-color">{{ t('sanction.historyEmpty') }}</p>
    <ol v-else class="m-0 flex list-none flex-col gap-2 p-0">
        <li v-for="line in lines" :key="line.id" class="flex flex-col gap-1.5 rounded-lg border px-3 py-2.5" :class="line.id === currentId ? 'border-primary' : 'border-(--line)'" :aria-current="line.id === currentId ? 'true' : undefined">
            <div class="flex flex-wrap items-center gap-1.5">
                <NuxtLink v-if="linkSanction" :to="{ path: '/sanctions', query: { sanction: line.id } }" class="text-xs tabular-nums text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ UtilSanctionMessages.no(t, line) }}</NuxtLink>
                <span v-else class="text-xs tabular-nums text-muted-color">{{ UtilSanctionMessages.no(t, line) }}</span>
                <SanctionTag type="kind" :kind="line.kind" :warning-no="line.warningNo" />
                <SanctionTag type="status" :kind="line.kind" :status="line.status" />
                <span v-if="line.skipped" class="inline-flex items-center gap-1 text-[11px] text-red-600 dark:text-red-400"><i class="pi pi-forward text-[10px]" aria-hidden="true" />{{ t('sanction.skipped') }}</span>
                <SanctionTag v-if="line.appeal" type="appeal" :status="line.appeal.status" class="ml-auto" />
            </div>
            <p class="m-0 text-[13px] break-words whitespace-pre-line [overflow-wrap:anywhere]" :class="line.status === 'VOIDED' ? 'text-muted-color line-through' : ''">{{ line.reason }}</p>
            <p class="m-0 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-color tabular-nums">
                <span>{{ UtilSanctionMessages.period(t, time, line) }}</span>
                <span>{{ t('sanction.issuedByValue', { name: UtilSanctionMessages.actor(t, line.issuedBy) }) }}</span>
                <NuxtLink v-if="line.reportCase" :to="{ path: '/reports', query: { report: line.reportCase.id, status: 'ALL' } }" class="inline-flex items-center gap-1 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                    <i class="pi pi-flag text-[10px]" aria-hidden="true" />{{ t('sanction.reportCaseValue', { no: line.reportCase.caseNo }) }}
                </NuxtLink>
            </p>
            <p v-if="line.status === 'LIFTED'" class="m-0 text-xs text-muted-color">
                {{ t('sanction.liftedLine', { name: UtilSanctionMessages.actor(t, line.liftedBy), at: time.full(line.liftedAt) }) }}<template v-if="line.liftReason"> — {{ line.liftReason }}</template>
            </p>
            <p v-else-if="line.status === 'VOIDED'" class="m-0 text-xs text-muted-color">{{ t('sanction.voidedLine', { at: time.full(line.voidedAt) }) }}</p>
            <div v-if="canLift(line)" class="flex justify-end">
                <Button type="button" :label="t('sanction.lift')" icon="pi pi-lock-open" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('lift', line)" />
            </div>
        </li>
    </ol>
</template>

<script setup lang="ts">
import type { SanctionLine } from '~/composables/sanction/api/useSanctionApi.types';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

interface Props {
    lines: SanctionLine[];
    /** 해제 권한 — 둘 다 false 면 [해제]를 그리지 않는다 */
    perm?: { canManage: boolean; canBan: boolean };
    /** 강조할 줄 */
    currentId?: string | null;
    /** 번호를 제재 화면 서랍 링크로 */
    linkSanction?: boolean;
    busy?: boolean;
}

const props = withDefaults(defineProps<Props>(), { perm: () => ({ canManage: false, canBan: false }), currentId: null, linkSanction: false, busy: false });
const emit = defineEmits<{ lift: [line: SanctionLine] }>();

const { t } = useI18n();
const time = useDisplayTime();

function canLift(line: SanctionLine): boolean {
    return UtilSanction.canLift(line, props.perm);
}
</script>
