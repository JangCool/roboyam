<template>
    <!-- 설정 묶음(`pages/settings.vue`) 안의 한 화면 — 넓은 화면은 왼쪽 메뉴, 좁은 화면은 위에 「‹ 계정·로그인 연결」 -->
    <div>
        <h1 class="mb-4 text-lg font-bold">{{ t('withdrawal.title') }}</h1>

        <!-- 로딩 -->
        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('withdrawal.loading')" data-testid="withdraw-loading">
            <div v-for="n in 3" :key="n">
                <div class="mb-2 h-3 w-20 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>

        <!-- 불러오기 실패 -->
        <ErrorState v-else-if="status === 'error'" :title="t('withdrawal.loadFailed')" :description="loadError" retry data-testid="withdraw-error" @retry="load" />

        <!-- 탈퇴 진행 중 — 남은 일·예상 완료·취소. 취소 단추는 탈퇴 단추와 같은 크기(한쪽을 작게 두지 않는다) -->
        <div v-else-if="view && view.state === 'SCHEDULED' && view.scheduled" class="flex flex-col gap-4" data-testid="withdraw-scheduled">
            <div class="panel-card p-4">
                <StatusPanel icon="pi pi-clock" tone="warn" :title="t('withdrawal.scheduledTitle')">
                    {{ t('withdrawal.scheduledText', { date: time.full(view.scheduled.requestedAt) }) }}
                    <b class="mt-1 block text-color" data-testid="withdraw-expected">
                        {{ view.scheduled.expectedCompleteAt ? t('withdrawal.scheduledExpected', { date: time.full(view.scheduled.expectedCompleteAt) }) : t('withdrawal.scheduledExpectedUnknown') }}
                    </b>
                </StatusPanel>
            </div>

            <section :aria-labelledby="`${uid}-sch-pending`">
                <h2 :id="`${uid}-sch-pending`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('withdrawal.pendingTitle') }}</h2>
                <ul class="panel-card m-0 list-none p-0" data-testid="withdraw-scheduled-pending">
                    <li v-for="(item, i) in view.scheduled.pending" :key="`${item.code}-${i}`" class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5 last:border-b-0">
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ pendingTitle(item) }}</span>
                            <small class="block text-xs leading-snug text-muted-color">{{ item.waitUntil ? t('withdrawal.pendingWaitUntil', { date: time.date(item.waitUntil) }) : t('withdrawal.pendingWaitUnknown') }}</small>
                        </span>
                        <Button v-if="item.link" as="router-link" :to="item.link" :label="t('withdrawal.pendingOpen')" size="small" severity="secondary" variant="outlined" class="shrink-0" />
                    </li>
                </ul>
            </section>

            <Message severity="info" :closable="false" size="small" data-testid="withdraw-scheduled-blocked">
                {{ t('withdrawal.scheduledBlocked') }} {{ t('withdrawal.scheduledNotify') }}
            </Message>

            <Message v-if="cancelError" severity="error" :closable="false" size="small" data-testid="withdraw-cancel-error">
                <b>{{ t('withdrawal.cancelFailed') }}</b> {{ cancelError }}
            </Message>
            <Button type="button" :label="t('withdrawal.cancel')" fluid size="large" :loading="cancelling" :disabled="cancelling" data-testid="withdraw-cancel" @click="onCancel" />
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('withdrawal.cancelHelp') }}</p>
        </div>

        <!-- 한 화면: 사실 고지(사라지는 것·남는 것·끝나야 하는 일) → 정할 것 → 재가입 → 사유(선택) → 본인 확인 → 단추 하나. 단계 나누기·만류 팝업 없음 -->
        <form v-else-if="view" class="flex flex-col gap-6" novalidate :aria-label="t('withdrawal.title')" @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('withdrawal.intro') }}</p>

            <!-- 사라지는 것 -->
            <section :aria-labelledby="`${uid}-losses`">
                <h2 :id="`${uid}-losses`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('withdrawal.lossesTitle') }}</h2>
                <div class="panel-card" data-testid="withdraw-losses">
                    <div class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5">
                        <span class="grid size-8 shrink-0 place-items-center rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-star" /></span>
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('withdrawal.lossFreePoints', { points: UtilPoint.points(view.losses.freePoints) }) }}</span>
                            <small class="block text-xs leading-snug text-muted-color">{{ t('withdrawal.lossFreePointsHelp') }}</small>
                        </span>
                    </div>
                    <div class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5">
                        <span class="grid size-8 shrink-0 place-items-center rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-shopping-bag" /></span>
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('withdrawal.lossItems', { count: view.losses.items }) }}</span>
                            <small class="block text-xs leading-snug text-muted-color">{{ t('withdrawal.lossItemsHelp') }}</small>
                            <small v-if="view.losses.refundableItems > 0" class="mt-1 block text-xs leading-snug text-blue-700 dark:text-blue-300" data-testid="withdraw-items-refundable">
                                {{ t('withdrawal.lossItemsRefundable', { count: view.losses.refundableItems }) }}
                                <NuxtLink to="/me/items" class="underline">{{ t('withdrawal.lossItemsRefundLink') }}</NuxtLink>
                            </small>
                        </span>
                    </div>
                    <div class="flex min-h-14 items-center gap-3 px-4 py-2.5">
                        <span class="grid size-8 shrink-0 place-items-center rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-calendar" /></span>
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('withdrawal.lossGrade') }}</span>
                            <small class="block text-xs leading-snug text-muted-color">
                                {{ view.losses.gradeName ? t('withdrawal.lossGradeHelp', { grade: view.losses.gradeName, days: view.losses.attendanceStreak }) : t('withdrawal.lossGradeHelpNoGrade') }}
                            </small>
                        </span>
                    </div>
                </div>
            </section>

            <!-- 남는 것 -->
            <section :aria-labelledby="`${uid}-remains`">
                <h2 :id="`${uid}-remains`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('withdrawal.remainsTitle') }}</h2>
                <div class="panel-card" data-testid="withdraw-remains">
                    <div class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5">
                        <span class="grid size-8 shrink-0 place-items-center rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-file" /></span>
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('withdrawal.remainPosts', { posts: view.remains.posts, comments: view.remains.comments }) }}</span>
                            <small class="block text-xs leading-snug text-muted-color">{{ t('withdrawal.remainPostsHelp') }}</small>
                        </span>
                        <Button v-if="myPostsPath" as="router-link" :to="myPostsPath" :label="t('withdrawal.remainPostsLink')" size="small" severity="secondary" variant="outlined" class="shrink-0" data-testid="withdraw-my-posts" />
                    </div>
                    <div class="flex min-h-14 items-center gap-3 px-4 py-2.5" :class="{ 'border-b border-surface': view.remains.hasPaymentHistory }">
                        <span class="grid size-8 shrink-0 place-items-center rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-receipt" /></span>
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('withdrawal.remainTrades') }}</span>
                            <small class="block text-xs leading-snug text-muted-color">{{ t('withdrawal.remainTradesHelp', { years: view.remains.retentionYears }) }}</small>
                        </span>
                    </div>
                    <div v-if="view.remains.hasPaymentHistory" class="flex min-h-14 items-center gap-3 px-4 py-2.5" data-testid="withdraw-contact-keep">
                        <span class="grid size-8 shrink-0 place-items-center rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-at" /></span>
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('withdrawal.remainContact') }}</span>
                            <small class="block text-xs leading-snug text-muted-color">{{ t('withdrawal.remainContactHelp', { years: view.remains.retentionYears }) }}</small>
                        </span>
                    </div>
                </div>
            </section>

            <!-- 끝나야 하는 일(있을 때만) — 건마다 링크, 끝나면 자동 완료 -->
            <section v-if="view.pending.length > 0" :aria-labelledby="`${uid}-pending`">
                <h2 :id="`${uid}-pending`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('withdrawal.pendingTitle') }}</h2>
                <ul class="panel-card m-0 list-none p-0" data-testid="withdraw-pending">
                    <li v-for="(item, i) in view.pending" :key="`${item.code}-${i}`" class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5 last:border-b-0">
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ pendingTitle(item) }}</span>
                            <small class="block text-xs leading-snug text-muted-color">{{ item.waitUntil ? t('withdrawal.pendingWaitUntil', { date: time.date(item.waitUntil) }) : t('withdrawal.pendingWaitUnknown') }}</small>
                        </span>
                        <Button v-if="item.link" as="router-link" :to="item.link" :label="t('withdrawal.pendingOpen')" size="small" severity="secondary" variant="outlined" class="shrink-0" />
                    </li>
                </ul>
                <p class="mt-2 px-1 text-xs leading-relaxed text-muted-color">{{ t('withdrawal.pendingIntro') }}</p>
            </section>

            <!-- 정할 것(있을 때만) — 두 선택지는 같은 모양·크기, 기본값은 회원에게 유리한 쪽 -->
            <section v-if="hasChoices" :aria-labelledby="`${uid}-choices`" class="flex flex-col gap-4">
                <h2 :id="`${uid}-choices`" class="px-1 text-xs font-semibold text-muted-color">{{ t('withdrawal.choicesTitle') }}</h2>

                <!-- 배송완료 7일 안 주문 -->
                <fieldset v-if="view.delivered.length > 0" class="m-0 flex flex-col gap-3 panel-card border-0 p-4" data-testid="withdraw-delivered">
                    <legend class="float-left mb-1 w-full text-sm font-medium">{{ t('withdrawal.deliveredTitle', { count: view.delivered.length }) }}</legend>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('withdrawal.deliveredHelp') }}</p>
                    <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs">
                        <li v-for="order in view.delivered" :key="order.orderNo">
                            <NuxtLink :to="order.link" class="text-primary hover:underline">{{ t('withdrawal.deliveredOrder', { orderNo: order.orderNo, date: time.date(order.returnDeadline) }) }}</NuxtLink>
                        </li>
                    </ul>
                    <div class="grid gap-2 sm:grid-cols-2">
                        <label v-for="value in (['WAIT', 'NOW'] as const)" :key="value" :class="choiceClass(deliveredChoice === value)" :data-testid="`withdraw-delivered-${value}`">
                            <RadioButton v-model="deliveredChoice" :value="value" :input-id="`${uid}-delivered-${value}`" name="deliveredChoice" class="mt-0.5" :disabled="busy" />
                            <span class="min-w-0">
                                <span class="block font-medium">{{ value === 'WAIT' ? t('withdrawal.deliveredWait') : t('withdrawal.deliveredNow') }}</span>
                                <span class="block text-xs leading-snug text-muted-color">{{ value === 'WAIT' ? t('withdrawal.deliveredWaitHelp') : t('withdrawal.deliveredNowHelp') }}</span>
                            </span>
                        </label>
                    </div>
                </fieldset>

                <!-- 충전 포인트 — 환불 예상액·수수료를 먼저 보이고, 「환불받고」/「포기하고」는 같은 모양 -->
                <fieldset v-if="view.money" class="m-0 flex flex-col gap-3 panel-card border-0 p-4" data-testid="withdraw-paid">
                    <legend class="float-left mb-1 w-full text-sm font-medium">{{ t('withdrawal.paidTitle', { points: UtilPoint.points(view.money.paidPoints) }) }}</legend>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('withdrawal.paidHelp') }}</p>
                    <div class="rounded-lg bg-surface-100 px-3 py-2 dark:bg-surface-800">
                        <p class="m-0 text-base font-bold tabular-nums text-primary" data-testid="withdraw-paid-estimate">{{ t('withdrawal.paidEstimate', { amount: won(view.money.refundEstimate) }) }}</p>
                        <p class="m-0 mt-0.5 text-xs leading-snug text-muted-color">
                            {{ view.money.fee > 0 ? t('withdrawal.paidFee', { points: UtilPoint.points(view.money.feeBasePoints), rate: view.money.feeRatePercent, fee: won(view.money.fee) }) : t('withdrawal.paidFeeNone') }}
                        </p>
                    </div>
                    <div class="grid gap-2 sm:grid-cols-2">
                        <label v-for="value in (['REFUND', 'FORFEIT'] as const)" :key="value" :class="choiceClass(paidPointChoice === value)" :data-testid="`withdraw-paid-${value}`">
                            <RadioButton v-model="paidPointChoice" :value="value" :input-id="`${uid}-paid-${value}`" name="paidPointChoice" class="mt-0.5" :disabled="busy" />
                            <span class="min-w-0">
                                <span class="block font-medium">{{ value === 'REFUND' ? t('withdrawal.paidRefund') : t('withdrawal.paidForfeit') }}</span>
                                <span class="block text-xs leading-snug text-muted-color">{{ value === 'REFUND' ? t('withdrawal.paidRefundHelp') : t('withdrawal.paidForfeitHelp') }}</span>
                            </span>
                        </label>
                    </div>
                    <div v-if="paidPointChoice === 'FORFEIT'" class="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/40" data-testid="withdraw-paid-consent">
                        <Checkbox v-model="paidForfeitConsent" binary :input-id="`${uid}-paid-consent`" :disabled="busy" :invalid="Boolean(consentError)" class="mt-0.5 shrink-0" />
                        <label :for="`${uid}-paid-consent`" class="min-w-0 flex-1 cursor-pointer text-sm leading-snug">
                            {{ t('withdrawal.paidForfeitConsent', { points: UtilPoint.points(view.money.paidPoints), amount: won(view.money.refundEstimate) }) }}
                        </label>
                    </div>
                </fieldset>

                <!-- 진행 중 광고·핫딜(사업자 회원) — 잃는 금액과 환불 대기 금액을 따로 -->
                <fieldset v-if="view.business && view.business.running.length > 0" class="m-0 flex flex-col gap-3 panel-card border-0 p-4" data-testid="withdraw-biz">
                    <legend class="float-left mb-1 w-full text-sm font-medium">{{ t('withdrawal.bizTitle', { count: view.business.running.length }) }}</legend>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('withdrawal.bizHelp') }}</p>
                    <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs">
                        <li v-for="run in view.business.running" :key="run.id">
                            <NuxtLink :to="run.link" class="text-primary hover:underline">{{ t('withdrawal.bizRunning', { label: run.label, date: time.date(run.endsAt) }) }}</NuxtLink>
                        </li>
                    </ul>
                    <dl class="m-0 grid grid-cols-1 gap-2 sm:grid-cols-2">
                        <div class="rounded-lg bg-surface-100 px-3 py-2 dark:bg-surface-800">
                            <dt class="text-xs text-muted-color">{{ t('withdrawal.bizForfeitTotal') }}</dt>
                            <dd class="m-0 text-base font-bold tabular-nums text-red-600 dark:text-red-400" data-testid="withdraw-biz-forfeit">{{ won(view.business.forfeitTotal) }}</dd>
                        </div>
                        <div class="rounded-lg bg-surface-100 px-3 py-2 dark:bg-surface-800">
                            <dt class="text-xs text-muted-color">{{ t('withdrawal.bizRefundWaiting') }}</dt>
                            <dd class="m-0 text-base font-bold tabular-nums text-primary" data-testid="withdraw-biz-waiting">{{ won(view.business.refundWaitingTotal) }}</dd>
                            <dd class="m-0 text-xs leading-snug text-muted-color">{{ t('withdrawal.bizRefundWaitingHelp') }}</dd>
                        </div>
                    </dl>
                    <div class="grid gap-2 sm:grid-cols-2">
                        <label v-for="value in (['WAIT', 'FORFEIT'] as const)" :key="value" :class="choiceClass(bizChoice === value)" :data-testid="`withdraw-biz-${value}`">
                            <RadioButton v-model="bizChoice" :value="value" :input-id="`${uid}-biz-${value}`" name="bizChoice" class="mt-0.5" :disabled="busy" />
                            <span class="min-w-0">
                                <span class="block font-medium">{{ value === 'WAIT' ? t('withdrawal.bizWait') : t('withdrawal.bizForfeit') }}</span>
                                <span class="block text-xs leading-snug text-muted-color">{{ value === 'WAIT' ? t('withdrawal.bizWaitHelp') : t('withdrawal.bizForfeitHelp') }}</span>
                            </span>
                        </label>
                    </div>
                    <div v-if="bizChoice === 'FORFEIT'" class="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/40" data-testid="withdraw-biz-consent">
                        <Checkbox v-model="bizForfeitConsent" binary :input-id="`${uid}-biz-consent`" :disabled="busy" :invalid="Boolean(consentError)" class="mt-0.5 shrink-0" />
                        <label :for="`${uid}-biz-consent`" class="min-w-0 flex-1 cursor-pointer text-sm leading-snug">
                            {{ t('withdrawal.bizForfeitConsent', { amount: won(view.business.forfeitTotal), waiting: won(view.business.refundWaitingTotal) }) }}
                        </label>
                    </div>
                </fieldset>

                <!-- 내가 모임장인 소모임 — 모임마다 넘기기/해산, 고르지 않으면 자동 위임·자동 해산 -->
                <fieldset v-if="view.groupsLed.length > 0" class="m-0 flex flex-col gap-3 panel-card border-0 p-4" data-testid="withdraw-groups">
                    <legend class="float-left mb-1 w-full text-sm font-medium">{{ t('withdrawal.groupsTitle', { count: view.groupsLed.length }) }}</legend>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('withdrawal.groupsHelp') }}</p>
                    <div v-for="(group, gi) in view.groupsLed" :key="group.id" class="flex flex-col gap-2 rounded-lg border border-surface p-3" :data-testid="`withdraw-group-${group.id}`">
                        <div class="flex items-center gap-2 text-sm">
                            <NuxtLink :to="group.link" class="min-w-0 flex-1 truncate font-medium text-primary hover:underline">{{ group.name }}</NuxtLink>
                            <span class="shrink-0 text-xs text-muted-color">{{ t('withdrawal.groupMembers', { count: group.memberCount }) }}</span>
                        </div>
                        <!-- 멤버가 없으면 고를 것이 없다 — 자동 해산 안내만 -->
                        <p v-if="group.candidates.length === 0" class="m-0 text-xs leading-snug text-muted-color">{{ t('withdrawal.groupAutoDissolve') }}</p>
                        <template v-else>
                            <div class="grid gap-2 sm:grid-cols-3">
                                <label v-for="action in (['AUTO', 'TRANSFER', 'DISSOLVE'] as const)" :key="action" :class="choiceClass(groupChoices[gi]?.action === action)">
                                    <RadioButton :model-value="groupChoices[gi]?.action" :value="action" :input-id="`${uid}-group-${group.id}-${action}`" :name="`group-${group.id}`" class="mt-0.5" :disabled="busy" @update:model-value="setGroupAction(gi, action)" />
                                    <span class="min-w-0">
                                        <span class="block font-medium">{{ t(`withdrawal.${GROUP_ACTION_KEY[action]}`) }}</span>
                                        <span class="block text-xs leading-snug text-muted-color">
                                            <template v-if="action === 'AUTO'">{{ group.autoDelegate ? t('withdrawal.groupAutoDelegate', { nickname: group.autoDelegate.nickname, role: t(`withdrawal.role.${group.autoDelegate.role}`) }) : t('withdrawal.groupAutoDissolve') }}</template>
                                            <template v-else-if="action === 'TRANSFER'">{{ t('withdrawal.groupTransferHelp') }}</template>
                                            <template v-else>{{ t('withdrawal.groupDissolveHelp') }}</template>
                                        </span>
                                    </span>
                                </label>
                            </div>
                            <LabeledField v-if="groupChoices[gi]?.action === 'TRANSFER'" :label="t('withdrawal.groupTransferTo')" :error="transferError && !groupChoices[gi]?.toMemberId ? transferError : ''">
                                <template #default="{ id, invalid }">
                                    <Select
                                        :input-id="id"
                                        :model-value="groupChoices[gi]?.toMemberId ?? null"
                                        :options="candidateOptions(group)"
                                        option-label="label"
                                        option-value="id"
                                        :placeholder="t('withdrawal.groupTransferPlaceholder')"
                                        :disabled="busy"
                                        :invalid="invalid"
                                        fluid
                                        :data-testid="`withdraw-group-${group.id}-target`"
                                        @update:model-value="(id: string | null) => setGroupTarget(gi, id)"
                                    />
                                </template>
                            </LabeledField>
                        </template>
                    </div>
                </fieldset>
            </section>

            <!-- 재가입 -->
            <section :aria-labelledby="`${uid}-rejoin`">
                <h2 :id="`${uid}-rejoin`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('withdrawal.rejoinTitle') }}</h2>
                <div class="panel-card p-4 text-sm leading-relaxed" data-testid="withdraw-rejoin">
                    <p class="m-0">{{ t('withdrawal.rejoinText', { date: time.date(view.rejoin.availableAt) }) }}</p>
                    <p v-if="view.rejoin.extendedByRestriction && view.rejoin.restrictionEndsAt" class="m-0 mt-1 text-xs text-amber-700 dark:text-amber-300" data-testid="withdraw-rejoin-extended">
                        {{ t('withdrawal.rejoinExtended', { date: time.date(view.rejoin.restrictionEndsAt), reason: view.rejoin.restrictionReason ?? '' }) }}
                    </p>
                </div>
            </section>

            <!-- 사유(선택) -->
            <section :aria-labelledby="`${uid}-reason`">
                <h2 :id="`${uid}-reason`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('withdrawal.reasonTitle') }}</h2>
                <div class="flex flex-col gap-3 panel-card p-4" data-testid="withdraw-reason">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('withdrawal.reasonHelp') }}</p>
                    <div>
                        <Select
                            v-model="reason"
                            :input-id="`${uid}-reason-select`"
                            :options="reasonOptions"
                            option-label="label"
                            option-value="code"
                            :placeholder="t('withdrawal.reasonPlaceholder')"
                            show-clear
                            :disabled="busy"
                            :invalid="Boolean(reasonError)"
                            :aria-describedby="reasonError ? `${uid}-reason-error` : undefined"
                            fluid
                            data-testid="withdraw-reason-select"
                        />
                        <small v-if="reasonError" :id="`${uid}-reason-error`" class="mt-1 block text-xs text-red-600 dark:text-red-400" data-testid="withdraw-reason-error">{{ reasonError }}</small>
                    </div>
                    <LabeledField :label="t('withdrawal.reasonNote')" :error="reasonNoteError">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea
                                :id="id"
                                v-model="reasonNote"
                                name="reasonNote"
                                rows="2"
                                auto-resize
                                maxlength="200"
                                :placeholder="t('withdrawal.reasonNotePlaceholder')"
                                :disabled="busy"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                fluid
                                data-testid="withdraw-reason-note"
                            />
                        </template>
                    </LabeledField>
                </div>
            </section>

            <!-- 본인 확인 — 비밀번호 1회. 소셜 전용 회원은 최근 10분 안 로그인이면 통과, 아니면 다시 로그인 -->
            <section :aria-labelledby="`${uid}-verify`">
                <h2 :id="`${uid}-verify`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('withdrawal.verifyTitle') }}</h2>
                <div class="flex flex-col gap-3 panel-card p-4" data-testid="withdraw-verify">
                    <template v-if="view.verification.method === 'PASSWORD'">
                        <Message v-if="locked" severity="error" :closable="false" size="small" data-testid="withdraw-locked">
                            <b>{{ t('password.lockedTitle') }}</b> {{ t('password.lockedText', { minutes: UtilAccount.minutes(lock.remaining.value) }) }}
                        </Message>
                        <PasswordField ref="passwordField" v-model="password" :label="t('password.currentLabel')" name="password" autocomplete="current-password" :help="t('withdrawal.verifyPasswordHelp')" :disabled="busy" :error="passwordError" />
                    </template>
                    <Message v-else-if="view.verification.recentLoginOk" severity="success" :closable="false" size="small" data-testid="withdraw-verify-ok">{{ t('withdrawal.verifyRecentOk') }}</Message>
                    <template v-else>
                        <Message severity="warn" :closable="false" size="small" data-testid="withdraw-verify-needed">{{ t('withdrawal.verifyRecentNeeded') }}</Message>
                        <Button as="router-link" :to="reloginPath" :label="t('withdrawal.verifyRelogin')" variant="outlined" severity="secondary" fluid data-testid="withdraw-relogin" />
                    </template>
                </div>
            </section>

            <!-- 칸에 붙지 않는 오류 -->
            <Message v-if="formError" severity="error" :closable="false" size="small" data-testid="withdraw-form-error">
                <b>{{ t('withdrawal.submitFailed') }}</b> {{ formError }}
            </Message>
            <Message v-else-if="consentError" severity="error" :closable="false" size="small" data-testid="withdraw-consent-error">{{ consentError }}</Message>

            <div class="flex flex-col gap-2">
                <Button type="submit" severity="danger" fluid size="large" :label="submitting ? t('withdrawal.submitting') : willSchedule ? t('withdrawal.submitScheduled') : t('withdrawal.submit')" :loading="submitting" :disabled="!canSubmit" data-testid="withdraw-submit" />
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ willSchedule ? t('withdrawal.submitHelpScheduled') : t('withdrawal.submitHelpNow') }}</p>
            </div>
        </form>
    </div>
