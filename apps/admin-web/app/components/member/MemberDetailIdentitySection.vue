<template>
    <!--
        회원 상세 — 본인 인증 구획(준비 중). 본인 인증은 나중에 도입한다 — 데이터 없이 안내만 보이고 나중에 채울 칸을 한 줄로 나열한다. 이 구획을 위한 목업 값·타입 칸은 없다.
        기본 정보 구획의 「성인 확인」(출생연도 자기 입력)은 본인 인증과 별개다.
    -->
    <EmptyState icon="pi pi-id-card" :title="t('member.detail.identity.title')" :description="t('member.detail.identity.description')">
        <p class="m-0 text-xs text-muted-color">{{ t('member.detail.identity.planned') }}</p>
    </EmptyState>
</template>

<script setup lang="ts">
const { t } = useI18n();
</script>
