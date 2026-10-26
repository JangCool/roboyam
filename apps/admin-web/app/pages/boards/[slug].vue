<template>
    <!--
        게시판 설정(게시판 하나) — 기본 정보 · 글쓰기·댓글 · 말머리 · 안내문 · 노출 · 보호·공개 시간·포인트 · 댓글 추천·의견 고정 요청 · 게시판 매니저·스텝 · 위험 구역. community 관리자 앱 `boards/[slug].vue` + `BoardSettingsDrawer` 를 한 화면으로 옮겼다.
        **저장 단위 둘**:
          · 기본 정보 + 정책 + 말머리(+ 안내문·한도 칸)는 **화면 하나로 한 번 저장**(`PUT /api/admin/boards/{id}`, 한 트랜잭션) → 하단 떠 있는 줄(`StickySaveBar`). 투자 고지를 새로 켜면 저장 전에 「다시 끌 수 없어요」를 묻는다.
          · 매니저·스텝 지정/해제, 게시판 켜고 끄기, 삭제는 **누르면 바로**(확인창 + 토스트) — 저장 줄과 무관하다.
        정책 칸은 모두 「기본값 따름」(null)을 고를 수 있고 그때 지금 기본값을 흐리게 보인다(`BoardSettingField`, 칸 정의는 `UtilBoard.SPECS` — 기본 설정 화면과 같은 부품). 읽기 전용 잠금(writeLock)은 기본값이 없는 게시판 전용 칸이다.
        「노출」의 가젯 노출 금지(gadgetExclusions)도 기본값이 없는 게시판 전용 칸이다(서버가 준 `gadgets` 순서의 체크 목록, 권한 `board.settings`). 이 칸이 「홈·인기·최신 포함」(inHome)을 대신해 inHome 은 화면에서 다루지 않는다(받은 값 그대로 저장 요청에 돌아간다).
        community 와 다른 점:
          · 최소 자격은 등급 목록(`useGradeAppearance`)의 등급 또는 운영진만(댓글은 끔도). 「지킴이」 → 매니저·스텝(정책 `docs/domain/operator-permissions.md`): 매니저 기본 3·최대 10, 스텝 최대 10, 한 회원 최대 5개 게시판. 핫딜·직접홍보·소모임 게시판은 지정 불가.
          · 말머리를 따로 저장하지 않고 게시판 저장에 함께 보낸다(서버가 목록을 diff — 빠진 말머리는 지우고, 쓰는 글이 있으면 409 `PREFIX_IN_USE`). 화면은 글이 있는 말머리의 삭제 단추를 잠그고 끄기를 안내한다. 사용 중 고유 말머리는 최대 6개.
          · 안내문은 지금 평문 textarea(보이는 글자 500자 카운터, 서버도 평문 500자)다 — 원본은 정화된 HTML·이미지 5장이고 에디터는 후속이다.
          · **글 없는 게시판은 지울 수 있다**(roboyam 카탈로그의 `board.delete` — 가정). community 는 끄기만 있었다. 글이 있으면 삭제 대신 끄기를 안내하고, 삭제는 이름을 입력해 확인한다.
          · 공개 사이트에서 보기 링크는 두지 않는다(공개 주소가 아직 없다). 신고 누적 자동 숨김은 없다(읽기 전용 한 줄).
        권한: 조회 `board.read` · 기본 정보·말머리·켜고 끄기 `board.manage` · 정책(잠금·안내문 포함) `board.settings` · 한도 칸 `board.staff.limit` · 매니저·스텝 지정 `board.staff.manage` · 삭제 `board.delete`.
        없는 권한의 단추는 숨기고 칸은 readonly(서버 값 그대로). 없는 slug 는 404 오류 상태. 저장하지 않은 변경이 있는 채 나가면 확인을 묻는다.
        서버 연결됨(`useBoardApi`): 저장 오류는 400 `VALIDATION` 과 409(`BOARD_NAME_DUPLICATE` `fields.name` · `PREFIX_LIMIT` `fields.prefixes` · `PREFIX_IN_USE` · `BOARD_INVESTMENT_NOTICE_LOCKED`) 모두 `fields` 를 칸 아래에 붙이고, 409 는 서버 문구를 저장 줄에 그대로 보인다.
        핫딜 게시판은 글쓰기 최소 자격에 첫 등급을 고를 수 없다(선택지에서 빼고 화면 검사도 같다). 카드형은 서버가 보기 전환을 늘 끈다.
        커머스 1-3: 「보호·공개 시간·포인트」의 포인트 지급(`pointMode`) 옆에 「적립 최소 글자 수 적용」(`pointMinLengthApplied`, 기본값 따름 가능 — 끄면 글 본문 최소 글자 수만 빠진다). 포인트 지급 안 함이면 비활성 + 안내.
    -->
    <div class="flex flex-col gap-4">
        <!-- 읽는 중 -->
        <template v-if="loading && !saved">
            <PageTitle :title="t('board.settingsTitle')" />
            <AppGadget :title="t('board.section.basic')">
                <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 4" :key="n" height="3rem" />
                </div>
            </AppGadget>
        </template>

        <!-- 없는 게시판 · 읽기 실패 -->
        <template v-else-if="!saved || !form">
            <PageTitle :title="t('board.settingsTitle')" />
            <AppGadget :title="t('board.settingsTitle')">
                <ErrorState v-if="notFound" code="404" :title="t('board.notFoundTitle')" :description="loadError">
                    <Button :label="t('board.backToList')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push('/boards')" />
                </ErrorState>
                <ErrorState v-else :title="t('board.detailLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>
        </template>

        <template v-else>
            <PageTitle :title="localizedName(saved.name, saved.nameEn)">
                <template #actions>
                    <Button :label="t('board.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push('/boards')" />
                </template>
            </PageTitle>
            <!-- 머리 — 종류 · 꺼짐 · 기본값과 다른 설정 수 · 주소 -->
            <div class="-mt-2 flex flex-wrap items-center gap-2">
                <BoardKindTag :kind="saved.kind" />
                <Tag v-if="!saved.active" class="board-tag" :value="t('board.inactive')" severity="secondary" icon="pi pi-eye-slash" />
                <Tag v-if="diffNow" class="board-tag" :value="t('board.diffCount', { n: diffNow })" severity="warn" />
                <span v-else class="text-xs text-muted-color">{{ t('board.followsDefaults') }}</span>
                <code class="text-xs text-muted-color">/{{ saved.slug }}</code>
            </div>

            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <!-- 1. 기본 정보 -->
                <AppGadget :title="t('board.section.basic')" anchor="board-basic" :changes="changes.basic" :invalid="invalidOf(['name', 'nameEn', 'description', 'viewToggle', 'investmentNotice', 'color', 'layout'])">
                    <div class="grid gap-x-8 gap-y-4 md:grid-cols-2">
                        <LabeledField :label="t('board.field.name')" :hint="t('board.nameHint', { max: UtilBoard.NAME_MAX })" :error="textErrorOf('name')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="form.name" name="name" :readonly="!canManage || saving" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('board.field.nameEn')" :hint="t('board.optional')" :help="t('board.nameEnHelp')" :error="textErrorOf('nameEn')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="form.nameEn" name="nameEn" lang="en" :readonly="!canManage || saving" :invalid="invalid" :aria-describedby="describedBy" autocomplete="off" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('board.field.slug')" :help="t('board.slugLocked')">
                            <template #default="{ id, describedBy }">
                                <InputText :id="id" :model-value="saved.slug" name="slug" readonly :aria-describedby="describedBy" fluid class="font-mono" />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('board.field.description')" :hint="t('board.descHint', { max: UtilBoard.DESC_MAX })" :error="textErrorOf('description')">
                            <template #default="{ id, describedBy, invalid }">
                                <Textarea :id="id" v-model="form.description" name="description" rows="2" auto-resize :readonly="!canManage || saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>

                        <!-- 목록 모양 + 보기 전환 -->
                        <div class="flex flex-col gap-1.5">
                            <span :id="`${idBase}-layout`" class="text-sm font-medium">{{ t('board.field.layout') }}</span>
                            <SelectButton v-model="form.layout" :options="layoutOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="!canManage || saving" :aria-labelledby="`${idBase}-layout`" class="self-start" />
                            <div class="mt-2 flex items-center gap-2">
                                <ToggleSwitch v-model="form.viewToggle" :input-id="`${idBase}-view`" :disabled="!canManage || saving || (form.layout === 'CARD' && !form.viewToggle)" />
                                <label :for="`${idBase}-view`" class="text-sm">{{ t('board.field.viewToggle') }}</label>
                            </div>
                            <p class="m-0 text-xs leading-relaxed" :class="serverFields.viewToggle ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ serverFields.viewToggle || t(form.layout === 'CARD' ? 'board.viewToggleCard' : 'board.viewToggleHelp') }}</p>
                        </div>

                        <!-- 종류(읽기 전용) · 19금 · 투자 고지 -->
                        <div class="flex flex-col gap-3">
                            <div class="flex flex-col gap-1">
                                <span class="text-sm font-medium">{{ t('board.field.kind') }}</span>
                                <div class="flex items-center gap-2">
                                    <BoardKindTag :kind="saved.kind" />
                                    <span class="text-xs text-muted-color">{{ t('board.kindLocked') }}</span>
                                </div>
                            </div>
                            <div class="flex items-start gap-2">
                                <ToggleSwitch v-model="form.allowAdult" :input-id="`${idBase}-adult`" :disabled="!canManage || saving" class="shrink-0" />
                                <label :for="`${idBase}-adult`" class="flex flex-col gap-0.5">
                                    <span class="text-sm">{{ t('board.field.allowAdult') }}</span>
                                    <span class="text-xs text-muted-color">{{ t('board.allowAdultHelp') }}</span>
                                </label>
                            </div>
                            <div class="flex items-start gap-2">
                                <ToggleSwitch v-model="form.investmentNotice" :input-id="`${idBase}-invest`" :disabled="!canManage || saving || saved.investmentNotice" class="shrink-0" />
                                <label :for="`${idBase}-invest`" class="flex flex-col gap-0.5">
                                    <span class="text-sm">{{ t('board.field.investmentNotice') }}</span>
                                    <span class="text-xs" :class="serverFields.investmentNotice ? 'text-red-600 dark:text-red-400' : saved.investmentNotice || form.investmentNotice ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'">
                                        {{ serverFields.investmentNotice || t(saved.investmentNotice ? 'board.investmentLocked' : form.investmentNotice ? 'board.investmentOnce' : 'board.investmentHelp') }}
                                    </span>
                                </label>
                            </div>
                        </div>

                        <!-- 표시색 — 프리셋 12색 또는 자동(같은 이름의 네이티브 라디오, grades.vue 와 같은 방식) -->
                        <div class="flex flex-col gap-1.5 md:col-span-2">
                            <span :id="`${idBase}-color`" class="text-sm font-medium">{{ t('board.field.color') }}</span>
                            <div role="radiogroup" :aria-labelledby="`${idBase}-color`" class="flex flex-wrap gap-1.5">
                                <label v-for="c in COLOR_CHOICES" :key="c" class="color-opt" :class="{ 'color-opt--disabled': !canManage || saving }" :title="t(`board.color.${c}`)">
                                    <input v-model="colorModel" type="radio" class="sr-only" :name="`${idBase}-color`" :value="c" :disabled="!canManage || saving" :aria-label="t(`board.color.${c}`)" />
                                    <span v-if="c === 'AUTO'" class="color-opt__auto" aria-hidden="true" />
                                    <span v-else class="color-opt__dot" :style="{ background: UtilBoard.colorVar(c) ?? undefined }" aria-hidden="true" />
                                </label>
                            </div>
                            <p class="m-0 text-xs text-muted-color">{{ t('board.colorHelp', { name: t(`board.color.${colorModel}`) }) }}</p>
                        </div>
                    </div>
                </AppGadget>

                <!-- 2. 글쓰기·댓글 -->
                <AppGadget :title="t('board.section.boardWrite')" anchor="board-write" :changes="changes.write" :invalid="invalidOf(['writeMin', 'commentMin', 'postEditor', 'imageUploadAllowed', 'commentEditor', 'writeLock'])">
                    <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                        <BoardSettingField v-for="field in WRITE_FIELDS" :key="field" v-bind="policyBind(field)" :allow-guest="UtilBoard.guestAllowed(saved.kind)" :exclude-grades="field === 'writeMin' && saved.kind === 'DEAL' && firstGradeKey ? [firstGradeKey] : []" :inactive="writeInactive(field)" :note="writeNote(field)" @update:model-value="(value: BoardFieldValue) => onPolicy(field, value)" />

                        <div class="flex flex-col gap-1.5">
                            <span :id="`${idBase}-lock`" class="text-sm font-medium">{{ t('board.field.writeLock') }}</span>
                            <SelectButton v-model="writeLockModel" :options="writeLockOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="!canSettings || saving" :aria-labelledby="`${idBase}-lock`" class="self-start" />
                            <p class="m-0 text-xs leading-relaxed" :class="serverFields.writeLock ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ serverFields.writeLock || t('board.writeLockHelp') }}</p>
                        </div>
                    </div>
                    <p class="mt-4 flex items-start gap-2 border-t border-(--line) pt-3 text-xs leading-relaxed text-muted-color">
                        <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                        <span>{{ t(UtilBoard.guestAllowed(saved.kind) ? 'board.guestBoardNote' : 'board.guestKindBlocked') }}</span>
                    </p>
                </AppGadget>

                <!-- 3. 말머리 -->
                <AppGadget :title="t('board.section.prefix')" anchor="board-prefix" :changes="changes.prefix" :invalid="prefixErrorCount">
                    <p class="mb-3 text-xs leading-relaxed text-muted-color">{{ t('board.prefixLead') }}</p>

                    <h3 class="mb-2 text-[13px] font-medium">{{ t('board.commonPrefixTitle') }}</h3>
                    <ul class="m-0 mb-5 grid list-none gap-2 p-0 sm:grid-cols-2">
                        <li v-for="p in form.commonPrefixes" :key="p.id" class="flex items-center gap-2 rounded-lg border border-(--line) px-3 py-2">
                            <span class="min-w-0 flex-1 truncate text-[13px] font-medium">{{ t(`board.commonPrefix.${p.common}`) }}</span>
                            <Tag v-if="p.staffOnly" class="board-tag" :value="t('board.staffOnly')" severity="secondary" />
                            <span class="text-[11px] tabular-nums text-muted-color">{{ t('board.usageCount', { n: p.usageCount }) }}</span>
                            <ToggleSwitch v-model="p.active" :disabled="!canManage || saving" :aria-label="t('board.prefixActive', { name: t(`board.commonPrefix.${p.common}`) })" />
                        </li>
                    </ul>

                    <div class="mb-2 flex flex-wrap items-center gap-2">
                        <h3 class="text-[13px] font-medium">{{ t('board.ownPrefixTitle') }}</h3>
                        <span class="text-xs tabular-nums" :class="activeOwn > UtilBoard.PREFIX_ACTIVE_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('board.prefixActiveCount', { n: activeOwn, max: UtilBoard.PREFIX_ACTIVE_MAX }) }}</span>
                    </div>
                    <p v-if="serverFields.prefixes" role="alert" class="mb-2 text-xs text-red-600 dark:text-red-400">{{ serverFields.prefixes }}</p>

                    <ul v-if="form.prefixes.length" class="m-0 flex list-none flex-col divide-y divide-(--line) border-y border-(--line) p-0">
                        <li v-for="(p, i) in form.prefixes" :key="p.key" class="flex flex-col gap-2 py-3">
                            <div class="flex flex-wrap items-start gap-x-3 gap-y-2">
                                <div class="flex min-w-0 flex-1 basis-48 flex-col gap-1">
                                    <InputText
                                        v-model="p.name"
                                        :name="`prefix-${p.key}`"
                                        size="small"
                                        :readonly="!canManage || saving"
                                        :invalid="Boolean(prefixErrorOf(p, i))"
                                        :placeholder="t('board.prefixNamePlaceholder')"
                                        :aria-label="t('board.prefixName', { n: i + 1 })"
                                        :aria-describedby="prefixErrorOf(p, i) ? `${idBase}-pe-${p.key}` : undefined"
                                        autocomplete="off"
                                        fluid
                                    />
                                    <p v-if="prefixErrorOf(p, i)" :id="`${idBase}-pe-${p.key}`" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ prefixErrorOf(p, i) }}</p>
                                </div>
                                <Select v-model="p.color" :options="prefixColorOptions" option-label="label" option-value="value" size="small" :disabled="!canManage || saving" :aria-label="t('board.prefixColor', { name: p.name || t('board.prefixUnnamed') })" class="w-32">
                                    <template #value="{ value }">
                                        <span class="inline-flex items-center gap-1.5 text-sm">
                                            <span class="size-2.5 rounded-full" :class="value ? '' : 'border border-dashed border-(--line-strong)'" :style="value ? { background: UtilBoard.colorVar(value) ?? undefined } : undefined" aria-hidden="true" />
                                            {{ t(`board.color.${value ?? 'AUTO'}`) }}
                                        </span>
                                    </template>
                                    <template #option="{ option }">
                                        <span class="inline-flex items-center gap-1.5 text-sm">
                                            <span class="size-2.5 rounded-full" :class="option.value ? '' : 'border border-dashed border-(--line-strong)'" :style="option.value ? { background: UtilBoard.colorVar(option.value) ?? undefined } : undefined" aria-hidden="true" />
                                            {{ option.label }}
                                        </span>
                                    </template>
                                </Select>
                                <div class="flex h-8 items-center gap-1.5">
                                    <Checkbox v-model="p.staffOnly" binary :input-id="`${idBase}-so-${p.key}`" :disabled="!canManage || saving" />
                                    <label :for="`${idBase}-so-${p.key}`" class="text-[13px]">{{ t('board.staffOnly') }}</label>
                                </div>
                                <div class="flex h-8 items-center gap-1.5">
                                    <span v-tooltip.top="!p.active && activeOwn >= UtilBoard.PREFIX_ACTIVE_MAX ? t('board.prefixActiveLimit', { max: UtilBoard.PREFIX_ACTIVE_MAX }) : undefined" class="inline-flex">
                                        <ToggleSwitch v-model="p.active" :input-id="`${idBase}-pa-${p.key}`" :disabled="!canManage || saving || (!p.active && activeOwn >= UtilBoard.PREFIX_ACTIVE_MAX)" />
                                    </span>
                                    <label :for="`${idBase}-pa-${p.key}`" class="text-[13px]">{{ t('board.inUse') }}</label>
                                </div>
                                <div v-if="canManage" class="ml-auto flex h-8 items-center gap-0.5">
                                    <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('board.moveUp', { name: p.name || t('board.prefixUnnamed') })" :disabled="saving || i === 0" @click="movePrefix(i, -1)" />
                                    <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('board.moveDown', { name: p.name || t('board.prefixUnnamed') })" :disabled="saving || i === form.prefixes.length - 1" @click="movePrefix(i, 1)" />
                                    <span v-tooltip.top="p.usageCount > 0 ? t('board.prefixInUse', { n: p.usageCount }) : undefined" class="inline-flex">
                                        <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :aria-label="t('board.prefixRemove', { name: p.name || t('board.prefixUnnamed') })" :aria-description="p.usageCount > 0 ? t('board.prefixInUse', { n: p.usageCount }) : undefined" :disabled="saving || p.usageCount > 0" @click="removePrefix(i)" />
                                    </span>
                                </div>
                            </div>
                            <p class="m-0 text-[11px] text-muted-color">
                                {{ p.id ? t('board.usageCount', { n: p.usageCount }) : t('board.newPrefix') }}<template v-if="p.usageCount > 0"> · {{ t('board.prefixInUseShort') }}</template>
                            </p>
                        </li>
                    </ul>
                    <p v-else class="m-0 rounded-lg border border-dashed border-(--line) py-4 text-center text-xs text-muted-color">{{ t('board.noOwnPrefix') }}</p>

                    <div v-if="canManage" class="mt-3 flex flex-wrap items-center gap-3">
                        <span v-tooltip.top="activeOwn >= UtilBoard.PREFIX_ACTIVE_MAX ? t('board.prefixActiveLimit', { max: UtilBoard.PREFIX_ACTIVE_MAX }) : undefined" class="inline-flex">
                            <Button type="button" :label="t('board.addPrefix')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="saving || activeOwn >= UtilBoard.PREFIX_ACTIVE_MAX" @click="addPrefix" />
                        </span>
                        <span v-if="activeOwn >= UtilBoard.PREFIX_ACTIVE_MAX" class="text-xs text-muted-color">{{ t('board.prefixActiveLimit', { max: UtilBoard.PREFIX_ACTIVE_MAX }) }}</span>
                    </div>
                </AppGadget>

                <!-- 4. 안내문 — 평문 textarea(원본은 정화된 HTML·이미지 5장, 에디터는 후속) -->
                <AppGadget :title="t('board.section.guide')" anchor="board-guide" :changes="changes.guide" :invalid="invalidOf(['introText', 'writeGuideText'])">
                    <div class="grid gap-x-8 gap-y-4 md:grid-cols-2">
                        <LabeledField v-for="field in GUIDE_FIELDS" :key="field" :label="t(`board.field.${field}`)" :help="t(`board.fieldHelp.${field}`)" :error="textErrorOf(field)">
                            <template #aside>
                                <span class="text-xs tabular-nums" :class="UtilBoard.textLength(form[field]) > UtilBoard.TEXT_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ UtilBoard.textLength(form[field]) }} / {{ UtilBoard.TEXT_MAX }}</span>
                            </template>
                            <template #default="{ id, describedBy, invalid }">
                                <Textarea :id="id" v-model="form[field]" :name="field" rows="4" auto-resize :readonly="!canSettings || saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                    </div>
                </AppGadget>

                <!-- 5. 노출 -->
                <AppGadget :title="t('board.section.exposure')" anchor="board-exposure" :changes="changes.exposure" :invalid="invalidOf([...EXPOSURE_FIELDS, 'gadgetExclusions'])">
                    <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                        <BoardSettingField v-for="field in EXPOSURE_FIELDS" :key="field" v-bind="policyBind(field)" @update:model-value="(value: BoardFieldValue) => onPolicy(field, value)" />

                        <!-- 가젯 노출 금지 — 게시판마다의 값(기본값 따름 없음). 고른 가젯에 이 게시판 글을 내지 않는다. 가젯 목록·이름은 서버가 준다 -->
                        <div class="flex flex-col gap-1.5" role="group" :aria-labelledby="`${idBase}-gadget`" :aria-describedby="`${idBase}-gadget-help`">
                            <span :id="`${idBase}-gadget`" class="text-sm font-medium">{{ t('board.field.gadgetExclusions') }}</span>
                            <ul v-if="saved.gadgets?.length" class="m-0 flex min-h-8 list-none flex-wrap items-center gap-x-5 gap-y-2 p-0">
                                <li v-for="g in saved.gadgets" :key="g.key" class="flex items-center gap-1.5">
                                    <Checkbox v-model="gadgetModel" :value="g.key" :input-id="`${idBase}-gadget-${g.key}`" :disabled="!canSettings || saving" />
                                    <label :for="`${idBase}-gadget-${g.key}`" class="text-[13px]">{{ g.label }}</label>
                                </li>
                            </ul>
                            <p :id="`${idBase}-gadget-help`" class="m-0 text-xs leading-relaxed" :class="serverFields.gadgetExclusions ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="serverFields.gadgetExclusions ? 'alert' : undefined">
                                {{ serverFields.gadgetExclusions || t('board.fieldHelp.gadgetExclusions') }}
                            </p>
                        </div>
                    </div>
                </AppGadget>

                <!-- 6. 보호 · 댓글 공개 시간 · 포인트 · 신고 -->
                <AppGadget :title="t('board.section.boardProtect')" anchor="board-protect" :changes="changes.protect" :invalid="invalidOf([...PROTECT_FIELDS])">
                    <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                        <BoardSettingField v-for="field in PROTECT_FIELDS" :key="field" v-bind="policyBind(field)" :inactive="protectInactive(field)" :note="protectNote(field)" @update:model-value="(value: BoardFieldValue) => onPolicy(field, value)" />
                    </div>
                    <p class="mt-4 flex items-start gap-2 border-t border-(--line) pt-3 text-xs leading-relaxed text-muted-color">
                        <i class="pi pi-lock mt-0.5 text-[11px]" aria-hidden="true" />
                        <span>{{ t('board.reportFixed') }}</span>
                    </p>
                </AppGadget>

                <!-- 7. 찬반 토론·댓글 추천 — 글쓴이 토론(`debateEnabled`) · 글쓴이 투표(`pollEnabled`) · 비추천 켬 · 추천/비추천 이름(비우면 기본 설정 따름) · 고정 요청 확인자. 토론 의견도 이 게시판 설정을 따른다 -->
                <AppGadget :title="t('board.section.reaction')" anchor="board-reaction" :changes="changes.reaction" :invalid="invalidOf([...REACTION_FIELDS])">
                    <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                        <BoardSettingField
                            v-for="field in REACTION_FIELDS"
                            :key="field"
                            v-bind="policyBind(field)"
                            :inactive="reactionInactive(field)"
                            :note="reactionNote(field)"
                            :help="UtilBoard.isLabel(field) ? t(`board.fieldHelpBoard.${field}`) : ''"
                            @update:model-value="(value: BoardFieldValue) => onPolicy(field, value)"
                        />
                    </div>
                    <p class="mt-4 flex items-start gap-2 border-t border-(--line) pt-3 text-xs leading-relaxed text-muted-color">
                        <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                        <span>{{ t('board.note.reactionDebate') }}</span>
                    </p>
                </AppGadget>

                <!-- 8. 게시판 매니저·스텝 — 지정/해제는 바로, 한도 칸은 저장 줄로 -->
                <AppGadget :title="t('board.section.staff')" anchor="board-staff" :changes="changes.staff" :invalid="invalidOf([...STAFF_LIMIT_FIELDS])">
                    <Message v-if="saved.kind !== 'GENERAL'" severity="warn" :closable="false" size="small" class="mb-4">{{ t('board.staffKindBlocked') }}</Message>
                    <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t('board.staffLead') }}</p>

                    <div v-if="staffLoading && !staff" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                        <Skeleton v-for="n in 2" :key="n" height="2.75rem" />
                    </div>
                    <div v-else-if="!staff" class="flex flex-wrap items-center gap-3 rounded-lg border border-(--line) px-3 py-3 text-sm">
                        <span class="text-red-600 dark:text-red-400" role="alert">{{ t('board.staffLoadFailed') }} — {{ staffError }}</span>
                        <Button type="button" :label="t('common.retry')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="staffLoading" @click="loadStaff" />
                    </div>
                    <div v-else class="grid gap-6 lg:grid-cols-2">
                        <section v-for="role in ROLES" :key="role" :aria-labelledby="`${idBase}-role-${role}`" class="flex flex-col gap-2">
                            <div class="flex flex-wrap items-center gap-2">
                                <h3 :id="`${idBase}-role-${role}`" class="text-[13px] font-medium">{{ t(`board.role.${role}`) }}</h3>
                                <span class="text-xs tabular-nums text-muted-color">{{ t('board.staffCap', { n: staffOfRole(role).length, max: capOf(role) }) }}</span>
                                <span v-if="canStaffManage" v-tooltip.top="appointBlockedReason(role) || undefined" class="ml-auto inline-flex">
                                    <Button type="button" :label="t('board.appoint')" icon="pi pi-user-plus" size="small" severity="secondary" variant="outlined" :disabled="Boolean(appointBlockedReason(role)) || staffBusy" :aria-description="appointBlockedReason(role) || undefined" @click="openAppoint(role)" />
                                </span>
                            </div>
                            <ul v-if="staffOfRole(role).length" class="m-0 flex list-none flex-col divide-y divide-(--line) border-y border-(--line) p-0">
                                <li v-for="m in staffOfRole(role)" :key="m.memberId" class="flex items-center gap-3 py-2">
                                    <div class="min-w-0 flex-1">
                                        <div class="flex items-center gap-2">
                                            <span class="truncate text-[13px] font-medium">{{ m.nickname }}</span>
                                            <GradeBadge v-bind="appearance.badgeOf(m.gradeKey)" size="small" />
                                        </div>
                                        <p class="m-0 text-[11px] text-muted-color">{{ t('board.appointedLine', { at: time.date(m.appointedAt), by: m.appointedBy || '—' }) }}</p>
                                    </div>
                                    <Button v-if="canStaffManage" type="button" :label="t('board.release')" size="small" severity="danger" variant="text" :disabled="staffBusy" :aria-label="t('board.releaseOf', { name: m.nickname })" @click="release(m)" />
                                </li>
                            </ul>
                            <p v-else class="m-0 rounded-lg border border-dashed border-(--line) py-3 text-center text-xs text-muted-color">{{ t('board.noStaff') }}</p>
                        </section>
                    </div>

                    <h3 class="mt-6 mb-1 text-[13px] font-medium">{{ t('board.staffLimitTitle') }}</h3>
                    <p class="mb-3 text-xs leading-relaxed text-muted-color">{{ t('board.staffLimitLead') }}</p>
                    <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                        <BoardSettingField v-for="field in STAFF_LIMIT_FIELDS" :key="field" v-bind="policyBind(field)" @update:model-value="(value: BoardFieldValue) => onPolicy(field, value)" />
                    </div>
                </AppGadget>

                <!-- 9. 위험 구역 — 바로 동작 -->
                <AppGadget v-if="canManage || canDelete" :title="t('board.section.danger')" anchor="board-danger">
                    <ul class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                        <li v-if="canManage" class="flex flex-wrap items-center gap-3 py-3 first:pt-0">
                            <div class="min-w-0 flex-1 basis-64">
                                <p class="m-0 text-sm font-medium">{{ t(saved.active ? 'board.deactivateTitleShort' : 'board.activateTitleShort') }}</p>
                                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(saved.active ? 'board.deactivateMessage' : 'board.activateHelp') }}</p>
                            </div>
                            <Button type="button" :label="t(saved.active ? 'board.deactivateConfirm' : 'board.activateConfirm')" :icon="saved.active ? 'pi pi-eye-slash' : 'pi pi-eye'" size="small" :severity="saved.active ? 'warn' : 'secondary'" variant="outlined" :loading="activeBusy" @click="toggleActive" />
                        </li>
                        <li v-if="canDelete" class="flex flex-wrap items-center gap-3 py-3 last:pb-0">
                            <div class="min-w-0 flex-1 basis-64">
                                <p class="m-0 text-sm font-medium">{{ t('board.deleteTitle') }}</p>
                                <p class="m-0 text-xs leading-relaxed" :class="saved.postCount > 0 ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'">
                                    {{ saved.postCount > 0 ? t('board.deleteBlocked', { n: saved.postCount.toLocaleString(locale) }) : t('board.deleteHelp') }}
                                </p>
                            </div>
                            <Button type="button" :label="t('board.delete')" icon="pi pi-trash" size="small" severity="danger" :disabled="saved.postCount > 0" @click="openDelete" />
                        </li>
                    </ul>
                </AppGadget>

                <!-- 저장 줄 — 구획 밖, 모든 구획 아래. 기본 정보 + 정책 + 말머리를 한 번에 저장한다 -->
                <StickySaveBar v-if="canManage || canSettings || canStaffLimit" submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changeCount" :error-count="errorCount" :error="formError" @revert="revert" />
                <p v-else class="text-xs text-muted-color">{{ t('board.readOnlyNote') }}</p>
            </form>
        </template>

        <!-- 매니저·스텝 지정 — 회원 검색 -->
        <Dialog v-model:visible="appointOpen" modal :draggable="false" :header="t('board.appointTitle', { role: t(`board.role.${appointRole}`) })" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }">
            <div class="flex flex-col gap-3">
                <IconField>
                    <InputIcon class="pi pi-search" />
                    <InputText v-model="candidateQuery" size="small" fluid :placeholder="t('board.candidateSearch')" :aria-label="t('board.candidateSearch')" autocomplete="off" />
                </IconField>
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('board.candidateRule') }}</p>

                <div v-if="candidatesLoading && !candidates" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 3" :key="n" height="2.75rem" />
                </div>
                <p v-else-if="candidatesError" role="alert" class="m-0 text-sm text-red-600 dark:text-red-400">{{ candidatesError }}</p>
                <ul v-else-if="candidates && candidates.length" class="m-0 flex max-h-80 list-none flex-col divide-y divide-(--line) overflow-y-auto p-0">
                    <li v-for="c in candidates" :key="c.memberId" class="flex items-center gap-3 py-2" :class="c.ineligible ? 'opacity-70' : ''">
                        <div class="min-w-0 flex-1">
                            <div class="flex items-center gap-2">
                                <span class="truncate text-[13px] font-medium">{{ c.nickname }}</span>
                                <GradeBadge v-bind="appearance.badgeOf(c.gradeKey)" size="small" />
                            </div>
                            <p class="m-0 text-[11px] text-muted-color">{{ c.email }} · {{ t('board.candidateBoards', { n: c.boardCount, max: UtilBoard.MEMBER_BOARD_MAX }) }}</p>
                            <p v-if="c.ineligible" class="m-0 text-[11px] text-amber-700 dark:text-amber-400">{{ UtilBoardMessages.ineligible(t, c.ineligible) }}</p>
                        </div>
                        <Button type="button" :label="t('board.appoint')" size="small" :disabled="Boolean(c.ineligible) || staffBusy" :loading="appointingId === c.memberId" :aria-label="t('board.appointOf', { name: c.nickname, role: t(`board.role.${appointRole}`) })" @click="appoint(c)" />
                    </li>
                </ul>
                <EmptyState v-else icon="pi pi-users" :title="t('board.noCandidate')" />
            </div>
            <template #footer>
                <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="appointOpen = false" />
            </template>
        </Dialog>

        <!-- 게시판 삭제 — 이름을 입력해 확인 -->
        <Dialog v-model:visible="deleteOpen" modal :draggable="false" :header="t('board.deleteTitle')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }">
            <form v-if="saved" :id="`${idBase}-delete`" class="flex flex-col gap-3" novalidate @submit.prevent="remove">
                <p class="m-0 text-sm leading-relaxed">{{ t('board.deleteConfirmMessage', { name: saved.name }) }}</p>
                <LabeledField :label="t('board.deleteTypeName')">
                    <template #default="{ id, describedBy }">
                        <InputText :id="id" v-model="deleteName" :placeholder="saved.name" :aria-describedby="describedBy" autocomplete="off" fluid :disabled="deleting" />
                    </template>
                </LabeledField>
                <Message v-if="deleteError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ deleteError }}</Message>
            </form>
            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="deleting" @click="deleteOpen = false" />
                <Button type="submit" :form="`${idBase}-delete`" :label="t('board.delete')" severity="danger" :loading="deleting" :disabled="deleteName.trim() !== saved?.name" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { BoardColor, BoardDetail, BoardPolicyField, BoardStaffCandidate, BoardStaffMember, BoardStaffRole, BoardWriteLock } from '~/composables/board/api/useBoardApi.types';