</template>

<script setup lang="ts">
import type {
    BizChoice,
    DeliveredChoice,
    GroupChoiceAction,
    PaidPointChoice,
    WithdrawalGroupChoice,
    WithdrawalGroupLed,
    WithdrawalPendingItem,
    WithdrawalReasonCode,
    WithdrawalView,
} from '~/composables/account/api/useWithdrawalApi.types';
import UtilAccount from '~/utils/util-account';
import UtilAccountMessages from '~/utils/util-account-messages';
import UtilPoint from '~/utils/util-point';
import UtilWithdrawal from '~/utils/util-withdrawal';

/**
 * 회원 탈퇴 `/settings/account/withdraw` — 로그인 필요(`auth` 미들웨어, 서버도 401). 계정 화면(`/settings/account`)의 「회원 탈퇴」 줄로 온다.
 * 규칙: `docs/domain/commerce.md` 17-1(탈퇴 신청·탈퇴 예약), 화면 기준: `docs/plans/member-withdrawal/00-analysis.md` 6절(다크패턴 규제 — 한 화면·사실만·만류 팝업 없음·사유 선택·두 선택지 같은 모양).
 *
 * - **한 화면**에 사라지는 것 · 남는 것 · 끝나야 하는 일(있을 때만) · 정할 것(있을 때만: 배송완료 7일 안 주문 / 충전 포인트 / 광고·핫딜 / 모임장) · 재가입 가능일 · 사유(선택) · 본인 확인 · 단추 하나.
 *   기본값은 회원에게 유리한 쪽(기다리기·환불). 「포기」는 금액이 적힌 체크 동의가 있어야 제출된다(`UtilWithdrawal.consentMissing`).
 * - 단추를 누르면 남은 일·「기다리기」·「환불받고」가 하나라도 있으면 **탈퇴 진행 중**(`SCHEDULED` — 같은 화면이 진행 중 상태로 바뀌고 로그인 정보를 새로 받아 전역 띠 `WithdrawalBanner` 가 켜진다),
 *   없으면 **즉시 완료** → 서버가 모든 세션을 끝냈으므로 `auth.clear()` 뒤 `/withdrawn`(결과는 `useWithdrawalDone` 으로 들고 간다).
 * - **탈퇴 진행 중 상태**: 남은 일 목록·예상 완료 시점·「탈퇴 취소」(탈퇴 단추와 같은 크기)·새 활동이 막힌다는 안내. 취소는 확인 대화상자 → `DELETE` → 로그인 정보도 새로 받는다(띠가 꺼진다).
 * - 본인 확인: 비밀번호 계정은 비밀번호 1회(`401 CURRENT_PASSWORD_WRONG` 은 칸 아래, `429 LOGIN_LOCKED` 는 잠금 안내). 소셜 전용 회원은 최근 10분 안 로그인이면 통과, 아니면 「다시 로그인」(`/login?next=…`).
 *   비밀번호는 이 화면의 `password` 하나에만 있고 성공·잠금 때 비운다.
 * - 오류는 서버 문구 그대로, 코드로 갈라 자리에 붙인다(`400 VALIDATION` 의 `fields.password`·`forfeitConsent`·`groupChoices`·`reason`·`reasonNote`, 그 밖은 폼 아래 한 줄).
 *   `409 WITHDRAWAL_ALREADY_SCHEDULED`·취소의 `409 WITHDRAWAL_NOT_SCHEDULED` 는 화면이 낡은 것이므로 다시 받는다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('withdrawal.title'), description: () => t('withdrawal.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const api = useWithdrawalApi();
const auth = useAuth();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const done = useWithdrawalDone();
const uid = useId();

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const view = ref<WithdrawalView | null>(null);

/* ── 고른 것 — 기본값은 회원에게 유리한 쪽 ── */
const deliveredChoice = ref<DeliveredChoice>('WAIT');
const paidPointChoice = ref<PaidPointChoice>('REFUND');
const bizChoice = ref<BizChoice>('WAIT');
const groupChoices = ref<WithdrawalGroupChoice[]>([]);
const paidForfeitConsent = ref(false);
const bizForfeitConsent = ref(false);
const reason = ref<WithdrawalReasonCode | null>(null);
const reasonNote = ref('');
/** 비밀번호는 여기에만 있다 */
const password = ref('');

