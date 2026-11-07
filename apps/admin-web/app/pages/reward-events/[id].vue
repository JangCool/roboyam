<template>
    <!--
        보상 이벤트 › 만들기(`/reward-events/new`) · 상세(`/reward-events/{id}`) · 고치기(`/reward-events/{id}?edit=1`) — 한 화면이 셋을 맡는다(공구 `/group-buys/[id]` 와 같은 꼴).
        · 만들기·고치기: `RewardEventForm` + 아래 떠 있는 저장 줄(화면 하나로 한 번에 저장 — `forms.md` 2-4). 고치기 범위는 서버가 상태로 정한다(`actions.editScope`).
        · 상세 머리: 이름·상태·방식·기간 + 동작(서버가 정한 `actions` × 권한): 시작하기 · 마감하기 · 추첨하기 · 지급 실행 · 이벤트 취소(지급 전만 — 지급이 있으면 「취소 대신 회수」 안내) · 고치기.
          일괄 대량 지급이 진행 중이면 진행률을 보이고 1.5초마다 다시 묻는다(서버가 나눠 처리).
        · 탭(주소 `?tab=`): 개요(조건·등수·보상·숫자) · 대상·당첨자(표 — 펼치면 보상 줄별 결과, 당첨자 추가·빼기, 선택 회수, 직접 전달 완료 표시) · 추첨 기록(당첨자 선정 + 댓글 추첨만)
          · 지급 내역(실행 한 번 = 한 줄, 선착순 자동은 날마다 한 줄, [실패만 다시 지급]) · 기록(감사 — 전체 감사 기록 화면 링크).
        권한(서버 `Permission`): 화면 `reward-event.read` · 만들기·고치기·후보·당첨자·추첨·시작·마감·취소 `reward-event.manage`
          · 지급 실행·재시도·회수·전달 완료 `reward-event.grant` · 고액(1인 포인트가 문턱 초과 또는 일괄 + 포인트 — 서버 `grants/preview` 의 `largeRequired`)은 `point.adjust.large`.
          권한이 없는 단추는 숨긴다(막는 것은 서버). 붙여 넣기·넣기·회수는 한 번에 500건까지(서버 `BATCH_MAX` — 화면도 미리 막는다).
        서버 호출은 `useRewardEventAdminApi`(admin `RewardEventAdminController`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="pageTitle" :subtitle="pageSubtitle">
            <template #actions>
                <Button v-if="editing && !isNew" type="button" :label="t('rewardEvent.backToDetail')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="text" @click="stopEdit" />
                <Button v-else as="router-link" to="/reward-events" :label="t('rewardEvent.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="text" />
            </template>
        </PageTitle>

        <!-- 읽는 중 · 실패 -->
        <div v-if="!isNew && loading && !detail" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="7rem" />
            <Skeleton height="16rem" />
        </div>
        <AppGadget v-else-if="!isNew && !detail" :title="t('rewardEvent.detailLoadFailed')">
            <ErrorState :code="loadError?.status === 404 ? '404' : loadError?.status === 403 ? '403' : '!'" :title="loadError?.status === 403 ? t('rewardEvent.forbidden') : loadError?.status === 404 ? t('rewardEvent.notFound') : t('rewardEvent.detailLoadFailed')" :description="loadError?.message" :retry="loadError?.status !== 404 && loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <!-- 만들기 · 고치기 -->
        <template v-else-if="editing">
            <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('rewardEvent.readOnly') }}</Message>
            <div v-else-if="!catalog" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <ErrorState v-if="catalogError" :title="t('rewardEvent.catalogFailed')" :description="catalogError.message" retry @retry="loadCatalog" />
                <Skeleton v-else height="12rem" />
            </div>
            <form v-else-if="form" :key="formKey" class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <RewardEventForm
                    v-model="form"
                    :scope="isNew ? 'FULL' : (detail?.actions.editScope ?? 'MEMO')"
                    :catalog="catalog"
                    :is-new="isNew"
                    :server-errors="saveFieldErrors"
                    :show-errors="submitted"
                    :comment-preview="commentPreview"
                    :comment-preview-error="commentPreviewError"
                    :target-preview="targetPreview"
                    :preview-busy="previewBusy"
                    :granted="detail?.stats.targets ?? 0"
                    @preview-comments="onPreviewComments"
                    @preview-targets="onPreviewTargets"
                />
                <StickySaveBar :dirty="dirty" :saving="saving" :error-count="submitted ? problemCount : 0" :error="saveError" revertable submit @revert="revert">
                    <template #extra>
                        <Button v-if="!isNew" type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="saving" @click="stopEdit" />
                    </template>
                </StickySaveBar>
            </form>
        </template>

        <!-- 상세 -->
        <template v-else-if="detail">
            <AppGadget :title="t('rewardEvent.statusPanel')">
                <div class="flex flex-col gap-3">
                    <div class="flex flex-wrap items-center gap-2">
                        <Tag :value="t(`rewardEvent.status.${detail.status}`)" :severity="UtilRewardEvent.statusSeverity(detail.status)" />
                        <Tag :value="t(`rewardEvent.mode.${detail.mode}`)" severity="secondary" />
                        <span class="text-xs tabular-nums text-muted-color">{{ t('rewardEvent.periodLine', { from: time.full(detail.startAt), to: time.full(detail.endAt) }) }}</span>
                        <span class="text-xs text-muted-color">· {{ t('rewardEvent.createdLine', { name: UtilRewardEventMessages.operatorName(t, detail.createdBy), at: time.shortDateTime(detail.createdAt) }) }}</span>
                    </div>
                    <p v-if="detail.status === 'CANCELED' && detail.cancelReason" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('rewardEvent.canceledLine', { at: time.full(detail.canceledAt), reason: detail.cancelReason }) }}</p>
                    <p v-else class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`rewardEvent.statusHelp.${detail.status}`) }}</p>

                    <!-- 진행 중인 일괄 지급 -->
                    <div v-if="detail.runningRun" class="flex flex-col gap-1 rounded-lg border border-(--line) p-3" role="status">
                        <div class="flex items-center justify-between gap-2 text-xs">
                            <span class="font-medium"><i class="pi pi-spin pi-spinner mr-1" aria-hidden="true" />{{ t('rewardEvent.running') }}</span>
                            <span class="tabular-nums">{{ t('rewardEvent.runningLine', { done: detail.runningRun.progress.done.toLocaleString(locale), total: detail.runningRun.progress.total.toLocaleString(locale) }) }}</span>
                        </div>
                        <ProgressBar :value="percent(detail.runningRun.progress)" :show-value="false" class="h-2!" :aria-label="t('rewardEvent.running')" />
                    </div>

                    <div class="flex flex-wrap items-center gap-2">
                        <Button v-if="detail.actions.canStart && canManage" type="button" :label="t('rewardEvent.act.start')" icon="pi pi-play" size="small" :loading="acting === 'start'" :disabled="acting !== null" @click="doStart" />
                        <Button v-if="detail.actions.canClose && canManage" type="button" :label="t('rewardEvent.act.close')" icon="pi pi-stop-circle" size="small" severity="secondary" variant="outlined" :loading="acting === 'close'" :disabled="acting !== null" @click="doClose" />
                        <Button v-if="detail.actions.canDraw && canManage" type="button" :label="t('rewardEvent.act.draw')" icon="pi pi-sparkles" size="small" :disabled="acting !== null" @click="openDraw" />
                        <Button v-if="canGrantPerm && (detail.actions.canGrant || detail.actions.grantBlockedReason)" type="button" :label="t('rewardEvent.act.grant')" icon="pi pi-send" size="small" :disabled="!detail.actions.canGrant || acting !== null" @click="openGrant" />
                        <Button v-if="canManage && (detail.actions.canCancel || detail.actions.cancelBlockedReason)" type="button" :label="t('rewardEvent.act.cancel')" icon="pi pi-times-circle" size="small" severity="danger" variant="outlined" :disabled="!detail.actions.canCancel || acting !== null" @click="openCancel" />
                        <span v-if="canManage" class="mx-1 hidden h-5 w-px bg-(--line) sm:inline-block" aria-hidden="true" />
                        <Button v-if="canManage" type="button" :label="t('rewardEvent.act.edit')" icon="pi pi-pencil" size="small" severity="secondary" variant="text" :disabled="acting !== null" @click="startEdit" />
                        <Button v-if="detail.noticeUrl" as="a" :href="detail.noticeUrl" target="_blank" rel="noopener" :label="t('rewardEvent.noticeLink')" icon="pi pi-external-link" size="small" severity="secondary" variant="text" />
                    </div>
                    <p v-if="canGrantPerm && detail.actions.grantBlockedReason" class="m-0 text-xs text-muted-color">{{ t(`rewardEvent.grantBlocked.${detail.actions.grantBlockedReason}`) }}</p>
                    <p v-if="canManage && detail.actions.cancelBlockedReason" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('rewardEvent.cancelBlocked') }}</p>
                    <p v-if="detail.status === 'CLOSED' && detail.stats.pending === 0 && detail.stats.manualPending > 0 && !detail.runningRun" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('rewardEvent.deliveryHoldsDone', { n: detail.stats.manualPending.toLocaleString(locale) }) }}</p>
                    <p v-if="canManage && commentSource && detail.status === 'ACTIVE'" class="m-0 text-xs text-muted-color">{{ t('rewardEvent.drawAfterClose') }}</p>
                    <Message v-if="actionError" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ actionError }}</Message>
                </div>
            </AppGadget>

            <Tabs :value="tab" scrollable class="min-w-0" @update:value="(v: string | number) => selectTab(String(v) as DetailTab)">
                <TabList :aria-label="t('rewardEvent.tabsLabel')">
                    <Tab v-for="tb in tabList" :key="tb" :value="tb">{{ t(`rewardEvent.tab.${tb}`) }}</Tab>
                </TabList>
            </Tabs>

            <!-- 개요 -->
            <div v-if="tab === 'overview'" class="flex flex-col gap-4">
                <AppGadget :title="t('rewardEvent.overview.numbers')">
                    <dl class="m-0 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
                        <div v-for="n in numberCells" :key="n.key" class="flex flex-col gap-0.5">
                            <dt class="text-xs text-muted-color">{{ n.label }}</dt>
                            <dd class="m-0 text-lg font-semibold tabular-nums" :class="n.tone">{{ n.value }}</dd>
                        </div>
                    </dl>
                </AppGadget>

                <AppGadget :title="t('rewardEvent.overview.conditions')">
                    <dl class="m-0 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[10rem_1fr]">
                        <template v-for="row in conditionRows" :key="row.label">
                            <dt class="text-muted-color">{{ row.label }}</dt>
                            <dd class="m-0">{{ row.value }}</dd>
                        </template>
                    </dl>
                </AppGadget>

                <AppGadget :title="t('rewardEvent.overview.tiers')">
                    <ul class="m-0 flex list-none flex-col gap-2 p-0">
                        <li v-for="tier in detail.tiers" :key="tier.id" class="flex flex-col gap-1.5 rounded-lg border border-(--line) p-3">
                            <div class="flex flex-wrap items-baseline gap-2">
                                <span class="text-sm font-medium">{{ tier.name }}</span>
                                <span class="text-xs tabular-nums text-muted-color">{{ tierQuotaText(tier) }}</span>
                            </div>
                            <ul class="m-0 flex list-none flex-col gap-1 p-0">
                                <li v-for="line in tier.rewards" :key="line.id" class="flex items-center gap-2 text-sm">
                                    <i :class="UtilRewardEvent.kindIcon(line.kind)" class="text-xs text-muted-color" aria-hidden="true" />
                                    <span>{{ UtilRewardEventMessages.line(t, line, locale) }}</span>
                                    <span class="text-xs text-muted-color">{{ lineNote(line) }}</span>
                                </li>
                            </ul>
                        </li>
                    </ul>
                </AppGadget>
            </div>

            <!-- 대상·당첨자 -->
            <div v-else-if="tab === 'recipients'" class="flex flex-col gap-3">
                <Message v-if="detail.mode === 'BULK' && detail.stats.targets === 0" severity="info" :closable="false" size="small">{{ t('rewardEvent.rcp.bulkBeforeGrant') }}</Message>
                <form class="flex flex-wrap items-center gap-2" role="search" :aria-label="t('rewardEvent.rcp.searchLabel')" @submit.prevent="searchRecipients">
                    <label class="sr-only" :for="ids.rq">{{ t('rewardEvent.rcp.searchLabel') }}</label>
                    <InputText :id="ids.rq" v-model="rFilter.q" type="search" maxlength="100" autocomplete="off" :placeholder="t('rewardEvent.rcp.searchPlaceholder')" class="w-full max-w-xs" />
                    <label class="sr-only" :for="ids.rs">{{ t('rewardEvent.rcp.col.status') }}</label>
                    <Select v-model="rFilter.status" :input-id="ids.rs" :options="recipientStatusOptions" option-label="label" option-value="value" class="w-40" @update:model-value="searchRecipients" />
                    <template v-if="detail.tiers.length > 1">
                        <label class="sr-only" :for="ids.rt">{{ t('rewardEvent.rcp.col.tier') }}</label>
                        <Select v-model="rFilter.tierId" :input-id="ids.rt" :options="tierFilterOptions" option-label="label" option-value="value" class="w-40" @update:model-value="searchRecipients" />
                    </template>
                    <Button type="submit" :label="t('common.search')" size="small" :loading="rBusy" />
                    <span class="flex-1" />
                    <Button v-if="canManage && detail.actions.canAddRecipients" type="button" :label="t('rewardEvent.rcp.add')" icon="pi pi-user-plus" size="small" severity="secondary" variant="outlined" @click="openAdd" />
                    <Button v-if="canGrantPerm && detail.status !== 'CANCELED'" type="button" :label="t('rewardEvent.rcp.revokeSelected', { n: picked.size })" icon="pi pi-undo" size="small" severity="danger" variant="outlined" :disabled="!picked.size || picked.size > UtilRewardEvent.BATCH_MAX || acting !== null" @click="openRevoke" />
                </form>
                <p v-if="canGrantPerm && picked.size > UtilRewardEvent.BATCH_MAX" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ t('rewardEvent.rcp.revokeTooMany', { max: UtilRewardEvent.BATCH_MAX }) }}</p>

                <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                    <DataTable
                        v-model:expanded-rows="expanded"
                        :value="rRows"
                        data-key="id"
                        lazy
                        paginator
                        :first="rFirst"
                        :rows="rSize"
                        :total-records="rTotal"
                        :rows-per-page-options="[20, 50, 100]"
                        :loading="rBusy"
                        scrollable
                        :current-page-report-template="totalTemplate"
                        paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                        @page="onRecipientPage"
                    >
                        <template #empty>
                            <ErrorState v-if="rError" :title="t('rewardEvent.rcp.loadFailed')" :description="rError.message" retry :retrying="rBusy" @retry="loadRecipients" />
                            <div v-else-if="rBusy" class="h-24" aria-hidden="true" />
                            <EmptyState v-else-if="rApplied.q || rApplied.status || rApplied.tierId" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                            <EmptyState v-else icon="pi pi-users" :title="t('rewardEvent.rcp.empty')" :description="t(`rewardEvent.rcp.emptyHint.${detail.mode}`)" />
                        </template>

                        <Column v-if="canGrantPerm" style="width: 2.5rem">
                            <template #header>
                                <Checkbox :model-value="allPicked" binary :disabled="!revocableOnPage.length" :aria-label="t('rewardEvent.rcp.pickAll')" @update:model-value="pickAll" />
                            </template>
                            <template #body="{ data }">
                                <Checkbox v-if="revocable(data)" :model-value="picked.has(data.id)" binary :aria-label="t('rewardEvent.rcp.pickOne', { name: UtilRewardEventMessages.memberName(t, data.member) })" @update:model-value="(v: boolean) => pickOne(data.id, v)" />
                            </template>
                        </Column>
                        <Column expander style="width: 3rem" />
                        <Column :header="t('rewardEvent.rcp.col.member')" style="min-width: 10rem">
                            <template #body="{ data }">
                                <div class="min-w-0">
                                    <NuxtLink v-if="canReadMember && !data.member.withdrawn" :to="{ path: '/members', query: { memberNo: data.member.id } }" class="block truncate text-[13px] text-(--p-primary-color) hover:underline">{{ UtilRewardEventMessages.memberName(t, data.member) }}</NuxtLink>
                                    <span v-else class="block truncate text-[13px]" :class="data.member.withdrawn ? 'text-muted-color' : ''">{{ UtilRewardEventMessages.memberName(t, data.member) }}</span>
                                    <code class="block truncate text-[11px] tabular-nums text-muted-color">{{ data.member.id }}<template v-if="data.member.withdrawn"> · {{ t('rewardEvent.rcp.withdrawn') }}</template></code>
                                </div>
                            </template>
                        </Column>
                        <Column v-if="detail.tiers.length > 1 || detail.mode === 'SELECTION'" :header="t('rewardEvent.rcp.col.tier')" style="min-width: 6rem">
                            <template #body="{ data }"><span class="text-[13px]">{{ data.tierName }}</span></template>
                        </Column>
                        <Column :header="t('rewardEvent.rcp.col.method')" style="min-width: 8rem">
                            <template #body="{ data }">
                                <div class="text-[12px]">{{ t(`rewardEvent.method.${data.method}`) }}</div>
                                <div class="text-[11px] tabular-nums text-muted-color">{{ time.shortDateTime(data.selectedAt) }} · {{ UtilRewardEventMessages.operatorName(t, data.selectedBy) }}</div>
                            </template>
                        </Column>
                        <Column :header="t('rewardEvent.rcp.col.status')" style="min-width: 9rem">
                            <template #body="{ data }">
                                <div class="flex flex-col items-start gap-0.5">
                                    <Tag :value="t(`rewardEvent.recipientStatus.${data.status}`)" :severity="UtilRewardEvent.recipientSeverity(data.status)" />
                                    <span v-if="firstIssue(data)" class="text-[11px] text-muted-color">{{ firstIssue(data) }}</span>
                                    <span v-if="data.revokeReason" class="max-w-[14rem] truncate text-[11px] text-muted-color" :title="data.revokeReason">{{ t('rewardEvent.rcp.revokeReason', { reason: data.revokeReason }) }}</span>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('rewardEvent.rcp.col.actions')" style="min-width: 8rem">
                            <template #body="{ data }">
                                <div class="flex flex-wrap gap-1">
                                    <Button v-for="res in pendingDeliveries(data)" :key="res.lineId" type="button" :label="t('rewardEvent.rcp.markDelivered')" icon="pi pi-check" size="small" severity="secondary" variant="outlined" :disabled="!canGrantPerm || acting !== null" @click="openDelivered(data, res.lineId)" />
                                    <Button v-if="canManage && removable(data)" type="button" icon="pi pi-trash" size="small" severity="secondary" variant="text" :aria-label="t('rewardEvent.rcp.remove', { name: UtilRewardEventMessages.memberName(t, data.member) })" :disabled="acting !== null" @click="removeRecipient(data)" />
                                </div>
                            </template>
                        </Column>

                        <template #expansion="{ data }">
                            <ul class="m-0 flex list-none flex-col gap-1.5 p-2 pl-12">
                                <li v-for="res in data.results" :key="res.lineId" class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[12px]">
                                    <i :class="UtilRewardEvent.kindIcon(res.kind)" class="text-xs text-muted-color" aria-hidden="true" />
                                    <span class="min-w-32">{{ lineName(data.tierId, res.lineId) }}</span>
                                    <Tag :value="t(`rewardEvent.lineStatus.${res.status}`)" :severity="UtilRewardEvent.lineSeverity(res.status)" />
                                    <span v-if="res.reason" class="text-muted-color">{{ t(`rewardEvent.reason.${res.reason}`) }}</span>
                                    <span v-if="res.revokedPoints !== null" class="tabular-nums text-muted-color">{{ t('rewardEvent.rcp.revokedPoints', { n: res.revokedPoints.toLocaleString(locale) }) }}</span>
                                    <span v-if="res.delivered" class="text-muted-color">{{ t('rewardEvent.rcp.deliveredLine', { at: time.shortDateTime(res.delivered.at), name: UtilRewardEventMessages.operatorName(t, res.delivered.operator) }) }}<template v-if="res.delivered.memo"> · {{ res.delivered.memo }}</template></span>
                                    <span v-else-if="res.at" class="tabular-nums text-muted-color">{{ time.shortDateTime(res.at) }}</span>
                                </li>
                            </ul>
                        </template>
                    </DataTable>
                </div>
            </div>

            <!-- 추첨 기록 -->
            <div v-else-if="tab === 'draws'" class="flex flex-col gap-3">
                <ErrorState v-if="drawsError" :title="t('rewardEvent.draws.loadFailed')" :description="drawsError.message" retry @retry="loadDraws" />
                <Skeleton v-else-if="draws === null" height="8rem" />
                <EmptyState v-else-if="!draws.length" icon="pi pi-sparkles" :title="t('rewardEvent.draws.empty')" :description="t('rewardEvent.draws.emptyHint')" />
                <AppGadget v-for="d in draws ?? []" :key="d.id" :title="t('rewardEvent.draws.item', { at: time.full(d.at), tier: d.tierName })">
                    <div class="flex flex-col gap-2 text-sm">
                        <dl class="m-0 grid gap-x-6 gap-y-1 sm:grid-cols-[8rem_1fr]">
                            <dt class="text-muted-color">{{ t('rewardEvent.draws.operator') }}</dt>
                            <dd class="m-0">{{ UtilRewardEventMessages.operatorName(t, d.operator) }}</dd>
                            <dt class="text-muted-color">{{ t('rewardEvent.draws.source') }}</dt>
                            <dd class="m-0">{{ d.postTitle ?? '—' }} <span class="text-xs tabular-nums text-muted-color">#{{ d.postId }}</span></dd>
                            <dt class="text-muted-color">{{ t('rewardEvent.draws.counts') }}</dt>
                            <dd class="m-0 tabular-nums">{{ t('rewardEvent.draws.countsLine', { candidates: d.candidateCount.toLocaleString(locale), picked: d.picked.toLocaleString(locale) }) }}</dd>
                            <dt class="text-muted-color">{{ t('rewardEvent.draws.seed') }}</dt>
                            <dd class="m-0"><code class="text-xs">{{ d.seed }}</code></dd>
                        </dl>
                        <div class="flex flex-wrap gap-1.5">
                            <Tag v-for="m in d.members" :key="m.id" :value="UtilRewardEventMessages.memberName(t, m)" severity="secondary" />
                        </div>
                    </div>
                </AppGadget>
                <p class="m-0 text-xs text-muted-color">{{ t('rewardEvent.draws.note') }}</p>
            </div>

            <!-- 지급 내역 -->
            <div v-else-if="tab === 'runs'" class="flex flex-col gap-3">
                <div v-if="canGrantPerm && detail.actions.canRetry" class="flex flex-wrap items-center gap-2">
                    <Button type="button" :label="t('rewardEvent.runs.retry', { n: (detail.stats.failed + detail.stats.partial).toLocaleString(locale) })" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" :loading="acting === 'retry'" :disabled="acting !== null" @click="doRetry" />
                    <span class="text-xs text-muted-color">{{ t('rewardEvent.runs.retryHelp') }}</span>
                </div>
                <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                    <DataTable :value="runs ?? []" data-key="id" :loading="runs === null && !runsError" scrollable paginator :rows="20" :always-show-paginator="false">
                        <template #empty>
                            <ErrorState v-if="runsError" :title="t('rewardEvent.runs.loadFailed')" :description="runsError.message" retry @retry="loadRuns" />
                            <EmptyState v-else-if="runs !== null" icon="pi pi-send" :title="t('rewardEvent.runs.empty')" />
                        </template>
                        <Column :header="t('rewardEvent.runs.col.at')" style="min-width: 9rem">
                            <template #body="{ data }">
                                <div class="text-[12px] tabular-nums">{{ data.kind === 'AUTO' && data.day ? t('rewardEvent.runs.dayLine', { day: data.day }) : time.shortDateTime(data.at) }}</div>
                                <div class="text-[11px] text-muted-color">{{ UtilRewardEventMessages.operatorName(t, data.operator) }}</div>
                            </template>
                        </Column>
                        <Column :header="t('rewardEvent.runs.col.kind')" style="min-width: 6rem">
                            <template #body="{ data }"><Tag :value="t(`rewardEvent.runKind.${data.kind}`)" :severity="UtilRewardEvent.runSeverity(data.kind)" /></template>
                        </Column>
                        <Column :header="t('rewardEvent.runs.col.targets')" style="min-width: 5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                            <template #body="{ data }"><div class="text-right text-[13px] tabular-nums">{{ data.targetCount.toLocaleString(locale) }}</div></template>
                        </Column>
                        <Column :header="t('rewardEvent.runs.col.result')" style="min-width: 12rem">
                            <template #body="{ data }">
                                <div class="text-[12px] tabular-nums">{{ UtilRewardEventMessages.runResult(t, data, locale) }}</div>
                                <div v-if="data.reason" class="max-w-[18rem] truncate text-[11px] text-muted-color" :title="data.reason">{{ t('rewardEvent.rcp.revokeReason', { reason: data.reason }) }}</div>
                            </template>
                        </Column>
                        <Column :header="t('rewardEvent.runs.col.amount')" style="min-width: 9rem">
                            <template #body="{ data }">
                                <div class="text-[12px] tabular-nums">{{ data.kind === 'REVOKE' ? t('rewardEvent.runs.revokedAmount', { points: data.points.toLocaleString(locale), items: data.items.toLocaleString(locale) }) : t('rewardEvent.runs.amount', { points: data.points.toLocaleString(locale), items: data.items.toLocaleString(locale) }) }}</div>
                            </template>
                        </Column>
                        <Column :header="t('rewardEvent.runs.col.progress')" style="min-width: 9rem">
                            <template #body="{ data }">
                                <div v-if="data.status === 'RUNNING'" class="flex flex-col gap-1">
                                    <ProgressBar :value="percent(data.progress)" :show-value="false" class="h-1.5!" :aria-label="t('rewardEvent.running')" />
                                    <span class="text-[11px] tabular-nums text-muted-color">{{ t('rewardEvent.runningLine', { done: data.progress.done.toLocaleString(locale), total: data.progress.total.toLocaleString(locale) }) }}</span>
                                </div>
                                <span v-else class="text-[12px] text-muted-color">{{ t('rewardEvent.runs.done') }}</span>
                            </template>
                        </Column>
                    </DataTable>
                </div>
            </div>

            <!-- 기록 -->
            <div v-else-if="tab === 'audit'" class="flex flex-col gap-3">
                <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                    <DataTable :value="auditRows ?? []" data-key="id" :loading="auditRows === null && !auditError" scrollable paginator :rows="20" :always-show-paginator="false">
                        <template #empty>
                            <ErrorState v-if="auditError" :title="t('rewardEvent.audit.loadFailed')" :description="auditError.message" retry @retry="loadAudit" />
                            <EmptyState v-else-if="auditRows !== null" icon="pi pi-history" :title="t('rewardEvent.audit.empty')" />
                        </template>
                        <Column :header="t('rewardEvent.audit.col.at')" style="min-width: 10rem">
                            <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ time.full(data.at) }}</span></template>
                        </Column>
                        <Column :header="t('rewardEvent.audit.col.operator')" style="min-width: 7rem">
                            <template #body="{ data }"><span class="text-[12px]">{{ UtilRewardEventMessages.operatorName(t, data.operator) }}</span></template>
                        </Column>
                        <Column :header="t('rewardEvent.audit.col.action')" style="min-width: 7rem">
                            <template #body="{ data }"><Tag :value="t(`rewardEvent.auditAction.${data.action}`)" severity="secondary" /></template>
                        </Column>
                        <Column :header="t('rewardEvent.audit.col.detail')" style="min-width: 16rem">
                            <template #body="{ data }"><span class="text-[12px]">{{ data.detail }}</span></template>
                        </Column>
                    </DataTable>
                </div>
                <p class="m-0 flex flex-wrap items-center gap-2 text-xs text-muted-color">
                    {{ t('rewardEvent.audit.note') }}
                    <NuxtLink v-if="can('audit.read')" to="/settings/audit" class="text-(--p-primary-color) hover:underline">{{ t('rewardEvent.audit.allLink') }} ›</NuxtLink>
                </p>
            </div>
        </template>

        <!-- 대화상자 -->
        <RewardEventGrantDialog v-if="detail" v-model:visible="grantOpen" :event-name="detail.name" :tiers="detail.tiers" :preview="grantPreview" :can-large="can('point.adjust.large')" :is-bulk="detail.mode === 'BULK'" :busy="acting === 'grant'" :error="dialogError" @submit="doGrant" />
        <RewardEventReasonDialog
            v-if="detail"
            v-model:visible="cancelOpen"
            :title="t('rewardEvent.cancel.title')"
            :description="t('rewardEvent.cancel.lead', { name: detail.name })"
            :notes="[t('rewardEvent.cancel.note1'), t('rewardEvent.cancel.note2')]"
            :confirm-label="t('rewardEvent.act.cancel')"
            :busy="acting === 'cancel'"
            :error="dialogError"
            :field-error="dialogFieldErrors.reason"
            @submit="doCancel"
        />
        <RewardEventReasonDialog
            v-if="detail"
            v-model:visible="revokeOpen"
            :title="t('rewardEvent.revoke.title')"
            :description="t('rewardEvent.revoke.lead', { n: picked.size })"
            :notes="[t('rewardEvent.revoke.note1'), t('rewardEvent.revoke.note2'), t('rewardEvent.revoke.note3')]"
            :confirm-label="t('rewardEvent.revoke.submit')"
            :busy="acting === 'revoke'"
            :error="dialogError"
            :field-error="dialogFieldErrors.reason"
            @submit="doRevoke"
        />
        <RewardEventDeliveredDialog v-model:visible="deliveredOpen" :member-name="deliveredTarget ? UtilRewardEventMessages.memberName(t, deliveredTarget.member) : ''" :reward-name="deliveredTarget ? lineName(deliveredTarget.tierId, deliveredLineId) : ''" :busy="acting === 'delivered'" :error="dialogError" :field-error="dialogFieldErrors.memo" @submit="doDelivered" />
        <RewardEventDrawDialog v-if="detail" v-model:visible="drawOpen" :post-title="detail.selection?.commentDraw?.postTitle ?? detail.selection?.commentDraw?.postRef ?? ''" :tiers="tierChoices" :candidate-count="drawCandidates" :already-in="detail.stats.targets" :busy="acting === 'draw'" :error="dialogError" :field-errors="dialogFieldErrors" @submit="doDraw" />
        <RewardEventRecipientAddDialog
            v-if="detail"
            v-model:visible="addOpen"
            :tiers="tierChoices"
            :resolve-result="resolveResult"
            :resolving="resolving"
            :search-results="memberResults"
            :searching="searching"
            :searched="searched"
            :busy="acting === 'add'"
            :error="dialogError"
            @resolve="onResolve"
            @search="onSearch"
            @submit="doAdd"
        />
    </div>
