<template>
    <!--
        하이라이트 — 운영자가 고른 글을 하이라이트로 지정해 순서·노출·기간을 정한다(계약 `docs/features/popular-highlight-contract.md`, community `api-content.md` 1절).
        찬반 토론은 **게시글 기능**이다(`post-debate-contract.md`): 하이라이트 폼의 토론 칸은 그 글의 토론을 열고·바꾸고·닫으며, 하이라이트를 내리거나 지워도 토론은 그대로다.
          줄의 [토론 설정](`DebateSettingsDialog`)·[의견 N](`DebateOpinionsDrawer`)은 글 번호로 쓰는 재사용 부품이다(다음 작업의 게시글 관리도 같은 부품을 쓴다) — 권한은 글 권한 `post.read`(보기)·`post.manage`(바꾸기).
        목록: 순서(↑↓ 한 칸씩 — 전체 ID 를 새 차례로 보낸다) · 대표 이미지 · 제목(없으면 글 제목)·요약 · 상태 딱지(SHOWING·SCHEDULED·EXPIRED·OFF·BLOCKED) · 노출 기간 · 수 · 노출 켜기/끄기 · [의견] · [고치기] · [지우기](확인).
          수: 토론이 있는 줄은 「참여 N명(찬성 · 반대 · 중립) · 의견 N · 일반 댓글 N」 — 투표 수(사람, `commentCounts.votes`)와 의견 수(원댓글)를 구별하고 입장 숫자는 입장 색. 중립은 `neutralEnabled || votes.neutral > 0 || neutral > 0` 일 때만. 토론이 없는 줄은 「댓글 N」.
        만들기·고치기는 대화상자(`HighlightFormDialog` — 글 찾기·제목·요약·대표 이미지 올리기·노출·기간·이 글의 찬반 토론·중립도 받기·입장 바꾸기 허용). 후보 구획은 추천 많은 글(최근 일수·최소 추천·게시판)을 보여 주고 [하이라이트로 보내기]로 바로 만든다(기본: 노출 켬·기간 없음·맨 끝, 글의 토론은 그대로).
        글 보기: 줄의 제목(단추)이나 내용 영역을 누르면 게시글 관리와 같은 글 서랍(`PostDetailDrawer` — `usePostDetail`, 주소 `?post=` replace)이 열려 본문·정보·조치 기록을 본다(`post.read`). 이전/다음은 지금 곳의 하이라이트 차례.
          서랍의 글 조치(숨기기·삭제 …)는 게시글 관리 권한(`post.manage`·`post.restore`)대로 `usePostActionFlow` 가 하고, 끝나면 하이라이트 목록·서랍을 새로 받는다. 서랍의 토론 구획은 이 화면의 토론 설정·의견 서랍을 연다. 줄의 단추(순서·노출·토론·고치기·지우기)는 서랍을 열지 않는다.
        의견 관리는 오른쪽 서랍(`DebateOpinionsDrawer` — 글 번호로, 찬성·반대·중립 탭, 고정·숨김, 「고정 요청」 탭에서 승인·거절).
        아이로빗 홈은 아직 자리표시라 **공개 홈 표시는 없다**(홈 + 테마 작업 때) — 지금 하이라이트가 하는 일은 그 글의 「하이라이트」 표시와 토론(의견 받기가 켜졌을 때)이다. 화면이 그 사실을 안내한다.
        권한: 보기 `highlight.read`(메뉴), 바꾸기 `highlight.manage` — 없으면 처리 단추가 없고 안내 한 줄. 토론 설정·의견 서랍은 `post.read` 가 있을 때 보이고 `post.manage` 가 없으면 읽기만. 후보의 게시판 고르개는 게시판 목록을 볼 권한(`board.read`)이 있을 때만.
        사라진 하이라이트(`404 HIGHLIGHT_NOT_FOUND`)·순서 집합 불일치(`fields.ids`)는 서버 문구를 토스트로 보이고 목록을 다시 받는다.
        **곳별**(tips-contract, V161): 왼쪽 카드형 곳 메뉴(넓으면 왼쪽 좁은 칸·위에 붙음, lg 미만이면 본문 위 가로로 넘기는 줄 — `AppSideNav` 와 같은 카드·강조 모양이지만 좁을 때 고르개 대신 가로 줄·묶음 소제목·개수가 있어 화면 안에 둔다. `?place=` — home 은 주소에서 뺀다, 이름은 서버 `GET /places`)로 고른 곳에서 목록·순서·만들기·후보·글 찾기가 모두 돈다. 곳은 만든 뒤 못 바꾼다. 목록에 없는 곳(지운 가젯 페이지 등)은 home 으로 돌린다.
        **게시판 곳**(board-recommend-contract — 곳 키 `board-{boardId}`, 일반 게시판만): 곳 메뉴 아래 「게시판」 묶음(고른 글이 있거나 자동 채우기를 끈 게시판 — 서버 곳 목록)과 「+ 게시판 곳 추가」(게시판 고르기 대화상자 → 그 곳을 연다, 목록에 없어도 돌리지 않는다).
          게시판 곳 화면 = 이 화면 그대로 + 목록 머리에 「위에서 3개까지 보여요」·「모자라면 추천 많은 글로 자동 채우기」 스위치(기본 켬, `highlight.manage`). 후보는 그 게시판 글만 찾는다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('highlight.title')" :subtitle="t('highlight.subtitle')">
            <template #actions>
                <Button v-if="canManage" type="button" :label="t('highlight.add')" icon="pi pi-plus" size="small" @click="openCreate" />
                <Button type="button" icon="pi pi-refresh" :label="t('highlight.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload" />
            </template>
        </PageTitle>

        <div class="flex min-w-0 flex-col gap-4 lg:flex-row lg:items-start">
            <!-- 곳 메뉴 — 고른 곳이 주소에 남는다. 개수는 곳마다 늘 보인다(곳 목록의 `total`, 고른 곳은 방금 받은 목록의 `total` 로 — 만들기·지우기가 바로 반영된다) -->
            <nav :aria-label="t('highlight.placeLabel')" class="w-full shrink-0 rounded-xl border border-(--line) bg-(--p-content-background) p-3 lg:sticky lg:top-18 lg:w-56" data-testid="highlight-place">
                <p class="m-0 mb-2 px-2.5 text-xs font-semibold text-muted-color">{{ t('highlight.placeLabel') }}</p>
                <div v-if="placesLoading && !places.length" class="flex gap-1 lg:flex-col" aria-hidden="true">
                    <Skeleton v-for="n in 3" :key="n" height="2.25rem" class="w-28! shrink-0 lg:w-full!" />
                </div>
                <ul v-else class="relative m-0 flex list-none gap-1 overflow-x-auto p-0 [scrollbar-width:none] lg:flex-col lg:gap-0.5 lg:overflow-visible">
                    <template v-for="(item, index) in navItems" :key="item.key">
                        <!-- 가젯 페이지 묶음 소제목 — 넓을 때만(좁은 가로 줄에서는 항목 이름 앞에 「가젯 페이지 ·」) -->
                        <li v-if="item.kind === 'PAGE' && navItems[index - 1]?.kind !== 'PAGE'" class="mt-2 mb-0.5 hidden px-2.5 text-[11px] font-medium text-muted-color lg:block" role="presentation">{{ t('highlight.placeGroupPages') }}</li>
                        <!-- 게시판 묶음 소제목 — 게시판 곳이 하나라도 있으면 그 첫 줄 앞(없으면 아래 「+ 게시판 곳 추가」 앞) -->
                        <li v-if="item.kind === 'BOARD' && navItems[index - 1]?.kind !== 'BOARD'" class="mt-2 mb-0.5 hidden px-2.5 text-[11px] font-medium text-muted-color lg:block" role="presentation">{{ t('highlight.placeGroupBoards') }}</li>
                        <li class="shrink-0">
                            <button
                                type="button"
                                class="flex min-h-9 w-full cursor-pointer items-center gap-2 whitespace-nowrap rounded-lg border-0 bg-transparent px-2.5 text-left text-sm focus-visible:outline-2 focus-visible:outline-primary"
                                :class="item.key === place ? 'bg-primary-50! font-medium text-primary-700 dark:bg-primary-950! dark:text-primary-300' : 'hover:bg-(--p-content-hover-background)'"
                                :aria-current="item.key === place ? 'page' : undefined"
                                :data-testid="`highlight-place-${item.key}`"
                                @click="changePlace(item.key)"
                            >
                                <i :class="placeIcon(item.kind)" class="shrink-0 text-sm" aria-hidden="true" />
                                <span class="min-w-0 truncate lg:hidden">{{ UtilHighlightMessages.placeLabel(t, item) }}</span>
                                <span class="hidden min-w-0 truncate lg:inline">{{ item.name }}</span>
                                <span class="ml-auto pl-1 text-xs tabular-nums" :class="item.key === place ? '' : 'text-muted-color'">{{ placeTotal(item) }}</span>
                            </button>
                        </li>
                    </template>
                    <!-- 게시판 곳 열기(board-recommend-contract) — 일반 게시판을 골라 그 게시판 곳으로. 게시판 목록을 볼 권한이 있을 때만 -->
                    <template v-if="canAddBoardPlace">
                        <li v-if="!navItems.some((item) => item.kind === 'BOARD')" class="mt-2 mb-0.5 hidden px-2.5 text-[11px] font-medium text-muted-color lg:block" role="presentation">{{ t('highlight.placeGroupBoards') }}</li>
                        <li class="shrink-0">
                            <button
                                type="button"
                                class="flex min-h-9 w-full cursor-pointer items-center gap-2 whitespace-nowrap rounded-lg border-0 bg-transparent px-2.5 text-left text-sm text-primary hover:bg-(--p-content-hover-background) focus-visible:outline-2 focus-visible:outline-primary"
                                data-testid="highlight-board-add"
                                @click="openBoardPicker"
                            >
                                <i class="pi pi-plus shrink-0 text-xs" aria-hidden="true" />
                                <span class="min-w-0 truncate">{{ t('highlight.boardAdd') }}</span>
                            </button>
                        </li>
                    </template>
                </ul>
                <p class="m-0 mt-3 hidden px-2.5 text-[11px] leading-relaxed text-muted-color lg:block">{{ t('highlight.placeHint') }}</p>
            </nav>

            <div class="flex min-w-0 flex-1 flex-col gap-4">
                <p v-if="placesError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('highlight.placesFailed', { message: placesError }) }}</p>

                <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
                    <i class="pi pi-lock" aria-hidden="true" />{{ t('highlight.readOnlyHint') }}
                </p>

                <AppGadget :title="t('highlight.listTitle')">
                    <template #title-extra>
                        <span v-if="list" class="text-xs tabular-nums text-muted-color">{{ t('highlight.countLine', { showing: list.visibleCount, total: list.total }) }}</span>
                    </template>

                    <!-- 게시판 곳 머리(board-recommend-contract) — 공개 게시판 목록 위 카드는 위에서 3개까지 · 모자라면 추천 많은 글로 자동 채우기(게시판별, 기본 켬) -->
                    <div v-if="placeBoardId" class="mb-3 flex flex-col gap-2 rounded-lg border border-(--line) bg-surface-50 px-3 py-2.5 sm:flex-row sm:items-center sm:justify-between dark:bg-surface-900" data-testid="highlight-board-head">
                        <p class="m-0 flex items-start gap-1.5 text-xs leading-relaxed text-muted-color">
                            <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                            <span>
                                <span class="font-semibold text-color">{{ t('highlight.boardShowHint', { n: list?.board?.shown ?? HIGHLIGHT_BOARD_SHOW_MAX }) }}</span>
                                <span class="block">{{ t('highlight.boardShowHelp') }}</span>
                            </span>
                        </p>
                        <div class="flex shrink-0 flex-col gap-0.5 sm:items-end">
                            <div class="flex items-center gap-2">
                                <label :for="ids.autoFill" class="text-xs font-medium">{{ t('highlight.autoFillLabel') }}</label>
                                <ToggleSwitch :model-value="autoFill" :input-id="ids.autoFill" :disabled="!canManage || autoFillBusy || !list" data-testid="highlight-auto-fill" @update:model-value="(v: boolean) => changeAutoFill(v)" />
                            </div>
                            <span class="text-[11px] text-muted-color">{{ t(autoFill ? 'highlight.autoFillOnHelp' : 'highlight.autoFillOffHelp') }}</span>
                        </div>
                    </div>

                    <Message v-if="placeBoardId && list?.board && !list.board.active" severity="warn" size="small" :closable="false" class="mb-3">{{ t('highlight.boardInactive') }}</Message>

                    <div v-if="busy && !list" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                        <Skeleton v-for="n in 3" :key="n" height="5rem" />
                    </div>
                    <ErrorState v-else-if="loadError && !list" :title="t('highlight.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                    <template v-else-if="list">
                        <EmptyState v-if="list.total === 0" icon="pi pi-bookmark" :title="t('highlight.empty')" :description="t('highlight.emptyHint')">
                            <Button v-if="canManage" type="button" :label="t('highlight.add')" icon="pi pi-plus" size="small" @click="openCreate" />
                        </EmptyState>
                        <template v-else>
                            <Message v-if="list.visibleCount === 0" severity="warn" size="small" :closable="false" class="mb-3">{{ t('highlight.noneShowing') }}</Message>
                            <ol class="m-0 flex list-none flex-col divide-y divide-(--line) p-0" :aria-busy="busy || ordering">
                                <li
                                    v-for="(item, index) in list.items"
                                    :key="item.id"
                                    class="flex flex-col gap-3 py-3 sm:flex-row sm:items-start"
                                    :class="drawerOpen && item.post && item.post.id === detail.openId.value ? '-mx-2 rounded-lg bg-(--p-highlight-background) px-2' : ''"
                                    data-post-keep-drawer
                                >
                                    <!-- 순서 -->
                                    <div class="flex shrink-0 items-center gap-1 sm:flex-col">
                                        <span class="w-7 text-center text-sm font-semibold tabular-nums">{{ item.order }}</span>
                                        <template v-if="canManage">
                                            <Button type="button" icon="pi pi-angle-up" size="small" severity="secondary" variant="text" :aria-label="t('highlight.moveUp', { title: UtilHighlight.displayTitle(item) })" :disabled="index === 0 || ordering" @click="move(item, -1)" />
                                            <Button type="button" icon="pi pi-angle-down" size="small" severity="secondary" variant="text" :aria-label="t('highlight.moveDown', { title: UtilHighlight.displayTitle(item) })" :disabled="index === list.items.length - 1 || ordering" @click="move(item, 1)" />
                                        </template>
                                    </div>

                                    <!-- 대표 이미지 -->
                                    <div class="flex aspect-[16/9] w-28 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-(--line) bg-surface-100 dark:bg-surface-800">
                                        <img v-if="item.imageUrl" :src="item.imageUrl" alt="" class="size-full object-cover" loading="lazy" />
                                        <i v-else class="pi pi-image text-muted-color" aria-hidden="true" />
                                    </div>

                                    <!-- 내용 -->
                                    <!-- 누르면 글 서랍(단추·복사 단추를 누른 것은 그 단추의 일) -->
                                    <div class="flex min-w-0 flex-1 flex-col gap-1" :class="canOpenPost(item) ? 'cursor-pointer' : ''" data-testid="highlight-row-content" @click="onContentClick(item, $event)">
                                        <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                                            <Tag :value="t(`highlight.status.${item.status}`)" :icon="UtilHighlight.statusIcon(item.status)" :severity="UtilHighlight.statusSeverity(item.status)" class="text-[11px]!" />
                                            <button
                                                v-if="canOpenPost(item)"
                                                type="button"
                                                class="min-w-0 cursor-pointer break-words rounded border-0 bg-transparent p-0 text-left text-sm font-semibold text-color [overflow-wrap:anywhere] hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                                                :title="t('highlight.openPost')"
                                                data-testid="highlight-open-post"
                                                @click.stop="openPost(item.post!.id)"
                                            >
                                                {{ UtilHighlight.displayTitle(item) }}
                                            </button>
                                            <span v-else class="min-w-0 break-words text-sm font-semibold [overflow-wrap:anywhere]">{{ UtilHighlight.displayTitle(item) }}</span>
                                            <Tag v-if="item.debate" :value="t('highlight.debateTag')" icon="pi pi-comments" severity="info" class="text-[11px]!" />
                                        </div>
                                        <p v-if="item.summary" class="m-0 text-xs leading-relaxed break-words text-muted-color">{{ item.summary }}</p>
                                        <div class="text-xs">
                                            <ContentPostCell v-if="item.post" :post="item.post" />
                                            <span v-else class="text-muted-color">{{ t('highlight.postGone') }}</span>
                                        </div>
                                        <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs tabular-nums text-muted-color">
                                            <span><i class="pi pi-calendar mr-1 text-[10px]" aria-hidden="true" />{{ UtilHighlightMessages.period(t, time, item) }}</span>
                                            <!-- 토론이 있으면 투표 수(사람)와 의견 수(원댓글)를 나눠 보인다 — 입장 숫자는 입장 색 -->
                                            <i18n-t v-if="UtilHighlight.hasDebate(item)" keypath="highlight.debateCounts" tag="span" scope="global">
                                                <template #total>{{ item.commentCounts.votes.total }}</template>
                                                <template #stances>
                                                    <template v-for="(stance, i) in stancesOf(item)" :key="stance">
                                                        <template v-if="i > 0"> · </template>
                                                        <span class="font-medium" :class="UtilDebate.stanceTextClass(stance)">{{ t(`debate.stanceTab.${stance}`, { n: item.commentCounts.votes[UtilDebate.countKey(stance)] }) }}</span>
                                                    </template>
                                                </template>
                                                <template #opinions>{{ UtilHighlight.opinionCount(item.commentCounts) }}</template>
                                                <template #general>{{ item.commentCounts.general }}</template>
                                            </i18n-t>
                                            <span v-else>{{ t('highlight.plainCounts', { all: item.commentCounts.all }) }}</span>
                                            <span v-if="item.updatedBy">{{ t('highlight.updatedBy', { name: item.updatedBy.name ?? '—', at: time.listLabel(item.updatedAt) }) }}</span>
                                        </p>
                                    </div>

                                    <!-- 동작 -->
                                    <div class="flex shrink-0 flex-wrap items-center gap-1 sm:flex-col sm:items-end">
                                        <div v-if="canManage" class="flex items-center gap-2">
                                            <label :for="`hl-visible-${item.id}`" class="text-xs text-muted-color">{{ t('highlight.visibleShort') }}</label>
                                            <ToggleSwitch :model-value="item.visible" :input-id="`hl-visible-${item.id}`" :disabled="togglingId === item.id" @update:model-value="(v: boolean) => setVisible(item, v)" />
                                        </div>
                                        <div class="flex flex-wrap gap-1">
                                            <Button v-if="canReadPost && item.post && UtilHighlight.hasDebate(item)" type="button" :label="t('highlight.opinions', { n: UtilHighlight.opinionCount(item.commentCounts) })" icon="pi pi-comments" size="small" severity="secondary" variant="outlined" @click="openOpinions(item)" />
                                            <Button v-if="canReadPost && item.post" type="button" :label="t('highlight.debateSettings')" icon="pi pi-sliders-h" size="small" severity="secondary" variant="text" @click="openDebateSettings(item)" />
                                            <Button v-if="canManage" type="button" :label="t('common.edit')" icon="pi pi-pencil" size="small" severity="secondary" variant="text" @click="openEdit(item)" />
                                            <Button v-if="canManage" type="button" :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="text" :loading="removingId === item.id" @click="remove(item)" />
                                        </div>
                                    </div>
                                </li>
                            </ol>
                        </template>
                    </template>
                </AppGadget>

                <!-- 후보 — 추천 많은 글. 고르면 바로 하이라이트가 된다(기본값으로) -->
                <AppGadget :title="t('highlight.candidatesTitle')">
                    <p class="mb-3 text-xs leading-relaxed text-muted-color">{{ t('highlight.candidatesLead') }}</p>
                    <form class="mb-3 flex flex-wrap items-end gap-3" @submit.prevent="loadCandidates">
                        <div class="flex flex-col gap-1">
                            <label :for="ids.days" class="text-xs text-muted-color">{{ t('highlight.daysLabel') }}</label>
                            <Select v-model="candidateQuery.days" :input-id="ids.days" :options="dayOptions" option-label="label" option-value="value" class="w-36" />
                        </div>
                        <div class="flex flex-col gap-1">
                            <label :for="ids.minLikes" class="text-xs text-muted-color">{{ t('highlight.minLikesLabel') }}</label>
                            <InputText :id="ids.minLikes" v-model="minLikesText" inputmode="numeric" class="w-24 text-right tabular-nums" :invalid="Boolean(minLikesError)" />
                        </div>
                        <div v-if="canReadBoards" class="flex flex-col gap-1">
                            <label :for="ids.board" class="text-xs text-muted-color">{{ t('highlight.boardLabel') }}</label>
                            <!-- 게시판 곳이면 그 게시판 글만 찾는다(고르개는 그 게시판으로 묶음) -->
                            <Select v-if="placeBoardId" :model-value="placeBoardId" :input-id="ids.board" :options="boardOptions" option-label="label" option-value="value" disabled class="w-48" />
                            <Select v-else v-model="candidateQuery.boardId" :input-id="ids.board" :options="boardOptions" option-label="label" option-value="value" show-clear filter :placeholder="t('common.all')" class="w-48" />
                        </div>
                        <Button type="submit" :label="t('highlight.findCandidates')" icon="pi pi-search" size="small" :loading="candidatesBusy" :disabled="Boolean(minLikesError)" />
                    </form>
                    <p v-if="minLikesError" role="alert" class="mb-3 text-xs text-red-600 dark:text-red-400">{{ minLikesError }}</p>

                    <ErrorState v-if="candidatesError" :title="t('highlight.candidatesFailed')" :description="candidatesError" retry :retrying="candidatesBusy" @retry="loadCandidates" />
                    <EmptyState v-else-if="candidates && candidates.length === 0" icon="pi pi-search" :title="t('highlight.candidatesEmpty')" :description="t('highlight.candidatesEmptyHint')" />
                    <p v-else-if="!candidates" class="m-0 text-xs text-muted-color">{{ t('highlight.candidatesIdle') }}</p>
                    <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                        <li v-for="post in candidates" :key="post.id" class="flex flex-wrap items-start justify-between gap-3 py-2.5">
                            <div class="flex min-w-0 flex-1 flex-col gap-0.5 text-[13px]">
                                <ContentPostCell :post="post" />
                                <span class="text-xs tabular-nums text-muted-color">{{ t('highlight.candidateStats', { likes: post.likeCount, comments: post.commentCount, views: post.viewCount }) }} · {{ time.listLabel(post.createdAt) }}</span>
                            </div>
                            <Button v-if="canManage" type="button" :label="t('highlight.sendToHighlight')" icon="pi pi-bookmark" size="small" severity="secondary" variant="outlined" :loading="sendingId === post.id" :disabled="sendingId !== null && sendingId !== post.id" @click="sendCandidate(post)" />
                        </li>
                    </ul>
                </AppGadget>
            </div>
        </div>

        <!-- 게시판 곳 추가 — 일반 게시판만 고른다. 고르면 그 게시판 곳이 열린다(곳 목록에는 글을 추가하거나 자동 채우기를 끄면 남는다) -->
        <Dialog v-model:visible="boardPickerOpen" modal :header="t('highlight.boardAddTitle')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }" :draggable="false">
            <form class="flex flex-col gap-3" novalidate @submit.prevent="submitBoardPicker">
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('highlight.boardAddHint') }}</p>
                <div class="flex flex-col gap-1">
                    <label :for="ids.boardPick" class="text-xs text-muted-color">{{ t('highlight.boardLabel') }}</label>
                    <Select
                        v-model="boardPick"
                        :input-id="ids.boardPick"
                        :options="generalBoardOptions"
                        option-label="label"
                        option-value="value"
                        filter
                        :placeholder="t('highlight.boardAddPlaceholder')"
                        :empty-message="t('highlight.boardAddEmpty')"
                        :loading="boardPlacesBusy"
                        fluid
                        data-testid="highlight-board-pick"
                    />
                </div>
                <p v-if="boardPlacesError" role="alert" class="m-0 flex flex-wrap items-center gap-2 text-xs text-red-600 dark:text-red-400">
                    {{ t('highlight.boardAddFailed', { message: boardPlacesError }) }}
                    <Button type="button" :label="t('highlight.refresh')" size="small" severity="secondary" variant="text" :loading="boardPlacesBusy" @click="loadBoardPlaces" />
                </p>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="boardPickerOpen = false" />
                    <Button type="submit" :label="t('highlight.boardAddOpen')" :disabled="!boardPick" />
                </div>
            </form>
        </Dialog>

        <HighlightFormDialog v-model:visible="formOpen" :item="editing" :actions="formActions" :saving="saving" :server-errors="formFieldErrors" :form-error="formError" @save="save" />
        <DebateOpinionsDrawer v-model:visible="opinionsOpen" :target="drawerTarget" :actions="opinionActions" :can-read="canReadPost" :can-manage="canManagePost" @changed="onDebateChanged" />
        <DebateSettingsDialog
            v-model:visible="debateSettingsOpen"
            :post-id="debateSettingsPost?.id ?? debateSettingsTarget?.post?.id ?? null"
            :title="debateSettingsPost?.title ?? (debateSettingsTarget ? UtilHighlight.displayTitle(debateSettingsTarget) : '')"
            :actions="debateSettingsActions"
            :can-manage="canManagePost"
            @changed="onDebateChanged"
        />

        <!-- 글 서랍 — 게시글 관리와 같은 부품. 조치 대화창은 `usePostActionFlow` -->
        <PostDetailDrawer
            v-model:visible="drawerOpen"
            :item="postDrawerItem"
            :detail="detail.detail.value"
            :loading="detail.loading.value"
            :error="detail.error.value"
            :actions="postDrawerActions"
            :busy="postFlow.acting.value"
            :has-prev="postNeighbors.prev !== null"
            :has-next="postNeighbors.next !== null"
            :debate="postDebate"
            :debate-loading="postDebateLoading"
            :debate-error="postDebateError"
            :can-manage="canManagePost"
            :can-restore="canRestorePost"
            :can-read-reports="canReadReports"
            :boards="postFlow.boards.value"
            @retry="detail.reload(false)"
            @act="onDrawerAct"
            @prev="postNeighbors.prev && openPost(postNeighbors.prev.id)"
            @next="postNeighbors.next && openPost(postNeighbors.next.id)"
            @debate-settings="openDrawerDebateSettings"
            @debate-opinions="openDrawerOpinions"
            @debate-retry="loadPostDebate()"
            @poll-delete="onDrawerPollDelete"
            @closed="onDrawerClosed"
        />
        <PostReasonDialog
            v-model:visible="postFlow.reasonOpen.value"
            :kind="postFlow.reasonKind.value"
            :item="postFlow.reasonTarget.value"
            :busy="postFlow.acting.value"
            :error="postFlow.reasonError.value"
            :field-error="postFlow.reasonFieldError.value"
            @submit="postFlow.submitReason"
        />
        <PostMoveDialog
            v-model:visible="postFlow.moveOpen.value"
            :item="postFlow.moveTarget.value"
            :boards="postFlow.boards.value"
            :options-loading="postFlow.optionsLoading.value"
            :options-error="postFlow.optionsError.value"
            :busy="postFlow.acting.value"
            :error="postFlow.moveError.value"
            :field-errors="postFlow.moveFieldErrors.value"
            @submit="postFlow.submitMove"
            @retry-options="postFlow.loadOptions"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';