const submitting = ref(false);
const cancelling = ref(false);
/** 비밀번호 칸 아래 · 동의 칸 · 넘길 멤버 칸 · 사유 칸 · 사유 글 칸 · 칸에 못 붙이는 오류 · 취소 실패 */
const passwordError = ref('');
const consentError = ref('');
const transferError = ref('');
const reasonError = ref('');
const reasonNoteError = ref('');
const formError = ref('');
const cancelError = ref('');

const passwordField = ref<{ focus: () => void } | null>(null);

/** 비밀번호를 여러 번 틀려 잠겼다 — 풀리면 오류를 걷고 칸으로 돌아온다 */
const lock = useCountdown(undefined, () => {
    passwordError.value = '';
    void nextTick(() => passwordField.value?.focus());
});
const locked = computed(() => lock.active.value);
const busy = computed(() => submitting.value || locked.value);

const GROUP_ACTION_KEY: Record<GroupChoiceAction, string> = { AUTO: 'groupAuto', TRANSFER: 'groupTransfer', DISSOLVE: 'groupDissolve' };
const REASON_CODES: readonly WithdrawalReasonCode[] = ['NOT_USING', 'PRIVACY', 'INCONVENIENT', 'OTHER_SERVICE', 'REJOIN', 'OTHER'];
const reasonOptions = computed(() => REASON_CODES.map((code) => ({ code, label: t(`withdrawal.reason.${code}`) })));