</template>

<script setup lang="ts">
import type { RewardDrawTierChoice } from '~/components/rewardevent/RewardEventDrawDialog.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    RewardCommentPreview,
    RewardCommentPreviewRequest,
    RewardDraw,
    RewardDrawRequest,
    RewardEventAudit,
    RewardEventCatalog,
    RewardEventDetail,
    RewardGrantPreview,
    RewardLine,
    RewardMemberRef,
    RewardRecipient,
    RewardRecipientAddRequest,
    RewardRecipientResolveResult,
    RewardRecipientStatus,
    RewardRun,
    RewardTargetPreview,
    RewardTargetPreviewRequest,
    RewardTier,
} from '~/composables/rewardevent/api/useRewardEventAdminApi.types';
import { REWARD_RECIPIENT_STATUSES } from '~/composables/rewardevent/api/useRewardEventAdminApi.types';
import UtilRewardEvent, { type RewardEventFormModel } from '~/utils/util-reward-event';
import UtilRewardEventMessages from '~/utils/util-reward-event-messages';

type DetailTab = 'overview' | 'recipients' | 'draws' | 'runs' | 'audit';
type Act = 'start' | 'close' | 'cancel' | 'grant' | 'retry' | 'revoke' | 'delivered' | 'draw' | 'add' | 'remove';