import type { DebateItem, DebateStance } from '~/composables/content/api/useDebateApi.types';
import type { AdminPostItem } from '~/composables/post/api/usePostApi.types';
import type { PostActionKind } from '~/utils/util-post';
import type { HighlightBoardPlace, HighlightCandidateQuery, HighlightItem, HighlightListView, HighlightPlace, HighlightPlaceKind, HighlightSaveRequest } from '~/composables/content/api/useHighlightApi.types';
import { HIGHLIGHT_BOARD_SHOW_MAX, HIGHLIGHT_PLACE_HOME } from '~/composables/content/api/useHighlightApi.types';
import type { BoardListItem } from '~/composables/board/api/useBoardApi.types';
import type { DebateDrawerTarget } from '~/components/debate/DebateOpinionsDrawer.types';
import type { HighlightFormActions } from '~/components/highlight/HighlightFormDialog.types';
import UtilDebate from '~/utils/util-debate';
import UtilHighlight from '~/utils/util-highlight';
import UtilHighlightMessages from '~/utils/util-highlight-messages';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
useSeoMeta({ title: () => t('highlight.title') });

const api = useHighlightApi();
const fileApi = useFileApi();
const boardApi = useBoardApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const ids = { days: useId(), minLikes: useId(), board: useId(), autoFill: useId(), boardPick: useId() };

