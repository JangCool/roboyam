<template>
    <!-- 프로필 팝오버 — 이름·이메일 머리, 내 정보, 설정(설정 메뉴로 전환 — 열 수 있는 설정 항목이 없으면 감춘다), 구분선, 로그아웃. 열고 닫는 것은 상단 바의 프로필 단추가 `toggle(event)` 로 한다 -->
    <Popover ref="panel" :pt="{ content: { class: 'p-0!' } }">
        <div v-if="user" class="flex w-64 flex-col">
            <div class="flex flex-col items-center gap-2 px-4 py-5 text-center">
                <UserAvatar :name="user.nickname" size="xlarge" />
                <div class="flex min-w-0 flex-col items-center gap-1">
                    <b class="max-w-full truncate text-sm">{{ user.nickname }}</b>
                    <RoleBadge :role="user.role" />
                    <span v-if="user.email" class="max-w-full truncate text-xs text-muted-color">{{ user.email }}</span>
                </div>
            </div>
            <ul class="m-0 flex list-none flex-col border-t border-(--line) p-1">
                <li>
                    <Button type="button" :label="t('shell.myProfile')" icon="pi pi-user" severity="secondary" variant="text" fluid class="justify-start!" @click="onProfile" />
                </li>
                <li v-if="hasSettings">
                    <Button type="button" :label="t('shell.settings')" icon="pi pi-cog" severity="secondary" variant="text" fluid class="justify-start!" @click="onSettings" />
                </li>
                <li class="my-1 border-t border-(--line)" role="separator" />
                <li>
                    <Button type="button" :label="t('common.logout')" icon="pi pi-sign-out" severity="secondary" variant="text" fluid class="justify-start!" :loading="busy" @click="onLogout" />
                </li>
            </ul>
        </div>
    </Popover>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { user } = useAdminAuth();
const { busy, run } = useAdminLogoutFlow();
const { hasSettings, openSettings } = useAppMenu();
const panel = useTemplateRef('panel');

async function onProfile() {
    panel.value?.hide();
    await navigateTo('/me');
}

/** 설정 메뉴로 바꾸고 첫 화면(일반)으로 간다 — 사이드바·상단 메뉴·드로어가 설정 메뉴로 바뀐다 */
async function onSettings() {
    panel.value?.hide();
    await openSettings();
}

async function onLogout() {
    panel.value?.hide();
    await run();
}

defineExpose({ toggle: (event: Event) => panel.value?.toggle(event) });
</script>
