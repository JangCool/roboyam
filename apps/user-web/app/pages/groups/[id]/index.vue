<template>
    <!--
        소모임 상세 `/groups/{id}?tab=posts|members|chat` — 머리(이름·멤버 수·개설·분류·모집·가입 방식·오프라인·내 상태·모임장) · 소개 · 오프라인/투자 고지 · 가입 영역 · 탭. 오른쪽 「모임 정보」 칸은 없앴다 — 모임장·신규 가입자는 위 카드에, 본문은 넓게(2026-11-06 사용자).
        가입 단추는 서버가 준 `join`(allowed·method·reason)으로 정한다 — 바로 가입(오프라인이면 확인 시트), 신청제·강퇴됐던 회원은 신청 시트(한마디). 비회원은 로그인 안내.
        탭은 글(기본 — 1단계 미확정 5번을 2단계에서 글로 정함)·일정(4단계)·멤버·채팅. 데스크톱은 탭 줄 없이 사이드 메뉴 아래 소모임 구역으로 고른다(`useSideMenuExtra`, 2026-11-06 사용자) — 탭 줄은 모바일 등 사이드 칸이 없을 때만.
        일정 탭(4단계, community `GroupEventsTab`): 멤버만 「다가오는 일정 N」(가까운 순, 쪽 번호 「더보기」)과 「지난 일정 보기」, 모임장·`EVENTS` 매니저(`permissions.canManageEvents`)는
          「일정 만들기」와 줄마다 「수정」. 비멤버·비회원은 요청 없이 잠금 + 가입 단추. 목록이 소모임 없음·비활성·멤버 아님을 답하면 상세를 다시 받는다.
        글 탭(2단계, community `GroupPostsTab`): 멤버는 비공개 글까지(줄마다 「비공개」/「공개 전환됨」) + [글쓰기](`/groups/{id}/write`, 정지 중이면 꺼 두고 까닭), 비멤버·비회원은 공개로 전환된 글과
          잠금 줄(「멤버에게만 보이는 글 N개 · 가입하면 볼 수 있어요」 + 가입 단추). 커서 「더보기」. 멤버가 목록을 받으면 소모임마다 한 번 `seen`(내 소모임의 새 글 수를 지운다).
        멤버 탭·채팅 링크는 멤버에게만 열린다(비멤버는 잠금 + 가입 단추). 채팅 링크는 확인 대화상자를 거쳐 새 탭으로 연다(허용 주소만 — `UtilGroup.safeChatUrl`).
        공개 범위(5단계): 머리 알약 줄 맨 앞에 「공개」/「비공개」 칩(누르면 한 줄 설명 시트, 기능이 꺼진 서버의 비공개 소모임은 없음). 공개 소모임이면 비멤버도 모든 글을 보고(서버가 `locked:false`)
          글 줄의 「공개 전환됨」을 붙이지 않는다(멤버 전용으로 돌린 글만 「비공개」).
        ⋯ 메뉴: 링크 복사 · 신고(모임장 본인 제외 — 매니저·멤버·비멤버·비회원, 비회원은 로그인 안내) · (멤버) 탈퇴. 모임장은 바로 탈퇴할 수 없다(넘기기·해산 안내 → 관리).
          신고는 글·댓글과 같은 신고 시트(`BoardReportDialog` target `GROUP`, 소모임 사유 — 투자 고지 소모임이면 「종목 추천·유료 모집」이 더해진다)와 `useGroupReport` — 닫힌·없는 소모임이라는 답이면 상세를 다시 받는다.
    -->
    <div>
        <div v-if="status === 'loading'" role="status" :aria-label="t('common.loading')" data-testid="group-loading">
            <div class="flex items-start gap-3">
                <div class="size-11 shrink-0 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
                <div class="flex-1 space-y-2 pt-1">
                    <div class="h-4 w-3/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div class="h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
            </div>
            <div class="mt-3 space-y-2">
                <div class="h-3.5 w-11/12 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="h-3.5 w-3/4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>
            <div class="mt-4 h-11 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'notfound' || (status === 'ready' && kind === 'unknown')" code="404" :title="t('group.notFoundTitle')" :description="t('group.notFoundText')" data-testid="group-notfound">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
        </ErrorState>

        <ErrorState v-else-if="status === 'error'" :title="t('group.loadError')" :description="errorMessage" retry data-testid="group-error" @retry="load()">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" severity="secondary" variant="outlined" />
        </ErrorState>

        <!-- 운영 조치로 비활성화된 소모임 — 이름만 알려 준다 -->
        <div v-else-if="kind === 'inactive' && detail" data-testid="group-inactive">
            <p class="mt-2 truncate text-center text-sm font-semibold text-muted-color">{{ detail.name }}</p>
            <ErrorState :title="t('group.inactiveTitle')" :description="t('group.inactiveText')">
                <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
            </ErrorState>
        </div>

        <div v-else-if="group">
            <!-- 본문 열을 한 카드에 — 기본 테마는 카드가 보이지 않고(역할 변수 기본값), 카드 테마는 흰 카드다(`main.css` `content-card`) -->
            <div class="content-card min-w-0" data-testid="group-main-card">
                <header>
                    <div class="flex items-start gap-3">
                        <GroupInitial :name="group.name" :src="group.image?.url" size="lg" />
                        <div class="min-w-0 flex-1">
                            <h1 class="m-0 break-words text-lg font-bold leading-tight" data-testid="group-name">{{ group.name }}</h1>
                            <p class="mt-0.5 text-xs text-muted-color" data-testid="group-meta">{{ UtilGroupMessages.metaLine(t, group, time) }}</p>
                            <p v-if="group.leader" class="mt-1 flex min-w-0 items-center gap-1 text-xs text-muted-color" data-testid="group-leader-line">
                                <span class="shrink-0">{{ t('group.roleLeader') }}</span>
                                <PostAuthorLabel :author="UtilGroup.authorOf(group.leader, t('group.withdrawn'))" link />
                            </p>
                        </div>
                        <div class="flex shrink-0 items-center gap-1">
                            <Button v-if="group.permissions.canManage" as="router-link" :to="UtilGroup.managePath(group.id)" :label="t('group.manage')" size="small" severity="secondary" variant="outlined" data-testid="group-manage" />
                            <Button type="button" icon="pi pi-ellipsis-h" severity="secondary" variant="text" rounded :aria-label="t('group.menu')" aria-haspopup="dialog" data-testid="group-menu" @click="moreOpen = true" />
                        </div>
                    </div>

                    <div class="mt-2.5 flex flex-wrap items-center gap-1" data-testid="group-badges">
                        <!-- 공개 범위(5단계) — 누르면 한 줄 설명 시트. 기능이 꺼진 서버의 비공개 소모임은 그리지 않는다 -->
                        <button
                            v-if="group.visibility"
                            type="button"
                            class="inline-flex rounded focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary"
                            :aria-label="t('groupVisibility.chipMore', { name: UtilGroupVisibilityMessages.nameOf(t, group.visibility) })"
                            aria-haspopup="dialog"
                            data-testid="group-visibility"
                            @click="visibilityInfoOpen = true"
                        >
                            <GroupVisibilityChip :visibility="group.visibility" />
                        </button>
                        <GroupTag v-if="group.category" tone="neutral">{{ group.category.name }}</GroupTag>
                        <GroupTag :tone="group.recruitState === 'OPEN' ? 'green' : 'gray'">{{ UtilGroupMessages.recruitLabel(t, group.recruitState) }}</GroupTag>
                        <GroupTag>{{ UtilGroupMessages.joinTypeLabel(t, group.joinType) }}</GroupTag>
                        <GroupTag v-if="group.offline" tone="amber">{{ t('group.offlineGroup') }}</GroupTag>
                        <!-- 내 역할 — 모임장 · 매니저(`myRole` — 매니저도 `myState` 는 MEMBER) · 가입함 -->
                        <GroupTag v-if="isMember" :tone="group.myRole === 'MANAGER' ? 'sky' : 'blue'" data-testid="group-my-state">
                            {{ group.myState === 'LEADER' ? t('group.roleLeader') : group.myRole === 'MANAGER' ? t('group.roleManager') : t('group.rowMember') }}
                        </GroupTag>
                    </div>

                    <!-- 가입 신청 대기(관리할 수 있는 사람에게) — 최근 가입한 멤버 목록은 보이지 않는다(2026-11-06 사용자) -->
                    <div v-if="newcomers?.pending" class="mt-3 rounded-lg border border-surface bg-surface-0 px-3 py-2.5 text-sm dark:bg-surface-900">
                        <p class="m-0">
                            <NuxtLink v-if="newcomers.pending.to" :to="newcomers.pending.to" class="font-semibold text-primary hover:underline">{{ newcomers.pending.text }} ›</NuxtLink>
                            <span v-else class="text-muted-color">{{ newcomers.pending.text }}</span>
                        </p>
                    </div>
                </header>

                <p v-if="group.description" class="mt-3 whitespace-pre-line break-words text-sm leading-relaxed" data-testid="group-description">{{ group.description }}</p>

                <GroupNotice v-if="group.offline" class="mt-3" tone="warn" data-testid="group-offline-notice">{{ t('group.offlineNotice') }}</GroupNotice>

                <div v-if="group.stockNotice" class="mt-3 space-y-2" data-testid="group-stock-notice">
                    <GroupNotice tone="warn" :title="t('group.stockNoticeTitle')">{{ t('group.stockNoticeText') }}</GroupNotice>
                    <BoardInvestmentNotice />
                </div>

                <!-- 가입 영역 — 멤버·모임장은 없다 -->
                <section v-if="joinAction.visible" :aria-label="t('group.joinButton')" class="mt-3 space-y-2" data-testid="group-join-panel">
                    <GroupNotice v-if="joinAction.banner" tone="danger" data-testid="group-join-banner">
                        <b class="font-semibold">{{ joinAction.banner === 'REJECTED' ? t('group.bannerRejected') : t('group.bannerKicked') }}</b>
                    </GroupNotice>
                    <GroupNotice v-if="joinNotice && restricted" tone="danger" :title="joinNotice.title" data-testid="group-join-notice">{{ joinNotice.text }}</GroupNotice>
                    <Button type="button" :label="UtilGroupMessages.joinLabel(t, joinAction.label)" :disabled="joinAction.disabled" :loading="joinBusy" fluid size="large" data-testid="group-join" @click="onJoin" />
                    <template v-if="group.myState === 'APPLIED'">
                        <p class="text-xs leading-relaxed text-muted-color" data-testid="group-applied-note">
                            {{ appliedAt ? t('group.appliedOnNotice', { date: time.date(appliedAt) }) : t('group.appliedNoticeTail') }}
                        </p>
                        <Button type="button" :label="t('group.cancelApplication')" severity="secondary" variant="text" size="small" :disabled="joinBusy" data-testid="group-cancel-application" @click="cancelApplication" />
                    </template>
                    <p v-else-if="joinNotice && !restricted" class="text-xs leading-relaxed text-muted-color" data-testid="group-join-notice">
                        <b class="font-semibold text-color">{{ joinNotice.title }}</b> {{ joinNotice.text }}
                    </p>
                </section>

                <!-- 탭 — 링크라서 주소(`?tab=`)만 바뀐다. 글이 기본.
                     데스크톱은 탭 대신 사이드 메뉴 아래 「게시글·일정·멤버·채팅」(`useSideMenuExtra`, 2026-11-06 사용자) — 탭 줄은 사이드 칸이 없는 곳(모바일·사이드 없는 배치)에서만 -->
                <nav :aria-label="t('group.tabsLabel')" class="mt-4 flex border-b border-surface" :class="sideMenu.hostShown.value ? 'md:hidden' : ''">
                    <NuxtLink
                        v-for="option in UtilGroup.TABS"
                        :key="option"
                        :to="{ path: route.path, query: UtilGroup.tabQuery(option) }"
                        replace
                        class="-mb-px min-h-11 flex-1 border-b-2 py-2.5 text-center text-sm focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                        :class="tab === option ? 'border-primary font-semibold text-color' : 'border-transparent text-muted-color hover:text-color'"
                        :aria-current="tab === option ? 'page' : undefined"
                        :data-testid="`group-tab-${option}`"
                    >
                        {{ t(TAB_LABEL[option]) }}
                    </NuxtLink>
                </nav>

                <!-- 탭 줄이 없으면(데스크톱) 가입 영역과 목록 사이를 탭 줄 자리만큼 띄운다 -->
                <div class="mt-1" :class="sideMenu.hostShown.value ? 'md:mt-4' : ''">
                    <!-- 글 탭 — 게시판 목록과 같은 모양(말머리 탭 · 정렬·보기 전환·검색 · 테마 목록 부품 · 쪽 번호, 2026-11-06 사용자). 조건은 주소(`?prefix=&sort=&q=&page=`) -->
                    <div v-if="tab === 'posts'" :aria-busy="posts.status === 'loading'" data-testid="group-posts">
                        <!-- 말머리 탭 — 전체 + 소모임 게시판 말머리 -->
                        <nav v-if="posts.board && posts.board.prefixes.length" class="-mx-4 mt-2" :aria-label="t('board.prefixNav')">
                            <AppScrollRow mode="ARROWS" touch scroller-class="px-4 py-0.5">
                                <ul class="m-0 flex list-none gap-1.5 p-0 py-1">
                                    <li v-for="chip in prefixChips" :key="chip.id">
                                        <NuxtLink
                                            :to="postsLink({ prefix: chip.id, page: 1 })"
                                            class="inline-flex h-8 items-center whitespace-nowrap rounded-full border px-3 text-sm focus-visible:outline-2 focus-visible:outline-primary"
                                            :class="postsQuery.prefix === chip.id ? 'border-surface-900 bg-surface-900 font-semibold text-surface-0 dark:border-surface-300 dark:bg-surface-300 dark:text-surface-900' : 'border-surface text-muted-color hover:text-color'"
                                            :aria-current="postsQuery.prefix === chip.id ? 'page' : undefined"
                                        >
                                            {{ chip.name }}
                                        </NuxtLink>
                                    </li>
                                </ul>
                            </AppScrollRow>
                        </nav>

                        <!-- 정렬 · 보기 전환 · 검색 · 글쓰기(멤버 — 정지 중이면 꺼 두고 까닭을 아래에 적는다) -->
                        <div class="mt-2 flex flex-wrap items-center gap-2 border-b border-surface pb-2" data-testid="group-posts-toolbar">
                            <div class="flex gap-1 text-xs" role="group" :aria-label="t('board.sortLabel')">
                                <NuxtLink v-for="s in POST_SORTS" :key="s" :to="postsLink({ sort: s, page: 1 })" :class="pillClass(postsQuery.sort === s)" :aria-current="postsQuery.sort === s ? 'true' : undefined">
                                    {{ t(s === 'LATEST' ? 'board.sortLatest' : 'board.sortOldest') }}
                                </NuxtLink>
                            </div>
                            <div v-if="canToggleView" class="flex gap-1 text-xs" role="group" :aria-label="t('board.viewLabel')">
                                <button v-for="m in VIEW_MODES" :key="m" type="button" :class="pillClass(activeView === m)" :aria-pressed="activeView === m" @click="onView(m)">
                                    <i :class="m === 'LIST' ? 'pi pi-list' : 'pi pi-th-large'" class="mr-1 text-[0.625rem]" aria-hidden="true" />{{ t(m === 'LIST' ? 'board.viewList' : 'board.viewGallery') }}
                                </button>
                            </div>
                            <form class="ml-auto flex w-full items-center gap-1 sm:w-auto" role="search" @submit.prevent="onSearch">
                                <IconField class="min-w-0 flex-1 sm:w-56">
                                    <InputIcon class="pi pi-search" />
                                    <InputText v-model="searchText" type="search" size="small" :placeholder="t('board.searchPlaceholder')" :aria-label="t('board.searchLabel')" fluid />
                                </IconField>
                                <Button type="submit" :label="t('common.search')" size="small" severity="secondary" variant="outlined" />
                            </form>
                            <template v-if="isMember">
                                <Button
                                    v-if="group.permissions.canWritePost"
                                    as="router-link"
                                    :to="UtilGroup.writePath(group.id)"
                                    :label="t('group.postsWrite')"
                                    icon="pi pi-pencil"
                                    size="small"
                                    class="shrink-0"
                                    data-testid="group-posts-write"
                                />
                                <Button v-else type="button" :label="t('group.postsWrite')" icon="pi pi-pencil" size="small" disabled class="shrink-0" />
                            </template>
                        </div>
                        <p v-if="isMember && !group.permissions.canWritePost" class="m-0 mt-2 text-xs text-muted-color" data-testid="group-posts-suspended">{{ t('group.postsSuspended') }}</p>
                        <p v-if="postsQuery.q" class="mt-2 flex items-center gap-2 text-xs text-muted-color" data-testid="group-posts-search-summary">
                            {{ t('board.searchSummary', { q: postsQuery.q, n: posts.totalCount }) }}
                            <NuxtLink :to="postsLink({ q: '', page: 1 })" class="font-semibold text-primary underline-offset-2 hover:underline">{{ t('board.searchClear') }}</NuxtLink>
                        </p>

                        <!-- 잠금 줄 — 멤버가 아니라 못 보는 비공개 글이 있다(제목·내용은 응답에 없다) -->
                        <div v-if="posts.status === 'ready' && posts.locked && posts.privateCount > 0" class="mt-3 flex items-start gap-2 rounded-lg bg-surface-100 px-3 py-2.5 text-sm dark:bg-surface-800" data-testid="group-posts-locked">
                            <i class="pi pi-lock mt-0.5 shrink-0 text-xs text-muted-color" aria-hidden="true" />
                            <p class="m-0 min-w-0 flex-1">
                                <b class="font-semibold">{{ t('group.postsLockedTitle', { n: posts.privateCount }) }}</b> · {{ t('group.postsLockedText') }}
                            </p>
                            <Button v-if="lockedJoinLabel" type="button" :label="lockedJoinLabel" size="small" class="shrink-0" @click="onJoin" />
                        </div>

                        <div v-if="posts.status === 'loading' || posts.status === 'idle'" class="-mx-4 md:mx-0" aria-hidden="true">
                            <div v-for="n in 7" :key="n" class="border-b border-surface px-4 py-3.5 md:px-0">
                                <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-2/3'" />
                                <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                            </div>
                        </div>
                        <ErrorState v-else-if="posts.status === 'error'" :title="t('group.postsError')" :description="posts.errorMessage" retry @retry="loadPosts" />
                        <template v-else-if="posts.items.length === 0">
                            <EmptyState v-if="postsQuery.q" :title="t('board.searchEmptyTitle')" :description="t('board.searchEmptyText')" icon="pi pi-search">
                                <Button as="router-link" :to="postsLink({ q: '', page: 1 })" :label="t('board.searchClear')" severity="secondary" variant="outlined" />
                            </EmptyState>
                            <EmptyState v-else-if="postsQuery.page > 1" :title="t('board.pageEmptyTitle')" icon="pi pi-file">
                                <Button as="router-link" :to="postsLink({ page: 1 })" :label="t('board.firstPage')" />
                            </EmptyState>
                            <EmptyState v-else-if="postsQuery.prefix" :title="t('board.prefixEmptyTitle', { name: activePrefixName })" :description="t('board.prefixEmptyText')" icon="pi pi-filter">
                                <Button as="router-link" :to="postsLink({ prefix: '', page: 1 })" :label="t('board.allPosts')" severity="secondary" variant="outlined" />
                            </EmptyState>
                            <EmptyState
                                v-else
                                :title="isMember ? t('group.postsEmptyMemberTitle') : t('group.postsEmptyPublicTitle')"
                                :description="isMember ? (publicGroup ? t('group.postsEmptyMemberPublicText') : t('group.postsEmptyMemberText')) : t('group.postsEmptyPublicText')"
                                icon="pi pi-file"
                                data-testid="group-posts-empty"
                            >
                                <Button v-if="isMember && group.permissions.canWritePost" as="router-link" :to="UtilGroup.writePath(group.id)" :label="t('group.postsFirstWrite')" />
                            </EmptyState>
                        </template>
                        <template v-else>
                            <component :is="postListComponent" :rows="posts.items" :pinned="[]" :date-display="display.dateDisplay" :new-post-hours="display.newPostHours" data-testid="group-posts-list" />
                            <BoardPager :page="posts.page" :total-pages="posts.totalPages" :to="postsPageLink" />
                        </template>
                        <!-- 스크린리더가 불러온 개수를 읽는다 -->
                        <p role="status" aria-live="polite" class="sr-only">{{ UtilGroupMessages.postsSummary(t, posts.status, posts.items.length, false) }}</p>
                    </div>

                    <!-- 일정 탭 — 멤버만 요청한다 -->
                    <div v-else-if="tab === 'events'" :aria-busy="events.status === 'loading'" data-testid="group-events">
                        <EmptyState v-if="!isMember || events.status === 'locked'" :title="t('groupEvent.lockedTitle')" :description="t('groupEvent.lockedText')" icon="pi pi-lock" data-testid="group-events-locked">
                            <Button v-if="lockedJoinLabel" type="button" :label="lockedJoinLabel" @click="onJoin" />
                        </EmptyState>
                        <div v-else-if="events.status === 'loading' || events.status === 'idle'" class="mt-3" aria-hidden="true">
                            <div v-for="n in 3" :key="n" class="flex items-center gap-3 border-b border-surface py-3">
                                <div class="h-16 w-12 shrink-0 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                                <div class="min-w-0 flex-1">
                                    <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-4/5' : 'w-3/5'" />
                                    <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                                </div>
                            </div>
                        </div>
                        <ErrorState v-else-if="events.status === 'error'" :title="t('groupEvent.loadError')" :description="UtilGroupEventMessages.loadErrorText(t, events.errorMessage)" retry @retry="events.load" />
                        <template v-else>
                            <div v-if="group.permissions.canManageEvents && events.items.length > 0" class="mt-3 flex justify-end">
                                <Button as="router-link" :to="UtilGroupEvent.newPath(group.id)" :label="t('groupEvent.create')" icon="pi pi-plus" size="small" data-testid="group-events-create" />
                            </div>
                            <EmptyState
                                v-if="events.items.length === 0"
                                :title="group.permissions.canManageEvents ? t('groupEvent.emptyManagerTitle') : t('groupEvent.emptyMemberTitle')"
                                :description="group.permissions.canManageEvents ? t('groupEvent.emptyManagerText') : t('groupEvent.emptyMemberText')"
                                icon="pi pi-calendar"
                                data-testid="group-events-empty"
                            >
                                <Button v-if="group.permissions.canManageEvents" as="router-link" :to="UtilGroupEvent.newPath(group.id)" :label="t('groupEvent.firstCreate')" />
                            </EmptyState>
                            <template v-else>
                                <h2 class="mt-3 text-sm font-semibold" data-testid="group-events-heading">{{ t('groupEvent.upcomingHeading', { n: events.total }) }}</h2>
                                <ul class="-mx-4 m-0 mt-1 list-none p-0 md:mx-0" data-testid="group-events-list">
                                    <GroupEventRow v-for="row in events.items" :key="row.id" :group-id="group.id" :row="row" :can-edit="group.permissions.canManageEvents" />
                                </ul>
                            </template>
                            <div v-if="events.hasNext" class="py-4 text-center">
                                <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="events.loadingMore" data-testid="group-events-more" @click="events.loadMore" />
                                <p v-if="events.moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ events.moreError }}</p>
                            </div>
                            <!-- 지난 일정 — 다가오는 일정이 없어도 지난 일정은 있을 수 있어 늘 둔다 -->
                            <NuxtLink
                                :to="UtilGroupEvent.pastPath(group.id)"
                                class="-mx-4 mt-2 flex min-h-11 items-center justify-between border-y border-surface px-4 text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:mx-0 md:px-2 dark:hover:bg-surface-800"
                                data-testid="group-events-past"
                            >
                                <span>{{ t('groupEvent.pastLink') }}</span>
                                <i class="pi pi-angle-right text-muted-color" aria-hidden="true" />
                            </NuxtLink>
                        </template>
                        <p role="status" aria-live="polite" class="sr-only">{{ UtilGroupEventMessages.summary(t, events.status, events.items.length, events.hasNext) }}</p>
                    </div>

                    <!-- 멤버 탭 -->
                    <div v-else-if="tab === 'members'" :aria-busy="members.status === 'loading'" data-testid="group-members">
                        <EmptyState v-if="!isMember || members.status === 'locked'" :title="t('group.membersLockedTitle')" :description="t('group.membersLockedText')" icon="pi pi-lock" data-testid="group-members-locked">
                            <Button v-if="lockedJoinLabel" type="button" :label="lockedJoinLabel" @click="onJoin" />
                        </EmptyState>
                        <div v-else-if="members.status === 'loading'" class="mt-3" aria-hidden="true">
                            <div v-for="n in 5" :key="n" class="border-b border-surface py-3.5">
                                <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-2/5' : 'w-1/3'" />
                                <div class="mt-2 h-3 w-1/4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                            </div>
                        </div>
                        <ErrorState v-else-if="members.status === 'error'" :title="t('group.membersError')" :description="members.error" retry @retry="loadMembers" />
                        <template v-else>
                            <p class="mt-3 text-sm font-semibold" data-testid="group-members-heading">
                                {{ t('group.membersHeading', { n: members.total }) }}<small class="ml-1 text-xs font-normal text-muted-color">{{ t('group.capacityHeading', { n: group.capacity }) }}</small>
                            </p>
                            <EmptyState v-if="members.items.length === 0" :title="t('group.membersEmpty')" icon="pi pi-users" />
                            <ul v-else class="-mx-4 m-0 mt-1 list-none p-0 md:mx-0">
                                <li v-for="(member, index) in members.items" :key="member.user.id ?? `withdrawn-${index}`" class="flex items-center gap-2 border-b border-surface px-4 py-3 md:px-2" data-testid="group-member-row">
                                    <div class="min-w-0 flex-1">
                                        <div class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1">
                                            <PostAuthorLabel :author="UtilGroup.authorOf(member.user, t('group.withdrawn'))" link />
                                            <GroupTag v-if="member.role === 'LEADER'" tone="amber">{{ t('group.roleLeader') }}</GroupTag>
                                            <GroupTag v-if="member.role === 'MANAGER'" tone="sky">{{ t('group.roleManager') }}</GroupTag>
                                            <GroupTag v-if="isMe(member.user.id)" tone="gray">{{ t('group.me') }}</GroupTag>
                                        </div>
                                        <small class="mt-0.5 block text-xs text-muted-color">{{ UtilGroupMessages.joinedMonth(t, member.joinedAt, time) }}</small>
                                    </div>
                                </li>
                            </ul>
                            <div v-if="members.hasNext" class="py-4 text-center">
                                <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="members.loadingMore" @click="loadMoreMembers" />
                                <p v-if="members.moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ members.moreError }}</p>
                            </div>
                        </template>
                    </div>

                    <!-- 채팅 탭 -->
                    <div v-else data-testid="group-chat">
                        <EmptyState v-if="group.chatLocked || !isMember" :title="t('group.chatLockedTitle')" :description="t('group.chatLockedText')" icon="pi pi-lock" data-testid="group-chat-locked">
                            <Button v-if="lockedJoinLabel" type="button" :label="lockedJoinLabel" @click="onJoin" />
                        </EmptyState>
                        <template v-else-if="chats.length > 0">
                            <GroupNotice class="mt-3">{{ t('group.chatInfo') }}</GroupNotice>
                            <ul class="m-0 mt-3 list-none space-y-2 p-0">
                                <li v-for="item in chats" :key="`${item.kind}:${item.url}`" class="flex items-center gap-3 rounded-lg border border-surface bg-surface-0 px-3 py-3 dark:bg-surface-900" data-testid="group-chat-link">
                                    <span class="grid size-10 shrink-0 place-items-center rounded-xl" :class="CHAT_ICON_CLASS[item.kind]" aria-hidden="true">
                                        <i class="pi" :class="item.kind === 'DISCORD' ? 'pi-discord' : 'pi-comments'" />
                                    </span>
                                    <div class="min-w-0 flex-1">
                                        <p class="m-0 text-sm font-semibold">{{ UtilGroupMessages.chatKindLabel(t, item.kind) }}</p>
                                        <small v-if="item.label" class="block truncate text-xs text-muted-color">{{ item.label }}</small>
                                    </div>
                                    <Button type="button" :label="t('group.chatOpen')" icon="pi pi-external-link" icon-pos="right" size="small" :aria-label="t('group.chatOpenAria', { name: UtilGroupMessages.chatKindLabel(t, item.kind) })" @click="openChat(item)" />
                                </li>
                            </ul>
                            <GroupNotice class="mt-3" tone="warn" :title="t('group.chatWarningTitle')">{{ t('group.chatWarningText') }}</GroupNotice>
                            <p v-if="group.permissions.canEditGroup" class="mt-3 text-center">
                                <NuxtLink :to="UtilGroup.managePath(group.id, 'chat')" class="text-sm text-primary hover:underline">{{ t('group.chatManage') }}</NuxtLink>
                            </p>
                        </template>
                        <EmptyState
                            v-else
                            :title="group.permissions.canEditGroup ? t('group.chatEmptyManageTitle') : t('group.chatEmptyTitle')"
                            :description="group.permissions.canEditGroup ? t('group.chatEmptyManageText') : t('group.chatEmptyText')"
                            icon="pi pi-comments"
                            data-testid="group-chat-empty"
                        >
                            <Button v-if="group.permissions.canEditGroup" as="router-link" :to="UtilGroup.managePath(group.id, 'chat')" :label="t('group.chatRegister')" />
                        </EmptyState>
                    </div>
                </div>
            </div>

        </div>

        <!-- ⋯ 메뉴 — 링크 복사 · (멤버) 탈퇴 -->
        <AppSheet v-if="group" v-model:visible="moreOpen" :title="group.name">
            <ul class="m-0 mt-3 list-none p-0">
                <li>
                    <button type="button" :class="SHEET_ITEM" data-testid="group-more-copy" @click="copyLink">{{ t('group.copyLink') }}</button>
                </li>
                <li v-if="reportable">
                    <button type="button" :class="SHEET_ITEM" data-testid="group-more-report" @click="onReport">{{ t('group.report') }}</button>
                </li>
                <li v-if="isMember">
                    <button type="button" :class="[SHEET_ITEM, 'text-red-600 dark:text-red-400']" data-testid="group-more-leave" @click="onLeave">{{ t('group.leave') }}</button>
                </li>
            </ul>
            <div class="px-5 pt-3">
                <Button type="button" :label="t('common.close')" severity="secondary" variant="outlined" fluid @click="moreOpen = false" />
            </div>
        </AppSheet>

        <!-- 소모임 신고 시트 -->
        <BoardReportDialog
            v-if="group"
            v-model:visible="report.visible.value"
            target="GROUP"
            :summary="t('group.reportTarget', { name: group.name })"
            :stock="group.stockNotice"
            :loading="report.loading.value"
            :error="report.error.value"
            :field-error="report.fieldError.value"
            @submit="report.submit"
        />

        <!-- 공개 범위 한 줄 설명(5단계) -->
        <AppSheet v-if="group?.visibility" v-model:visible="visibilityInfoOpen" :title="visibilitySheet.title" :description="visibilitySheet.text">
            <div class="px-5 pt-3" data-testid="group-visibility-sheet">
                <Button type="button" :label="t('common.close')" severity="secondary" variant="outlined" fluid @click="visibilityInfoOpen = false" />
            </div>
        </AppSheet>

        <!-- 가입 신청 시트(신청제) · 오프라인 모임 가입 확인 시트 -->
        <AppSheet
            v-if="group"
            v-model:visible="applyOpen"
            :title="group.join.method === 'APPLY' ? t('group.applyTitle') : t('group.joinSheetTitle')"
            :description="group.join.method === 'APPLY' ? t('group.applyText') : t('group.joinSheetText')"
        >
            <form class="px-5 pt-4" novalidate @submit.prevent="submitApply">
                <LabeledField
                    v-if="group.join.method === 'APPLY'"
                    :label="t('group.applyMessageLabel')"
                    :hint="t('group.optional')"
                    :help="t('group.applyNote')"
                    :error="applyErrors.message || (applyTooLong ? t('group.applyTooLong', { max: UtilGroup.MESSAGE_MAX, n: applyCount }) : '')"
                >
                    <template #aside>
                        <span class="text-xs tabular-nums" :class="applyTooLong ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: applyCount, max: UtilGroup.MESSAGE_MAX }) }}</span>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="applyMessage" rows="3" auto-resize fluid :placeholder="t('group.applyPlaceholder')" :disabled="joinBusy" :invalid="invalid" :aria-describedby="describedBy" data-testid="group-apply-message" />
                    </template>
                </LabeledField>

                <div v-if="group.offline" :class="group.join.method === 'APPLY' ? 'mt-3' : ''">
                    <div class="flex items-start gap-2.5">
                        <Checkbox v-model="applyAck" binary :input-id="`${uid}-ack`" :disabled="joinBusy" :invalid="Boolean(applyErrors.offlineAck)" data-testid="group-apply-ack" />
                        <label :for="`${uid}-ack`" class="cursor-pointer text-sm leading-snug">{{ t('group.offlineAck') }}</label>
                    </div>
                    <p v-if="applyErrors.offlineAck" role="alert" class="mt-1 pl-7 text-xs text-red-600 dark:text-red-400">{{ applyErrors.offlineAck }}</p>
                </div>

                <Message v-if="applyError" severity="error" variant="simple" class="mt-3" data-testid="group-apply-error">{{ applyError }}</Message>

                <div class="mt-5 flex gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="joinBusy" @click="applyOpen = false" />
                    <Button type="submit" :label="group.join.method === 'APPLY' ? t('group.applySubmit') : t('group.joinButton')" class="flex-1" :loading="joinBusy" :disabled="!canApply" data-testid="group-apply-submit" />
                </div>
            </form>
        </AppSheet>
    </div>