/* ── 곳 ── */
const { places, loading: placesLoading, error: placesError, load: loadPlaces } = useHighlightPlaces();
/** 고른 곳 — 주소의 `?place=`(없으면 home) */
const place = computed(() => UtilHighlight.placeOf(route.query));
/** 지금 곳이 게시판 곳이면 그 게시판 ID(board-recommend-contract — 곳 키 `board-{boardId}`) */
const placeBoardId = computed(() => UtilHighlight.boardIdOf(place.value));

/**
 * 메뉴 항목 — 일반 곳(홈·팁·가젯 페이지) 다음에 게시판 곳 묶음. 목록을 못 받았으면 지금 곳 하나(키 그대로).
 * 지금 곳이 목록에 없는 게시판 곳(「+ 게시판 곳 추가」로 막 연 곳)이면 게시판 이름으로 묶음 끝에 붙인다.
 */
const navItems = computed<HighlightPlace[]>(() => {
    const boardId = placeBoardId.value;
    const base: HighlightPlace[] = places.value.length || boardId ? places.value : [{ key: place.value, name: place.value, kind: 'HOME', total: 0 }];
    const boardPlaces = base.filter((item) => item.kind === 'BOARD');
    if (boardId && !boardPlaces.some((item) => item.key === place.value)) boardPlaces.push({ key: place.value, name: boardName(boardId), kind: 'BOARD', total: 0, autoFill: null });
    return [...base.filter((item) => item.kind !== 'BOARD'), ...boardPlaces];
});

