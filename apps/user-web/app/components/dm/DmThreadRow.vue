<template>
    <!-- 쪽지함 한 줄 — 등급 뱃지 + 닉네임(+메모) / 마지막 쪽지 한 줄 / 시간 / 안 읽은 수. 안 읽은 줄은 굵게, 탈퇴한 회원은 흐리게.
         줄 전체가 대화방 링크라 닉네임에 링크를 걸지 않는다(링크 안 링크 금지) -->
    <li>
        <NuxtLink
            :to="UtilDm.threadPath(item.id)"
            class="flex min-h-16 items-center gap-3 border-b border-surface px-4 py-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
            :class="active ? 'bg-primary-50 dark:bg-primary-950/40' : 'hover:bg-surface-100 dark:hover:bg-surface-800'"
            :aria-current="active ? 'page' : undefined"
            data-testid="dm-thread"
        >
            <div class="min-w-0 flex-1">
                <p class="m-0 flex min-w-0 items-center text-sm"><PostAuthorLabel :author="UtilDm.partnerAuthor(item.partner)" :strong="unread" /></p>
                <p
                    class="m-0 mt-0.5 truncate text-xs"
                    :class="[unread ? 'font-semibold text-color' : 'text-muted-color', item.partner.withdrawn ? 'opacity-70' : '']"
                    data-testid="dm-thread-last"
                >
                    {{ UtilDmMessages.lastLine(t, item.last) }}
                </p>
            </div>
            <div class="flex shrink-0 flex-col items-end gap-1 text-xs text-muted-color">
                <time :datetime="item.last.createdAt" :title="time.full(item.last.createdAt)">{{ UtilDmMessages.threadTime(t, item.last.createdAt, time, now) }}</time>
                <template v-if="unread">
                    <span class="grid min-w-4 place-items-center rounded-full bg-red-600 px-1.5 text-[0.6875rem] font-semibold leading-4 text-white" aria-hidden="true" data-testid="dm-thread-unread">{{
                        UtilDm.unreadText(item.unreadCount)
                    }}</span>
                    <span class="sr-only">{{ t('dm.threadUnread', { n: UtilDm.unreadText(item.unreadCount) }) }}</span>
                </template>
            </div>
        </NuxtLink>
    </li>
</template>

<script setup lang="ts">
import type { DmThreadItem } from '~/composables/dm/api/useDmApi.types';
import UtilDm from '~/utils/util-dm';
import UtilDmMessages from '~/utils/util-dm-messages';

interface Props {
    item: DmThreadItem;
    /** 데스크톱 2단에서 지금 열려 있는 대화 */
    active?: boolean;
    /** 지금(밀리초) — 상대 시각의 기준. 목록이 한 번에 갱신하게 부모가 준다 */
    now: number;
}

const props = withDefaults(defineProps<Props>(), { active: false });

const { t } = useI18n();
const time = useDisplayTime();

/** 안 읽은 줄인가 — 지금 열려 있는 대화는 읽는 중이라 아니다 */
const unread = computed(() => !props.active && props.item.unreadCount > 0);
</script>
