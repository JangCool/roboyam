<template>
    <!--
        소모임 상세 `/groups/{id}?tab=posts|members|chat` — 머리(이름·멤버 수·개설·분류·모집·가입 방식·오프라인·내 상태·모임장) · 소개 · 오프라인/투자 고지 · 가입 영역 · 탭 · 데스크톱 오른쪽 「모임 정보」.
        가입 단추는 서버가 준 `join`(allowed·method·reason)으로 정한다 — 바로 가입(오프라인이면 확인 시트), 신청제·강퇴됐던 회원은 신청 시트(한마디). 비회원은 로그인 안내.
        탭은 글(기본 — 1단계 미확정 5번을 2단계에서 글로 정함)·멤버·채팅. 일정 탭(4단계)은 그 단계에서 더한다(「준비 중」 자리를 두지 않는다).
        글 탭(2단계, community `GroupPostsTab`): 멤버는 비공개 글까지(줄마다 「비공개」/「공개 전환됨」) + [글쓰기](`/groups/{id}/write`, 정지 중이면 꺼 두고 까닭), 비멤버·비회원은 공개로 전환된 글과
          잠금 줄(「멤버에게만 보이는 글 N개 · 가입하면 볼 수 있어요」 + 가입 단추). 커서 「더보기」. 멤버가 목록을 받으면 소모임마다 한 번 `seen`(내 소모임의 새 글 수를 지운다).
        멤버 탭·채팅 링크는 멤버에게만 열린다(비멤버는 잠금 + 가입 단추). 채팅 링크는 확인 대화상자를 거쳐 새 탭으로 연다(허용 주소만 — `UtilGroup.safeChatUrl`).
        ⋯ 메뉴: 링크 복사 · (멤버) 탈퇴. 모임장은 바로 탈퇴할 수 없다(넘기기·해산 안내 → 관리). community 대비: 소모임 신고(⋯ 메뉴)는 로보얌 신고 대상에 소모임이 없어 뺐다.
    -->
    <div class="mx-auto max-w-2xl lg:max-w-none">
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

        <div v-else-if="group" class="lg:grid lg:grid-cols-[minmax(0,1fr)_15rem] lg:items-start lg:gap-6">
            <div class="min-w-0">
                <header>
                    <div class="flex items-start gap-3">
                        <GroupInitial :name="group.name" size="lg" />
                        <div class="min-w-0 flex-1">
                            <h1 class="m-0 break-words text-lg font-bold leading-tight" data-testid="group-name">{{ group.name }}</h1>
                            <p class="mt-0.5 text-xs text-muted-color" data-testid="group-meta">{{ UtilGroupMessages.metaLine(t, group, time) }}</p>
                            <p v-if="group.leader" class="mt-1 flex min-w-0 items-center gap-1 text-xs text-muted-color lg:hidden" data-testid="group-leader-line">
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
                        <GroupTag v-if="group.category" tone="neutral">{{ group.category.name }}</GroupTag>
                        <GroupTag :tone="group.recruitState === 'OPEN' ? 'green' : 'gray'">{{ UtilGroupMessages.recruitLabel(t, group.recruitState) }}</GroupTag>
                        <GroupTag>{{ UtilGroupMessages.joinTypeLabel(t, group.joinType) }}</GroupTag>
                        <GroupTag v-if="group.offline" tone="amber">{{ t('group.offlineGroup') }}</GroupTag>
                        <GroupTag v-if="isMember" tone="blue" data-testid="group-my-state">{{ group.myState === 'LEADER' ? t('group.roleLeader') : t('group.rowMember') }}</GroupTag>
                    </div>

                    <!-- 신규 가입자 — 모바일은 여기, 데스크톱은 오른쪽 「모임 정보」 -->
                    <div v-if="newcomers" class="mt-3 space-y-1.5 rounded-lg border border-surface bg-surface-0 px-3 py-2.5 text-sm lg:hidden dark:bg-surface-900">
                        <p v-if="newcomers.pending" class="m-0">
                            <NuxtLink v-if="newcomers.pending.to" :to="newcomers.pending.to" class="font-semibold text-primary hover:underline">{{ newcomers.pending.text }} ›</NuxtLink>
                            <span v-else class="text-muted-color">{{ newcomers.pending.text }}</span>
                        </p>
                        <div v-if="newcomers.recent">
                            <p class="m-0" :class="newcomers.recent.empty ? 'text-muted-color' : 'font-semibold'">{{ newcomers.recent.title }}</p>
                            <ul v-if="newcomers.recent.users.length" class="m-0 mt-1 flex list-none flex-wrap gap-x-3 gap-y-1 p-0">
                                <li v-for="(u, i) in newcomers.recent.users" :key="u.id ?? `w-${i}`" class="min-w-0 max-w-full">
                                    <PostAuthorLabel :author="UtilGroup.authorOf(u, t('group.withdrawn'))" link />
                                </li>
                            </ul>
                            <NuxtLink v-if="newcomers.recent.more" :to="newcomers.recent.more" class="mt-1 inline-block text-xs text-primary hover:underline">{{ t('group.newcomersMore') }}</NuxtLink>
                        </div>
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

                <!-- 탭 — 링크라서 주소(`?tab=`)만 바뀐다. 글이 기본 -->
                <nav :aria-label="t('group.tabsLabel')" class="mt-4 flex border-b border-surface">
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

                <div class="mt-1">
                    <!-- 글 탭 -->
                    <div v-if="tab === 'posts'" :aria-busy="posts.status === 'loading'" data-testid="group-posts">
                        <!-- 멤버: 글쓰기. 정지 중이면 단추는 꺼 두고 까닭을 적는다(조건이 바뀌면 되는 일이라 감추지 않는다) -->
                        <div v-if="isMember" class="mt-3 flex items-center justify-between gap-3" data-testid="group-posts-toolbar">
                            <p v-if="!group.permissions.canWritePost" class="m-0 min-w-0 flex-1 text-xs text-muted-color" data-testid="group-posts-suspended">{{ t('group.postsSuspended') }}</p>
                            <Button
                                v-if="group.permissions.canWritePost"
                                as="router-link"
                                :to="UtilGroup.writePath(group.id)"
                                :label="t('group.postsWrite')"
                                icon="pi pi-pencil"
                                size="small"
                                class="ml-auto shrink-0"
                                data-testid="group-posts-write"
                            />
                            <Button v-else type="button" :label="t('group.postsWrite')" icon="pi pi-pencil" size="small" disabled class="shrink-0" />
                        </div>

                        <div v-if="posts.status === 'loading' || posts.status === 'idle'" class="mt-3" aria-hidden="true">
                            <div v-for="n in 6" :key="n" class="border-b border-surface py-3.5">
                                <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-2/3'" />
                                <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                            </div>
                        </div>
                        <ErrorState v-else-if="posts.status === 'error'" :title="t('group.postsError')" :description="posts.errorMessage" retry @retry="posts.load" />
                        <template v-else>
                            <!-- 잠금 줄 — 멤버가 아니라 못 보는 비공개 글이 있다(제목·내용은 응답에 없다) -->
                            <div v-if="posts.locked && posts.privateCount > 0" class="mt-3 flex items-start gap-2 rounded-lg bg-surface-100 px-3 py-2.5 text-sm dark:bg-surface-800" data-testid="group-posts-locked">
                                <i class="pi pi-lock mt-0.5 shrink-0 text-xs text-muted-color" aria-hidden="true" />
                                <p class="m-0 min-w-0 flex-1">
                                    <b class="font-semibold">{{ t('group.postsLockedTitle', { n: posts.privateCount }) }}</b> · {{ t('group.postsLockedText') }}
                                </p>
                                <Button v-if="lockedJoinLabel" type="button" :label="lockedJoinLabel" size="small" class="shrink-0" @click="onJoin" />
                            </div>

                            <EmptyState
                                v-if="posts.items.length === 0"
                                :title="isMember ? t('group.postsEmptyMemberTitle') : t('group.postsEmptyPublicTitle')"
                                :description="isMember ? t('group.postsEmptyMemberText') : t('group.postsEmptyPublicText')"
                                icon="pi pi-file"
                                data-testid="group-posts-empty"
                            >
                                <Button v-if="isMember && group.permissions.canWritePost" as="router-link" :to="UtilGroup.writePath(group.id)" :label="t('group.postsFirstWrite')" />
                            </EmptyState>

                            <ul v-else class="-mx-4 m-0 mt-1 list-none p-0 md:mx-0" data-testid="group-posts-list">
                                <li v-for="row in posts.items" :key="row.id" class="border-b border-surface last:border-b-0" data-testid="group-post-row">
                                    <p v-if="row.hiddenReason" class="m-0 flex items-center gap-1.5 px-4 py-3 text-sm text-muted-color md:px-2">
                                        <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                                    </p>
                                    <!-- 차단한 회원·단어의 글은 접힌 한 줄(「보기」로 그 자리에서 펼친다) -->
                                    <FoldedRow v-else :reason="overrides.blockedOf(row.author.memberId, row.blocked)" kind="post" class="px-4 py-2.5 text-sm md:px-2" hide-class="px-4 pb-2 md:px-2">
                                        <NuxtLink
                                            :to="`/posts/${row.id}`"
                                            class="flex items-start gap-3 px-4 py-2.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-2 dark:hover:bg-surface-800/60"
                                        >
                                            <div class="min-w-0 flex-1">
                                                <p class="m-0 line-clamp-2 break-words text-sm leading-snug">
                                                    <!-- 공개 라벨 — 글자가 늘 있다(색만으로 가르지 않는다) -->
                                                    <GroupTag v-if="row.group" :tone="row.group.visibility === 'PUBLIC' ? 'blue' : 'gray'" class="mr-1 align-[1px]" data-testid="group-post-visibility">
                                                        {{ UtilGroupMessages.postRowVisibility(t, row.group.visibility) }}
                                                    </GroupTag>
                                                    <PostRowTitle :row="row" :new-post-hours="display.newPostHours" :pinned-mark="false" />
                                                </p>
                                                <p class="m-0 mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                                                    <PostAuthorLabel :author="row.author" />
                                                    <span aria-hidden="true">·</span>
                                                    <span>{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                                                    <span aria-hidden="true">·</span>
                                                    <PostListDate :iso="row.createdAt" :mode="display.dateDisplay" />
                                                </p>
                                            </div>
                                            <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="size-12 shrink-0 rounded-md object-cover" loading="lazy" />
                                        </NuxtLink>
                                    </FoldedRow>
                                </li>
                            </ul>

                            <div v-if="posts.hasNext" class="py-4 text-center">
                                <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="posts.loadingMore" data-testid="group-posts-more" @click="posts.loadMore" />
                                <p v-if="posts.moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ posts.moreError }}</p>
                            </div>
                            <!-- 비멤버 — 공개된 글은 여기까지 -->
                            <p v-else-if="posts.locked && posts.items.length > 0" class="py-4 text-center text-xs text-muted-color" data-testid="group-posts-end">{{ t('group.postsEnd') }}</p>
                        </template>
                        <!-- 스크린리더가 불러온 개수를 읽는다 -->
                        <p role="status" aria-live="polite" class="sr-only">{{ UtilGroupMessages.postsSummary(t, posts.status, posts.items.length, posts.hasNext) }}</p>
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

            <!-- 오른쪽 — 데스크톱에서만: 모임 정보(신규 가입자 포함) · 채팅 열기(멤버만) -->
            <aside class="space-y-4 max-lg:hidden" :aria-label="t('group.info')" data-testid="group-sidebar">
                <section class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                    <h2 class="text-sm font-semibold">{{ t('group.info') }}</h2>
                    <dl class="m-0 mt-3 space-y-2 text-sm">
                        <div v-if="group.leader" class="flex items-center justify-between gap-3">
                            <dt class="shrink-0 text-muted-color">{{ t('group.roleLeader') }}</dt>
                            <dd class="m-0 flex min-w-0 justify-end"><PostAuthorLabel :author="UtilGroup.authorOf(group.leader, t('group.withdrawn'))" link /></dd>
                        </div>
                        <div class="flex items-center justify-between gap-3">
                            <dt class="shrink-0 text-muted-color">{{ t('group.infoMembers') }}</dt>
                            <dd class="m-0">{{ group.memberCount }} / {{ group.capacity }}</dd>
                        </div>
                        <div class="flex items-center justify-between gap-3">
                            <dt class="shrink-0 text-muted-color">{{ t('group.infoRecruit') }}</dt>
                            <dd class="m-0 text-right">{{ UtilGroupMessages.recruitLabel(t, group.recruitState) }} · {{ UtilGroupMessages.joinTypeLabel(t, group.joinType) }}</dd>
                        </div>
                        <div v-if="group.offline" class="flex items-center justify-between gap-3">
                            <dt class="shrink-0 text-muted-color">{{ t('group.infoMode') }}</dt>
                            <dd class="m-0">{{ t('group.offlineGroup') }}</dd>
                        </div>
                    </dl>
                    <div v-if="newcomers" class="mt-3 space-y-1.5 border-t border-surface pt-3 text-sm">
                        <p v-if="newcomers.pending" class="m-0">
                            <NuxtLink v-if="newcomers.pending.to" :to="newcomers.pending.to" class="font-semibold text-primary hover:underline">{{ newcomers.pending.text }} ›</NuxtLink>
                            <span v-else class="text-muted-color">{{ newcomers.pending.text }}</span>
                        </p>
                        <div v-if="newcomers.recent">
                            <p class="m-0" :class="newcomers.recent.empty ? 'text-muted-color' : 'font-semibold'">{{ newcomers.recent.title }}</p>
                            <ul v-if="newcomers.recent.users.length" class="m-0 mt-1 flex list-none flex-wrap gap-x-3 gap-y-1 p-0">
                                <li v-for="(u, i) in newcomers.recent.users" :key="u.id ?? `w-${i}`" class="min-w-0 max-w-full">
                                    <PostAuthorLabel :author="UtilGroup.authorOf(u, t('group.withdrawn'))" link />
                                </li>
                            </ul>
                            <NuxtLink v-if="newcomers.recent.more" :to="newcomers.recent.more" class="mt-1 inline-block text-xs text-primary hover:underline">{{ t('group.newcomersMore') }}</NuxtLink>
                        </div>
                    </div>
                </section>

                <section v-if="isMember" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                    <h2 class="text-sm font-semibold">{{ t('group.tabChat') }}</h2>
                    <template v-if="chats.length > 0">
                        <p class="m-0 mt-1 text-xs leading-relaxed text-muted-color">{{ t('group.chatSideText') }}</p>
                        <div class="mt-3 flex flex-col gap-2">
                            <Button v-for="item in chats" :key="`${item.kind}:${item.url}`" type="button" :label="UtilGroupMessages.chatKindLabel(t, item.kind)" icon="pi pi-external-link" icon-pos="right" size="small" fluid @click="openChat(item)" />
                        </div>
                    </template>
                    <template v-else>
                        <p class="m-0 mt-1 text-xs leading-relaxed text-muted-color">{{ group.permissions.canEditGroup ? t('group.chatEmptyManageTitle') : t('group.chatEmptyTitle') }}</p>
                        <NuxtLink v-if="group.permissions.canEditGroup" :to="UtilGroup.managePath(group.id, 'chat')" class="mt-2 inline-block text-xs text-primary hover:underline">{{ t('group.chatRegister') }}</NuxtLink>
                    </template>
                </section>
            </aside>
        </div>

        <!-- ⋯ 메뉴 — 링크 복사 · (멤버) 탈퇴 -->
        <AppSheet v-if="group" v-model:visible="moreOpen" :title="group.name">
            <ul class="m-0 mt-3 list-none p-0">
                <li>
                    <button type="button" :class="SHEET_ITEM" data-testid="group-more-copy" @click="copyLink">{{ t('group.copyLink') }}</button>
                </li>
                <li v-if="isMember">
                    <button type="button" :class="[SHEET_ITEM, 'text-red-600 dark:text-red-400']" data-testid="group-more-leave" @click="onLeave">{{ t('group.leave') }}</button>
                </li>
            </ul>
            <div class="px-5 pt-3">
                <Button type="button" :label="t('common.close')" severity="secondary" variant="outlined" fluid @click="moreOpen = false" />
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
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup, { type GroupTab } from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';

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

const TAB_LABEL: Record<GroupTab, string> = { posts: 'group.tabPosts', members: 'group.tabMembers', chat: 'group.tabChat' };

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
const newcomers = computed(() =>
    group.value ? UtilGroupMessages.newcomerLines(t, group.value, UtilGroup.managePath(group.value.id, 'applications'), UtilGroup.tabPath(group.value.id, 'members')) : null,
);
const chats = computed(() => UtilGroup.openableChats(group.value?.chatLinks ?? []));

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

// 글 탭을 보고 있고 소모임·멤버 여부·로그인한 사람이 바뀌면(가입·탈퇴 포함) 다시 받는다 — 비공개 글이 나타나거나 사라진다
watch(
    () => [tab.value, group.value?.id, isMember.value, user.value?.id ?? null] as const,
    ([current, groupId]) => {
        if (current === 'posts' && groupId) void posts.load();
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