/** 게시판 곳 줄 이름 — 지금 곳 목록 응답의 게시판 이름 → 받은 게시판 목록의 이름 → 「게시판 #ID」 */
function boardName(boardId: string): string {
    if (list.value?.board?.boardId === boardId) return list.value.board.name;
    const picked = boardPlaces.value?.find((b) => b.boardId === boardId);
    if (picked) return picked.name;
    const board = boards.value.find((b) => b.id === boardId);
    return board ? localizedName(board.name, board.nameEn) : t('highlight.boardFallbackName', { id: boardId });
}

/** 곳 메뉴의 개수 — 고른 곳은 지금 목록의 수(만들기·지우기 직후에도 맞게), 나머지는 곳 목록이 준 수 */
function placeTotal(item: HighlightPlace): number {
    return item.key === place.value && list.value && list.value.place === place.value ? list.value.total : (item.total ?? 0);
}

function placeIcon(kind: HighlightPlaceKind): string {
    if (kind === 'BOARD') return 'pi pi-list';
    return kind === 'HOME' ? 'pi pi-home' : kind === 'TIPS' ? 'pi pi-book' : 'pi pi-th-large';
}

function changePlace(next: string | null): void {
    if (!next || next === place.value) return;
    void router.replace({ query: { ...route.query, place: UtilHighlight.placeQuery(next) } });
}