import type { BoardDetailForm, BoardFieldValue, BoardLevelProblem, BoardPrefixForm } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';

const { t, locale } = useI18n();

const api = useBoardApi();
const appearance = useGradeAppearance();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const time = useDisplayTime();
const idBase = useId();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('board.manage'));
const canSettings = computed(() => can('board.settings'));
const canStaffLimit = computed(() => can('board.staff.limit'));
const canStaffManage = computed(() => can('board.staff.manage'));
const canDelete = computed(() => can('board.delete'));

const WRITE_FIELDS = ['writeMin', 'commentMin', 'postEditor', 'imageUploadAllowed', 'commentEditor'] as const;
/** 「홈·인기·최신 포함」(inHome)은 두지 않는다 — 가젯 노출 금지가 대신한다(서버 칸은 받은 값 그대로 저장 요청에 돌아간다) */
const EXPOSURE_FIELDS = ['listSize', 'pinnedMax', 'indexable', 'searchable'] as const;
const PROTECT_FIELDS = ['commentProtectMin', 'commentProtectEdit', 'commentProtectDelete', 'commentProtectDelayMinutes', 'commentGuestDelayMinutes', 'staffHiddenNotice', 'pointMode', 'pointMinLengthApplied'] as const;
const REACTION_FIELDS = ['debateEnabled', 'pollEnabled', 'commentDislikeEnabled', 'commentLikeLabel', 'commentDislikeLabel', 'pinRequestReviewers'] as const;
const STAFF_LIMIT_FIELDS = ['managerMax', 'staffPostDailyLimit', 'staffCommentDailyLimit', 'staffSanctionDailyLimit'] as const;
const GUIDE_FIELDS = ['introText', 'writeGuideText'] as const;
const ROLES: readonly BoardStaffRole[] = ['MANAGER', 'STAFF'];
/** 표시색 선택지 — 자동(색 없음) + 프리셋 12색 */
const COLOR_CHOICES = ['AUTO', ...UtilBoard.COLORS] as const;

