<template>
    <!-- 사람 한 명(모임장·신청자·처리자·매니저) — 닉네임 + 등급 뱃지. 탈퇴한 회원은 서버가 준 대체 이름을 흐리게, 「탈퇴」 딱지를 붙인다. 등급이 없으면(운영자) 이름만 -->
    <span class="inline-flex min-w-0 max-w-full items-center gap-1.5">
        <span class="min-w-0 truncate" :class="[person.withdrawn ? 'text-muted-color italic' : '', bold ? 'font-medium' : '']">{{ person.nickname }}</span>
        <GradeBadge v-if="person.grade && !person.withdrawn" :label="person.grade.label" :color="person.grade.color" :icon="person.grade.icon" size="small" />
        <GroupTag v-if="person.withdrawn" :value="t('group.withdrawn')" />
    </span>
</template>

<script setup lang="ts">
import type { GroupPerson } from '~/composables/group/api/useGroupAdminApi.types';

interface Props {
    person: GroupPerson;
    bold?: boolean;
}

withDefaults(defineProps<Props>(), { bold: false });

const { t } = useI18n();
</script>
