<template>
    <!-- 「이 조언 고르기」 — AI 상담사 답·회원 답 아래(사연 쓴 사람에게만, 아직 고르지 않았을 때 — 판정은 서버 `canPick`). 누르면 페이지가 확인창 뒤 고른다(ai-love-counsel-contract) -->
    <div class="mt-1.5">
        <Button
            type="button"
            icon="pi pi-check-circle"
            :label="t('counsel.pick')"
            size="small"
            severity="success"
            variant="outlined"
            rounded
            :loading="busy"
            :disabled="disabled"
            data-testid="counsel-pick"
            @click="$emit('pick')"
        />
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 이 답을 고르는 중 */
    busy?: boolean;
    /** 다른 답을 고르는 중 — 눌리지 않게 */
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), { busy: false, disabled: false });

defineEmits<{ pick: [] }>();

const { t } = useI18n();
</script>