const slug = computed(() => String(route.params.slug ?? ''));

const saved = ref<BoardDetail | null>(null);
useSeoMeta({ title: () => (saved.value ? localizedName(saved.value.name, saved.value.nameEn) : t('board.settingsTitle')) });
const form = ref<BoardDetailForm | null>(null);
const loading = ref(false);
const loadError = ref('');
const notFound = ref(false);
const saving = ref(false);
const formError = ref('');
/** 서버가 거절한 칸 — 정책은 `policy.` 를 뗀 이름, 말머리는 `prefixes[i].name`. 그 칸을 고치면 지운다 */
const serverFields = ref<Record<string, string>>({});
/** 저장 요청을 보낼 때의 고유 말머리 행 — 서버 `prefixes[i]` 오류를 행에 붙인다 */
let sentPrefixes: BoardPrefixForm[] = [];
let prefixSeq = 0;

const grades = computed(() => appearance.grades.value);
const layoutOptions = computed(() => UtilBoard.LAYOUTS.map((value) => ({ value, label: t(`board.layout.${value}`) })));
const writeLockOptions = computed(() => UtilBoard.WRITE_LOCKS.map((value) => ({ value: value ?? 'NONE', label: t(`board.writeLock.${value ?? 'NONE'}`) })));
const prefixColorOptions = computed(() => [{ value: null as BoardColor | null, label: t('board.color.AUTO') }, ...UtilBoard.COLORS.map((value) => ({ value: value as BoardColor | null, label: t(`board.color.${value}`) }))]);