// 만들기(`new`)에서 저장하면 같은 화면이 다른 번호로 바뀐다 — 번호가 바뀌면 처음부터 다시 그린다
definePageMeta({ key: (r) => String(r.params.id ?? '') });

const { t, locale } = useI18n();
const api = useRewardEventAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const route = useRoute();
const router = useRouter();

const canManage = computed(() => can('reward-event.manage'));
const canGrantPerm = computed(() => can('reward-event.grant'));
const canReadMember = computed(() => can('member.read'));
const ids = { rq: useId(), rs: useId(), rt: useId() };
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const idParam = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => idParam.value === 'new');
const editing = computed(() => isNew.value || route.query.edit === '1');

const detail = ref<RewardEventDetail | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);

const pageTitle = computed(() => (isNew.value ? t('rewardEvent.newEvent') : (detail.value?.name ?? t('rewardEvent.detailTitle'))));
const pageSubtitle = computed(() => {
    if (isNew.value) return t('rewardEvent.newSubtitle');
    if (editing.value) return t('rewardEvent.editSubtitle');
    return detail.value ? t('rewardEvent.detailSubtitle', { no: detail.value.no }) : '';
});
useSeoMeta({ title: () => pageTitle.value });

const commentSource = computed(() => detail.value?.mode === 'SELECTION' && (detail.value.selection?.sources.includes('COMMENT_DRAW') ?? false));