/** 내 글 목록 — 내 공개 프로필(`/users/{내 ID}`) */
const myPostsPath = computed(() => (auth.user.value ? `/users/${auth.user.value.id}` : ''));
/** 소셜 전용 회원이 다시 로그인하고 돌아올 곳 */
const reloginPath = computed(() => `/login?next=${encodeURIComponent('/settings/account/withdraw')}`);

const hasChoices = computed(() => {
    const v = view.value;
    return Boolean(v && (v.delivered.length > 0 || v.money || (v.business && v.business.running.length > 0) || v.groupsLed.length > 0));
});

const choices = computed(() => ({
    paidPointChoice: paidPointChoice.value,
    deliveredChoice: deliveredChoice.value,
    bizChoice: bizChoice.value,
    groupChoices: groupChoices.value,
    paidForfeitConsent: paidForfeitConsent.value,
    bizForfeitConsent: bizForfeitConsent.value,
}));
/** 지금 선택대로면 탈퇴 진행 중이 되는가(아니면 즉시 완료) — 단추 이름·안내가 달라진다 */
const willSchedule = computed(() => Boolean(view.value && UtilWithdrawal.pendingAfter(view.value, choices.value).length > 0));
const canSubmit = computed(() => Boolean(view.value) && status.value === 'ready' && !busy.value && !passwordError.value && UtilWithdrawal.verified(view.value!, password.value));