/** 읽기 전용 잠금 — SelectButton 은 null 값을 「안 고름」으로 보므로 `NONE` 으로 주고받는다 */
const writeLockModel = computed<string>({
    get: () => form.value?.writeLock ?? 'NONE',
    set: (value) => {
        if (form.value) form.value.writeLock = value === 'NONE' ? null : (value as BoardWriteLock);
    },
});
/** 표시색 — 색 없음은 `AUTO` */
const colorModel = computed<string>({
    get: () => form.value?.color ?? 'AUTO',
    set: (value) => {
        if (form.value) form.value.color = value === 'AUTO' ? null : (value as BoardColor);
    },
});

/** 가젯 노출 금지 — 체크 목록이 고른 키 배열을 주고받는다. 고치면 그 칸의 서버 오류를 지운다 */
const gadgetModel = computed<string[]>({
    get: () => form.value?.gadgetExclusions ?? [],
    set: (value) => {
        if (!form.value) return;
        form.value.gadgetExclusions = value;
        clearServer('gadgetExclusions');
    },
});

// ── 바뀐 칸 · 오류 ─

const changes = computed(() => (form.value && saved.value ? UtilBoard.detailChanges(form.value, saved.value) : { basic: 0, write: 0, prefix: 0, guide: 0, exposure: 0, protect: 0, reaction: 0, staff: 0 }));
const changeCount = computed(() => Object.values(changes.value).reduce((a, b) => a + b, 0));
const dirty = computed(() => changeCount.value > 0);
const diffNow = computed(() => (form.value ? UtilBoard.diffCount(form.value.policy, form.value.writeLock) : 0));
const activeOwn = computed(() => (form.value ? UtilBoard.activePrefixCount(form.value.prefixes) : 0));

