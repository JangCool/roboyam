<template>
    <!--
        처리에 곁들이는 작성자 제재 구획 — 신고 처리 대화창(`ReportActionDialog`)과 체험단 협찬 숨김 확인(`ReviewQueueList`, 점검 D2)이 같은 부품.
        「제재 곁들이기」 체크(「제재만」은 체크 없이 늘 열림) → 단계 고르개(`SanctionStepPicker`) + 제재 사유(처음은 처리 글과 같다 — 고치면 따로).
        비회원·탈퇴·익명 작성자는 제재 불가 안내, `sanction.manage`·`sanction.ban` 이 없으면 권한 안내. 상태는 부른 쪽의 `useReportSanctionAttach`.
    -->
    <section class="flex flex-col gap-3 rounded-lg border border-(--line) px-3 py-2.5" :aria-label="t('report.sanctionWith')">
        <p v-if="!sanction?.eligible" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
            <i :class="sanction?.sealed ? 'pi pi-lock' : 'pi pi-info-circle'" class="text-[11px]" aria-hidden="true" />{{ notEligibleText }}
        </p>
        <p v-else-if="!UtilSanction.canIssue(sanction.perm)" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock text-[11px]" aria-hidden="true" />{{ t('sanction.noManage') }}</p>
        <template v-else>
            <div v-if="!only" class="flex items-start gap-2">
                <Checkbox v-model="withSanction" binary :input-id="`${baseId}-with`" :disabled="busy" />
                <label :for="`${baseId}-with`" class="flex flex-col text-sm">
                    {{ t('report.sanctionWithLabel', { name: sanction.authorName }) }}
                    <span class="text-xs leading-relaxed text-muted-color">{{ withHelp || t('report.sanctionWithHelp') }}</span>
                </label>
            </div>
            <template v-if="on">
                <ErrorState v-if="sanction.error" :title="t('sanction.previewFailed')" :description="sanction.error.message" retry :retrying="sanction.loading" @retry="emit('retry')" />
                <Skeleton v-else-if="!sanction.preview" height="6rem" />
                <template v-else>
                    <SanctionStepPicker v-model="step" :preview="sanction.preview" :perm="sanction.perm" :field-errors="stepFieldErrors" :tried="tried" :disabled="busy" />
                    <LabeledField :label="t('report.sanctionReasonLabel')" :hint="t('sanction.textCounter', { n: UtilSanction.textLength(reasonShown), max: UtilSanction.TEXT_MAX })" :help="reasonTouched ? t('sanction.reasonHelp') : t('report.sanctionReasonSame')" :error="reasonError">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" :model-value="reasonShown" rows="3" auto-resize fluid :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" @update:model-value="(v: string | undefined) => emit('update:reason', v ?? '')" />
                        </template>
                    </LabeledField>
                </template>
            </template>
        </template>
    </section>
</template>

<script setup lang="ts">
import type { ReportSanctionContext } from '~/components/report/ReportActionDialog.types';
import type { SanctionStepValue } from '~/composables/sanction/useSanctionIssue.types';
import UtilSanction from '~/utils/util-sanction';

const withSanction = defineModel<boolean>('with', { required: true });
const step = defineModel<SanctionStepValue>('step', { required: true });

interface Props {
    /** 작성자 현황 */
    sanction: ReportSanctionContext | null;
    /** 「제재만」 — 체크 없이 늘 연다 */
    only?: boolean;
    /** 제재를 함께 보내는 중인가(`useReportSanctionAttach().on`) */
    on?: boolean;
    busy?: boolean;
    tried?: boolean;
    /** 제재 사유 칸에 보이는 값(고치기 전에는 처리 글) */
    reasonShown?: string;
    /** 제재 사유를 따로 고쳤나 */
    reasonTouched?: boolean;
    reasonError?: string;
    stepFieldErrors?: { kind: string; skipStep: string; confirmNickname: string };
    /** 체크 아래 도움말(비우면 신고 처리 문구) */
    withHelp?: string;
    /** 제재할 수 없는 작성자 안내(비우면 신고 처리 문구 — 비회원·탈퇴, 봉인 익명) */
    notEligible?: string;
}

const props = withDefaults(defineProps<Props>(), {
    only: false,
    on: false,
    busy: false,
    tried: false,
    reasonShown: '',
    reasonTouched: false,
    reasonError: '',
    stepFieldErrors: () => ({ kind: '', skipStep: '', confirmNickname: '' }),
    withHelp: '',
    notEligible: '',
});
const emit = defineEmits<{ 'update:reason': [value: string]; retry: [] }>();

const { t } = useI18n();
const baseId = useId();

const notEligibleText = computed(() => {
    if (props.notEligible) return props.notEligible;
    return props.sanction?.sealed ? t('anonymousAuthor.reportSanctionSealed') : t('report.sanctionNotMember');
});
</script>