/** 금액 — `12,000원` */
function won(amount: number): string {
    return t('payment.won', { amount: UtilPoint.number(amount) });
}

/** 남은 일 한 줄의 제목 — 종류 + 서버가 준 식별 글 */
function pendingTitle(item: WithdrawalPendingItem): string {
    const kind = t(`withdrawal.pendingKind.${item.code}`);
    return item.label ? `${kind} · ${item.label}` : kind;
}

/** 선택지 카드 — 두 선택지가 같은 모양·크기(고른 쪽만 테두리 색) */
function choiceClass(selected: boolean): string {
    return `flex cursor-pointer items-start gap-2 rounded-lg border p-3 text-sm ${selected ? 'border-primary bg-primary-50/40 dark:bg-primary-950/30' : 'border-surface'}`;
}

function candidateOptions(group: WithdrawalGroupLed): { id: string; label: string }[] {
    return group.candidates.map((c) => ({ id: c.id, label: `${c.nickname} (${t(`withdrawal.role.${c.role}`)})` }));
}

function setGroupAction(index: number, action: GroupChoiceAction) {
    const current = groupChoices.value[index];
    if (!current) return;
    groupChoices.value[index] = { ...current, action, toMemberId: action === 'TRANSFER' ? current.toMemberId : null };
    transferError.value = '';
}