const commonNames = computed(() => (form.value?.commonPrefixes ?? []).map((p) => t(`board.commonPrefix.${p.common}`)));
const prefixProblems = computed(() => (form.value ? UtilBoard.prefixProblems(form.value.prefixes, commonNames.value) : {}));
const policyProblems = computed(() => (form.value ? UtilBoard.policyProblems(form.value.policy) : {}));
/** 최소 자격 칸 문제 — 비회원을 받지 않는 종류의 GUEST · 핫딜 글쓰기의 첫 등급 */
const firstGradeKey = computed(() => grades.value[0]?.gradeKey ?? null);
const levelProblems = computed<Record<string, BoardLevelProblem | undefined>>(() => (form.value && saved.value ? UtilBoard.levelProblems(saved.value.kind, form.value.policy, firstGradeKey.value) : {}));
/** 기본 정보·안내문의 화면 검사 문제 */
const textProblems = computed(() => {
    const f = form.value;
    if (!f) return {};
    const out: Record<string, ReturnType<typeof UtilBoard.nameProblem>> = {
        name: UtilBoard.nameProblem(f.name),
        nameEn: UtilBoard.nameEnProblem(f.nameEn),
        description: UtilBoard.descProblem(f.description),
        introText: UtilBoard.textProblem(f.introText),
        writeGuideText: UtilBoard.textProblem(f.writeGuideText),
    };
    return Object.fromEntries(Object.entries(out).filter(([, v]) => v));
});
const localErrorCount = computed(() => Object.keys(textProblems.value).length + Object.keys(prefixProblems.value).length + Object.keys(policyProblems.value).length + Object.keys(levelProblems.value).length + (activeOwn.value > UtilBoard.PREFIX_ACTIVE_MAX ? 1 : 0));
const errorCount = computed(() => localErrorCount.value + Object.keys(serverFields.value).filter((k) => !textProblems.value[k] && !(policyProblems.value as Record<string, unknown>)[k]).length);
const prefixErrorCount = computed(() => Object.keys(prefixProblems.value).length + Object.keys(serverFields.value).filter((k) => k.startsWith('prefixes')).length + (activeOwn.value > UtilBoard.PREFIX_ACTIVE_MAX ? 1 : 0));
const canSave = computed(() => (canManage.value || canSettings.value || canStaffLimit.value) && dirty.value && localErrorCount.value === 0);

