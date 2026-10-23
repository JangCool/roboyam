<template>
    <!--
        운영자 템플릿(`/settings/notification-templates/templates/operator`, 설정 > 알림·이벤트 > 알림 템플릿 › 운영자 템플릿) — 운영자에게 가는 이벤트·필수 안내 메일의 템플릿 목록.
        목록 내용은 `NotificationTemplateList`(회원 템플릿 `templates/member` 와 같이 쓴다). 정적 주소라 편집 화면(`templates/[id]`)보다 먼저 맞는다.
        권한: 화면 `template.read`(메뉴가 거른다) · 만들기·복사·삭제 `template.manage`. ⚠ MOCK: API 는 메모리 목업이다(`useNotificationTemplateApi`).
    -->
    <NotificationTemplateList audience="OPERATOR" :templates="templates" :loading="loading" :load-error="loadError" :denied="denied" :can-manage="can('template.manage')" :deleting-id="deletingId" @retry="load" @delete="confirmDelete" />
</template>

<script setup lang="ts">
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';

const { t } = useI18n();
useSeoMeta({ title: () => t('notificationTemplate.templatesOperatorTitle') });

// MOCK: 목업 스위치(`?mock=template-readonly|template-denied`)로 권한을 흉내 낸다 — 서버가 붙으면 아래 REAL 줄로 바꾼다
const can = mockTemplateCan(useAdminPermission().can);
// REAL: const { can } = useAdminPermission();

const { templates, loading, loadError, denied, deletingId, load, confirmDelete } = useNotificationTemplateList();

onMounted(load);
</script>