/** 바꾸기 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('highlight.manage'));
/** 글의 찬반 토론(설정 대화창·의견 서랍) — 글 권한. 보기 `post.read`, 바꾸기 `post.manage`(post-debate-contract 범위 변경 2026-10-24) */
const canReadPost = computed(() => can('post.read'));
const canManagePost = computed(() => can('post.manage'));
const canReadBoards = computed(() => can('board.read'));
/** 「+ 게시판 곳 추가」 — 바꾸기 권한(고를 게시판 목록은 하이라이트 API 가 준다) */
const canAddBoardPlace = computed(() => canManage.value);

/** 이미 바뀐 하이라이트(다른 운영자가 지웠거나 순서가 바뀌었다) — 목록을 다시 받는다 */
const STALE_CODES: ReadonlySet<string> = new Set(['HIGHLIGHT_NOT_FOUND']);

const list = ref<HighlightListView | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const ordering = ref(false);
const togglingId = ref<string | null>(null);
const removingId = ref<string | null>(null);

/* ── 게시판 곳(board-recommend-contract) ── */
/** 「모자라면 추천 많은 글로 자동 채우기」 — 게시판 곳 목록을 받을 때 채운다(기본 켬) */
const autoFill = ref(true);
const autoFillBusy = ref(false);

/** 자동 채우기 켜기·끄기 — 실패하면 되돌린다. 끈 게시판은 곳 목록에 남으므로 곳 목록을 새로 받는다 */
async function changeAutoFill(value: boolean): Promise<void> {
    if (autoFillBusy.value || !placeBoardId.value) return;
    const target = place.value;
    const before = autoFill.value;
    autoFill.value = value;
    autoFillBusy.value = true;
    try {
        const saved = await api.setAutoFill(target, { autoFill: value });
        if (place.value === target) autoFill.value = saved.autoFill;
        toast.success(value ? t('highlight.autoFillOn') : t('highlight.autoFillOff'));
        void loadPlaces(true);
    } catch (e) {
        if (place.value === target) autoFill.value = before;
        toast.error(errorOf(e).message, 8000);
    } finally {
        autoFillBusy.value = false;
    }
}

