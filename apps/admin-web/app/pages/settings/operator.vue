<template>
    <!--
        운영 주체(`/settings/operator`) — 설정 메뉴 「운영·약관」 묶음의 첫 화면(약관 `/settings/terms` 앞). 2026-11-07 사용자 지시로 설정 > 일반의 ④ 구획에서 따로 뺐다.
        구획 하나(`SiteOperatorSettings` — 단계·운영자·사업자 정보·전환 이력, 구획 주소 `site-operator`). 약관 자리표시(`{{운영자}}` 등)는 게시할 때 이 값으로 바뀐다(docs/features/terms-operator-contract.md).
        권한: 조회 `site.read`(메뉴), 변경 `site.manage` — 없으면 저장 줄이 사라지고 입력칸은 `readonly`. 감추는 것은 편의이고 막는 것은 서버다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('operatorStage.title')" :subtitle="t('operatorStage.pageSubtitle')" />

        <Message v-if="!can('site.manage')" severity="info" :closable="false" size="small">{{ t('site.readOnly') }}</Message>

        <SiteOperatorSettings />
    </div>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { can } = useAdminPermission();
useSeoMeta({ title: () => t('operatorStage.title') });
</script>
