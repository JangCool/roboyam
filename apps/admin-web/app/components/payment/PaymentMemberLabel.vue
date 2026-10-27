<template>
    <!-- 결제 화면의 회원 한 칸 — 닉네임 + 회원 번호(이메일 없음, 계약 결정 17). 탈퇴 회원은 흐리게, 번호 없음 -->
    <div class="min-w-0">
        <span class="block truncate text-[13px]" :class="member.withdrawn ? 'text-muted-color' : ''">{{ member.nickname }}</span>
        <code v-if="member.id" class="block truncate text-[11px] tabular-nums text-muted-color">{{ member.id }}</code>
    </div>
</template>

<script setup lang="ts">
import type { PaymentMember } from '~/composables/payment/api/usePaymentAdminApi.types';

defineProps<{ member: PaymentMember }>();
</script>