// ── 읽기 ─
let pollTimer: ReturnType<typeof setTimeout> | null = null;
function clearPoll(): void {
    if (pollTimer) clearTimeout(pollTimer);
    pollTimer = null;
}
onBeforeUnmount(clearPoll);

async function load(): Promise<void> {
    if (isNew.value) return;
    loading.value = true;
    loadError.value = null;
    try {
        detail.value = await api.detail(idParam.value);
        if (editing.value && !form.value) initForm();
        schedulePoll();
    } catch (e) {
        loadError.value = toApiError(e);
        detail.value = null;
    } finally {
        loading.value = false;
    }
}

/** 일괄 대량 지급이 진행 중이면 1.5초 뒤 다시 묻는다(진행률) — 끝나면 보이는 탭도 다시 읽는다 */
function schedulePoll(): void {
    clearPoll();
    if (!detail.value?.runningRun) return;
    pollTimer = setTimeout(async () => {
        const wasRunning = Boolean(detail.value?.runningRun);
        let next: RewardEventDetail;
        try {
            next = await api.detail(idParam.value);
        } catch {
            return;
        }
        detail.value = next;
        if (tab.value === 'runs' || (wasRunning && !next.runningRun)) staleTabs();
        schedulePoll();
    }, 1500);
}

// ── 보상 고르기 목록 ─
const catalog = ref<RewardEventCatalog | null>(null);
const catalogError = ref<ApiError | null>(null);
async function loadCatalog(): Promise<void> {
    catalogError.value = null;
    try {
        catalog.value = await api.catalog();
    } catch (e) {
        catalogError.value = toApiError(e);
    }
}

