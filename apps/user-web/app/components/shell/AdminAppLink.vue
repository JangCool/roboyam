<template>
    <!-- "관리자 앱" 링크 — 운영자·운영진에게만(`useAuth().adminUrl` 이 null 이면 그리지 않는다). 새 탭에서 열린다.
         바깥 모양(여백·배경·hover)은 쓰는 쪽이 `class` 로 정한다. 보이는 글자는 "관리자 앱", 스크린리더 이름은 새 탭에서 열린다는 말까지 담는다 -->
    <a v-if="adminUrl" :href="adminUrl" target="_blank" :rel="UtilAdminLink.REL" :aria-label="t('shell.adminAppNewTab')" class="flex items-center gap-2" data-testid="admin-app-link">
        <i :class="[UtilAdminLink.ICON, 'text-muted-color']" aria-hidden="true" />
        <span>{{ t('shell.adminApp') }}</span>
        <i class="pi pi-external-link ml-auto text-xs text-muted-color" aria-hidden="true" />
    </a>
</template>

<script setup lang="ts">
import UtilAdminLink from '~/utils/util-admin-link';

const { t } = useI18n();
const { adminUrl } = useAuth();
</script>