function setGroupTarget(index: number, toMemberId: string | null) {
    const current = groupChoices.value[index];
    if (!current) return;
    groupChoices.value[index] = { ...current, toMemberId };
    transferError.value = '';
}

watch(password, () => {
    passwordError.value = '';
    formError.value = '';
});
watch([paidForfeitConsent, bizForfeitConsent, paidPointChoice, bizChoice], () => {
    consentError.value = '';
});
watch(reason, () => {
    reasonError.value = '';
});
watch(reasonNote, () => {
    reasonNoteError.value = '';
});

/** 신청·취소 뒤 로그인 정보(`withdrawalScheduled`)를 새로 받는다 — 전역 띠가 켜지고 꺼진다. 실패해도 이 화면의 결과는 바뀌지 않는다 */
function refreshAuth(): Promise<unknown> {
    return auth.refresh().catch(() => null);
}

let loadSeq = 0;
async function load() {
    const mine = ++loadSeq;
    status.value = 'loading';
    loadError.value = '';
    try {
        const value = await api.view();
        if (mine !== loadSeq) return;
        view.value = value;
        groupChoices.value = value.groupsLed.map((g) => ({ groupId: g.id, action: 'AUTO', toMemberId: null }));
        status.value = 'ready';
    } catch (e) {
        if (mine !== loadSeq) return;
        loadError.value = UtilAccountMessages.of(t, e);
        status.value = 'error';
    }
}
void load();
onBeforeUnmount(() => loadSeq++);