// ── 만들기 · 고치기 ─
const form = ref<RewardEventFormModel | null>(null);
/** 처음 값(되돌리기·바뀜 판정) — 보낼 본문 글자 */
const initial = ref('');
const formKey = ref(0);
const submitted = ref(false);
const saving = ref(false);
const saveError = ref('');
const saveFieldErrors = ref<Record<string, string>>({});
const commentPreview = ref<{ key: string; result: RewardCommentPreview } | null>(null);
const commentPreviewError = ref('');
const targetPreview = ref<{ key: string; result: RewardTargetPreview } | null>(null);
const previewBusy = ref<'comments' | 'targets' | null>(null);

const currentRequest = computed(() => (form.value ? JSON.stringify(UtilRewardEvent.toRequest(form.value)) : ''));
const dirty = computed(() => Boolean(form.value) && currentRequest.value !== initial.value);
const problemCount = computed(() => (form.value ? Object.keys(UtilRewardEvent.validate(UtilRewardEvent.toRequest(form.value), catalog.value)).length : 0));

function initForm(): void {
    const next = isNew.value || !detail.value ? UtilRewardEvent.emptyForm(Date.now(), t('rewardEvent.form.tierDefaultName', { n: 1 })) : UtilRewardEvent.fromDetail(detail.value);
    form.value = next;
    initial.value = JSON.stringify(UtilRewardEvent.toRequest(next));
    formKey.value += 1;
    submitted.value = false;
    saveError.value = '';
    saveFieldErrors.value = {};
    commentPreview.value = null;
    commentPreviewError.value = '';
    targetPreview.value = null;
    if (!catalog.value) void loadCatalog();
}

function revert(): void {
    initForm();
}

async function startEdit(): Promise<void> {
    initForm();
    await router.push({ query: { ...route.query, edit: '1' } });
}

async function stopEdit(): Promise<void> {
    await router.push({ query: { ...route.query, edit: undefined } });
}

// 브라우저 뒤로·앞으로로 고치기에 들어오면(주소만 바뀐다) 지금 값으로 칸을 채운다
watch(editing, (on) => {
    if (on && detail.value && !isNew.value) initForm();
});

async function save(): Promise<void> {
    if (!form.value || saving.value) return;
    submitted.value = true;
    if (problemCount.value) return;
    saving.value = true;
    saveError.value = '';
    saveFieldErrors.value = {};
    const body = UtilRewardEvent.toRequest(form.value);
    try {
        if (isNew.value) {
            const created = await api.create(body);
            initial.value = JSON.stringify(body);
            toast.success(t('rewardEvent.created', { name: created.name }));
            await router.replace(`/reward-events/${encodeURIComponent(created.id)}`);
            return;
        }
        // 준비 상태에서 고치면 보상 줄 번호가 모두 새로 바뀐다 — 응답 상세로 칸을 다시 채운다(바뀜 판정도 새 값 기준)
        detail.value = await api.update(idParam.value, body);
        initForm();
        toast.success(t('rewardEvent.saved'));
        staleTabs();
        await stopEdit();
    } catch (e) {
        const error = toApiError(e);
        const fields = error.fields ?? {};
        saveFieldErrors.value = fields;
        saveError.value = Object.keys(fields).length ? error.message : `${t('rewardEvent.saveFailed')} — ${error.message}`;
    } finally {
        saving.value = false;
    }
}

async function leaveGuard(): Promise<boolean> {
    if (!dirty.value || saving.value) return true;
    return confirm({ title: t('rewardEvent.leaveTitle'), message: t('rewardEvent.leaveMessage'), confirmLabel: t('rewardEvent.leaveConfirm'), cancelLabel: t('common.cancel'), tone: 'danger' });
}
onBeforeRouteLeave(leaveGuard);
// 고치기 → 상세(주소의 `edit` 만 빠진다)도 같은 확인
onBeforeRouteUpdate(async (to) => {
    if (editing.value && to.query.edit !== '1' && !isNew.value) {
        const ok = await leaveGuard();
        if (ok) form.value = null;
        return ok;
    }
    return true;
});

async function onPreviewComments(body: RewardCommentPreviewRequest): Promise<void> {
    previewBusy.value = 'comments';
    commentPreviewError.value = '';
    const key = JSON.stringify(body);
    try {
        commentPreview.value = { key, result: await api.previewComments({ ...body }) };
    } catch (e) {
        const error = toApiError(e);
        commentPreview.value = null;
        commentPreviewError.value = error.fields?.postRef ?? error.message;
    } finally {
        previewBusy.value = null;
    }
}

async function onPreviewTargets(body: RewardTargetPreviewRequest): Promise<void> {
    previewBusy.value = 'targets';
    const key = JSON.stringify(body);
    try {
        targetPreview.value = { key, result: await api.previewTargets({ bulk: { ...body.bulk }, excludeOperators: body.excludeOperators }) };
    } catch (e) {
        toast.error(`${t('rewardEvent.previewFailed')} — ${toApiError(e).message}`, 8000);
    } finally {
        previewBusy.value = null;
    }
}