</template>

<script setup lang="ts">
import type { GroupChatLink, GroupDetail, GroupMember, GroupJoinRequest } from '~/composables/group/api/useGroupApi.types';
import type { PostListQuery, PostSort } from '~/composables/board/api/usePostApi.types';
import type { BoardViewMode } from '~/composables/board/useBoardViewMode';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBoardQuery from '~/utils/util-board-query';
import UtilTheme from '~/utils/util-theme';
import UtilGroup, { type GroupTab } from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilGroupEvent from '~/utils/util-group-event';
import UtilGroupEventMessages from '~/utils/util-group-event-messages';
import UtilGroupVisibilityMessages from '~/utils/util-group-visibility-messages';

const { t } = useI18n();
const route = useRoute();
const uid = useId();
const api = useGroupApi();
const toast = useAppToast();
const time = useDisplayTime();
const { user } = useAuth();
const { prompt } = useLoginPrompt();
const { confirm } = useConfirmDialog();
const { copy } = useCopyText();
const overrides = useBlockOverrides();
const { display, ensureLoaded: ensureBoardDisplay } = useBoardDisplay();
void ensureBoardDisplay();

const TAB_LABEL: Record<GroupTab, string> = { posts: 'group.tabPosts', events: 'groupEvent.tab', members: 'group.tabMembers', chat: 'group.tabChat' };

