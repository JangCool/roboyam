<template>
    <!--
        운영자 문구(`/settings/notification-messages/operator`, 설정 > 알림·이벤트 > 문구 › 운영자) — 운영자에게 가는 이벤트·필수 안내 메일의 문구(이벤트·채널별 제목과 본문) 목록. 계약 「개정 4」.
        목록 내용은 `NotificationMessageList`(회원 문구 `…/member` 와 같이 쓴다). 정적 주소라 편집 화면(`[id]`)보다 먼저 맞는다.
        권한: 화면 `template.read`(메뉴가 거른다) · 만들기·복사·삭제 `template.manage`. API 는 실제 서버(`useNotificationMessageApi`)다.
    -->
    <NotificationMessageList audience="OPERATOR" :messages="messages" :loading="loading" :load-error="loadError" :denied="denied" :can-manage="can('template.manage')" :deleting-id="deletingId" @retry="load" @delete="confirmDelete" />
</template>

<script setup lang="ts">

const { t } = useI18n();
useSeoMeta({ title: () => t('notificationMessage.operatorTitle') });

const { can } = useAdminPermission();

const { messages, loading, loadError, denied, deletingId, load, confirmDelete } = useNotificationMessageList();

onMounted(load);
</script>