// ── 탭 ─
const tabList = computed<DetailTab[]>(() => (commentSource.value ? ['overview', 'recipients', 'draws', 'runs', 'audit'] : ['overview', 'recipients', 'runs', 'audit']));
const TABS: readonly DetailTab[] = ['overview', 'recipients', 'draws', 'runs', 'audit'];
const tab = ref<DetailTab>(typeof route.query.tab === 'string' && (TABS as readonly string[]).includes(route.query.tab) ? (route.query.tab as DetailTab) : 'overview');

function selectTab(next: DetailTab): void {
    tab.value = next;
    void router.replace({ query: { ...route.query, tab: next === 'overview' ? undefined : next } });
    ensureTab(next);
}

/** 탭 자료는 처음 열 때 받는다 — 동작 뒤에는 지금 탭만 다시 읽고 나머지는 다음에 열 때 */
const tabLoaded = reactive<Record<DetailTab, boolean>>({ overview: true, recipients: false, draws: false, runs: false, audit: false });
function ensureTab(next: DetailTab): void {
    if (tabLoaded[next]) return;
    tabLoaded[next] = true;
    if (next === 'recipients') void loadRecipients();
    else if (next === 'draws') void loadDraws();
    else if (next === 'runs') void loadRuns();
    else if (next === 'audit') void loadAudit();
}
function staleTabs(): void {
    tabLoaded.recipients = false;
    tabLoaded.draws = false;
    tabLoaded.runs = false;
    tabLoaded.audit = false;
    ensureTab(tab.value);
}

// ── 대상·당첨자 ─
const rFilter = reactive({ q: '', status: '', tierId: '' });
const rApplied = ref({ q: '', status: '', tierId: '' });
const rRows = ref<RewardRecipient[]>([]);
const rTotal = ref(0);
const rFirst = ref(0);
const rSize = ref(20);
const rBusy = ref(false);
const rError = ref<ApiError | null>(null);
const expanded = ref<RewardRecipient[]>([]);
const picked = ref(new Set<string>());

const recipientStatusOptions = computed(() => [{ value: '', label: t('rewardEvent.rcp.allStatus') }, ...REWARD_RECIPIENT_STATUSES.map((s) => ({ value: s, label: t(`rewardEvent.recipientStatus.${s}`) }))]);
const tierFilterOptions = computed(() => [{ value: '', label: t('rewardEvent.rcp.allTiers') }, ...(detail.value?.tiers ?? []).map((tier) => ({ value: tier.id, label: tier.name }))]);

let rTicket = 0;
async function loadRecipients(): Promise<void> {
    if (!detail.value) return;
    const mine = ++rTicket;
    rBusy.value = true;
    rError.value = null;
    try {
        const page = await api.recipients(idParam.value, { status: (rApplied.value.status || undefined) as RewardRecipientStatus | undefined, tierId: rApplied.value.tierId || undefined, q: rApplied.value.q || undefined, page: Math.floor(rFirst.value / rSize.value), size: rSize.value });
        if (mine !== rTicket) return;
        rRows.value = page.items;
        rTotal.value = page.total;
        // 다시 읽은 줄 중 더는 회수할 수 없는 것은 선택에서 뺀다
        const still = new Set(page.items.filter(revocable).map((r) => r.id));
        picked.value = new Set([...picked.value].filter((id) => still.has(id)));
    } catch (e) {
        if (mine !== rTicket) return;
        rRows.value = [];
        rTotal.value = 0;
        rError.value = toApiError(e);
    } finally {
        if (mine === rTicket) rBusy.value = false;
    }
}

function searchRecipients(): void {
    rApplied.value = { q: rFilter.q.trim(), status: rFilter.status, tierId: rFilter.tierId };
    rFirst.value = 0;
    picked.value = new Set();
    void loadRecipients();
}

function onRecipientPage(e: { first: number; rows: number }): void {
    rFirst.value = e.first;
    rSize.value = e.rows;
    void loadRecipients();
}

function revocable(r: RewardRecipient): boolean {
    return r.status === 'GRANTED' || r.status === 'PARTIAL';
}
function removable(r: RewardRecipient): boolean {
    const s = detail.value?.status;
    return r.status === 'PENDING' && detail.value?.mode === 'SELECTION' && (s === 'DRAFT' || s === 'ACTIVE' || s === 'CLOSED');
}
const revocableOnPage = computed(() => rRows.value.filter(revocable));
const allPicked = computed(() => revocableOnPage.value.length > 0 && revocableOnPage.value.every((r) => picked.value.has(r.id)));
function pickOne(id: string, on: boolean): void {
    const next = new Set(picked.value);
    if (on) next.add(id);
    else next.delete(id);
    picked.value = next;
}
function pickAll(on: boolean): void {
    const next = new Set(picked.value);
    revocableOnPage.value.forEach((r) => (on ? next.add(r.id) : next.delete(r.id)));
    picked.value = next;
}

function tierOf(tierId: string): RewardTier | undefined {
    return detail.value?.tiers.find((tier) => tier.id === tierId);
}
function lineName(tierId: string, lineId: string): string {
    const line = tierOf(tierId)?.rewards.find((l) => l.id === lineId);
    return line ? UtilRewardEventMessages.line(t, line, locale.value) : '—';
}
function pendingDeliveries(r: RewardRecipient) {
    return r.status === 'REVOKED' ? [] : r.results.filter((x) => x.status === 'DELIVERY_PENDING');
}
/** 상태 아래 한 줄 — 첫 실패·건너뜀 사유, 직접 전달 대기 수 */
function firstIssue(r: RewardRecipient): string {
    const failed = r.results.find((x) => x.status === 'FAILED' && x.reason);
    if (failed?.reason) return t(`rewardEvent.reason.${failed.reason}`);
    const skipped = r.results.find((x) => x.status === 'SKIPPED' && x.reason);
    if (skipped?.reason) return t('rewardEvent.rcp.skippedLine', { reason: t(`rewardEvent.reason.${skipped.reason}`) });
    const waiting = r.results.filter((x) => x.status === 'DELIVERY_PENDING').length;
    if (waiting && r.status !== 'REVOKED') return t('rewardEvent.rcp.deliveryWaiting', { n: waiting });
    return '';
}

// ── 추첨 기록 · 지급 내역 · 기록 ─
const draws = ref<RewardDraw[] | null>(null);
const drawsError = ref<ApiError | null>(null);
async function loadDraws(): Promise<void> {
    drawsError.value = null;
    try {
        draws.value = await api.draws(idParam.value);
    } catch (e) {
        drawsError.value = toApiError(e);
    }
}
const runs = ref<RewardRun[] | null>(null);
const runsError = ref<ApiError | null>(null);
async function loadRuns(): Promise<void> {
    runsError.value = null;
    try {
        runs.value = await api.runs(idParam.value);
    } catch (e) {
        runsError.value = toApiError(e);
    }
}
const auditRows = ref<RewardEventAudit[] | null>(null);
const auditError = ref<ApiError | null>(null);
async function loadAudit(): Promise<void> {
    auditError.value = null;
    try {
        auditRows.value = await api.audit(idParam.value);
    } catch (e) {
        auditError.value = toApiError(e);
    }
}

function percent(p: { done: number; total: number }): number {
    return p.total ? Math.round((p.done / p.total) * 100) : 100;
}

