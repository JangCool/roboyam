<template>
    <!-- 글·댓글·쪽지 상대 — 회원: 닉네임 + 등급 도트(운영자 회원 계정이면 그 자리에 「운영자」 공식 뱃지) + 승인된 사업자면 「사업자」 뱃지(+ 나에게만 보이는 이용자 메모 칩) · 비회원: 이름 + 「비회원」 (+ 글 보기·댓글에서는 작성 IP 앞 두 묶음).
         공식 뱃지는 작성자 `role` 로 고르지 않는다 — 서버가 회원이면 늘 `USER` 로 준다(관리자 앱 운영자 자체는 공개 사이트에 로그인하지 않고, 매니저·스텝 표시는 응답에 아직 없다).
         「운영자」 뱃지는 `author.operator`(운영자 회원 계정이고 힘이 살아 있을 때만 true)로만 그린다 — 글자는 사이트 용어 `ADMIN_BADGE`(`StaffBadge role="ADMIN"`)
         AI 회원(`author.ai` — ai-members-contract): 등급 뱃지 자리에 「AI · {모델명}」(`AiBadge`). 늘 보인다 — 좁은 줄(`grade` 끔)에서도 빼지 않는다. 뱃지 차례: 운영자 → AI → 등급(AI 면 없음) → 사업자 -->
    <!--
        익명 작성자(`kind === 'ANONYMOUS'` — anonymous-board-contract): 사람 물음표 아이콘 + 「익명」(댓글이면 서버 번호로 「익명3」). 아바타·등급·닉네임·뱃지·메모·프로필 링크 없음 —
        목록·글 보기·댓글·홈/가젯·최신글·검색이 모두 이 줄을 쓰므로 여기 한 곳에서 가린다. `avatar` 를 켠 자리(글 줄·댓글)는 프로필 동그라미 자리에 회색 원 아이콘
    -->
    <span v-if="anonymous" class="inline-flex min-w-0 items-center gap-1" data-testid="author-anonymous">
        <AnonymousAuthorIcon :circle="avatar" :class="avatar ? 'mr-1' : ''" />
        <span class="truncate" :class="strong ? 'font-semibold text-color' : ''">{{ anonymousName }}</span>
    </span>
    <span v-else class="inline-flex min-w-0 items-center gap-1">
        <!-- 글 목록·피드·가젯 글 줄의 작성자 앞 작은 프로필 동그라미(`avatar`) — 글 보기 머리와 같은 크기(`xs`)·같은 테두리. 회원만(비회원·숨긴 글·사업자 핫딜 글은 그리지 않는다 — `memberId` 가 없다) -->
        <ProfileBorderAvatar
            v-if="showAvatar"
            :initial="UtilPointShop.initial(name)"
            :src="avatarSrc"
            size="xs"
            :border-style="decoration?.profileBorder ?? null"
            :ai="!!ai"
            lazy
            class="mr-1"
            data-testid="author-avatar"
        />
        <!-- 회원 이름은 공개 프로필(`/users/{id}`)로 가는 링크 — 글 줄 전체가 링크인 자리(모바일 목록 줄)에서는 `link` 를 켜지 않는다(링크 안 링크 금지) -->
        <!-- 회원 메뉴(`menu` — 글 목록·글 보기·댓글·가젯): 이름을 누르면 활동 보기·쪽지·메모·차단 메뉴(AuthorMenuName). 링크 자리(글 보기·댓글)는 이름 전체, 글 줄은 6자 + 「…」 -->
        <AuthorMenuName
            v-if="menuShown"
            :member-id="author.memberId!"
            :name="name"
            :display="link ? null : shortName"
            :role="author.role"
            :operator="operator"
            :memo="memo"
            :blocked="blocked"
            class="truncate"
            :class="strong ? 'font-semibold text-color' : ''"
            :style="nameStyle"
        />
        <NuxtLink
            v-else-if="profilePath"
            :to="profilePath"
            class="truncate hover:underline focus-visible:outline-2 focus-visible:outline-primary"
            :class="strong ? 'font-semibold text-color' : ''"
            :style="nameStyle"
            data-testid="author-profile-link"
            >{{ name }}</NuxtLink
        >
        <!-- 링크가 아닌 이름(글 줄) — 6자를 넘으면 6자 + 「…」, 누르면 전체 이름(Popover, 사용자 결정 2026-11-12) -->
        <TruncatedText v-else :text="name" class="truncate" :class="strong ? 'font-semibold text-color' : ''" :style="nameStyle" data-testid="author-name" />
        <!-- 운영자 회원 계정 — 등급 뱃지 대신 「운영자」 공식 뱃지(공개 프로필·헤더의 운영진 표시와 같은 자리 규칙) -->
        <StaffBadge v-if="operator" role="ADMIN" class="shrink-0" data-testid="author-operator-badge" />
        <!-- AI 회원 — 등급 대신 「AI · 모델」(AI 는 포인트·등급에서 빠진다). 운영자 뱃지와 겹치면 그 뒤에 함께 선다 -->
        <AiBadge v-if="ai" :provider="ai.provider" :model="ai.model" plain data-testid="author-ai-badge" />
        <!-- 등급 — 작성자 줄에서는 기본으로 그리지 않는다(`grade` 기본 끔 — 등급·레벨은 공개 프로필·내 정보에서만, 사용자 결정 2026-11-12). 켜면 작은 도트만 -->
        <GradeBadge v-else-if="!operator && grade && author.kind === 'MEMBER' && author.grade" :grade="author.grade" :label="author.gradeLabel" :name="false" />
        <!-- 승인된 사업자 — 서버 `author.business`(재심사 중·정지는 false, 로그인 정보의 `businessStatus` 와 따로). 모양은 공식 뱃지 부품의 사업자 스타일 -->
        <StaffBadge v-if="author.kind === 'MEMBER' && author.business" role="BUSINESS" class="shrink-0" data-testid="author-business-badge" />
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
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';
import UtilAnonymousMessages from '~/utils/util-anonymous-messages';
import UtilColor from '~/utils/util-color';
import UtilPointShop from '~/utils/util-point-shop';
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
    /** 이름 앞에 프로필 동그라미(글 보기 머리와 같은 `xs`) — 글 목록·피드·가젯 글 줄 */
    avatar?: boolean;
    /** 프로필 동그라미를 움직이는 그림 그대로(댓글 — 글 보기·프로필처럼 움직인다). 끄면 정지 그림이 있으면 그것(글 줄) */
    animated?: boolean;
    /** 등급 도트 — 기본 끔(사용자 결정 2026-11-12: 글 목록·글 보기·댓글 등 작성자 줄에는 등급을 보이지 않고 공개 프로필·내 정보에서만). 「운영자」·「AI」·「사업자」 뱃지는 그대로 */
    grade?: boolean;
    /** 이름을 누르면 회원 메뉴(활동 보기·쪽지·메모·차단 — AuthorMenuName). 회원 글·댓글 자리만 켠다(쪽지·소모임·상점 미리보기는 끔) */
    menu?: boolean;
    /** 내가 이 작성자를 차단했는가(서버 값 — 목록 줄·댓글의 `blocked === 'USER'`). 회원 메뉴의 「차단 해제」에 쓴다 */
    blocked?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    showIp: false,
    strong: false,
    link: false,
    preview: false,
    avatar: false,
    animated: false,
    grade: false,
    menu: false,
    blocked: false,
});

