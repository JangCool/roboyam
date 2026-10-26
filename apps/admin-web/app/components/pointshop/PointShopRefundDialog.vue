<template>
    <!--
        포인트 상점 예외 환불 대화상자(`pointshop.refund`) — 구매 내역의 완료 줄에서 연다. 셀프 환불 조건(안 썼고 환불 기한 안) 밖의 구매를 운영자가 돌려준다(commerce.md 7-3).
        위에서 아래로: 회원 · 품목(산 때 이름·코드) · 돌려줄 포인트(산 때 가격) · 셀프 환불 판정 안내(조건 밖이면 이유, 안이면 「회원이 직접 환불할 수 있어요」) · 처리 안내 · 사유(필수 5~200자) · 감사 안내 한 줄.
        서버 호출은 하지 않는다(`components.md`) — 사유는 `submit` 으로 올리고, 확인 대화상자·저장·토스트·다시 읽기는 부모 몫이다.
        서버 오류는 사유 칸 아래(`fieldErrors.reason`) 또는 폼 아래(`error`)에 서버 문구 그대로. 칸을 고치면 그 칸의 서버 문구는 숨긴다.
        `mode="reject"` 는 정지 중 **환불 신청 거절**(계약 「1-2 정지 중 환불 신청」) — 같은 회원·품목·포인트 칸에 신청 시각을 더하고, 셀프 환불 판정·처리 안내 대신
        「사유가 회원 알림에 실려요 · 아이템은 다시 쓸 수 있어요」를 보인다. 사유 규칙(5~200자)은 같다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="mode === 'reject' ? t('pointShop.request.rejectTitle') : t('pointShop.refund.title')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="purchase" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <dl class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-2 text-sm">
                <dt class="text-muted-color">{{ t('pointShop.refund.member') }}</dt>
                <dd class="m-0 min-w-0">
                    <span :class="purchase.member.withdrawn ? 'text-muted-color' : 'font-medium'">{{ purchase.member.nickname }}</span>
                    <code v-if="purchase.member.id" class="ml-1.5 text-xs tabular-nums text-muted-color">{{ purchase.member.id }}</code>
                </dd>
                <dt class="text-muted-color">{{ t('pointShop.refund.item') }}</dt>
                <dd class="m-0 min-w-0">
                    {{ purchase.itemName }}
                    <code class="ml-1.5 text-xs text-muted-color">{{ purchase.itemCode }}</code>
                </dd>
                <dt class="text-muted-color">{{ t('pointShop.refund.purchasedAt') }}</dt>
                <dd class="m-0 tabular-nums">{{ time.full(purchase.createdAt) }}</dd>
                <template v-if="mode === 'reject' && purchase.refundRequest">
                    <dt class="text-muted-color">{{ t('pointShop.request.requestedAt') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(purchase.refundRequest.requestedAt) }}</dd>
                </template>
                <dt class="text-muted-color">{{ mode === 'reject' ? t('pointShop.request.points') : t('pointShop.refund.points') }}</dt>
                <dd class="m-0 font-semibold tabular-nums">{{ t('pointShop.pricePoint', { price: purchase.pricePoint.toLocaleString(locale) }) }}</dd>
            </dl>

            <!-- 신청 거절 안내 -->
            <ul v-if="mode === 'reject'" class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.request.rejectEffect') }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.request.rejectNoRetry') }}</span></li>
            </ul>

            <!-- 셀프 환불 판정 — 조건 밖이 예외 환불의 보통 경우 -->
            <template v-else>
            <Message v-if="purchase.selfRefund && !purchase.selfRefund.allowed" severity="warn" :closable="false" size="small">
                {{ t('pointShop.refund.outside', { reason: purchase.selfRefund.blockedBy ? UtilPointShopMessages.selfRefundBlock(t, purchase.selfRefund.blockedBy) : '—' }) }}
            </Message>
            <Message v-else-if="purchase.selfRefund" severity="info" :closable="false" size="small">
                {{ t('pointShop.refund.inside', { until: time.full(purchase.selfRefund.until) }) }}
            </Message>

            <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.refund.effect') }}</span></li>
                <li v-if="purchase.kind === 'PROFILE_AVATAR'" class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.refund.avatarRevert') }}</span></li>
                <li v-if="purchase.firstUsedAt && purchase.kind === 'NICKNAME_CHANGE_TICKET'" class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.refund.ticketUsed') }}</span></li>
            </ul>
            </template>

            <LabeledField :label="t('pointShop.refund.reason')" :hint="t('pointShop.refund.reasonHint', { min: UtilPointShop.REASON.min, max: UtilPointShop.REASON.max })" :error="reasonError" :help="mode === 'reject' ? t('pointShop.request.rejectReasonHelp') : t('pointShop.refund.reasonHelp')">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" name="reason" rows="3" auto-resize :placeholder="mode === 'reject' ? t('pointShop.request.rejectReasonPlaceholder') : t('pointShop.refund.reasonPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
                <template #aside>
                    <small class="shrink-0 text-xs tabular-nums" :class="reasonLength > UtilPointShop.REASON.max ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">
                        {{ t('pointShop.counter', { length: reasonLength, max: UtilPointShop.REASON.max }) }}
                    </small>
                </template>
            </LabeledField>

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-history mt-0.5 text-xs" aria-hidden="true" />
                {{ t('pointShop.refund.audit') }}
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="mode === 'reject' ? t('pointShop.request.rejectSubmit') : t('pointShop.refund.submit')" severity="danger" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { PointShopPurchaseRow } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilPointShop from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

interface Props {
    visible: boolean;
    /** 예외 환불(`exception`) · 환불 신청 거절(`reject`) */
    mode?: 'exception' | 'reject';
    /** 환불할 구매(완료 줄) */
    purchase?: PointShopPurchaseRow | null;
    /** 보내는 중 — 입력·단추가 잠긴다 */
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 칸마다 준 오류(`fields.reason`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { mode: 'exception', purchase: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [reason: string] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const formId = useId();

const reason = ref('');
const submitted = ref(false);
const dismissed = ref(false);

/** 열 때마다 빈 사유로 시작한다 — 다른 구매에 적던 사유가 남으면 잘못 환불하기 쉽다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reason.value = '';
        submitted.value = false;
        dismissed.value = false;
    },
    { immediate: true },
);
watch(
    () => props.fieldErrors,
    () => (dismissed.value = false),
);
watch(reason, () => (dismissed.value = true));

const reasonLength = computed(() => UtilPointShop.length(UtilPointShop.reasonText(reason.value)));
const reasonError = computed(() => {
    const problem = UtilPointShop.reasonProblem(reason.value);
    if (problem && (submitted.value || problem === 'TOO_LONG')) return UtilPointShopMessages.reasonProblem(t, problem);
    return dismissed.value ? '' : (props.fieldErrors.reason ?? '');
});

function submit(): void {
    submitted.value = true;
    if (props.busy || UtilPointShop.reasonProblem(reason.value)) return;
    emit('submit', UtilPointShop.reasonText(reason.value));
}
</script>