/** 게시판 곳에서 글을 넣거나 뺐다 — 게시판 묶음(고른 글이 있는 게시판만 남는다)을 맞추려 곳 목록을 새로 받는다 */
function refreshBoardPlaces(): void {
    if (placeBoardId.value) void loadPlaces(true);
}

/** 게시판 곳 추가 — 일반 게시판만(서버 `GET /boards` — 꺼진 게시판은 「꺼짐」을 붙여 보인다). 대화상자를 처음 열 때 받는다 */
const boardPickerOpen = ref(false);
const boardPick = ref<string | null>(null);
const boardPlaces = ref<HighlightBoardPlace[] | null>(null);
const boardPlacesBusy = ref(false);
const boardPlacesError = ref('');
const generalBoardOptions = computed(() =>
    (boardPlaces.value ?? []).map((board) => ({ value: board.boardId, label: board.active ? board.name : t('highlight.boardOffName', { name: board.name }) })),
);

async function loadBoardPlaces(): Promise<void> {
    if (boardPlacesBusy.value) return;
    boardPlacesBusy.value = true;
    boardPlacesError.value = '';
    try {
        boardPlaces.value = await api.boards();
    } catch (e) {
        boardPlacesError.value = errorOf(e).message;
    } finally {
        boardPlacesBusy.value = false;
    }
}

function openBoardPicker(): void {
    boardPick.value = placeBoardId.value;
    boardPickerOpen.value = true;
    if (!boardPlaces.value) void loadBoardPlaces();
}

function submitBoardPicker(): void {
    if (!boardPick.value) return;
    boardPickerOpen.value = false;
    changePlace(UtilHighlight.boardPlace(boardPick.value));
}

onMounted(() => {
    void reload();
    // 가젯 페이지가 새로 생겼을 수 있어 들어올 때마다 새로 받는다. 목록에 없는 곳이면 home 으로
    void loadPlaces(true).then(() => {
        if (UtilHighlight.unknownPlace(place.value, places.value)) changePlace(HIGHLIGHT_PLACE_HOME);
    });
    if (canReadBoards.value) void loadBoards();
});

// 곳을 바꾸면 그 곳의 목록을 받고, 앞 곳의 후보는 지운다(후보는 「그 곳에 없는 글」이라 곳마다 다르다)
watch(place, () => {
    list.value = null;
    candidates.value = null;
    candidatesError.value = '';
    void reload();
});

let request = 0;
async function reload(): Promise<void> {
    const mine = ++request;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list(place.value);
        if (mine !== request) return;
        list.value = found;
        // 게시판 곳의 자동 채우기 — 목록 응답 `board`(없으면 곳 목록)의 값, 둘 다 없으면 기본 켬
        autoFill.value = found.board?.autoFill ?? places.value.find((item) => item.key === found.place)?.autoFill ?? true;
        // 의견 서랍이 열린 하이라이트의 줄도 새 값으로
        if (opinionsTarget.value) opinionsTarget.value = found.items.find((item) => item.id === opinionsTarget.value?.id) ?? opinionsTarget.value;
    } catch (e) {
        if (mine !== request) return;
        loadError.value = errorOf(e);
        if (list.value) toast.error(loadError.value.message, 8000);
    } finally {
        if (mine === request) busy.value = false;
    }
}

/** 의견을 바꾼 뒤 수만 맞춘다 — 실패는 조용히 */
function reloadQuietly(): void {
    void reload();
}

/** 처리 실패 — 이미 바뀐 하이라이트면 목록을 다시 받는다 */
function fail(e: unknown): void {
    const error = errorOf(e);
    toast.error(error.fields?.ids ?? error.fields?.visible ?? error.message, 8000);
    if (STALE_CODES.has(error.code) || error.fields?.ids) void reload();
}

async function move(item: HighlightItem, delta: -1 | 1): Promise<void> {
    if (!list.value || ordering.value) return;
    const next = UtilHighlight.moved(
        list.value.items.map((row) => row.id),
        item.id,
        delta,
    );
    if (!next) return;
    ordering.value = true;
    try {
        await api.reorder(place.value, next);
        await reload();
    } catch (e) {
        fail(e);
    } finally {
        ordering.value = false;
    }
}

async function setVisible(item: HighlightItem, visible: boolean): Promise<void> {
    if (togglingId.value) return;
    togglingId.value = item.id;
    try {
        const updated = await api.setVisible(item.id, visible);
        replace(updated);
        toast.success(visible ? t('highlight.shown') : t('highlight.hidden'));
        void reload();
    } catch (e) {
        fail(e);
    } finally {
        togglingId.value = null;
    }
}

function replace(updated: HighlightItem): void {
    if (!list.value) return;
    list.value = { ...list.value, items: list.value.items.map((row) => (row.id === updated.id ? updated : row)) };
}