const { t } = useI18n();
const overrides = useBlockOverrides();
const decorations = useMyDecoration();
/** 보일 이름 — 탈퇴가 완료된 회원은 서버가 `name: null` 로 주므로 「탈퇴한 회원」. 빈 글자(숨긴 글·지운 댓글)는 그대로 비워 둔다 */
const name = computed(() => props.author.name ?? t('board.authorWithdrawn'));
/**
 * 닉네임 색 — 서버 `author.decoration`(후보)에 내가 방금 장착·해제한 것을 겹친다(`useMyDecoration`). 대비 보정은 가젯 색과 같은 규칙(`UtilColor.cssPair` — 두 테마 값을 `light-dark()` 로).
 * 뱃지·메모 칩 등 줄의 나머지는 그대로다. 테두리는 이 줄이 그리지 않는다(글 보기 머리·공개 프로필이 `ProfileBorderAvatar` 로).
 */
const decoration = computed(() => {
    if (props.author.kind !== 'MEMBER') return null;
    return props.preview ? (props.author.decoration ?? null) : decorations.decorationOf(props.author.memberId, props.author.decoration);
});
const nameStyle = computed(() => {
    const color = UtilColor.cssPair(decoration.value?.nicknameColor);
    return color ? { color } : undefined;
});
/** 프로필 동그라미 — 회원 글이고 이름이 보일 때만(서버 `avatarUrl` 에 내가 방금 고른 것을 겹친다 — 글 보기 머리와 같은 규칙) */
const showAvatar = computed(() => props.avatar && props.author.kind === 'MEMBER' && !!props.author.memberId && props.author.name !== '');
/** 움직이는 아바타는 글 줄에서 정지 그림(`avatarStillUrl`) — 닉네임 그림(GIF)과 같은 규칙(글 목록에서는 정지, 글 보기·댓글·프로필에서 움직인다) */
const avatarSrc = computed(() => {
    const { memberId, avatarUrl, avatarStillUrl } = props.author;
    if (props.preview) return avatarStillUrl ?? avatarUrl ?? null;
    const url = decorations.avatarOf(memberId, avatarUrl);
    if (props.animated) return url;
    return (url ? decorations.avatarStillOf(memberId, avatarStillUrl) : null) ?? url;
});
const guest = computed(() => props.author.kind === 'GUEST');
/** 익명 작성자 — 봉인된 글·댓글(이름·아바타 칸은 서버가 null 로 준다) */
const anonymous = computed(() => props.author.kind === 'ANONYMOUS');
const anonymousName = computed(() => UtilAnonymousMessages.name(t, props.author.anonymous));
/** AI 회원의 글·댓글(`author.ai`) — 회원만 */
const ai = computed(() => (props.author.kind === 'MEMBER' ? (props.author.ai ?? null) : null));
/** 운영자 회원 계정의 글·댓글(`author.operator`) — 회원만 */
const operator = computed(() => props.author.kind === 'MEMBER' && props.author.operator === true);
/** 이용자 메모 — 서버가 준 `author.memo`(나에게만 오는 값, 서버 후보)에 이 기기에서 방금 남기거나 지운 것을 겹친다 */
const memo = computed(() => (props.author.kind === 'MEMBER' ? overrides.memoOf(props.author.memberId, props.author.memo) : null));
/** 회원 메뉴를 그리는가 — 회원이고 이름이 보이며(탈퇴·숨긴 글 제외) 상점 미리보기가 아닐 때 */
const menuShown = computed(() => props.menu && !props.preview && props.author.kind === 'MEMBER' && !!props.author.memberId && !!props.author.name);
/** 글 줄의 이름 — 6자를 넘으면 6자 + 「…」(전체 이름은 회원 메뉴 머리글, 사용자 결정 2026-11-12) */
const shortName = computed(() => {
    const chars = Array.from(name.value);
    return chars.length > 6 ? `${chars.slice(0, 6).join('')}…` : name.value;
});
const profilePath = computed(() => (props.link && props.author.kind === 'MEMBER' && props.author.memberId ? UtilProfile.path(props.author.memberId) : null));
</script>