async function submit() {
    const v = view.value;
    if (!v || !canSubmit.value) return;
    passwordError.value = '';
    consentError.value = '';
    transferError.value = '';
    reasonError.value = '';
    reasonNoteError.value = '';
    formError.value = '';

    if (UtilWithdrawal.consentMissing(v, choices.value)) {
        consentError.value = t('withdrawal.consentRequired');
        return;
    }
    if (UtilWithdrawal.transferTargetMissing(groupChoices.value)) {
        transferError.value = t('withdrawal.transferTargetRequired');
        return;
    }

    submitting.value = true;
    try {
        const result = await api.withdraw({
            password: v.verification.method === 'PASSWORD' ? password.value : null,
            reason: reason.value,
            reasonNote: reasonNote.value.trim() || null,
            paidPointChoice: v.money ? paidPointChoice.value : null,
            deliveredChoice: v.delivered.length > 0 ? deliveredChoice.value : null,
            bizChoice: v.business && v.business.running.length > 0 ? bizChoice.value : null,
            groupChoices: groupChoices.value.map((c) => ({ ...c })),
            forfeitConsent: {
                paidPoints: v.money && paidPointChoice.value === 'FORFEIT' ? v.money.paidPoints : null,
                refundEstimate: v.money && paidPointChoice.value === 'FORFEIT' ? v.money.refundEstimate : null,
                bizForfeit: v.business && bizChoice.value === 'FORFEIT' ? v.business.forfeitTotal : null,
            },
        });
        password.value = '';
        if (result.state === 'COMPLETED') {
            // 서버가 모든 세션을 끝냈다 — 화면 상태를 먼저 비우고(뒤따르는 401 이 「세션 끊김」으로 로그인 화면에 가지 않게) 완료 화면으로(결과는 완료 화면이 들고 간다)
            done.result.value = result;
            auth.clear();
            await navigateTo('/withdrawn', { replace: true });
            return;
        }
        toast.success(t('withdrawal.scheduledToast'));
        await Promise.all([load(), refreshAuth()]);
    } catch (e) {
        await onFailed(e);
    } finally {
        submitting.value = false;
    }
}