function invalidOf(fields: readonly string[]): number {
    return fields.filter((f) => Boolean(serverFields.value[f]) || Boolean(textProblems.value[f]) || Boolean((policyProblems.value as Record<string, unknown>)[f]) || Boolean((levelProblems.value as Record<string, unknown>)[f])).length;
}

function textErrorOf(field: string): string {
    const problem = textProblems.value[field];
    if (problem) return UtilBoardMessages.problem(t, problem);
    return serverFields.value[field] ?? '';
}

function prefixErrorOf(p: BoardPrefixForm, index: number): string {
    const problem = prefixProblems.value[p.key];
    if (problem) return UtilBoardMessages.problem(t, problem);
    const sentAt = sentPrefixes.findIndex((s) => s.key === p.key);
    return serverFields.value[`prefixes[${sentAt >= 0 ? sentAt : index}].name`] ?? '';
}

// ── 정책 칸 ─

function editableOf(field: BoardPolicyField): boolean {
    return can(UtilBoard.SPECS[field].permission) && !saving.value;
}

/** 정책 칸 하나에 넘길 값 */
function policyBind(field: BoardPolicyField) {
    const f = form.value!;
    const d = saved.value!.defaults;
    const problem = policyProblems.value[field];
    return {
        field,
        modelValue: f.policy[field] as BoardFieldValue,
        inheritable: true,
        defaultValue: d[field] as BoardFieldValue,
        grades: grades.value,
        editable: editableOf(field),
        error: problem ? UtilBoardMessages.numProblem(t, field, problem) : levelProblems.value[field] ? t(levelProblems.value[field] === 'DEAL_FIRST_GRADE' ? 'board.problemDealFirstGrade' : 'board.problemGuestKind') : (serverFields.value[field] ?? ''),
    };
}