// ── 개요 ─
const numberCells = computed(() => {
    const s = detail.value?.stats;
    if (!s) return [];
    const n = (v: number) => v.toLocaleString(locale.value);
    return [
        { key: 'targets', label: t('rewardEvent.num.targets'), value: n(s.targets), tone: '' },
        { key: 'granted', label: t('rewardEvent.num.granted'), value: n(s.granted), tone: '' },
        { key: 'pending', label: t('rewardEvent.num.pending'), value: n(s.pending), tone: '' },
        { key: 'failed', label: t('rewardEvent.num.failed'), value: n(s.failed + s.partial), tone: s.failed + s.partial ? 'text-red-600 dark:text-red-400' : '' },
        { key: 'skipped', label: t('rewardEvent.num.skipped'), value: t('rewardEvent.num.skippedValue', { people: n(s.skipped), lines: n(s.skippedLines) }), tone: '' },
        { key: 'revoked', label: t('rewardEvent.num.revoked'), value: n(s.revoked), tone: '' },
        { key: 'points', label: t('rewardEvent.num.points'), value: UtilRewardEventMessages.points(t, s.grantedPoints, locale.value), tone: '' },
        { key: 'items', label: t('rewardEvent.num.items'), value: n(s.grantedItems), tone: '' },
        { key: 'manual', label: t('rewardEvent.num.manual'), value: t('rewardEvent.num.manualValue', { done: n(s.manualDelivered), waiting: n(s.manualPending) }), tone: s.manualPending ? 'text-amber-700 dark:text-amber-400' : '' },
    ];
});