const id = computed(() => String(route.params.id ?? ''));

/* ── 상세 ── */
type Status = 'loading' | 'ready' | 'notfound' | 'error';
const detail = ref<GroupDetail | null>(null);
const status = ref<Status>('loading');
const errorMessage = ref('');
let detailSeq = 0;
let detailAbort: AbortController | null = null;

/** 상세를 받는다 — `silent` 면 보이는 화면을 두고 조용히 바꾼다(가입·탈퇴 뒤) */
async function load(silent = false): Promise<void> {
    const mine = ++detailSeq;
    detailAbort?.abort();
    detailAbort = new AbortController();
    if (!silent) {
        detail.value = null;
        status.value = 'loading';
    }
    errorMessage.value = '';
    try {
        const result = await api.detail(id.value, detailAbort.signal);
        if (mine !== detailSeq) return;
        detail.value = result;
        status.value = 'ready';
    } catch (e) {
        if (mine !== detailSeq || UtilGroup.isAborted(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_NOT_FOUND')) {
            detail.value = null;
            status.value = 'notfound';
            return;
        }
        if (silent && detail.value) {
            toast.error(UtilBoardMessages.of(t, e), 8000);
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}
const refresh = () => load(true);

watch(id, () => void load(), { immediate: true });
// 로그인·로그아웃으로 보는 사람이 바뀌면 내 상태·채팅 링크가 달라진다 — 조용히 다시 받는다
watch(
    () => user.value?.id ?? null,
    () => void refresh(),
);
onBeforeUnmount(() => {
    detailSeq++;
    detailAbort?.abort();
});

const kind = computed(() => (detail.value ? UtilGroup.kindOf(detail.value) : null));
const group = computed(() => (status.value === 'ready' && detail.value && kind.value === 'active' ? UtilGroup.active(detail.value) : null));
const isMember = computed(() => UtilGroup.isMember(group.value?.myState));
const tab = computed(() => UtilGroup.parseTab(route.query.tab));
/** 사이드 메뉴 아래 소모임 구역 — 탭 대신(데스크톱) */
const sideMenu = useSideMenuExtra();
sideMenu.use(() =>
    group.value
        ? {
              title: group.value.name,
              items: UtilGroup.TABS.map((option) => ({ key: option, label: t(TAB_LABEL[option]), to: { path: route.path, query: UtilGroup.tabQuery(option) }, active: tab.value === option })),
          }
        : null,
);
const newcomers = computed(() =>
    group.value ? UtilGroupMessages.newcomerLines(t, group.value, UtilGroup.managePath(group.value.id, 'applications'), UtilGroup.tabPath(group.value.id, 'members')) : null,
);
const chats = computed(() => UtilGroup.openableChats(group.value?.chatLinks ?? []));
/** 공개 소모임(5단계) — 글 탭 줄의 「공개 전환됨」을 붙이지 않고 빈 상태 문구가 달라진다 */
const publicGroup = computed(() => group.value?.visibility === 'PUBLIC');
const visibilityInfoOpen = ref(false);
const visibilitySheet = computed(() => UtilGroupVisibilityMessages.chipSheet(t, group.value?.visibility ?? 'PRIVATE'));

useSeoMeta({ title: () => detail.value?.name ?? t('group.title'), robots: 'noindex' });

/* ── 가입 영역 ── */
const joinAction = computed(() => (group.value ? UtilGroup.joinAction(group.value.myState, group.value.join) : { visible: false, label: 'JOIN' as const, disabled: true, banner: null }));
const joinNotice = computed(() => (group.value && group.value.myState !== 'APPLIED' ? UtilGroupMessages.joinNotice(t, group.value.join.reason, group.value.join.retryAfterSeconds) : null));
const restricted = computed(() => UtilGroup.joinReasonIsRestricted(group.value?.join.reason ?? null));
/** 잠긴 탭의 가입 단추 — 누를 수 있을 때만 */
const lockedJoinLabel = computed(() => (joinAction.value.visible && !joinAction.value.disabled ? UtilGroupMessages.joinLabel(t, joinAction.value.label) : null));

const joinBusy = ref(false);
const appliedAt = ref<string | null>(null);
const applyOpen = ref(false);
const applyMessage = ref('');
const applyAck = ref(false);
const applyError = ref('');
const applyErrors = ref<{ message?: string; offlineAck?: string }>({});
const applyCount = computed(() => UtilGroup.trimmedLength(applyMessage.value));
const applyTooLong = computed(() => applyCount.value > UtilGroup.MESSAGE_MAX);
const canApply = computed(() => !joinBusy.value && !applyTooLong.value && (!group.value?.offline || applyAck.value));

watch(applyOpen, (open) => {
    if (!open) return;
    applyMessage.value = '';
    applyAck.value = false;
    applyError.value = '';
    applyErrors.value = {};
});
watch([applyMessage, applyAck], () => {
    applyError.value = '';
    applyErrors.value = {};
});

function onJoin() {
    const g = group.value;
    if (!g || joinBusy.value) return;
    if (!user.value) {
        prompt('groupJoin');
        return;
    }
    if (!g.join.allowed) return;
    if (g.join.method === 'APPLY' || g.offline) applyOpen.value = true;
    else void join({}, false);
}

async function submitApply() {
    const g = group.value;
    if (!g || !canApply.value) return;
    const ok = await join({ message: applyMessage.value.trim() || undefined, offlineAck: g.offline ? true : undefined }, true);
    if (ok) applyOpen.value = false;
}

/** 가입·신청 — 화면이 낡았다는 오류(정원 참·마감·이미 멤버 …)면 상세를 다시 받는다. 시트에서 보냈으면 오류를 시트 안에 */
async function join(body: GroupJoinRequest, viaSheet: boolean): Promise<boolean> {
    if (joinBusy.value) return false;
    joinBusy.value = true;
    try {
        const result = await api.join(id.value, body);
        toast.success(result.result === 'APPLIED' ? t('group.appliedToast') : t('group.joinedToast'));
        await refresh();
        return true;
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return false;
        if (UtilGroup.isStaleJoin(e)) {
            toast.error(UtilBoardMessages.of(t, e), 5000);
            await refresh();
            return true;
        }
        if (viaSheet) {
            applyError.value = UtilBoardMessages.of(t, e);
            applyErrors.value = UtilGroup.serverFields(e, ['message', 'offlineAck'] as const);
        } else {
            toast.error(UtilBoardMessages.of(t, e), 5000);
        }
        return false;
    } finally {
        joinBusy.value = false;
    }
}

async function cancelApplication() {
    if (joinBusy.value) return;
    joinBusy.value = true;
    try {
        await api.cancelApplication(id.value);
        toast.success(t('group.applicationCanceledToast'));
        appliedAt.value = null;
        await refresh();
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        toast.error(UtilBoardMessages.of(t, e), 5000);
        if (UtilGroup.isStaleJoin(e)) await refresh();
    } finally {
        joinBusy.value = false;
    }
}

// 승인 대기면 신청한 날을 내 소모임에서 찾아 안내에 쓴다(상세 응답에는 없다)
let appliedSeq = 0;
watch(
    () => [group.value?.id, group.value?.myState] as const,
    async ([groupId, state]) => {
        const mine = ++appliedSeq;
        appliedAt.value = null;
        if (!groupId || state !== 'APPLIED') return;
        try {
            const res = await api.mine();
            if (mine === appliedSeq) appliedAt.value = res.applications.find((a) => a.id === groupId)?.appliedAt ?? null;
        } catch {
            // 날짜 없이 안내한다
        }
    },
    { immediate: true },
);

/* ── 글 탭 — 비멤버도 공개 글을 받는다 ── */
const posts = reactive(
    useGroupPosts({
        groupId: id,
        member: isMember,
        onGone: () => void refresh(),
    }),
);

/** 글 목록 조건 — 게시판 목록과 같은 주소 쿼리(`?prefix=&sort=&q=&page=`, 기본값은 쓰지 않는다) */
const postsQuery = computed<PostListQuery>(() => UtilBoardQuery.parse(route.query));
function loadPosts(): void {
    void posts.load(postsQuery.value);
}

// 글 탭을 보고 있고 조건·소모임·멤버 여부·로그인한 사람이 바뀌면(가입·탈퇴 포함) 다시 받는다 — 비공개 글이 나타나거나 사라진다
watch(
    () => [tab.value, group.value?.id, isMember.value, user.value?.id ?? null, JSON.stringify(postsQuery.value)] as const,
    ([current, groupId]) => {
        if (current === 'posts' && groupId) loadPosts();
    },
    { immediate: true },
);

/** 글 목록 주소 — 글 탭은 기본 탭이라 `tab` 을 쓰지 않는다 */
function postsLink(change: Partial<PostListQuery>) {
    return { path: route.path, query: UtilBoardQuery.build({ ...postsQuery.value, ...change }) };
}
function postsPageLink(page: number) {
    return postsLink({ page });
}

const POST_SORTS: readonly PostSort[] = ['LATEST', 'OLDEST'];
const VIEW_MODES: readonly BoardViewMode[] = ['LIST', 'GALLERY'];
const prefixChips = computed(() => [{ id: '', name: t('common.all') }, ...(posts.board?.prefixes ?? []).map((p) => ({ id: p.id, name: p.name }))]);
const activePrefixName = computed(() => posts.board?.prefixes.find((p) => p.id === postsQuery.value.prefix)?.name ?? '');

/* 보기 전환 — 게시판과 같다(소모임 게시판 설정 `viewToggle`·`layout`, 고른 모양은 게시판 slug 로 기억) */
const viewMode = useBoardViewMode();
const { theme } = useTheme();
const canToggleView = computed(() => Boolean(posts.board?.viewToggle) && posts.board?.layout !== 'CARD');
const activeView = computed<BoardViewMode>(() => {
    const chosen = canToggleView.value && posts.board ? viewMode.chosen(posts.board.slug) : null;
    return chosen ?? (posts.board?.layout === 'GALLERY' ? 'GALLERY' : 'LIST');
});
/** 그릴 목록 부품 — 현재 테마의 `postList:<유형>`(게시판 목록과 같다) */
const postListComponent = computed(() => {
    const kind = posts.board?.layout === 'CARD' ? 'CARD' : activeView.value === 'GALLERY' ? 'GALLERY' : 'LIST';
    return UtilTheme.resolve(theme.value, 'postList', kind);
});
function onView(mode: BoardViewMode): void {
    if (posts.board) viewMode.choose(posts.board.slug, mode);
}
function pillClass(on: boolean): string {
    return [
        'inline-flex items-center rounded-full px-2.5 py-1 focus-visible:outline-2 focus-visible:outline-primary',
        on ? 'bg-surface-900 font-semibold text-surface-0 dark:bg-surface-300 dark:text-surface-900' : 'text-muted-color hover:bg-surface-100 dark:hover:bg-surface-800',
    ].join(' ');
}

/* 검색 */
const searchText = ref('');
watch(
    () => postsQuery.value.q,
    (q) => {
        searchText.value = q;
    },
    { immediate: true },
);
function onSearch(): void {
    void navigateTo(postsLink({ q: searchText.value.trim(), page: 1 }));
}

/* 글 줄의 공개 라벨(「비공개」·「공개 전환됨」) — 게시판 목록 부품이 그린다 */
provideGroupRowTag(computed(() => publicGroup.value));

/* ── 일정 탭 — 멤버만 요청한다(4단계) ── */
const events = reactive(
    useGroupEvents({
        groupId: id,
        scope: 'upcoming',
        onGone: () => void refresh(),
    }),
);

// 일정 탭을 보고 있고 소모임·멤버 여부·로그인한 사람이 바뀌면(가입·탈퇴 포함) 다시 받는다. 멤버가 아니면 요청 없이 잠근다.
// 탭을 다시 열 때마다 새로 받는다(일정을 만들거나 고치고 돌아와도 맞는다)
watch(
    () => [tab.value, group.value?.id, isMember.value, user.value?.id ?? null] as const,
    ([current, groupId, member]) => {
        if (current !== 'events' || !groupId) return;
        if (member) void events.load();
        else events.lock();
    },
    { immediate: true },
);

/* ── 멤버 탭 — 멤버만 요청한다 ── */
const members = reactive({
    status: 'loading' as 'loading' | 'ready' | 'error' | 'locked',
    error: '',
    items: [] as GroupMember[],
    page: 1,
    hasNext: false,
    total: 0,
    loadingMore: false,
    moreError: '',
});
let membersSeq = 0;

async function loadMembers(): Promise<void> {
    const mine = ++membersSeq;
    if (!isMember.value) {
        members.status = 'locked';
        return;
    }
    members.status = 'loading';
    members.moreError = '';
    try {
        const res = await api.members(id.value, { page: 1, size: UtilGroup.MEMBER_PAGE_SIZE });
        if (mine !== membersSeq) return;
        members.items = res.items;
        members.page = res.page;
        members.hasNext = res.hasNext;
        members.total = res.total;
        members.status = 'ready';
    } catch (e) {
        if (mine !== membersSeq || UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_NOT_MEMBER')) {
            members.status = 'locked';
            void refresh();
            return;
        }
        members.error = UtilBoardMessages.of(t, e);
        members.status = 'error';
    }
}

async function loadMoreMembers(): Promise<void> {
    if (members.loadingMore || !members.hasNext) return;
    const mine = membersSeq;
    members.loadingMore = true;
    members.moreError = '';
    try {
        const res = await api.members(id.value, { page: members.page + 1, size: UtilGroup.MEMBER_PAGE_SIZE });
        if (mine !== membersSeq) return;
        const seen = new Set(members.items.map((m) => m.user.id).filter((v): v is string => v !== null));
        members.items = [...members.items, ...res.items.filter((m) => m.user.id === null || !seen.has(m.user.id))];
        members.page = res.page;
        members.hasNext = res.hasNext;
        members.total = res.total;
    } catch (e) {
        if (mine !== membersSeq) return;
        members.moreError = t('group.moreFailed', { reason: UtilBoardMessages.of(t, e) });
    } finally {
        if (mine === membersSeq) members.loadingMore = false;
    }
}

// 멤버 탭을 보고 있고 멤버 여부·소모임이 바뀌면(가입·탈퇴 포함) 다시 받는다
watch(
    () => [tab.value, group.value?.id, isMember.value, group.value?.memberCount] as const,
    ([current, groupId]) => {
        if (current === 'members' && groupId) void loadMembers();
    },
    { immediate: true },
);
onBeforeUnmount(() => membersSeq++);

function isMe(userId: string | null): boolean {
    return Boolean(userId && user.value && userId === user.value.id);
}

/* ── 채팅 ── */
const CHAT_ICON_CLASS: Record<GroupChatLink['kind'], string> = {
    KAKAO: 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300',
    DISCORD: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-950 dark:text-indigo-300',
};

async function openChat(item: GroupChatLink) {
    const url = UtilGroup.safeChatUrl(item.kind, item.url);
    if (!url) return;
    if (!(await confirm(UtilGroupMessages.chatLeaveConfirm(t, item.kind)))) return;
    window.open(url, '_blank', 'noopener,noreferrer');
}

/* ── ⋯ 메뉴 ── */
const moreOpen = ref(false);
const SHEET_ITEM = 'block w-full border-b border-surface px-5 py-3 text-left text-sm hover:bg-surface-100 dark:hover:bg-surface-800';

async function copyLink() {
    moreOpen.value = false;
    const ok = await copy(`${window.location.origin}${UtilGroup.detailPath(id.value)}`);
    if (ok) toast.success(t('group.linkCopied'));
    else toast.error(t('group.linkCopyFailed'), 5000);
}

/** 신고 항목 — 모임장 본인에게는 없다(서버도 `400 CANNOT_REPORT_OWN`). 매니저는 신고할 수 있다 */
const reportable = computed(() => Boolean(group.value) && group.value?.myState !== 'LEADER' && group.value?.myRole !== 'LEADER');
const report = useGroupReport({ groupId: id, onGone: () => void refresh() });

function onReport() {
    moreOpen.value = false;
    if (reportable.value) report.open();
}

async function showLeaderNotice() {
    const toManage = await confirm({ title: t('group.leaderLeaveTitle'), message: t('group.leaderLeaveMessage'), confirmLabel: t('group.leaderLeaveConfirm'), cancelLabel: t('common.confirm') });
    if (toManage) await navigateTo(UtilGroup.managePath(id.value));
}

async function onLeave() {
    moreOpen.value = false;
    const g = group.value;
    if (!g) return;
    if (g.myState === 'LEADER' || g.myRole === 'LEADER') return await showLeaderNotice();
    if (!(await confirm({ title: t('group.leaveTitle'), message: t('group.leaveMessage'), confirmLabel: t('group.leaveConfirm'), tone: 'danger' }))) return;
    if (joinBusy.value) return;
    joinBusy.value = true;
    try {
        await api.leave(id.value);
        toast.success(t('group.leftToast'));
        await refresh();
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_LEADER_CANNOT_LEAVE')) {
            joinBusy.value = false;
            await showLeaderNotice();
            return;
        }
        toast.error(UtilBoardMessages.of(t, e), 5000);
        if (UtilGroup.isStaleJoin(e)) await refresh();
    } finally {
        joinBusy.value = false;
    }
}
</script>
