<template>
    <!-- 옛 주소 — 화면은 없다. 본인인증 화면의 「19금 게시판」 구역으로 넘긴다(아래 `redirect`) -->
    <div />
</template>

<script setup lang="ts">
/**
 * 옛 주소 `/settings/adult-board` — 19금 게시판 접근 설정·핀번호는 본인인증 화면(`/settings/identity`) 안의 구역으로 옮겼다(사용자 결정 2026-11-18).
 * 이미 나간 주소(핀 입력 화면의 [핀번호를 잊었어요] `?reset=1`, 즐겨찾기, 로그인 뒤 돌아올 곳)가 깨지지 않게 쿼리를 그대로 들고 그 구역(`#adult-board`)으로 넘긴다.
 * 로그인 확인은 넘어간 화면이 한다(`auth` 미들웨어 — 돌아올 곳도 새 주소가 된다).
 * ⚠ `definePageMeta` 는 파일 밖으로 끌어올려지므로 경로·해시를 글자 그대로 적는다 — `UtilAdultBoard.SETTINGS_PATH`·`ANCHOR` 와 같은 값이다.
 */
definePageMeta({
    redirect: (to) => ({ path: '/settings/identity', query: to.query, hash: '#adult-board' }),
});
</script>