async function remove(item: HighlightItem): Promise<void> {
    const ok = await confirm({ title: t('highlight.deleteTitle'), message: t('highlight.deleteMessage', { title: UtilHighlight.displayTitle(item) }), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    removingId.value = item.id;
    try {
        await api.remove(item.id);
        toast.success(t('highlight.deleted'));
        refreshBoardPlaces();
        await reload();
        if (candidates.value) void loadCandidates();
    } catch (e) {
        fail(e);
    } finally {
        removingId.value = null;
    }
}

/* ── 만들기·고치기 ── */
const formOpen = ref(false);
const editing = ref<HighlightItem | null>(null);
const saving = ref(false);
const formFieldErrors = ref<Record<string, string>>({});
const formError = ref('');

/** 줄에 보일 입장 — 중립은 받거나 쌓인 표·의견이 있을 때만 */
function stancesOf(item: HighlightItem): DebateStance[] {
    return UtilHighlight.STANCES.filter((stance) => stance !== 'NEUTRAL' || UtilHighlight.showNeutral(item));
}

const formActions: HighlightFormActions = {
    searchPosts: (q, signal) => api.searchPosts(q, place.value, signal),
    uploadImage: async (file) => (await fileApi.upload(file, 'HIGHLIGHT_IMAGE')).fileId,
};

function openCreate(): void {
    editing.value = null;
    formFieldErrors.value = {};
    formError.value = '';
    formOpen.value = true;
}

function openEdit(item: HighlightItem): void {
    editing.value = item;
    formFieldErrors.value = {};
    formError.value = '';
    formOpen.value = true;
}

async function save(body: HighlightSaveRequest): Promise<void> {
    if (saving.value) return;
    saving.value = true;
    formFieldErrors.value = {};
    formError.value = '';
    const target = editing.value;
    try {
        if (target) await api.update(target.id, body);
        else await api.create({ ...body, place: place.value });
        formOpen.value = false;
        toast.success(target ? t('highlight.updated') : t('highlight.created'));
        if (!target) refreshBoardPlaces();
        await reload();
        if (!target && candidates.value) void loadCandidates();
    } catch (e) {
        const error = errorOf(e);
        if (error.code === 'HIGHLIGHT_NOT_FOUND') {
            formOpen.value = false;
            fail(error);
        } else if (error.fields && Object.keys(error.fields).length) {
            // 글 오류(`HIGHLIGHT_POST_INVALID`)도 `fields.postId` 로 까닭이 온다
            formFieldErrors.value = { ...error.fields };
            const known = ['postId', 'title', 'summary', 'showUntil', 'showFrom', 'imageFileId'];
            formError.value = Object.entries(error.fields)
                .filter(([key]) => !known.includes(key))
                .map(([, message]) => message)
                .join(' ');
            // 기간 시작(`showFrom`)은 따로 칸이 없어 아래 한 줄로
            if (error.fields.showFrom) formError.value = [formError.value, error.fields.showFrom].filter(Boolean).join(' ');
        } else {
            // 409 HIGHLIGHT_DUPLICATE 등 — 서버 문구 그대로
            formError.value = error.message;
        }
    } finally {
        saving.value = false;
    }
}

/* ── 의견 관리 ── */
const opinionsOpen = ref(false);
const opinionsTarget = ref<HighlightItem | null>(null);

const opinionActions = useDebateOpinionActions();

/** 서랍이 다룰 글 — 하이라이트 줄에서 글 번호·제목·입장별 의견 수 */
/** 글 서랍에서 연 의견 서랍의 대상(하이라이트 줄 대신 열린 글) — 줄에서 열면 비운다 */
const opinionsPostTarget = ref<DebateDrawerTarget | null>(null);
const drawerTarget = computed<DebateDrawerTarget | null>(() => {
    if (opinionsPostTarget.value) return opinionsPostTarget.value;
    const item = opinionsTarget.value;
    if (!item?.post) return null;
    const counts = item.commentCounts;
    return { postId: item.post.id, title: UtilHighlight.displayTitle(item), neutralEnabled: item.neutralEnabled, counts: { pro: counts.pro, con: counts.con, neutral: counts.neutral } };
});

function openOpinions(item: HighlightItem): void {
    opinionsPostTarget.value = null;
    opinionsTarget.value = item;
    opinionsOpen.value = true;
}

/* ── 글의 토론 설정 ── */
const debateSettingsActions = useDebateSettingsActions();
const debateSettingsOpen = ref(false);
const debateSettingsTarget = ref<HighlightItem | null>(null);
/** 글 서랍에서 연 토론 설정의 글(줄에서 열면 비운다) */
const debateSettingsPost = ref<{ id: string; title: string } | null>(null);

function openDebateSettings(item: HighlightItem): void {
    debateSettingsPost.value = null;
    debateSettingsTarget.value = item;
    debateSettingsOpen.value = true;
}

/** 토론 설정·의견을 바꿨다 — 하이라이트 줄의 수와, 글 서랍이 열려 있으면 그 글·토론 요약을 조용히 새로 받는다 */
function onDebateChanged(): void {
    reloadQuietly();
    if (drawerOpen.value && detail.openId.value) {
        void loadPostDebate(true);
        void detail.reload();
    }
}

/* ── 글 서랍(게시글 관리와 같은 `PostDetailDrawer`) ── */
const canRestorePost = computed(() => can('post.restore'));
const canReadReports = computed(() => can('report.read'));

const detail = usePostDetail();
const drawerOpen = ref(false);
/** 글 조치 — 끝나면 하이라이트 목록(글 상태 딱지)과 열린 글을 새로 받는다 */
const postFlow = usePostActionFlow({
    after: async (id) => {
        await reload();
        if (detail.openId.value === id) await detail.reload();
    },
});

/** 글을 열 수 있는 줄 — 글이 남아 있고 글 보기 권한이 있을 때 */
function canOpenPost(item: HighlightItem): boolean {
    return canReadPost.value && item.post !== null;
}

/** 머리를 바로 그릴 글 — 받은 상세(하이라이트 줄에는 관리 목록 줄 모양이 없어 받기 전에는 비운다) */
const postDrawerItem = computed<AdminPostItem | null>(() => detail.detail.value?.post ?? null);
/** 이전/다음 — 지금 곳의 하이라이트 차례(글이 지워진 줄은 건너뛴다) */
const postNeighbors = computed(() =>
    UtilPost.neighbors(
        (list.value?.items ?? []).flatMap((item) => (item.post ? [{ id: item.post.id }] : [])),
        detail.openId.value,
    ),
);
const postDrawerActions = computed(() => (detail.detail.value ? UtilPost.actionsOf(detail.detail.value.post, { manage: canManagePost.value, restore: canRestorePost.value }) : UtilPost.noActions()));

/** `?post=` 만 바꾼다(replace — 곳·목록은 그대로) */
function setPostQuery(id: string | null): void {
    void router.replace({ query: { ...route.query, post: id ?? undefined } });
}

function openPost(id: string): void {
    if (!canReadPost.value) return;
    drawerOpen.value = true;
    if (detail.openId.value !== id) void detail.open(id);
    if (postFlow.boards.value.length === 0 && !postFlow.optionsLoading.value) void postFlow.loadOptions();
    setPostQuery(id);
}

/** 줄 내용 영역을 눌렀다 — 안의 단추·링크(글 번호 복사 등)를 누른 것은 그 단추의 일 */
function onContentClick(item: HighlightItem, event: MouseEvent): void {
    if (!item.post || !canOpenPost(item)) return;
    const target = event.target as HTMLElement | null;
    if (target?.closest('button, a, input, label, [role="switch"]')) return;
    openPost(item.post.id);
}

function onDrawerAct(kind: PostActionKind): void {
    const item = detail.detail.value?.post;
    if (item) postFlow.openAct(item, kind);
}

function onDrawerPollDelete(): void {
    const item = detail.detail.value?.post;
    if (item) postFlow.openPollDelete(item);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    detail.close();
    postDebate.value = null;
    postDebateError.value = '';
}

// 서랍이 닫히면(×·Esc·바깥) 주소의 `post` 만 지운다
watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilPost.parsePost(route.query) !== null) setPostQuery(null);
});