function onPolicy(field: BoardPolicyField, value: BoardFieldValue) {
    if (!form.value) return;
    (form.value.policy as Record<BoardPolicyField, BoardFieldValue>)[field] = value;
    clearServer(field);
}

function clearServer(field: string) {
    if (!serverFields.value[field]) return;
    const { [field]: _removed, ...rest } = serverFields.value;
    serverFields.value = rest;
}

/** 이미지 올리기는 고급 글 에디터에서만 쓰인다 — 유효 글 에디터가 평문·간단이면 칸을 끄고 까닭을 보인다(값은 그대로 저장된다) */
function writeInactive(field: (typeof WRITE_FIELDS)[number]): boolean {
    if (field !== 'imageUploadAllowed' || !form.value || !saved.value) return false;
    return UtilBoard.imageUploadIdle(UtilBoard.effective(form.value.policy, saved.value.defaults, 'postEditor'));
}

/** 잠금이 걸리면 최소 자격 칸은 지금 쓰이지 않는다 · 이미지 올리기는 고급 에디터가 아니면 쓰이지 않는다 */
function writeNote(field: (typeof WRITE_FIELDS)[number]): string {
    if (writeInactive(field)) return t('board.inactiveNote.imageEditor');
    const lock = form.value?.writeLock;
    if (!lock) return '';
    if (field === 'writeMin' || (field === 'commentMin' && lock === 'POST_COMMENT')) return t('board.lockedNote');
    if (field === 'commentMin' && lock === 'POST') return '';
    return '';
}

/** 예약 삭제 시간은 예약 삭제일 때만 · 적립 최소 글자 수 적용은 포인트를 주는 게시판일 때만 쓰인다(유효값 기준 — 값은 그대로 저장된다) */
function protectInactive(field: (typeof PROTECT_FIELDS)[number]): boolean {
    if (!form.value || !saved.value) return false;
    if (field === 'pointMinLengthApplied') return UtilBoard.effective(form.value.policy, saved.value.defaults, 'pointMode') === 'OFF';
    if (field !== 'commentProtectDelayMinutes') return false;
    return UtilBoard.effective(form.value.policy, saved.value.defaults, 'commentProtectDelete') !== 'DELAY';
}

function protectNote(field: (typeof PROTECT_FIELDS)[number]): string {
    if (!form.value || !saved.value) return '';
    if (protectInactive(field)) return t(field === 'pointMinLengthApplied' ? 'board.inactiveNote.pointOff' : 'board.inactiveNote.delay');
    const off = UtilBoard.effective(form.value.policy, saved.value.defaults, 'commentProtectMin') === 0;
    if (off && (field === 'commentProtectEdit' || field === 'commentProtectDelete')) return t('board.inactiveNote.protectOff');
    return '';
}

/** 비추천 이름은 비추천을 보일 때만 쓰인다(유효값 기준 — 값은 그대로 저장된다) */
function reactionInactive(field: (typeof REACTION_FIELDS)[number]): boolean {
    if (field !== 'commentDislikeLabel' || !form.value || !saved.value) return false;
    return !UtilBoard.effective(form.value.policy, saved.value.defaults, 'commentDislikeEnabled');
}

function reactionNote(field: (typeof REACTION_FIELDS)[number]): string {
    if (!form.value || !saved.value) return '';
    if (reactionInactive(field)) return t('board.inactiveNote.dislikeOff');
    if (field === 'pinRequestReviewers' && UtilBoard.effective(form.value.policy, saved.value.defaults, 'pinRequestReviewers').length === 0) return t('board.inactiveNote.pinRequestOff');
    return '';
}

// ── 읽기 · 저장 ─

watch(slug, () => void load());
onMounted(() => {
    void appearance.ensureLoaded();
    void load();
});

async function load() {
    if (!slug.value) return;
    loading.value = true;
    loadError.value = '';
    notFound.value = false;
    saved.value = null;
    staff.value = null;
    try {
        apply(await api.bySlug(slug.value));
        void loadStaff();
    } catch (e) {
        notFound.value = e instanceof ApiError && e.status === 404;
        loadError.value = UtilBoardMessages.of(t, e);
    } finally {
        loading.value = false;
    }
}

function apply(detail: BoardDetail) {
    saved.value = detail;
    form.value = UtilBoard.detailFormOf(detail);
    serverFields.value = {};
    sentPrefixes = [];
}

function revert() {
    if (!saved.value) return;
    form.value = UtilBoard.detailFormOf(saved.value);
    serverFields.value = {};
    formError.value = '';
}

async function save() {
    if (saving.value || !canSave.value || !form.value || !saved.value) return;
    const body = UtilBoard.toSaveRequest(form.value);
    if (!body) return;
    // 투자 고지는 켜기만 된다 — 새로 켜면 먼저 묻는다
    if (body.investmentNotice && !saved.value.investmentNotice) {
        const ok = await confirm({ title: t('board.investmentConfirmTitle'), message: t('board.investmentConfirmMessage'), confirmLabel: t('board.investmentConfirm'), cancelLabel: t('common.cancel') });
        if (!ok) return;
    }
    formError.value = '';
    serverFields.value = {};
    sentPrefixes = [...form.value.prefixes];
    saving.value = true;
    const previousSlug = saved.value.slug;
    try {
        apply(await api.save(saved.value.id, body));
        toast.success(t('board.saved'));
        if (saved.value && saved.value.slug !== previousSlug) await router.replace(`/boards/${saved.value.slug}`);
    } catch (e) {
        const rejected = UtilBoard.fieldErrorsOf(e);
        if (rejected) {
            serverFields.value = rejected.fields;
            formError.value = e instanceof ApiError && e.status === 409 ? e.message : t('board.saveFieldsRejected');
        } else {
            formError.value = `${t('board.saveFailed')} — ${UtilBoardMessages.of(t, e)}`;
        }
    } finally {
        saving.value = false;
    }
}

// ── 말머리 ─

async function addPrefix() {
    if (!form.value || activeOwn.value >= UtilBoard.PREFIX_ACTIVE_MAX) return;
    const row = UtilBoard.newPrefix(++prefixSeq);
    form.value.prefixes = [...form.value.prefixes, row];
    await nextTick();
    document.querySelector<HTMLInputElement>(`input[name="prefix-${row.key}"]`)?.focus();
}

function movePrefix(index: number, step: -1 | 1) {
    if (!form.value) return;
    form.value.prefixes = UtilBoard.moved(form.value.prefixes, index, step);
}

/** 목록에서만 뺀다(되돌리기로 복구) — 저장하면 서버가 지운다. 쓰는 글이 있는 말머리는 단추가 잠겨 있다 */
function removePrefix(index: number) {
    if (!form.value) return;
    const row = form.value.prefixes[index];
    if (!row || row.usageCount > 0) return;
    form.value.prefixes = form.value.prefixes.filter((p) => p.key !== row.key);
}

// ── 매니저·스텝(바로 동작) ─

const staff = ref<BoardStaffMember[] | null>(null);
const staffLoading = ref(false);
const staffError = ref('');
const staffBusy = ref(false);

