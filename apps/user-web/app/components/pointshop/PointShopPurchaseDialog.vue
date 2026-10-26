<template>
    <!--
        구매 확인 — 계약 「구매 확인 대화상자」: 가격 · 지금 N P → 산 뒤 M P · 적립 포인트부터 · (기간제) 기간 · 고지 동의(필수) · 잔액 부족이면 [구매] 꺼짐 + 이유.
        품목 상세(`/point-shop/{id}`)와 상점 아바타 탭이 같이 쓴다. 그리기만 한다 — 요청·오류 판정은 부르는 쪽(`usePointShopPurchase`)이 하고 결과를 속성으로 내려준다.
    -->
    <Dialog v-model:visible="visible" modal :header="t('pointShop.confirmTitle')" :style="{ width: '26rem', maxWidth: 'calc(100vw - 2rem)' }" :closable="!buying" data-testid="pointshop-confirm">
        <div class="flex flex-col gap-3 text-sm">
            <div class="flex items-center gap-3">
                <slot name="preview" />
                <p class="m-0 font-semibold">{{ name }}</p>
            </div>
            <dl class="m-0 flex flex-col gap-1.5 rounded-lg bg-surface-50 px-3 py-2.5 dark:bg-surface-950">
                <div class="flex items-baseline justify-between gap-3">
                    <dt class="text-muted-color">{{ t('pointShop.confirmPrice') }}</dt>
                    <dd class="m-0 font-bold tabular-nums">{{ UtilPoint.points(price) }}</dd>
                </div>
                <div class="flex items-baseline justify-between gap-3">
                    <dt class="text-muted-color">{{ t('pointShop.confirmBalance') }}</dt>
                    <dd class="m-0 tabular-nums" data-testid="pointshop-confirm-balance">
                        {{ t('pointShop.confirmBalanceChange', { before: UtilPoint.points(balance), after: UtilPoint.points(Math.max(0, balance - price)) }) }}
                    </dd>
                </div>
            </dl>
            <p class="m-0 text-xs text-muted-color">{{ t('pointShop.confirmFreeFirst') }}</p>
            <p v-if="periodLine" class="m-0 text-xs" data-testid="pointshop-confirm-period">{{ periodLine }}</p>

            <FeatureRestrictionNotice v-if="restriction" :restriction="restriction" />

            <div class="flex items-start gap-2">
                <Checkbox v-model="agreed" :input-id="agreeId" binary :disabled="buying" :invalid="Boolean(agreeError)" :aria-describedby="agreeError ? `${agreeId}-error` : undefined" data-testid="pointshop-confirm-agree" />
                <label :for="agreeId" class="leading-snug">{{ t('pointShop.confirmNotice', { days: refundDays }) }}</label>
            </div>
            <small v-if="agreeError" :id="`${agreeId}-error`" class="-mt-2 text-xs text-red-600 dark:text-red-400">{{ agreeError }}</small>

            <Message v-if="insufficient" severity="warn" variant="simple" size="small" data-testid="pointshop-confirm-insufficient">
                {{ t('pointShop.blockedInsufficient', { points: UtilPoint.points(price - balance) }) }}
            </Message>
            <Message v-if="error" severity="error" variant="simple" size="small" data-testid="pointshop-confirm-error">{{ error }}</Message>
        </div>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="buying" @click="visible = false" />
            <Button type="button" :label="t('pointShop.confirmBuy')" :loading="buying" :disabled="!agreed || insufficient || Boolean(restriction)" data-testid="pointshop-confirm-buy" @click="$emit('buy')" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { FeatureRestriction } from '~/utils/util-feature-restriction';
import UtilPoint from '~/utils/util-point';

interface Props {
    /** 품목 이름(지금 언어) */
    name: string;
    price: number;
    /** 지금 내 포인트 */
    balance: number;
    refundDays: number;
    /** 기간제 한 줄(「산 순간부터 N일」·「이어 붙어요」) — 기간제가 아니면 빈 글자 */
    periodLine?: string;
    buying?: boolean;
    /** 칸에 붙지 않는 오류 한 줄(서버 문구 그대로) */
    error?: string;
    /** 고지 동의 칸의 서버 오류 */
    agreeError?: string;
    /** 기능 제한(403 `MEMBER_RESTRICTED`) — 있으면 [구매]를 끈다 */
    restriction?: FeatureRestriction | null;
}

const props = withDefaults(defineProps<Props>(), {
    periodLine: '',
    buying: false,
    error: '',
    agreeError: '',
    restriction: null,
});

defineEmits<{ buy: [] }>();

const visible = defineModel<boolean>('visible', { required: true });
const agreed = defineModel<boolean>('agreed', { required: true });

const { t } = useI18n();
const agreeId = useId();
const insufficient = computed(() => props.balance < props.price);
</script>