// 주소의 `post`(새로고침·뒤로 가기)를 따라 서랍을 연다
watch(
    () => UtilPost.parsePost(route.query),
    (id) => {
        if (id && canReadPost.value && (id !== detail.openId.value || !drawerOpen.value)) {
            drawerOpen.value = true;
            if (id !== detail.openId.value) void detail.open(id);
            if (postFlow.boards.value.length === 0 && !postFlow.optionsLoading.value) void postFlow.loadOptions();
        } else if (!id && drawerOpen.value) {
            drawerOpen.value = false;
        }
    },
    { immediate: true },
);

/* 서랍의 토론 구획 — 게시글 관리와 같은 읽기(`useDebateSettingsActions().get`) */
const postDebate = ref<DebateItem | null>(null);
const postDebateLoading = ref(false);
const postDebateError = ref('');
let postDebateTicket = 0;

async function loadPostDebate(silent = false): Promise<void> {
    const post = detail.detail.value?.post;
    const mine = ++postDebateTicket;
    if (!silent) {
        postDebate.value = null;
        postDebateError.value = '';
        postDebateLoading.value = false;
    }
    if (!post) return;
    if (!post.debate && !silent) return;
    postDebateLoading.value = true;
    try {
        const found = await debateSettingsActions.get(post.id);
        if (mine === postDebateTicket) {
            postDebate.value = found;
            postDebateError.value = '';
        }
    } catch (e) {
        if (mine !== postDebateTicket) return;
        const error = errorOf(e);
        if (error.code === 'DEBATE_NOT_FOUND') {
            postDebate.value = null;
            postDebateError.value = '';
        } else if (!silent) postDebateError.value = error.message;
    } finally {
        if (mine === postDebateTicket) postDebateLoading.value = false;
    }
}

// 글을 받으면(다른 글 · 토론 열림이 바뀜) 토론 요약을 읽는다
watch(
    () => [detail.detail.value?.post.id ?? null, detail.detail.value?.post.debate ?? null] as const,
    ([id], [prevId]) => void loadPostDebate(id === prevId && id !== null),
);

function drawerPostTitle(): string {
    const post = detail.detail.value?.post;
    return post ? UtilPostMessages.title(t, post) : `#${detail.openId.value ?? ''}`;
}

function openDrawerDebateSettings(): void {
    const id = detail.openId.value;
    if (!id) return;
    debateSettingsTarget.value = null;
    debateSettingsPost.value = { id, title: drawerPostTitle() };
    debateSettingsOpen.value = true;
}

function openDrawerOpinions(): void {
    const id = detail.openId.value;
    if (!id) return;
    opinionsTarget.value = null;
    opinionsPostTarget.value = { postId: id, title: drawerPostTitle(), neutralEnabled: postDebate.value?.neutralEnabled ?? false, counts: null };
    opinionsOpen.value = true;
}

/* ── 후보 ── */
const candidateQuery = ref<Omit<HighlightCandidateQuery, 'place'>>({ days: UtilHighlight.DAYS_DEFAULT, minLikes: UtilHighlight.MIN_LIKES_DEFAULT, boardId: null });
const minLikesText = ref(String(UtilHighlight.MIN_LIKES_DEFAULT));
const candidates = ref<AdminPostRef[] | null>(null);
const candidatesBusy = ref(false);
const candidatesError = ref('');
const sendingId = ref<string | null>(null);
const boards = ref<BoardListItem[]>([]);

const dayOptions = computed(() => UtilHighlight.DAY_OPTIONS.map((value) => ({ value, label: UtilHighlightMessages.days(t, value) })));
const boardOptions = computed(() => boards.value.filter((board) => board.active).map((board) => ({ value: board.id, label: localizedName(board.name, board.nameEn) })));
const minLikesError = computed(() => UtilHighlightMessages.candidateProblem(t, UtilHighlight.minLikesProblem(minLikesText.value)));

async function loadBoards(): Promise<void> {
    try {
        boards.value = (await boardApi.list()).boards;
    } catch {
        // 게시판 고르개만 비운다 — 후보는 전체 게시판으로 찾을 수 있다
        boards.value = [];
    }
}

let candidateRequest = 0;
async function loadCandidates(): Promise<void> {
    if (minLikesError.value) return;
    const mine = ++candidateRequest;
    candidatesBusy.value = true;
    candidatesError.value = '';
    try {
        // 게시판 곳이면 그 게시판 글만
        const found = await api.candidates({ ...candidateQuery.value, boardId: placeBoardId.value ?? candidateQuery.value.boardId, minLikes: Number(minLikesText.value.trim()), place: place.value });
        if (mine !== candidateRequest) return;
        candidates.value = found.items;
    } catch (e) {
        if (mine !== candidateRequest) return;
        const error = errorOf(e);
        candidatesError.value = error.fields ? Object.values(error.fields).join(' ') : error.message;
    } finally {
        if (mine === candidateRequest) candidatesBusy.value = false;
    }
}

async function sendCandidate(post: AdminPostRef): Promise<void> {
    if (sendingId.value) return;
    sendingId.value = post.id;
    try {
        await api.create({ postId: post.id, title: null, summary: null, imageFileId: null, visible: null, showFrom: null, showUntil: null, debate: null, neutralEnabled: null, stanceChangeAllowed: null, place: place.value });
        toast.success(t('highlight.sent', { title: post.title }));
        refreshBoardPlaces();
        await reload();
        void loadCandidates();
    } catch (e) {
        const error = errorOf(e);
        toast.error(error.fields?.postId ?? error.message, 8000);
        void loadCandidates();
    } finally {
        sendingId.value = null;
    }
}
</script>