async function loadStaff() {
    if (!saved.value) return;
    staffLoading.value = true;
    staffError.value = '';
    try {
        staff.value = await api.staff(saved.value.id);
    } catch (e) {
        staff.value = null;
        staffError.value = UtilBoardMessages.of(t, e);
    } finally {
        staffLoading.value = false;
    }
}

function staffOfRole(role: BoardStaffRole): BoardStaffMember[] {
    return (staff.value ?? []).filter((m) => m.role === role);
}

/** 인원 상한 — 저장된 유효값(매니저: 게시판 값 ?? 기본값, 스텝: 기본값). 저장 전 입력은 반영하지 않는다 */
function capOf(role: BoardStaffRole): number {
    if (!saved.value) return 0;
    return role === 'MANAGER' ? (saved.value.policy.managerMax ?? saved.value.defaults.managerMax) : saved.value.defaults.staffMax;
}

/** 지정 단추를 막는 이유 — 종류 · 꺼짐 없음 · 인원 상한 */
function appointBlockedReason(role: BoardStaffRole): string {
    if (!saved.value) return '';
    if (saved.value.kind !== 'GENERAL') return t('board.staffKindBlocked');
    if (staffOfRole(role).length >= capOf(role)) return t('board.staffCapReached', { max: capOf(role) });
    return '';
}

const appointOpen = ref(false);
const appointRole = ref<BoardStaffRole>('MANAGER');
const candidateQuery = ref('');
const candidates = ref<BoardStaffCandidate[] | null>(null);
const candidatesLoading = ref(false);
const candidatesError = ref('');
const appointingId = ref('');
let searchTimer: ReturnType<typeof setTimeout> | null = null;
let searchSeq = 0;

function openAppoint(role: BoardStaffRole) {
    appointRole.value = role;
    candidateQuery.value = '';
    candidates.value = null;
    appointOpen.value = true;
    void searchCandidates();
}

watch(candidateQuery, () => {
    if (searchTimer) clearTimeout(searchTimer);
    searchTimer = setTimeout(() => void searchCandidates(), 300);
});

async function searchCandidates() {
    if (!saved.value) return;
    const seq = ++searchSeq;
    candidatesLoading.value = true;
    candidatesError.value = '';
    try {
        const list = await api.staffCandidates(saved.value.id, candidateQuery.value);
        if (seq === searchSeq) candidates.value = list;
    } catch (e) {
        if (seq === searchSeq) candidatesError.value = UtilBoardMessages.of(t, e);
    } finally {
        if (seq === searchSeq) candidatesLoading.value = false;
    }
}

async function appoint(c: BoardStaffCandidate) {
    if (!saved.value || staffBusy.value) return;
    const role = t(`board.role.${appointRole.value}`);
    const ok = await confirm({ title: t('board.appointConfirmTitle', { name: c.nickname, role }), message: t('board.appointConfirmMessage', { board: saved.value.name, role }), confirmLabel: t('board.appoint'), cancelLabel: t('common.cancel') });
    if (!ok) return;
    staffBusy.value = true;
    appointingId.value = c.memberId;
    try {
        staff.value = await api.appointStaff(saved.value.id, { memberId: c.memberId, role: appointRole.value });
        appointOpen.value = false;
        toast.success(t('board.appointed', { name: c.nickname, role }));
    } catch (e) {
        toast.error(`${t('board.appointFailed')} — ${UtilBoardMessages.of(t, e)}`, 8000);
    } finally {
        staffBusy.value = false;
        appointingId.value = '';
    }
}

async function release(m: BoardStaffMember) {
    if (!saved.value || staffBusy.value) return;
    const role = t(`board.role.${m.role}`);
    const ok = await confirm({ title: t('board.releaseConfirmTitle', { name: m.nickname, role }), message: t('board.releaseConfirmMessage'), confirmLabel: t('board.release'), cancelLabel: t('common.cancel'), tone: 'danger' });
    if (!ok) return;
    staffBusy.value = true;
    try {
        staff.value = await api.removeStaff(saved.value.id, m.memberId);
        toast.success(t('board.released', { name: m.nickname, role }));
    } catch (e) {
        toast.error(`${t('board.releaseFailed')} — ${UtilBoardMessages.of(t, e)}`, 8000);
    } finally {
        staffBusy.value = false;
    }
}

// ── 위험 구역(바로 동작) ─

const activeBusy = ref(false);

async function toggleActive() {
    if (!saved.value || activeBusy.value) return;
    const next = !saved.value.active;
    const name = localizedName(saved.value.name, saved.value.nameEn);
    if (!next) {
        const ok = await confirm({ title: t('board.deactivateTitle', { name }), message: t('board.deactivateMessage'), confirmLabel: t('board.deactivateConfirm'), cancelLabel: t('common.cancel'), tone: 'danger' });
        if (!ok) return;
    }
    activeBusy.value = true;
    try {
        const item = await api.setActive(saved.value.id, next);
        saved.value = { ...saved.value, active: item.active };
        toast.success(t(item.active ? 'board.activated' : 'board.deactivated', { name }));
    } catch (e) {
        toast.error(`${t('board.activeFailed')} — ${UtilBoardMessages.of(t, e)}`, 8000);
    } finally {
        activeBusy.value = false;
    }
}

const deleteOpen = ref(false);
const deleteName = ref('');
const deleting = ref(false);
const deleteError = ref('');

function openDelete() {
    deleteName.value = '';
    deleteError.value = '';
    deleteOpen.value = true;
}

async function remove() {
    if (!saved.value || deleting.value || deleteName.value.trim() !== saved.value.name) return;
    deleting.value = true;
    deleteError.value = '';
    try {
        const name = saved.value.name;
        await api.remove(saved.value.id);
        deleteOpen.value = false;
        // 지웠으니 저장하지 않은 변경을 묻지 않고 떠난다
        saved.value = null;
        form.value = null;
        toast.success(t('board.deleted', { name }));
        await router.push('/boards');
    } catch (e) {
        deleteError.value = UtilBoardMessages.of(t, e);
    } finally {
        deleting.value = false;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('board.leaveTitle'), message: t('board.leaveMessage'), confirmLabel: t('board.leaveConfirm'), cancelLabel: t('board.leaveCancel'), tone: 'danger' });
});
onBeforeRouteUpdate(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('board.leaveTitle'), message: t('board.leaveMessage'), confirmLabel: t('board.leaveConfirm'), cancelLabel: t('board.leaveCancel'), tone: 'danger' });
});
</script>

<style scoped>
/* 뱃지 글자 상하 중앙(`components.md` 2-4) */
.board-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.board-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}

/* 표시색 고르개 — 원형 견본. 선택은 윤곽(색만으로 구분하지 않고 이름은 aria-label·title 이 말한다) */
.color-opt {
    display: inline-grid;
    place-items: center;
    width: 2rem;
    height: 2rem;
    border: 1px solid var(--line);
    border-radius: 9999px;
    cursor: pointer;
}
.color-opt:has(input:checked) {
    outline: 2px solid var(--p-text-color);
    outline-offset: 1px;
}
.color-opt:has(input:focus-visible) {
    box-shadow: 0 0 0 4px var(--p-focus-ring-color, var(--p-primary-color));
}
.color-opt--disabled {
    cursor: default;
}
.color-opt:has(input:disabled:not(:checked)) {
    opacity: 0.5;
}
.color-opt__dot {
    width: 1.1rem;
    height: 1.1rem;
    border-radius: 9999px;
}
.color-opt__auto {
    width: 1.1rem;
    height: 1.1rem;
    border: 1.5px dashed var(--line-strong);
    border-radius: 9999px;
}
</style>
