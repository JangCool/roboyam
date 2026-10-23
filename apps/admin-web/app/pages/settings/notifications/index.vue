<template>
    <!--
        옛 주소(`/settings/notifications` — 개정 3 전의 「이벤트」 화면) — 화면이 없고 운영자 이벤트(`/settings/notifications/operator`)로 보낸다(주소의 쿼리는 그대로 넘긴다).
        서버 `AdminScreenRegistry`(관리자 메뉴 구성)가 아직 이 주소를 갖고 있고, 서랍 알림·다른 화면의 옛 링크도 여기로 올 수 있다. 회원 이벤트는 `/settings/notifications/member`.
    -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => navigateTo({ path: '/settings/notifications/operator', query: to.query }, { replace: true }),
});
</script>