/** 서버 오류를 코드로 갈라 자리에 붙인다 — 문구는 서버가 준 것 그대로 */
async function onFailed(e: unknown) {
    if (!(e instanceof ApiError)) {
        formError.value = UtilAccountMessages.of(t, e);
        return;
    }
    switch (e.code) {
        case 'CURRENT_PASSWORD_WRONG':
            passwordError.value = e.message;
            await nextTick();
            passwordField.value?.focus();
            return;
        case 'LOGIN_LOCKED':
            password.value = '';
            await nextTick();
            passwordError.value = e.message;
            lock.start(UtilAccount.waitSeconds(e.retryAfterSeconds));
            return;
        case 'REAUTH_REQUIRED':
            // 소셜 전용 회원인데 최근 로그인이 아니다 — 다시 로그인 안내로 바뀐다
            if (view.value) view.value.verification = { method: 'RECENT_LOGIN', recentLoginOk: false };
            formError.value = e.message;
            return;
        case 'WITHDRAWAL_ALREADY_SCHEDULED':
            // 이미 진행 중이다(다른 탭에서 신청 등) — 진행 중 화면으로 바뀌고 띠도 켜진다
            await Promise.all([load(), refreshAuth()]);
            return;
        case 'VALIDATION':
            passwordError.value = e.field('password') ?? '';
            consentError.value = e.field('forfeitConsent') ?? '';
            transferError.value = e.field('groupChoices') ?? '';
            reasonError.value = e.field('reason') ?? '';
            reasonNoteError.value = e.field('reasonNote') ?? '';
            if (!passwordError.value && !consentError.value && !transferError.value && !reasonError.value && !reasonNoteError.value) formError.value = e.message;
            return;
        default:
            formError.value = e.message;
    }
}

/** 탈퇴 진행 중 취소 — 확인 대화상자 → `DELETE` → 다시 받는다 */
async function onCancel() {
    if (cancelling.value) return;
    const ok = await confirm({ title: t('withdrawal.cancelTitle'), message: t('withdrawal.cancelMessage'), confirmLabel: t('withdrawal.cancelConfirm') });
    if (!ok) return;
    cancelling.value = true;
    cancelError.value = '';
    try {
        await api.cancel();
        toast.success(t('withdrawal.cancelDone'));
        await Promise.all([load(), refreshAuth()]);
    } catch (e) {
        // 진행 중이 아니다(이미 취소됐거나 그 사이 완료) — 화면이 낡았으니 다시 받는다
        if (e instanceof ApiError && e.code === 'WITHDRAWAL_NOT_SCHEDULED') await Promise.all([load(), refreshAuth()]);
        else cancelError.value = UtilAccountMessages.of(t, e);
    } finally {
        cancelling.value = false;
    }
}
</script>