const conditionRows = computed(() => {
    const d = detail.value;
    if (!d) return [];
    const rows: { label: string; value: string }[] = [{ label: t('rewardEvent.form.secMode'), value: t(`rewardEvent.mode.${d.mode}`) }];
    if (d.mode === 'FIRST_COME' && d.firstCome) {
        rows.push({ label: t('rewardEvent.form.trigger'), value: t(`rewardEvent.trigger.${d.firstCome.trigger}`) });
        rows.push({ label: t('rewardEvent.form.limit'), value: t('rewardEvent.peopleValue', { n: d.firstCome.limit.toLocaleString(locale.value) }) });
    }
    if (d.mode === 'SELECTION' && d.selection) {
        rows.push({ label: t('rewardEvent.form.sources'), value: d.selection.sources.map((s) => t(`rewardEvent.source.${s}`)).join(' · ') });
        const c = d.selection.commentDraw;
        if (c) {
            rows.push({ label: t('rewardEvent.overview.post'), value: `${c.postTitle ?? c.postRef}${c.postId ? ` (#${c.postId})` : ''}` });
            rows.push({ label: t('rewardEvent.overview.commentPeriod'), value: `${time.value.shortDateTime(c.from)} ~ ${time.value.shortDateTime(c.to)}` });
            rows.push({ label: t('rewardEvent.overview.excludeAuthor'), value: c.excludeAuthor ? t('common.yes') : t('common.no') });
        }
    }
    if (d.mode === 'BULK' && d.bulk) {
        const b = d.bulk;
        rows.push({ label: t('rewardEvent.form.bulkScope'), value: t(`rewardEvent.bulkScope.${b.scope}`) });
        if (b.scope === 'CONDITION') {
            const parts: string[] = [];
            const grade = b.minGradeLevel !== null ? catalog.value?.grades.find((g) => g.level === b.minGradeLevel)?.label ?? `Lv.${b.minGradeLevel}` : null;
            if (grade) parts.push(t('rewardEvent.form.gradeOrAbove', { name: grade }));
            if (b.joinedFrom || b.joinedTo) parts.push(t('rewardEvent.overview.joined', { from: b.joinedFrom ?? '…', to: b.joinedTo ?? '…' }));
            if (b.identityVerifiedOnly) parts.push(t('rewardEvent.form.identityOnly'));
            if (b.activeWithinDays !== null) parts.push(t('rewardEvent.overview.activeWithin', { n: b.activeWithinDays }));
            rows.push({ label: t('rewardEvent.overview.conditionsLabel'), value: parts.join(' · ') || '—' });
        }
    }
    rows.push({ label: t('rewardEvent.form.secExclude'), value: [d.excludeOperators ? t('rewardEvent.overview.excludeOps') : null, t('rewardEvent.overview.alwaysExcluded')].filter(Boolean).join(' · ') });
    rows.push({ label: t('rewardEvent.form.noticeUrl'), value: d.noticeUrl || '—' });
    rows.push({ label: t('rewardEvent.form.memo'), value: d.memo || '—' });
    return rows;
});

function tierQuotaText(tier: RewardTier): string {
    const d = detail.value;
    if (!d) return '';
    if (d.mode === 'BULK') return t('rewardEvent.form.quotaByTargets');
    return t('rewardEvent.peopleValue', { n: tier.quota.toLocaleString(locale.value) });
}

function lineNote(line: RewardLine): string {
    if (line.kind === 'POINT') return t('rewardEvent.overview.pointNote');
    if (line.kind === 'SHOP_ITEM') return line.itemUsage ? t(`rewardEvent.usage.${line.itemUsage}`, { days: line.itemDurationDays ?? 0 }) : '';
    if (line.kind === 'EMOTICON_PACK') return t('rewardEvent.overview.packNote');
    return line.manualMethod ?? '';
}

// ── 동작 ─
const acting = ref<Act | null>(null);
const actionError = ref('');
const dialogError = ref('');
const dialogFieldErrors = ref<Record<string, string>>({});

function resetDialogErrors(): void {
    dialogError.value = '';
    dialogFieldErrors.value = {};
}

/** 상태가 바뀐 동작 뒤 — 머리·지금 탭을 다시 읽는다 */
async function afterAction(next?: RewardEventDetail): Promise<void> {
    if (next) {
        detail.value = next;
        schedulePoll();
    } else await load();
    staleTabs();
}

/** 다른 운영자가 먼저 바꿔 상태가 안 맞으면(409) 서버 문구 토스트 + 다시 읽기 */
function failAction(e: unknown, inDialog: boolean): void {
    const error = toApiError(e);
    if (error.status === 409 || error.status === 404) {
        toast.error(error.message, 8000);
        void afterAction();
        return;
    }
    if (inDialog) {
        dialogFieldErrors.value = error.status === 400 && error.fields ? error.fields : {};
        dialogError.value = Object.keys(dialogFieldErrors.value).length ? '' : error.message;
    } else actionError.value = error.message;
}

async function doStart(): Promise<void> {
    const d = detail.value;
    if (!d) return;
    const ok = await confirm({ title: t('rewardEvent.startTitle'), message: t(`rewardEvent.startMessage.${d.mode}`, { name: d.name, at: time.value.full(d.startAt) }), confirmLabel: t('rewardEvent.act.start'), cancelLabel: t('common.cancel') });
    if (!ok) return;
    acting.value = 'start';
    actionError.value = '';
    try {
        await afterAction(await api.start(d.id));
        toast.success(t('rewardEvent.startedToast'));
    } catch (e) {
        failAction(e, false);
    } finally {
        acting.value = null;
    }
}

async function doClose(): Promise<void> {
    const d = detail.value;
    if (!d) return;
    const ok = await confirm({ title: t('rewardEvent.closeTitle'), message: t(`rewardEvent.closeMessage.${d.mode}`, { name: d.name }), confirmLabel: t('rewardEvent.act.close'), cancelLabel: t('common.cancel') });
    if (!ok) return;
    acting.value = 'close';
    actionError.value = '';
    try {
        await afterAction(await api.close(d.id));
        toast.success(t('rewardEvent.closedToast'));
    } catch (e) {
        failAction(e, false);
    } finally {
        acting.value = null;
    }
}

// 취소
const cancelOpen = ref(false);
function openCancel(): void {
    resetDialogErrors();
    cancelOpen.value = true;
}
async function doCancel(reason: string): Promise<void> {
    const d = detail.value;
    if (!d) return;
    acting.value = 'cancel';
    resetDialogErrors();
    try {
        await afterAction(await api.cancel(d.id, reason));
        cancelOpen.value = false;
        toast.success(t('rewardEvent.canceledToast'));
    } catch (e) {
        failAction(e, true);
        if (toApiError(e).status === 409) cancelOpen.value = false;
    } finally {
        acting.value = null;
    }
}

// 지급 실행
const grantOpen = ref(false);
const grantPreview = ref<RewardGrantPreview | null>(null);
let grantKey = '';
async function openGrant(): Promise<void> {
    const d = detail.value;
    if (!d) return;
    resetDialogErrors();
    grantPreview.value = null;
    grantKey = UtilRewardEvent.idempotencyKey();
    grantOpen.value = true;
    try {
        grantPreview.value = await api.grantPreview(d.id);
    } catch (e) {
        grantOpen.value = false;
        failAction(e, false);
    }
}
async function doGrant(): Promise<void> {
    const d = detail.value;
    if (!d || acting.value) return;
    acting.value = 'grant';
    resetDialogErrors();
    try {
        const run = await api.grant(d.id, { idempotencyKey: grantKey });
        grantOpen.value = false;
        toast.success(run.status === 'RUNNING' ? t('rewardEvent.grantStarted', { n: run.targetCount.toLocaleString(locale.value) }) : t('rewardEvent.grantDone', { ok: run.success.toLocaleString(locale.value), failed: run.failed.toLocaleString(locale.value) }));
        await afterAction();
    } catch (e) {
        failAction(e, true);
        if (toApiError(e).status === 409) grantOpen.value = false;
    } finally {
        acting.value = null;
    }
}

async function doRetry(): Promise<void> {
    const d = detail.value;
    if (!d) return;
    const n = d.stats.failed + d.stats.partial;
    const ok = await confirm({ title: t('rewardEvent.retryTitle'), message: t('rewardEvent.retryMessage', { n: n.toLocaleString(locale.value) }), confirmLabel: t('rewardEvent.retrySubmit'), cancelLabel: t('common.cancel') });
    if (!ok) return;
    acting.value = 'retry';
    actionError.value = '';
    try {
        const run = await api.retry(d.id);
        toast.success(t('rewardEvent.retryDone', { ok: run.success.toLocaleString(locale.value), failed: run.failed.toLocaleString(locale.value) }));
        await afterAction();
    } catch (e) {
        failAction(e, false);
        if (actionError.value) toast.error(actionError.value, 8000);
    } finally {
        acting.value = null;
    }
}

// 회수
const revokeOpen = ref(false);
function openRevoke(): void {
    resetDialogErrors();
    revokeOpen.value = true;
}
async function doRevoke(reason: string): Promise<void> {
    const d = detail.value;
    if (!d || !picked.value.size || picked.value.size > UtilRewardEvent.BATCH_MAX) return;
    acting.value = 'revoke';
    resetDialogErrors();
    try {
        const run = await api.revoke(d.id, { recipientIds: [...picked.value], reason });
        revokeOpen.value = false;
        picked.value = new Set();
        toast.success(t('rewardEvent.revokeDone', { n: run.success.toLocaleString(locale.value), points: run.points.toLocaleString(locale.value), kept: run.skipped.toLocaleString(locale.value) }));
        await afterAction();
    } catch (e) {
        failAction(e, true);
        if (toApiError(e).status === 409) revokeOpen.value = false;
    } finally {
        acting.value = null;
    }
}

// 직접 전달 완료
const deliveredOpen = ref(false);
const deliveredTarget = ref<RewardRecipient | null>(null);
const deliveredLineId = ref('');
function openDelivered(r: RewardRecipient, lineId: string): void {
    resetDialogErrors();
    deliveredTarget.value = r;
    deliveredLineId.value = lineId;
    deliveredOpen.value = true;
}
async function doDelivered(memo: string): Promise<void> {
    const d = detail.value;
    const r = deliveredTarget.value;
    if (!d || !r) return;
    acting.value = 'delivered';
    resetDialogErrors();
    try {
        await api.markDelivered(d.id, r.id, { lineId: deliveredLineId.value, memo });
        deliveredOpen.value = false;
        toast.success(t('rewardEvent.deliveredDone', { name: UtilRewardEventMessages.memberName(t, r.member) }));
        await afterAction();
    } catch (e) {
        failAction(e, true);
        if (toApiError(e).status === 409) deliveredOpen.value = false;
    } finally {
        acting.value = null;
    }
}

// 추첨
const drawOpen = ref(false);
const drawCandidates = ref<number | null>(null);
const tierChoices = computed<RewardDrawTierChoice[]>(() => {
    const d = detail.value;
    if (!d) return [];
    // 등수별 지금 인원 — 상세의 `recipientsByTier`(서버 숫자)
    return d.tiers.map((tier) => ({ id: tier.id, name: tier.name, quota: tier.quota, current: d.recipientsByTier[tier.id] ?? 0 }));
});
async function openDraw(): Promise<void> {
    const d = detail.value;
    const c = d?.selection?.commentDraw;
    if (!d || !c) return;
    resetDialogErrors();
    drawCandidates.value = null;
    drawOpen.value = true;
    try {
        const preview = await api.previewComments({ postRef: c.postRef, from: c.from, to: c.to, excludeAuthor: c.excludeAuthor, excludeOperators: d.excludeOperators });
        drawCandidates.value = preview.candidateCount;
    } catch {
        drawCandidates.value = null;
    }
}
async function doDraw(body: RewardDrawRequest): Promise<void> {
    const d = detail.value;
    if (!d) return;
    acting.value = 'draw';
    resetDialogErrors();
    try {
        const draw = await api.draw(d.id, { ...body });
        drawOpen.value = false;
        toast.success(t('rewardEvent.drawDone', { n: draw.picked, candidates: draw.candidateCount.toLocaleString(locale.value), seed: draw.seed }));
        await afterAction();
    } catch (e) {
        const error = toApiError(e);
        // 후보가 모자란 것(409 REWARD_DRAW_NOT_ENOUGH)은 대화상자 안에서 알린다
        if (error.code === 'REWARD_DRAW_NOT_ENOUGH') dialogError.value = error.message;
        else {
            failAction(e, true);
            if (error.status === 409) drawOpen.value = false;
        }
    } finally {
        acting.value = null;
    }
}

// 당첨자 추가 · 빼기
const addOpen = ref(false);
const resolveResult = ref<RewardRecipientResolveResult | null>(null);
const resolving = ref(false);
const memberResults = ref<RewardMemberRef[]>([]);
const searching = ref(false);
const searched = ref(false);
function openAdd(): void {
    resetDialogErrors();
    resolveResult.value = null;
    memberResults.value = [];
    searched.value = false;
    addOpen.value = true;
}
async function onResolve(lines: string[]): Promise<void> {
    resolving.value = true;
    dialogError.value = '';
    try {
        resolveResult.value = await api.resolveRecipients(idParam.value, [...lines]);
    } catch (e) {
        dialogError.value = toApiError(e).message;
    } finally {
        resolving.value = false;
    }
}
async function onSearch(q: string): Promise<void> {
    if (!q) return;
    searching.value = true;
    try {
        memberResults.value = await api.searchMembers(q);
        searched.value = true;
    } catch (e) {
        dialogError.value = toApiError(e).message;
    } finally {
        searching.value = false;
    }
}
async function doAdd(body: RewardRecipientAddRequest): Promise<void> {
    const d = detail.value;
    if (!d) return;
    acting.value = 'add';
    resetDialogErrors();
    try {
        await afterAction(await api.addRecipients(d.id, { tierId: body.tierId, memberIds: [...body.memberIds] }));
        addOpen.value = false;
        toast.success(t('rewardEvent.addDone', { n: body.memberIds.length }));
    } catch (e) {
        const error = toApiError(e);
        if (error.code === 'REWARD_EVENT_TIER_FULL') dialogError.value = error.message;
        else failAction(e, true);
    } finally {
        acting.value = null;
    }
}
async function removeRecipient(r: RewardRecipient): Promise<void> {
    const d = detail.value;
    if (!d) return;
    const ok = await confirm({ title: t('rewardEvent.removeTitle'), message: t('rewardEvent.removeMessage', { name: UtilRewardEventMessages.memberName(t, r.member), tier: r.tierName }), confirmLabel: t('rewardEvent.removeSubmit'), cancelLabel: t('common.cancel'), tone: 'danger' });
    if (!ok) return;
    acting.value = 'remove';
    try {
        await afterAction(await api.removeRecipient(d.id, r.id));
        toast.success(t('rewardEvent.removeDone', { name: UtilRewardEventMessages.memberName(t, r.member) }));
    } catch (e) {
        const error = toApiError(e);
        toast.error(error.message, 8000);
        void afterAction();
    } finally {
        acting.value = null;
    }
}

// ── 시작 ─
watch(idParam, () => void load());
onMounted(async () => {
    if (isNew.value) {
        initForm();
        return;
    }
    await load();
    ensureTab(tab.value);
    if (!catalog.value) void loadCatalog();
});
</script>
