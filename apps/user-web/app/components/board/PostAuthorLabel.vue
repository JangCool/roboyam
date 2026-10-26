<template>
    <!-- 글·댓글·쪽지 상대 — 회원: 닉네임 + 등급 뱃지(+ 나에게만 보이는 이용자 메모 칩) · 비회원: 이름 + 「비회원」 (+ 글 보기·댓글에서는 작성 IP 앞 두 묶음).
         운영진 공식 뱃지는 그리지 않는다 — 서버가 작성자 `role` 을 회원이면 늘 `USER` 로 준다(운영자는 공개 사이트에 로그인하지 않고, 매니저·스텝 표시는 응답에 아직 없다) -->
    <span class="inline-flex min-w-0 items-center gap-1">
        <!-- 회원 이름은 공개 프로필(`/users/{id}`)로 가는 링크 — 글 줄 전체가 링크인 자리(모바일 목록 줄)에서는 `link` 를 켜지 않는다(링크 안 링크 금지) -->
        <NuxtLink
            v-if="profilePath"
            :to="profilePath"
            class="truncate hover:underline focus-visible:outline-2 focus-visible:outline-primary"
            :class="strong ? 'font-semibold text-color' : ''"
            :style="nameStyle"
            data-testid="author-profile-link"
            >{{ author.name }}</NuxtLink
        >
        <span v-else class="truncate" :class="strong ? 'font-semibold text-color' : ''" :style="nameStyle">{{ author.name }}</span>
        <GradeBadge v-if="author.kind === 'MEMBER' && author.grade" :grade="author.grade as Grade" :label="author.gradeLabel ?? ''" class="shrink-0" />
        <!-- 이용자 메모 — 나에게만 보이는 노란 칩. 좁은 줄에서는 말줄임으로 줄어든다 -->
        <span
            v-if="memo"
            class="inline-flex min-w-0 max-w-[8rem] shrink items-center rounded bg-amber-100 px-1.5 py-1 text-[0.6875rem] font-normal leading-none text-amber-900 dark:bg-amber-900/40 dark:text-amber-200"
            :title="t('block.memoChipTitle', { memo })"
            data-testid="author-memo"
            ><span class="relative top-[-0.09em] truncate">{{ t('block.memoChip', { memo }) }}</span></span
        >
        <span v-if="guest" class="inline-flex shrink-0 items-center justify-center rounded border border-surface px-1.5 py-1 text-[0.6875rem] font-medium leading-none text-muted-color">
            <span class="relative top-[-0.09em]">{{ t('board.guestBadge') }}</span>
        </span>
        <!-- 비회원 작성 IP 앞 두 묶음 — 서버가 가려서 준다(IPv4 `123.45.*.*` · IPv6 `2001:db8:*:*`) -->
        <span v-if="guest && showIp && author.ipMasked" class="shrink-0 text-xs text-muted-color" :aria-label="t('board.guestIp', { ip: author.ipMasked })">({{ author.ipMasked }})</span>
    </span>
</template>

<script setup lang="ts">
import type { Grade } from '~/composables/auth/api/useAuthApi.types';
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';
import UtilColor from '~/utils/util-color';
import UtilProfile from '~/utils/util-profile';

interface Props {
    author: PostAuthor;
    /** 비회원 IP 앞자리를 보인다(글 보기·댓글) */
    showIp?: boolean;
    /** 이름을 굵게(글 보기 머리) */
    strong?: boolean;
    /** 회원 이름을 공개 프로필 링크로(글 보기 머리·댓글·데스크톱 목록 표) */
    link?: boolean;
    /** 포인트 상점 미리보기 — `author.decoration` 을 겹치기 없이 그대로 그린다(내 장착을 덮어쓰지 않는다) */
    preview?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    showIp: false,
    strong: false,
    link: false,
    preview: false,
});

const { t } = useI18n();
const overrides = useBlockOverrides();
const decorations = useMyDecoration();
/**
 * 닉네임 색 — 서버 `author.decoration`(후보)에 내가 방금 장착·해제한 것을 겹친다(`useMyDecoration`). 대비 보정은 홈 가젯 색과 같은 규칙(`UtilColor.cssPair` — 두 테마 값을 `light-dark()` 로).
 * 뱃지·메모 칩 등 줄의 나머지는 그대로다. 테두리는 이 줄이 그리지 않는다(글 보기 머리·공개 프로필이 `ProfileBorderAvatar` 로).
 */
const nameStyle = computed(() => {
    if (props.author.kind !== 'MEMBER') return undefined;
    const decoration = props.preview ? (props.author.decoration ?? null) : decorations.decorationOf(props.author.memberId, props.author.decoration);
    const color = UtilColor.cssPair(decoration?.nicknameColor);
    return color ? { color } : undefined;
});
const guest = computed(() => props.author.kind === 'GUEST');
/** 이용자 메모 — 서버가 준 `author.memo`(나에게만 오는 값, 서버 후보)에 이 기기에서 방금 남기거나 지운 것을 겹친다 */
const memo = computed(() => (props.author.kind === 'MEMBER' ? overrides.memoOf(props.author.memberId, props.author.memo) : null));
const profilePath = computed(() => (props.link && props.author.kind === 'MEMBER' && props.author.memberId ? UtilProfile.path(props.author.memberId) : null));
</script>
