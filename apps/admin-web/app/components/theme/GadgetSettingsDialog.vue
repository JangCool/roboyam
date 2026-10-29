<template>
    <!--
        가젯 설정 창(⚙) — 홈·가젯 페이지 편집기(`HomeLayoutEditor`)가 연다. 8종 전부(2026-10-30). 구획 셋(탭):
          · 「내용」 종류별 설정(`config` — 칸·범위·처음 값은 `UtilGadgetConfig`):
              공동구매 — 보일 개수(1~10) · 정렬 / 글 목록 — 대상 게시판 · 개수(1~30) · 정렬 · 인기일 때 기간 탭 + 보기 모양(줄 모양·열·사진·제목 줄·큰 글·줄 정보·구분선·NEW·HOT) /
              갤러리 — 대상 게시판 · 개수(1~24) / 게시판 카드 — 카드로 보일 게시판 · 카드마다 글 수 · 열 수 · 카드 머리 /
              하이라이트 — 개수(1~6) · 큰 글 · 사진(노출할 글은 「하이라이트 관리」) / 인기 소모임 — 개수(1~6) / 바로가기 — 모양 · 항목(12개까지) / 안내 글 — 글(300자) · 모양
          · 「모양」 제목(1~40자) · 부제 · 아이콘 · 머리 모양 · 제목 크기·색 · 카드 모양·색(`style` — `UtilGadgetStyle`)
          · 「노출」 PC/모바일(둘 다 끌 수 없다) · 모바일 접기(머리 숨김이면 못 고름) · 비었을 때(자리 남기기 · 안내 · 안내 문구)
        대상 게시판: 전체 게시판(홈) · 「이 페이지가 연결된 폴더의 게시판 전체」(가젯 페이지) · 「지금 보는 게시판」(게시판 레이아웃) · 직접 고르기(여러 개, 고른 차례 — 20개까지). 게시판 카드의 「전체」는 서버가 켜진 일반 게시판 앞 6개.
        값은 관리자 응답의 꾸미기·설정(칸 전부 — 서버가 저장 값, 없으면 테마 기본·처음 값을 준다)으로 채우고, 없으면(저장 전 새 가젯) 처음 값으로 채운다.
        [적용]은 편집 중인 값에만 넣는다(저장은 편집기 저장 줄). 꾸미기·내용은 바뀌었을 때만 넘긴다.
        지난 저장 실패의 이 가젯 칸 오류(`serverErrors` — `fields.gadgets[i].` 뒤의 경로)는 탭 점과 칸 아래(제목 · 노출 · 바로가기 항목), 나머지는 탭 위 한 줄에 보인다. [적용]을 막지는 않는다(판정은 서버).
        고를 게시판 목록은 메뉴 대상 고르기 값(`useGadgetPageFolders`)이다.
    -->
    <Dialog :visible="visible" modal :header="t('homeLayout.settingsTitle', { title: gadget?.title ?? '' })" :draggable="false" :style="{ width: '40rem', maxWidth: '94vw' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="draft && look && spec && gadget" class="flex flex-col gap-4" novalidate @submit.prevent="apply">
            <Message v-if="serverList.length" severity="error" variant="simple" size="small" :closable="false" role="alert" data-testid="gadget-settings-server-errors">
                {{ serverList.join(' · ') }}
            </Message>
            <Tabs v-model:value="tab">
                <TabList>
                    <Tab value="content" data-testid="gadget-settings-tab-content">
                        {{ t('homeLayout.tabContent') }}<span v-if="(submitted && contentInvalid) || serverTabs.has('content')" class="ml-1.5 inline-block size-1.5 rounded-full bg-red-500 align-middle" aria-hidden="true" />
                    </Tab>
                    <Tab value="look" data-testid="gadget-settings-tab-look">
                        {{ t('homeLayout.tabLook') }}<span v-if="(submitted && lookInvalid) || serverTabs.has('look')" class="ml-1.5 inline-block size-1.5 rounded-full bg-red-500 align-middle" aria-hidden="true" />
                    </Tab>
                    <Tab value="show" data-testid="gadget-settings-tab-show">
                        {{ t('homeLayout.tabShow') }}<span v-if="(submitted && showInvalid) || serverTabs.has('show')" class="ml-1.5 inline-block size-1.5 rounded-full bg-red-500 align-middle" aria-hidden="true" />
                    </Tab>
                </TabList>
                <TabPanels class="px-0! pb-0!">
                    <!-- ── 내용 ── -->
                    <TabPanel value="content">
                        <div class="flex flex-col gap-4">
                            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t(`homeLayout.settingsLead_${gadget.type}`) }}</p>

                            <!-- 하이라이트 — 노출할 글은 하이라이트 관리 몫 -->
                            <p v-if="gadget.type === 'HIGHLIGHT'" class="m-0 text-xs leading-relaxed text-muted-color" data-testid="gadget-highlight-note">
                                {{ t('homeLayout.highlightNote') }}
                                <NuxtLink v-if="can('highlight.read')" to="/content/highlights" target="_blank" class="text-primary hover:underline">{{ t('homeLayout.highlightManage') }} ↗</NuxtLink>
                            </p>

                            <!-- 대상 게시판 -->
                            <div v-if="spec.boards" class="flex flex-col gap-1.5">
                                <span :id="`${uid}-scope`" class="text-sm font-medium">{{ t(gadget.type === 'BOARD_CARDS' ? 'homeLayout.settingsCardBoards' : 'homeLayout.settingsBoards') }}</span>
                                <SelectButton v-model="draft.boardScope" :options="scopeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-scope`" class="self-start" />
                                <p v-if="draft.boardScope === 'FOLDER'" class="m-0 text-xs leading-relaxed text-muted-color" data-testid="gadget-scope-folder">
                                    <template v-if="folder?.name">{{ t('homeLayout.scopeFolderNote', { folder: folder.name, boards: folderBoardNames || t('homeLayout.scopeFolderEmpty') }) }}</template>
                                    <template v-else>{{ t('homeLayout.scopeFolderNone') }}</template>
                                </p>
                                <p v-else-if="draft.boardScope === 'BOARD'" class="m-0 text-xs leading-relaxed text-muted-color" data-testid="gadget-scope-board">
                                    {{ t(gadget.type === 'BOARD_CARDS' ? 'homeLayout.scopeBoardCards' : 'homeLayout.scopeBoardNote') }}
                                </p>
                                <p v-else-if="draft.boardScope === 'ALL'" class="m-0 text-xs leading-relaxed text-muted-color">
                                    {{ t(gadget.type === 'BOARD_CARDS' ? 'homeLayout.scopeAllCards' : 'homeLayout.scopeAllNote') }}
                                </p>
                                <template v-else>
                                    <AppMultiSelect
                                        v-model="pickedModel"
                                        :options="boardOptions"
                                        :input-id="`${uid}-boards`"
                                        filter
                                        :filter-placeholder="t('homeLayout.boardSearch')"
                                        :placeholder="t('homeLayout.boardPickPlaceholder')"
                                        :aria-label="t('homeLayout.settingsBoards')"
                                        :invalid="Boolean(boardError)"
                                        :max-selected-labels="6"
                                        :selected-items-label="t('homeLayout.boardPicked')"
                                        :empty-message="boardsLoading ? t('common.loading') : boardsError || t('homeLayout.boardEmpty')"
                                    />
                                    <small class="text-xs text-muted-color">{{ t('homeLayout.boardPickHint', { max: UtilGadgetConfig.BOARD_PICK_MAX }) }}</small>
                                    <p v-if="boardError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ boardError }}</p>
                                </template>
                            </div>

                            <!-- 개수 -->
                            <div v-if="spec.count" class="flex flex-col gap-1">
                                <label :for="`${uid}-count`" class="text-sm font-medium">{{ t(`homeLayout.settingsCount_${gadget.type}`) }}</label>
                                <InputNumber
                                    v-model="countModel"
                                    :input-id="`${uid}-count`"
                                    :min="spec.count.min"
                                    :max="spec.count.max"
                                    show-buttons
                                    button-layout="horizontal"
                                    :allow-empty="false"
                                    :aria-describedby="`${uid}-count-hint`"
                                    class="w-40"
                                    input-class="w-16 text-center"
                                />
                                <small :id="`${uid}-count-hint`" class="text-xs text-muted-color">{{ t('homeLayout.settingsCountHint', { min: spec.count.min, max: spec.count.max }) }}</small>
                            </div>

                            <!-- 정렬 -->
                            <div v-if="spec.sorts.length" class="flex flex-col gap-1">
                                <span :id="`${uid}-sort`" class="text-sm font-medium">{{ t('homeLayout.settingsSort') }}</span>
                                <SelectButton v-if="spec.sorts.length <= 3" v-model="draft.sort" :options="sortOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-sort`" />
                                <Select v-else v-model="draft.sort" :options="sortOptions" option-label="label" option-value="value" :aria-labelledby="`${uid}-sort`" class="w-56" />
                            </div>

                            <!-- 인기글 기간 탭 -->
                            <div v-if="gadget.type === 'POST_LIST' && draft.sort === 'POPULAR'" class="flex items-center gap-2">
                                <ToggleSwitch v-model="periodModel" :input-id="`${uid}-period`" />
                                <label :for="`${uid}-period`" class="cursor-pointer text-sm">{{ t('homeLayout.settingsPeriodTabs') }}</label>
                            </div>

                            <!-- 글 목록 보기 모양 -->
                            <section v-if="gadget.type === 'POST_LIST'" class="flex flex-col gap-4 border-t border-(--line) pt-4" :aria-labelledby="`${uid}-view`">
                                <h3 :id="`${uid}-view`" class="m-0 text-sm font-semibold">{{ t('homeLayout.postViewTitle') }}</h3>
                                <div class="grid gap-4 sm:grid-cols-2">
                                    <div class="flex flex-col gap-1">
                                        <label :for="`${uid}-row`" class="text-sm font-medium">{{ t('homeLayout.rowStyle') }}</label>
                                        <Select v-model="draft.rowStyle" :input-id="`${uid}-row`" :options="optionsOf(UtilGadgetConfig.ROW_STYLES, 'rowStyle')" option-label="label" option-value="value" fluid />
                                    </div>
                                    <div class="flex flex-col gap-1">
                                        <label :for="`${uid}-thumb`" class="text-sm font-medium">{{ t('homeLayout.thumbnail') }}</label>
                                        <Select v-model="draft.thumbnail" :input-id="`${uid}-thumb`" :options="optionsOf(UtilGadgetConfig.THUMBNAILS, 'thumbnail')" option-label="label" option-value="value" fluid />
                                    </div>
                                    <div class="flex flex-col gap-1">
                                        <span :id="`${uid}-cols`" class="text-sm font-medium">{{ t('homeLayout.postColumns') }}</span>
                                        <SelectButton v-model="draft.columns" :options="optionsOf(UtilGadgetConfig.POST_COLUMNS, 'postColumns')" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-cols`" />
                                    </div>
                                    <div class="flex flex-col gap-1">
                                        <span :id="`${uid}-lines`" class="text-sm font-medium">{{ t('homeLayout.titleLines') }}</span>
                                        <SelectButton v-model="draft.titleLines" :options="optionsOf(UtilGadgetConfig.TITLE_LINES, 'titleLines')" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-lines`" />
                                    </div>
                                    <div class="flex flex-col gap-1">
                                        <span :id="`${uid}-lead`" class="text-sm font-medium">{{ t('homeLayout.postLead') }}</span>
                                        <SelectButton v-model="draft.lead" :options="optionsOf(UtilGadgetConfig.POST_LEADS, 'postLead')" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-lead`" />
                                    </div>
                                    <div class="flex items-center gap-2 self-end pb-1">
                                        <ToggleSwitch v-model="dividersModel" :input-id="`${uid}-div`" />
                                        <label :for="`${uid}-div`" class="cursor-pointer text-sm">{{ t('homeLayout.dividers') }}</label>
                                    </div>
                                </div>
                                <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0">
                                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('homeLayout.meta') }}</legend>
                                    <div class="flex flex-wrap gap-x-4 gap-y-2">
                                        <div v-for="m in UtilGadgetConfig.META" :key="m" class="flex items-center gap-1.5">
                                            <Checkbox v-model="metaModel" :value="m" :input-id="`${uid}-meta-${m}`" />
                                            <label :for="`${uid}-meta-${m}`" class="cursor-pointer text-sm">{{ t(`homeLayout.meta_${m}`) }}</label>
                                        </div>
                                    </div>
                                </fieldset>
                                <div class="grid gap-4 sm:grid-cols-2">
                                    <div class="flex flex-col gap-1">
                                        <label :for="`${uid}-new`" class="text-sm font-medium">{{ t('homeLayout.newBadgeHours') }}</label>
                                        <InputNumber
                                            :model-value="draft.newBadgeHours ?? 0"
                                            :input-id="`${uid}-new`"
                                            :min="UtilGadgetConfig.NEW_BADGE.min"
                                            :max="UtilGadgetConfig.NEW_BADGE.max"
                                            show-buttons
                                            button-layout="horizontal"
                                            :allow-empty="false"
                                            :aria-describedby="`${uid}-new-hint`"
                                            class="w-40"
                                            input-class="w-16 text-center"
                                            @update:model-value="(v: number | null) => draft && (draft.newBadgeHours = v ?? 0)"
                                        />
                                        <small :id="`${uid}-new-hint`" class="text-xs text-muted-color">{{ t('homeLayout.newBadgeHint', { max: UtilGadgetConfig.NEW_BADGE.max }) }}</small>
                                    </div>
                                    <div class="flex flex-col gap-1">
                                        <label :for="`${uid}-hot`" class="text-sm font-medium">{{ t('homeLayout.hotBadgeLikes') }}</label>
                                        <InputNumber
                                            :model-value="draft.hotBadgeLikes ?? 0"
                                            :input-id="`${uid}-hot`"
                                            :min="UtilGadgetConfig.HOT_BADGE.min"
                                            :max="UtilGadgetConfig.HOT_BADGE.max"
                                            :allow-empty="false"
                                            :aria-describedby="`${uid}-hot-hint`"
                                            class="w-40"
                                            input-class="w-full"
                                            @update:model-value="(v: number | null) => draft && (draft.hotBadgeLikes = v ?? 0)"
                                        />
                                        <small :id="`${uid}-hot-hint`" class="text-xs text-muted-color">{{ t('homeLayout.hotBadgeHint', { max: UtilGadgetConfig.HOT_BADGE.max }) }}</small>
                                    </div>
                                </div>
                            </section>

                            <!-- 게시판 카드 — 열 수 · 카드 머리 -->
                            <template v-if="gadget.type === 'BOARD_CARDS'">
                                <div class="flex flex-col gap-1">
                                    <span :id="`${uid}-ccols`" class="text-sm font-medium">{{ t('homeLayout.cardColumns') }}</span>
                                    <SelectButton v-model="draft.columns" :options="optionsOf(UtilGadgetConfig.CARD_COLUMNS, 'cardColumns')" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-ccols`" />
                                </div>
                                <div class="flex flex-col gap-1">
                                    <div class="flex items-center gap-2">
                                        <ToggleSwitch v-model="cardHeadModel" :input-id="`${uid}-head`" />
                                        <label :for="`${uid}-head`" class="cursor-pointer text-sm">{{ t('homeLayout.settingsCardHead') }}</label>
                                    </div>
                                    <small class="text-xs text-muted-color">{{ t('homeLayout.settingsCardHeadHint') }}</small>
                                </div>
                            </template>

                            <!-- 하이라이트 — 큰 글 · 사진 -->
                            <template v-if="gadget.type === 'HIGHLIGHT'">
                                <div class="flex flex-col gap-1">
                                    <span :id="`${uid}-hlead`" class="text-sm font-medium">{{ t('homeLayout.highlightLead') }}</span>
                                    <SelectButton v-model="draft.lead" :options="optionsOf(UtilGadgetConfig.HIGHLIGHT_LEADS, 'highlightLead')" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-hlead`" />
                                </div>
                                <div class="flex flex-col gap-1">
                                    <span :id="`${uid}-hthumbs`" class="text-sm font-medium">{{ t('homeLayout.highlightThumbs') }}</span>
                                    <SelectButton v-model="draft.thumbs" :options="optionsOf(UtilGadgetConfig.HIGHLIGHT_THUMBS, 'highlightThumbs')" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-hthumbs`" />
                                </div>
                            </template>

                            <!-- 바로가기 — 모양 · 항목 -->
                            <template v-if="gadget.type === 'QUICK_LINKS'">
                                <div class="flex flex-col gap-1">
                                    <span :id="`${uid}-shape`" class="text-sm font-medium">{{ t('homeLayout.quickShape') }}</span>
                                    <SelectButton v-model="draft.shape" :options="optionsOf(UtilGadgetConfig.QUICK_SHAPES, 'quickShape')" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-shape`" />
                                </div>
                                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0" data-testid="gadget-quick-links">
                                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('homeLayout.quickItems', { count: links.length, max: UtilGadgetConfig.QUICK_LINK_MAX }) }}</legend>
                                    <p v-if="!links.length" class="m-0 rounded-md border border-dashed border-(--line) px-3 py-4 text-center text-xs text-muted-color">{{ t('homeLayout.quickEmpty') }}</p>
                                    <ol v-else class="m-0 flex list-none flex-col gap-2 p-0">
                                        <li v-for="(item, i) in links" :key="linkKeys[i]" class="flex flex-col gap-1.5 rounded-md border border-(--line) p-2">
                                            <div class="flex flex-wrap items-start gap-2">
                                                <InputText
                                                    :model-value="item.label"
                                                    :maxlength="UtilGadgetConfig.QUICK_LABEL_MAX"
                                                    :placeholder="t('homeLayout.quickLabel')"
                                                    :aria-label="t('homeLayout.quickLabelOf', { n: i + 1 })"
                                                    :invalid="Boolean(linkProblem(i, 'label'))"
                                                    class="w-32"
                                                    @update:model-value="(v: string | undefined) => (item.label = v ?? '')"
                                                />
                                                <Select
                                                    :model-value="item.kind"
                                                    :options="quickKindOptions"
                                                    option-label="label"
                                                    option-value="value"
                                                    :aria-label="t('homeLayout.quickKindOf', { n: i + 1 })"
                                                    class="w-36"
                                                    @update:model-value="(v: HomeQuickLinkKind) => setKind(i, v)"
                                                />
                                                <Select
                                                    v-if="item.kind === 'BOARD'"
                                                    :model-value="item.value"
                                                    :options="quickBoardOptions"
                                                    option-label="label"
                                                    option-value="value"
                                                    filter
                                                    :filter-placeholder="t('homeLayout.boardSearch')"
                                                    :placeholder="t('homeLayout.quickBoardPlaceholder')"
                                                    :empty-message="boardsLoading ? t('common.loading') : boardsError || t('homeLayout.boardEmpty')"
                                                    :aria-label="t('homeLayout.quickValueOf', { n: i + 1 })"
                                                    :invalid="Boolean(linkProblem(i, 'value'))"
                                                    class="min-w-40 flex-1"
                                                    @update:model-value="(v: string | null) => (item.value = v ?? '')"
                                                />
                                                <InputText
                                                    v-else-if="item.kind === 'PATH'"
                                                    :model-value="item.value ?? ''"
                                                    maxlength="300"
                                                    placeholder="/popular"
                                                    :aria-label="t('homeLayout.quickValueOf', { n: i + 1 })"
                                                    :invalid="Boolean(linkProblem(i, 'value'))"
                                                    class="min-w-40 flex-1 font-mono"
                                                    @update:model-value="(v: string | undefined) => (item.value = v ?? '')"
                                                />
                                                <span v-else class="min-w-40 flex-1 self-center text-xs text-muted-color">{{ t('homeLayout.quickFixedPath') }}</span>
                                                <span class="flex shrink-0 gap-0.5">
                                                    <Button type="button" icon="pi pi-arrow-up" severity="secondary" variant="text" size="small" :disabled="i === 0" :aria-label="t('homeLayout.quickUp', { n: i + 1 })" @click="moveLink(i, -1)" />
                                                    <Button type="button" icon="pi pi-arrow-down" severity="secondary" variant="text" size="small" :disabled="i === links.length - 1" :aria-label="t('homeLayout.quickDown', { n: i + 1 })" @click="moveLink(i, 1)" />
                                                    <Button type="button" icon="pi pi-trash" severity="danger" variant="text" size="small" :aria-label="t('homeLayout.quickRemove', { n: i + 1 })" @click="removeLink(i)" />
                                                </span>
                                            </div>
                                            <p v-if="linkProblem(i, 'label') || linkProblem(i, 'value')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">
                                                {{ [linkProblem(i, 'label'), linkProblem(i, 'value')].filter(Boolean).join(' ') }}
                                            </p>
                                        </li>
                                    </ol>
                                    <Button type="button" :label="t('homeLayout.quickAdd')" icon="pi pi-plus" severity="secondary" variant="outlined" size="small" class="self-start" :disabled="links.length >= UtilGadgetConfig.QUICK_LINK_MAX" @click="addLink" />
                                </fieldset>
                            </template>

                            <!-- 안내 글 — 글 · 모양 -->
                            <template v-if="gadget.type === 'NOTICE_TEXT'">
                                <div class="flex flex-col gap-1">
                                    <label :for="`${uid}-text`" class="text-sm font-medium">{{ t('homeLayout.noticeText') }}</label>
                                    <Textarea v-model="noticeModel" :id="`${uid}-text`" rows="5" auto-resize :maxlength="UtilGadgetConfig.NOTICE_MAX" :aria-describedby="`${uid}-text-hint`" fluid />
                                    <small :id="`${uid}-text-hint`" class="text-xs text-muted-color">{{ t('homeLayout.noticeTextHint', { count: noticeModel.length, max: UtilGadgetConfig.NOTICE_MAX }) }}</small>
                                </div>
                                <div class="flex flex-col gap-1">
                                    <span :id="`${uid}-format`" class="text-sm font-medium">{{ t('homeLayout.noticeFormat') }}</span>
                                    <SelectButton v-model="draft.format" :options="optionsOf(UtilGadgetConfig.NOTICE_FORMATS, 'noticeFormat')" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-format`" />
                                </div>
                            </template>

                            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(contentNoteKey) }}</p>
                        </div>
                    </TabPanel>

                    <!-- ── 모양 ── -->
                    <TabPanel value="look">
                        <div class="flex flex-col gap-4">
                            <div class="grid gap-4 sm:grid-cols-2">
                                <div class="flex flex-col gap-1">
                                    <label :for="`${uid}-title`" class="text-sm font-medium">{{ t('homeLayout.gadgetTitle') }}</label>
                                    <InputText v-model="title" :id="`${uid}-title`" :maxlength="UtilGadgetStyle.TITLE_MAX" :invalid="Boolean(titleShown)" aria-required="true" :aria-describedby="titleShown ? `${uid}-title-err` : undefined" fluid />
                                    <p v-if="titleShown" :id="`${uid}-title-err`" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ titleShown }}</p>
                                </div>
                                <div class="flex flex-col gap-1">
                                    <label :for="`${uid}-sub`" class="text-sm font-medium">{{ t('homeLayout.subtitle') }}</label>
                                    <InputText v-model="look.subtitle" :id="`${uid}-sub`" :maxlength="UtilGadgetStyle.SUBTITLE_MAX" :placeholder="t('homeLayout.optional')" fluid />
                                </div>
                                <div class="flex flex-col gap-1">
                                    <label :for="`${uid}-icon`" class="text-sm font-medium">{{ t('homeLayout.icon') }}</label>
                                    <Select v-model="iconModel" :input-id="`${uid}-icon`" :options="iconOptions" option-label="label" option-value="value" fluid>
                                        <template #value="{ value }">
                                            <span class="inline-flex items-center gap-2 text-sm"><i v-if="value !== 'NONE'" :class="['pi', value]" aria-hidden="true" />{{ iconLabel(value) }}</span>
                                        </template>
                                        <template #option="{ option }">
                                            <span class="inline-flex items-center gap-2 text-sm"><i v-if="option.value !== 'NONE'" :class="['pi', option.value]" aria-hidden="true" />{{ option.label }}</span>
                                        </template>
                                    </Select>
                                </div>
                                <div class="flex flex-col gap-1">
                                    <label :for="`${uid}-header`" class="text-sm font-medium">{{ t('homeLayout.headerStyle') }}</label>
                                    <Select v-model="look.headerStyle" :input-id="`${uid}-header`" :options="optionsOf(UtilGadgetStyle.HEADER_STYLES, 'headerStyle')" option-label="label" option-value="value" fluid />
                                </div>
                                <div class="flex flex-col gap-1">
                                    <span :id="`${uid}-tsize`" class="text-sm font-medium">{{ t('homeLayout.titleSize') }}</span>
                                    <SelectButton v-model="look.titleSize" :options="optionsOf(UtilGadgetStyle.TITLE_SIZES, 'titleSize')" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-tsize`" />
                                </div>
                                <div class="flex flex-col gap-1">
                                    <span :id="`${uid}-tcolor`" class="text-sm font-medium">{{ t('homeLayout.titleColor') }}</span>
                                    <GadgetColorField v-model="look.titleColor" :auto-label="t('homeLayout.colorAuto')" :aria-labelledby="`${uid}-tcolor`" />
                                </div>
                                <div class="flex flex-col gap-1">
                                    <label :for="`${uid}-card`" class="text-sm font-medium">{{ t('homeLayout.cardLook') }}</label>
                                    <Select v-model="look.cardLook" :input-id="`${uid}-card`" :options="optionsOf(UtilGadgetStyle.CARD_LOOKS, 'cardLook')" option-label="label" option-value="value" fluid />
                                </div>
                                <div class="flex flex-col gap-1">
                                    <span :id="`${uid}-ccolor`" class="text-sm font-medium">{{ t('homeLayout.cardColor') }}</span>
                                    <GadgetColorField v-model="look.cardColor" :auto-label="t('homeLayout.colorFollowTitle')" :aria-labelledby="`${uid}-ccolor`" />
                                </div>
                            </div>
                            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('homeLayout.settingsPreviewNoteStyle') }}</p>
                        </div>
                    </TabPanel>

                    <!-- ── 노출 ── -->
                    <TabPanel value="show">
                        <div class="flex flex-col gap-4">
                            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('homeLayout.showWhere') }}</legend>
                                <div class="flex flex-wrap gap-x-6 gap-y-2">
                                    <div class="flex items-center gap-2">
                                        <ToggleSwitch v-model="desktop" :input-id="`${uid}-pc`" :invalid="Boolean(showShown)" />
                                        <label :for="`${uid}-pc`" class="cursor-pointer text-sm">{{ t('homeLayout.showDesktop') }}</label>
                                    </div>
                                    <div class="flex items-center gap-2">
                                        <ToggleSwitch v-model="mobile" :input-id="`${uid}-mo`" :invalid="Boolean(showShown)" />
                                        <label :for="`${uid}-mo`" class="cursor-pointer text-sm">{{ t('homeLayout.showMobile') }}</label>
                                    </div>
                                </div>
                                <p v-if="showShown" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ showShown }}</p>
                            </fieldset>

                            <div class="flex flex-col gap-1">
                                <span :id="`${uid}-fold`" class="text-sm font-medium">{{ t('homeLayout.mobileFold') }}</span>
                                <SelectButton
                                    v-model="look.mobileFold"
                                    :options="optionsOf(UtilGadgetStyle.MOBILE_FOLDS, 'mobileFold')"
                                    option-label="label"
                                    option-value="value"
                                    :allow-empty="false"
                                    size="small"
                                    :disabled="!UtilGadgetStyle.foldSelectable(look)"
                                    :aria-labelledby="`${uid}-fold`"
                                    :aria-describedby="`${uid}-fold-hint`"
                                />
                                <small :id="`${uid}-fold-hint`" class="text-xs text-muted-color">{{ t(UtilGadgetStyle.foldSelectable(look) ? 'homeLayout.mobileFoldHint' : 'homeLayout.mobileFoldHidden') }}</small>
                            </div>

                            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('homeLayout.whenEmpty') }}</legend>
                                <div class="flex items-center gap-2">
                                    <ToggleSwitch v-model="look.empty.keep" :input-id="`${uid}-keep`" />
                                    <label :for="`${uid}-keep`" class="cursor-pointer text-sm">{{ t('homeLayout.emptyKeep') }}</label>
                                </div>
                                <div class="flex items-center gap-2">
                                    <ToggleSwitch v-model="look.empty.message" :input-id="`${uid}-msg`" :disabled="!look.empty.keep" />
                                    <label :for="`${uid}-msg`" class="cursor-pointer text-sm" :class="look.empty.keep ? '' : 'opacity-60'">{{ t('homeLayout.emptyMessage') }}</label>
                                </div>
                                <div class="flex flex-col gap-1">
                                    <label :for="`${uid}-etext`" class="text-sm" :class="look.empty.keep && look.empty.message ? '' : 'opacity-60'">{{ t('homeLayout.emptyText') }}</label>
                                    <InputText
                                        v-model="look.empty.text"
                                        :id="`${uid}-etext`"
                                        :maxlength="UtilGadgetStyle.EMPTY_TEXT_MAX"
                                        :placeholder="t('homeLayout.emptyTextPlaceholder')"
                                        :disabled="!look.empty.keep || !look.empty.message"
                                        class="w-full sm:w-80"
                                    />
                                </div>
                                <small class="text-xs text-muted-color">{{ t('homeLayout.emptyHint') }}</small>
                            </fieldset>
                        </div>
                    </TabPanel>
                </TabPanels>
            </Tabs>

            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="emit('update:visible', false)" />
                <Button type="submit" :label="t('homeLayout.settingsApply')" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import type { HomeGadgetConfig, HomeGadgetStyle, HomeLayoutGadget, HomePostMeta, HomeQuickLink, HomeQuickLinkKind } from '~/composables/home/api/useHomeLayoutApi.types';
import type { GadgetPageBoard } from '~/composables/page/api/useGadgetPageApi.types';
import type { GadgetPlace } from '~/utils/util-gadget-config';
import UtilGadgetConfig from '~/utils/util-gadget-config';
import UtilGadgetStyle from '~/utils/util-gadget-style';

/** [적용]으로 넘기는 값 — 꾸미기·내용은 바뀌었을 때만(null 이면 편집 중인 값 그대로) */
export interface GadgetSettingsPatch {
    title: string;
    desktop: boolean;
    mobile: boolean;
    style: HomeGadgetStyle | null;
    config: HomeGadgetConfig | null;
}

interface Props {
    visible: boolean;
    /** 고칠 가젯 */
    gadget: HomeLayoutGadget | null;
    /** 지난 저장 실패의 이 가젯 칸 오류 — `fields.gadgets[i].` 뒤의 경로(`title` · `style.subtitle` · `config.items[0].label` …) → 서버 문구 */
    serverErrors?: Record<string, string> | null;
    /** 가젯이 놓인 자리 — 가젯 페이지면 대상 게시판에 「폴더 게시판 전체」, 게시판 레이아웃이면 「지금 보는 게시판」이 생긴다 */
    place?: GadgetPlace;
    /** 페이지의 기준 폴더(이름·게시판) — 안내 줄 */
    folder?: { name: string | null; boards: readonly GadgetPageBoard[] } | null;
}
const props = withDefaults(defineProps<Props>(), { serverErrors: null, place: 'HOME', folder: null });
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'apply', patch: GadgetSettingsPatch): void }>();

const { t } = useI18n();
const uid = useId();
const { localizedName } = useLocalizedName();
const { can } = useAdminPermission();
const { boards, loading: boardsLoading, error: boardsError, load: loadBoards } = useGadgetPageFolders();

type TabKey = 'content' | 'look' | 'show';
const TABS: readonly TabKey[] = ['content', 'look', 'show'];
const tab = ref<TabKey>('content');

/* ── 지난 저장 실패의 칸 오류(서버) ── */

/** 서버 칸 경로가 어느 탭의 것인가 — 제목·꾸미기는 「모양」, 노출·모바일 접기·비었을 때는 「노출」, 나머지(내용 설정·종류)는 「내용」 */
function serverTabOf(key: string): TabKey {
    if (key === 'desktop' || key === 'mobile' || key === 'style.mobileFold' || key.startsWith('style.empty')) return 'show';
    if (key === 'title' || key.startsWith('style')) return 'look';
    return 'content';
}
/** 칸 아래에 붙이는 서버 오류(제목 · 노출 · 바로가기 항목 이름·값) — 나머지는 탭 위 한 줄 */
const SERVER_INLINE = /^(title|desktop|mobile|config\.items\[\d+\]\.(label|value))$/;
const serverTabs = computed(() => new Set(Object.keys(props.serverErrors ?? {}).map(serverTabOf)));
const serverList = computed(() => Object.entries(props.serverErrors ?? {}).filter(([k]) => !SERVER_INLINE.test(k)).map(([, m]) => m));
function serverError(key: string): string {
    return props.serverErrors?.[key] ?? '';
}
const submitted = ref(false);

/* 편집 중인 값 — 창을 열 때 채운다. `seed*` 는 연 때의 값(바뀜 판정) */
const draft = ref<HomeGadgetConfig | null>(null);
const look = ref<HomeGadgetStyle | null>(null);
const title = ref('');
const desktop = ref(true);
const mobile = ref(true);
let seedConfig: HomeGadgetConfig | null = null;
let seedStyle: HomeGadgetStyle | null = null;

/** 바로가기 줄의 그리기 키(줄을 옮겨도 입력 칸이 따라가게) */
const linkKeys = ref<number[]>([]);
let linkSeq = 0;

const spec = computed(() => (props.gadget ? UtilGadgetConfig.spec(props.gadget.type) : null));

watch(
    () => props.visible,
    (open) => {
        const g = props.gadget;
        if (!open || !g) return;
        seedConfig = UtilGadgetConfig.normalize(g.type, g.config ?? null, props.place);
        seedStyle = UtilGadgetStyle.normalize(g.style ?? null, g.type);
        draft.value = seedConfig ? (JSON.parse(JSON.stringify(seedConfig)) as HomeGadgetConfig) : null;
        look.value = JSON.parse(JSON.stringify(seedStyle)) as HomeGadgetStyle;
        title.value = g.title;
        desktop.value = g.desktop;
        mobile.value = g.mobile;
        linkKeys.value = (draft.value?.items ?? []).map(() => ++linkSeq);
        tab.value = TABS.find((k) => serverTabs.value.has(k)) ?? 'content';
        submitted.value = false;
        if (spec.value?.boards || g.type === 'QUICK_LINKS') void loadBoards();
    },
    { immediate: true },
);

/** 값 목록 → 고르기 선택지(문구 키 `homeLayout.<마디>_<값>`) */
function optionsOf<T extends string | number>(values: readonly T[], prefix: string): { value: T; label: string }[] {
    return values.map((value) => ({ value, label: t(`homeLayout.${prefix}_${value}`) }));
}

const scopeOptions = computed(() => UtilGadgetConfig.scopes(props.place).map((value) => ({ value, label: t(`homeLayout.scope_${value}`) })));

const sortOptions = computed(() =>
    (spec.value?.sorts ?? []).map((value) => ({ value, label: t(props.gadget?.type === 'GROUP_BUY' ? `homeLayout.sort_${value}` : `homeLayout.postSort_${value}`) })),
);

const folderBoardNames = computed(() => (props.folder?.boards ?? []).map((b) => b.name).join(' · '));

function boardLabel(b: { name: string; nameEn: string | null; active: boolean }): string {
    return `${localizedName(b.name, b.nameEn)}${b.active ? '' : ` (${t('homeLayout.boardInactive')})`}`;
}

/** 고를 게시판 — 이미 고른 slug 가 목록에 없으면(꺼짐·지움) 그 slug 로 남겨 보인다 */
const boardOptions = computed(() => {
    const known = boards.value.map((b) => ({ value: b.slug, label: boardLabel(b) }));
    const missing = (draft.value?.boardIds ?? []).filter((slug) => !known.some((o) => o.value === slug)).map((slug) => ({ value: slug, label: slug }));
    return [...known, ...missing];
});

const pickedModel = computed<unknown[]>({
    get: () => draft.value?.boardIds ?? [],
    set: (value) => {
        if (draft.value) draft.value.boardIds = value.map(String).slice(0, UtilGadgetConfig.BOARD_PICK_MAX);
    },
});

const countModel = computed<number>({
    get: () => {
        const c = spec.value?.count;
        return c && draft.value ? Number(draft.value[c.field] ?? c.initial) : 1;
    },
    set: (value) => {
        const c = spec.value?.count;
        if (c && draft.value) draft.value[c.field] = value;
    },
});

const periodModel = computed<boolean>({
    get: () => draft.value?.periodTabs === true,
    set: (value) => {
        if (draft.value) draft.value.periodTabs = value;
    },
});

const cardHeadModel = computed<boolean>({
    get: () => draft.value?.cardHead !== false,
    set: (value) => {
        if (draft.value) draft.value.cardHead = value;
    },
});

const dividersModel = computed<boolean>({
    get: () => draft.value?.dividers !== false,
    set: (value) => {
        if (draft.value) draft.value.dividers = value;
    },
});

const metaModel = computed<HomePostMeta[]>({
    get: () => draft.value?.meta ?? [],
    // 고른 차례가 아니라 정해진 차례로 둔다(공개 화면도 그 차례로 그린다)
    set: (value) => {
        if (draft.value) draft.value.meta = UtilGadgetConfig.META.filter((m) => value.includes(m));
    },
});

const noticeModel = computed<string>({
    get: () => draft.value?.text ?? '',
    set: (value) => {
        if (draft.value) draft.value.text = (value ?? '').slice(0, UtilGadgetConfig.NOTICE_MAX);
    },
});

/* ── 바로가기 항목 ── */

const links = computed<HomeQuickLink[]>(() => draft.value?.items ?? []);

const quickKindOptions = computed(() => optionsOf(UtilGadgetConfig.QUICK_KINDS, 'quickKind'));

/** 바로가기로 고를 게시판 — 목록에 없는 slug(꺼짐·지움)도 남겨 보인다 */
const quickBoardOptions = computed(() => {
    const known = boards.value.map((b) => ({ value: b.slug, label: boardLabel(b) }));
    const missing = links.value
        .filter((it) => it.kind === 'BOARD' && it.value && !known.some((o) => o.value === it.value))
        .map((it) => ({ value: it.value as string, label: it.value as string }));
    return [...known, ...missing];
});

function setKind(i: number, kind: HomeQuickLinkKind): void {
    const it = links.value[i];
    if (!it || it.kind === kind) return;
    it.kind = kind;
    it.value = kind === 'BOARD' || kind === 'PATH' ? '' : null;
}

function addLink(): void {
    if (!draft.value || links.value.length >= UtilGadgetConfig.QUICK_LINK_MAX) return;
    draft.value.items = [...links.value, { label: '', kind: 'BOARD', value: '' }];
    linkKeys.value = [...linkKeys.value, ++linkSeq];
}

function removeLink(i: number): void {
    if (!draft.value) return;
    draft.value.items = links.value.filter((_, at) => at !== i);
    linkKeys.value = linkKeys.value.filter((_, at) => at !== i);
}

function moveLink(i: number, step: -1 | 1): void {
    const j = i + step;
    if (!draft.value || j < 0 || j >= links.value.length) return;
    const items = [...links.value];
    const keys = [...linkKeys.value];
    [items[i], items[j]] = [items[j]!, items[i]!];
    [keys[i], keys[j]] = [keys[j]!, keys[i]!];
    draft.value.items = items;
    linkKeys.value = keys;
}

const linkProblems = computed(() => (props.gadget ? UtilGadgetConfig.problems(props.gadget.type, draft.value) : []));

/** 항목 칸의 문제 문구 — 화면 검사(적용을 누른 뒤에만), 없으면 지난 저장 실패의 서버 문구 */
function linkProblem(i: number, field: 'label' | 'value'): string {
    const p = submitted.value ? linkProblems.value.find((x) => x.index === i && x.field === field) : undefined;
    return p ? t(`homeLayout.${p.key}`) : serverError(`config.items[${i}].${field}`);
}

/* ── 아이콘 ── */

const iconOptions = computed(() => {
    const list = [...UtilGadgetStyle.ICONS];
    const current = look.value?.icon;
    if (current && !list.includes(current)) list.unshift(current);
    return [{ value: 'NONE', label: t('homeLayout.iconNone') }, ...list.map((value) => ({ value, label: value.slice(3) }))];
});

function iconLabel(value: unknown): string {
    return iconOptions.value.find((o) => o.value === value)?.label ?? '';
}

/** 아이콘 — 고르기는 null 을 「안 고름」으로 보므로 `NONE` 으로 주고받는다 */
const iconModel = computed<string>({
    get: () => look.value?.icon ?? 'NONE',
    set: (value) => {
        if (look.value) look.value.icon = value === 'NONE' ? null : value;
    },
});

/* ── 검사 ── */

const boardError = computed(() => (submitted.value && draft.value?.boardScope === 'PICK' && !draft.value.boardIds?.length ? t('homeLayout.boardPickRequired') : ''));
const titleError = computed(() => {
    if (!submitted.value) return '';
    const key = UtilGadgetStyle.titleProblem(title.value);
    return key ? t(`homeLayout.${key}`, { max: UtilGadgetStyle.TITLE_MAX }) : '';
});
const showError = computed(() => (submitted.value && !desktop.value && !mobile.value ? t('homeLayout.showBothOff') : ''));
/** 보일 문구 — 화면 검사, 없으면 지난 저장 실패의 서버 문구(서버 문구는 [적용]을 막지 않는다) */
const titleShown = computed(() => titleError.value || serverError('title'));
const showShown = computed(() => showError.value || serverError('desktop') || serverError('mobile'));

const contentInvalid = computed(() => Boolean(boardError.value) || linkProblems.value.length > 0);
const lookInvalid = computed(() => Boolean(titleError.value));
const showInvalid = computed(() => Boolean(showError.value));

/** 내용 구획 아래 안내 — 저장 전 실제 화면이 어디까지 따르는지 */
const contentNoteKey = computed(() => {
    const type = props.gadget?.type;
    if (type === 'GROUP_BUY') return 'homeLayout.settingsPreviewNote';
    if (type === 'QUICK_LINKS' || type === 'NOTICE_TEXT') return 'homeLayout.settingsPreviewNoteDirect';
    return 'homeLayout.settingsPreviewNoteBoards';
});

function apply(): void {
    submitted.value = true;
    const gadget = props.gadget;
    if (!gadget || !draft.value || !look.value) return;
    if (contentInvalid.value || lookInvalid.value || showInvalid.value) {
        tab.value = contentInvalid.value ? 'content' : lookInvalid.value ? 'look' : 'show';
        return;
    }
    const config = UtilGadgetConfig.finish(gadget.type, draft.value, props.place);
    const style = UtilGadgetStyle.finish(look.value, gadget.type);
    if (!config) return;
    const styleChanged = !seedStyle || UtilGadgetStyle.key(style, gadget.type) !== UtilGadgetStyle.key(seedStyle, gadget.type);
    const configChanged = UtilGadgetConfig.key(config) !== UtilGadgetConfig.key(seedConfig);
    emit('apply', {
        title: title.value.trim(),
        desktop: desktop.value,
        mobile: mobile.value,
        style: styleChanged ? style : null,
        config: configChanged ? config : null,
    });
    emit('update:visible', false);
}
</script>
