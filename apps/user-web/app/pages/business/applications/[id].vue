<template>
    <!--
        신청 건 상세 `/business/applications/{id}` — 단계 표시(신청 → 심사 → 결제 → 진행 → 종료) + 상태별 본문 + 신청 내용·소재 + 처리 기록(Timeline).
        심사 중 = [신청 취소] · [소재 바꾸기](광고) / 반려 = 사유 코드·글 + [고쳐서 다시 신청] /
        결제 대기(온라인) = 확정 금액·기한 카운트다운(24시간 안이면 경고)·[결제하기] → 결제 공용 흐름(`usePaymentCheckout` — 가짜 결제창 또는 포트원) → 완료 뒤 상세를 다시 읽는다 ·
          [소재 바꾸기](광고 — 다시 심사로) /
        입금 대기(수동) = 입금 계좌·금액·기한·입금자명, 계약서 상태, 입금 확인 전이면 [신청 취소]·[소재 바꾸기] /
        예약·진행 = 기간·소재 버전·[취소·환불 요청](시작 전만 — 전액 자동 + 사유, 한 번만. 진행 중은 아래 [중단하고 환불 요청]) · 환불은 `refund` 하위 값(요청·승인·거절).
        사업자 핫딜(`HOTDEAL_POST`, 2차)은 「게시된 글」 칸(상세 `post`) — 게시 전(「{시각}에 {게시판}에 올라가요」) · 게시됨(딜 상태 Tag·종료 요청 사유별 수·[글 보기]·[품절로 종료] → 확인창 「환불 없음 · 노출도 멈춤」, 409 `DEAL_ENDED` 면 알리고 다시 읽기) ·
          의심(「N명이 알렸어요 — {시각}까지 확인하지 않으면 자동 종료」, keep 은 글 보기에서) · 종료(누가 끝냈는지 · 환불 없음)·기간 종료 · 게시 실패(「운영팀이 확인 중이에요」 + 사유) · 숨김(내림·취소) + 노출 줄(대기·노출 중·멈춤(까닭)·끝남).
        일시 정지·중단(광고주 요청 — `biz-pause-contract.md`, `useBusinessApi.pause|resume|stop`): 진행 중(정지할 수 있는 건 — `UtilBusiness.runningPausable`)이면 [일시 정지](확인 창 `BusinessPauseDialog` · 남은 횟수 n/3, 다 쓰면 비활성) +
          [중단하고 환불 요청](`BusinessStopDialog` — 옛 진행 중 [취소·환불 요청]을 대신(서버도 진행 중 요청을 중단으로 처리), 시작 전 [취소·환불 요청]은 그대로 전액) / 정지 중(`TAKEN_DOWN` + `ADVERTISER_PAUSE`) = 멈춘 시각·운영팀 대신 처리 경로·「{끝 날}까지 다시 켜지 않으면 환불 없이 끝나요」·[다시 켜기]·[중단하고 환불 요청],
          정지한 채 끝 날짜가 지났으면 다른 제목·문구(단추 없음) / 중단(`ADVERTISER_STOP`) = 「중단했어요」 + 환불 칸(`STOP_PRORATED` · 내역 「일할 − 중도 해지 수수료(n%) = 예상액」 · 기준 시각 — 서버 환불 줄 값 그대로) + [새로 신청], 승인 뒤 `CANCELED` 는 「광고주 요청으로 중단」.
          409 `BIZ_PAUSE_LIMIT` 는 알리고 다시 읽는다. 사업자 핫딜 글은 정지·중단하면 글이 숨겨지고(「게시된 글」 칸 문구) 노출 자리도 멈춘다.
        `?pay=1` 로 오면(홈의 [결제하기]) 결제창을 바로 연다. 포트원 모바일 리디렉션은 쿼리 없이 이 화면으로 돌아오고 `resumeRedirect` 가 이어 간다.
        성과 칸(`#performance` — 광고 성과 2026-11-02 — `ad-performance-contract.md`, `BusinessAdStats`): 노출 자리가 있는 건(광고 둘 · 핫딜 노출 · 노출 자리 있는 사업자 핫딜 글)이고 결제(입금)까지 마쳤으면
          시작 뒤(진행·정지·내림·끝남·시작 뒤 취소) 합계 3칸·날짜별 차트·멈춘 구간·자리별·버전별, 예약(시작 전)이면 「시작하면 여기에 하루 단위로 쌓여요」 한 줄, 그 밖은 칸 없음(`UtilAdStats.section`).
          기간 = 시작 날짜 ~ min(끝 날짜, 오늘 — 한국 날짜) 고정(기간 최대 30일이라 고르개 없음). 서버 `useBusinessApi.stats`(`ad_daily_stats` 합 — 정지·내림 구간은 서버가 처리 기록에서 계산).
    -->
    <div class="content-card flex flex-col gap-4" data-testid="business-application">
        <header class="flex items-center gap-2">
            <Button as="router-link" :to="UtilBusiness.APPLICATIONS_PATH" icon="pi pi-angle-left" severity="secondary" variant="text" rounded :aria-label="t('business.detail.back')" />
            <h1 class="m-0 min-w-0 truncate text-lg font-bold">{{ app?.title ?? t('business.detail.title') }}</h1>
        </header>

        <div v-if="status === 'loading' && !app" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="!app" :title="t('business.detail.loadFailed')" :description="loadError" retry @retry="load" />

        <template v-else>
            <div class="flex flex-wrap items-center gap-1.5">
                <BusinessStatusTag :status="app.status" :phase="app.phase" :cause="cause" :end-date="app.endDate" />
                <span class="text-xs text-muted-color">{{ kindLabel }} · {{ app.id }}</span>
            </div>

            <section class="panel-card p-4">
                <BusinessStageTrack :status="app.status" :phase="app.phase" :paid="paid" :cause="app.takedownCause" />
            </section>

            <Message v-if="justPaid && app.status === 'SCHEDULED'" severity="success" :closable="false" data-testid="business-paid">
                <p class="m-0 font-semibold">{{ t('business.detail.paidTitle') }}</p>
                <p class="m-0 mt-1 text-sm">{{ t('business.detail.paidText', { date: time.serviceDate(app.startDate) }) }}</p>
            </Message>

            <!-- 상태별 본문 -->
            <section class="panel-card flex flex-col gap-3 p-4" data-testid="business-application-state">
                <!-- 심사 중 -->
                <template v-if="app.status === 'SUBMITTED'">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.reviewTitle') }}</h2>
                    <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('business.detail.reviewText') }}</p>
                    <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('business.detail.creativeEditNote') }}</p>
                    <div class="flex flex-wrap gap-2">
                        <Button v-if="canEditCreative" type="button" :label="t('business.detail.creativeEdit')" icon="pi pi-pencil" severity="secondary" variant="outlined" size="small" :disabled="actionBusy" data-testid="business-creative-edit" @click="openCreative" />
                        <Button type="button" :label="t('business.detail.cancel')" severity="danger" variant="outlined" size="small" :loading="actionBusy" data-testid="business-cancel" @click="cancel" />
                    </div>
                </template>

                <!-- 반려 -->
                <template v-else-if="app.status === 'REJECTED'">
                    <h2 class="m-0 flex flex-wrap items-center gap-1.5 text-base font-semibold">
                        {{ t('business.detail.rejectedTitle') }}
                        <PointShopTag v-if="app.rejectCode" tone="danger">{{ t(`business.applicationRejectCode.${app.rejectCode}`) }}</PointShopTag>
                    </h2>
                    <p class="m-0 whitespace-pre-line text-sm leading-relaxed">{{ app.rejectReason }}</p>
                    <div>
                        <Button as="router-link" :to="retryPath" :label="t('business.detail.retry')" icon="pi pi-refresh" size="small" />
                    </div>
                </template>

                <!-- 결제 대기(온라인) -->
                <template v-else-if="app.status === 'AWAITING_PAYMENT'">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.payTitle') }}</h2>
                    <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('business.detail.payText') }}</p>
                    <Message v-if="dueSoon" severity="warn" size="small" :closable="false" data-testid="business-due-soon">{{ t('business.deadline.soon') }}</Message>
                    <BusinessDeadline v-if="app.paymentDueAt" :due-at="app.paymentDueAt" kind="PAYMENT" variant="box" />
                    <BusinessPriceSummary v-if="app.confirmed" :amount="app.confirmed" confirmed :adjust-reason="app.adjustReason" />
                    <div class="flex flex-wrap gap-2">
                        <Button type="button" :label="t('business.detail.pay')" icon="pi pi-credit-card" :loading="payStarting || pay.busy.value" data-testid="business-pay" @click="startPay" />
                        <Button v-if="canEditCreative" type="button" :label="t('business.detail.creativeEdit')" icon="pi pi-pencil" severity="secondary" variant="outlined" :disabled="actionBusy || pay.busy.value" @click="openCreative" />
                        <Button type="button" :label="t('business.detail.cancel')" severity="secondary" variant="text" :loading="actionBusy" @click="cancel" />
                    </div>
                    <p v-if="payNote" class="m-0 text-sm text-muted-color" role="status">{{ payNote }}</p>
                    <Message v-if="payError || pay.completeError.value" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ payError || pay.completeError.value }}</Message>
                </template>

                <!-- 입금 대기(수동) -->
                <template v-else-if="app.status === 'AWAITING_DEPOSIT' && app.deposit">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.depositTitle') }}</h2>
                    <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('business.detail.depositText') }}</p>
                    <BusinessDeadline v-if="app.depositDueAt" :due-at="app.depositDueAt" kind="DEPOSIT" variant="box" />
                    <dl class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 rounded-lg border border-hairline p-3.5 text-sm">
                        <dt class="text-muted-color">{{ t('business.detail.bank') }}</dt>
                        <dd class="m-0">{{ app.deposit.bankName }}</dd>
                        <dt class="text-muted-color">{{ t('business.detail.account') }}</dt>
                        <dd class="m-0 font-semibold tabular-nums">{{ app.deposit.accountNo }}</dd>
                        <template v-if="app.deposit.accountHolder">
                            <dt class="text-muted-color">{{ t('business.detail.holder') }}</dt>
                            <dd class="m-0">{{ app.deposit.accountHolder }}</dd>
                        </template>
                        <dt class="text-muted-color">{{ t('business.detail.depositAmount') }}</dt>
                        <dd class="m-0 font-bold tabular-nums">{{ UtilBusinessMessages.won(t, app.confirmed?.total ?? app.amount.total) }}</dd>
                        <dt class="text-muted-color">{{ t('business.detail.depositor') }}</dt>
                        <dd class="m-0">
                            <span class="font-semibold">{{ app.deposit.depositorName }}</span>
                            <span class="block text-xs text-muted-color">{{ t('business.detail.depositorHelp') }}</span>
                        </dd>
                        <dt class="text-muted-color">{{ t('business.detail.contract') }}</dt>
                        <dd class="m-0 flex items-center">
                            <PointShopTag :tone="app.deposit.contract === 'RECEIVED' ? 'success' : 'neutral'">{{ t(`business.detail.contractStatus.${app.deposit.contract}`) }}</PointShopTag>
                        </dd>
                        <dt class="text-muted-color">{{ t('business.detail.deposit') }}</dt>
                        <dd class="m-0 flex items-center">
                            <PointShopTag :tone="app.deposit.depositConfirmed ? 'success' : 'neutral'">{{ t(`business.detail.depositState.${app.deposit.depositConfirmed ? 'CONFIRMED' : 'WAITING'}`) }}</PointShopTag>
                        </dd>
                    </dl>
                    <template v-if="!app.deposit.depositConfirmed">
                        <p class="m-0 text-xs text-muted-color">{{ t('business.detail.depositCancelNote') }}</p>
                        <div class="flex flex-wrap gap-2">
                            <Button v-if="canEditCreative" type="button" :label="t('business.detail.creativeEdit')" icon="pi pi-pencil" severity="secondary" variant="outlined" size="small" :disabled="actionBusy" @click="openCreative" />
                            <Button type="button" :label="t('business.detail.cancel')" severity="danger" variant="outlined" size="small" :loading="actionBusy" data-testid="business-cancel" @click="cancel" />
                        </div>
                    </template>
                </template>

                <!-- 예약·진행·종료 -->
                <template v-else-if="app.status === 'SCHEDULED'">
                    <!-- 사업자 핫딜이 시작 시각에 올라가지 못했으면(게시 실패) 「진행 중」 대신 운영팀 확인 중 -->
                    <h2 class="m-0 text-base font-semibold">
                        {{ dealPost?.status === 'FAILED' ? t('business.post.failedTitle') : app.phase === 'RUNNING' ? t('business.detail.runningTitle') : app.phase === 'ENDED' ? t('business.detail.endedTitle') : t('business.detail.upcomingTitle') }}
                    </h2>
                    <p v-if="dealPost?.status === 'FAILED'" class="m-0 text-sm text-muted-color">{{ t('business.post.failedText') }}</p>
                    <p v-else-if="app.phase === 'UPCOMING'" class="m-0 text-sm text-muted-color">{{ t('business.detail.upcomingText', { date: time.serviceDate(app.startDate) }) }}</p>
                    <p v-else-if="app.phase === 'RUNNING'" class="m-0 text-sm text-muted-color">{{ t('business.detail.runningText', { date: time.serviceDate(app.endDate) }) }}</p>
                    <!-- 진행 중 · 정지할 수 있는 건 — [일시 정지] + [중단하고 환불 요청](옛 진행 중 [취소·환불 요청]을 대신) -->
                    <template v-if="pausable && pauseInfo">
                        <p v-if="pausedSpan" class="m-0 text-xs text-muted-color" data-testid="business-paused-total">{{ t('business.pause.pausedTotal', { span: pausedSpan }) }}</p>
                        <div class="flex flex-wrap gap-2">
                            <Button type="button" :label="t('business.pause.pause')" icon="pi pi-pause" severity="warn" variant="outlined" size="small" :disabled="actionBusy || pausesLeft <= 0" data-testid="business-pause" @click="openPause" />
                            <Button type="button" :label="t('business.pause.stop')" icon="pi pi-stop-circle" severity="danger" variant="outlined" size="small" :disabled="actionBusy" data-testid="business-stop" @click="openStop" />
                        </div>
                        <p class="m-0 text-xs" :class="pausesLeft > 0 ? 'text-muted-color' : 'font-medium text-amber-700 dark:text-amber-300'" data-testid="business-pause-left">
                            {{ pausesLeft > 0 ? t('business.pause.left', { n: pausesLeft, limit: pauseInfo.limit }) : t('business.pause.limitReached') }}
                        </p>
                    </template>
                    <!-- 시작 전만 [취소·환불 요청](전액) — 진행 중 요청은 서버가 중단으로 처리하므로 옛 길을 열지 않는다 -->
                    <div v-else-if="app.phase === 'UPCOMING' && !app.refund">
                        <Button type="button" :label="t('business.detail.refund')" icon="pi pi-replay" severity="secondary" variant="outlined" size="small" data-testid="business-refund" @click="refundOpen = true" />
                    </div>
                </template>

                <!-- 일시 정지(광고주 요청) — 정지한 채 끝 날짜가 지났으면 환불 없이 끝남 -->
                <template v-else-if="app.status === 'TAKEN_DOWN' && cause === 'ADVERTISER_PAUSE'">
                    <template v-if="pausedEnded">
                        <h2 class="m-0 text-base font-semibold" data-testid="business-paused-ended">{{ t('business.pause.endedTitle') }}</h2>
                        <p class="m-0 text-sm text-muted-color">{{ t('business.pause.endedText', { end: endLabel }) }}</p>
                        <p v-if="pausedSince" class="m-0 text-xs tabular-nums text-muted-color">{{ t('business.pause.since', { at: pausedSince }) }}</p>
                    </template>
                    <template v-else>
                        <h2 class="m-0 text-base font-semibold" data-testid="business-paused">{{ t('business.pause.pausedTitle') }}</h2>
                        <p v-if="pausedSince" class="m-0 text-sm tabular-nums">{{ t('business.pause.since', { at: pausedSince }) }}</p>
                        <p v-if="pauseInfo?.pausedBy === 'OPERATOR'" class="m-0 flex items-start gap-1.5 text-sm text-muted-color" data-testid="business-paused-by-operator">
                            <i class="pi pi-shield mt-1 text-xs" aria-hidden="true" />{{ t(`business.pause.byOperator.${pauseInfo.channel ?? 'OTHER'}`) }}
                        </p>
                        <Message severity="warn" size="small" :closable="false" data-testid="business-paused-warn">{{ t('business.pause.pausedWarn', { end: endLabel }) }}</Message>
                        <p class="m-0 text-xs text-muted-color">{{ t('business.pause.pausedHelp') }}<template v-if="pausedSpan"> · {{ t('business.pause.pausedTotal', { span: pausedSpan }) }}</template></p>
                        <div class="flex flex-wrap gap-2">
                            <Button type="button" :label="t('business.pause.resume')" icon="pi pi-play" size="small" :loading="actionBusy" data-testid="business-resume" @click="resume" />
                            <Button type="button" :label="t('business.pause.stop')" icon="pi pi-stop-circle" severity="danger" variant="outlined" size="small" :disabled="actionBusy" data-testid="business-stop" @click="openStop" />
                        </div>
                    </template>
                </template>

                <!-- 중단(광고주 요청) — 되돌리기 없음, 환불은 아래 칸 -->
                <template v-else-if="app.status === 'TAKEN_DOWN' && cause === 'ADVERTISER_STOP'">
                    <h2 class="m-0 text-base font-semibold" data-testid="business-stopped">{{ t('business.pause.stoppedTitle') }}</h2>
                    <p class="m-0 text-sm text-muted-color">{{ stoppedAt ? t('business.pause.stoppedText', { at: stoppedAt }) : t('business.pause.stoppedTextNoTime') }}</p>
                    <p v-if="!app.refund" class="m-0 text-sm" data-testid="business-stopped-no-refund">{{ t('business.pause.stoppedNoRefund') }}</p>
                    <div>
                        <Button as="router-link" :to="retryPath" :label="t('business.detail.newAgain')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" data-testid="business-stopped-new" />
                    </div>
                </template>

                <template v-else-if="app.status === 'TAKEN_DOWN'">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.takenDownTitle') }}</h2>
                    <p v-if="app.takedownReason" class="m-0 whitespace-pre-line text-sm">{{ app.takedownReason }}</p>
                </template>

                <template v-else-if="app.status === 'EXPIRED'">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.expiredTitle') }}</h2>
                    <p class="m-0 text-sm text-muted-color">{{ t('business.detail.expiredText') }}</p>
                    <div>
                        <Button as="router-link" :to="retryPath" :label="t('business.detail.newAgain')" size="small" severity="secondary" variant="outlined" />
                    </div>
                </template>

                <template v-else>
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.canceledTitle') }}</h2>
                    <p v-if="stoppedByMe" class="m-0 text-sm text-muted-color" data-testid="business-canceled-stop">{{ t('business.pause.canceledByStop') }}</p>
                </template>

                <!-- 환불 요청 -->
                <div v-if="app.refund" class="flex flex-col gap-1 rounded-lg border border-hairline p-3 text-sm" data-testid="business-refund-info">
                    <p class="m-0 flex flex-wrap items-center gap-1.5 font-semibold">
                        {{ t('business.detail.refundTitle') }}
                        <PointShopTag :tone="app.refund.status === 'APPROVED' ? 'success' : app.refund.status === 'DENIED' ? 'danger' : 'warn'">{{ t(`business.detail.refundStatus.${app.refund.status}`) }}</PointShopTag>
                        <span v-if="!multiRefund" class="text-xs font-normal text-muted-color">{{ t(`business.detail.refundType.${app.refund.type}`) }}</span>
                    </p>
                    <!-- 환불이 두 줄 이상이면 마지막 건의 종류·금액은 아래 줄 목록과 겹친다 — 목록만 -->
                    <template v-if="!multiRefund">
                        <p v-if="app.refund.refundAmount !== null" class="m-0 tabular-nums">{{ t('business.detail.refundApproved', { amount: UtilBusinessMessages.won(t, app.refund.refundAmount) }) }}</p>
                        <p v-else-if="app.refund.status === 'REQUESTED'" class="m-0 tabular-nums">{{ t('business.detail.refundEstimate', { amount: UtilBusinessMessages.won(t, app.refund.proposedAmount) }) }}</p>
                    </template>
                    <p v-if="refundBreakdown" class="m-0 text-xs tabular-nums" data-testid="business-refund-breakdown">{{ refundBreakdown }}</p>
                    <p v-if="app.refund.type === 'STOP_PRORATED' && refundFromLabel" class="m-0 text-xs tabular-nums text-muted-color" data-testid="business-refund-from">{{ t('business.pause.refundFrom', { at: refundFromLabel }) }}</p>
                    <p v-if="app.refund.decisionReason" class="m-0 whitespace-pre-line text-sm">{{ app.refund.decisionReason }}</p>
                    <!-- 운영팀 추가 환불(`CLAIM`)은 사유가 오지 않는다(클레임 내용은 관리자만) — 금액만 -->
                    <p v-if="app.refund.reason" class="m-0 whitespace-pre-line text-xs text-muted-color">{{ app.refund.reason }}</p>
                    <!-- 환불이 두 줄 이상(중단 환불 + 추가 환불 등)이면 줄마다 종류·상태·금액·시각 + 합계/결제액 -->
                    <template v-if="multiRefund || (app.refundTotals && app.refundTotals.refunded > 0)">
                        <ul v-if="multiRefund" class="m-0 mt-1 flex list-none flex-col gap-1 border-t border-hairline p-0 pt-2" :aria-label="t('business.detail.refundLines')" data-testid="business-refund-lines">
                            <li v-for="(line, i) in app.refunds" :key="i" class="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-xs">
                                <span class="font-medium">{{ t(`business.detail.refundType.${line.type}`) }}</span>
                                <span class="text-muted-color">{{ t(`business.detail.refundStatus.${line.status}`) }}</span>
                                <span class="ml-auto font-semibold tabular-nums">{{ UtilBusinessMessages.won(t, line.amount) }}</span>
                                <span class="w-full tabular-nums text-muted-color">{{ time.full(line.returnedAt ?? line.decidedAt ?? line.requestedAt) }}</span>
                            </li>
                        </ul>
                        <p v-if="app.refundTotals" class="m-0 mt-1 flex flex-wrap items-baseline justify-between gap-x-2 border-t border-hairline pt-2 font-semibold tabular-nums" data-testid="business-refund-total">
                            <span>{{ t('business.detail.refundTotal') }}</span>
                            <span>{{ t('business.detail.refundTotalValue', { refunded: UtilBusinessMessages.won(t, app.refundTotals.refunded), paid: UtilBusinessMessages.won(t, app.refundTotals.paid) }) }}</span>
                        </p>
                    </template>
                </div>
            </section>

            <!-- 게시된 글(사업자 핫딜 2차 — 상세 `post`, 없으면 감춘다) -->
            <section v-if="dealPost" class="panel-card flex flex-col gap-3 p-4" :aria-labelledby="`${uid}-post`" data-testid="business-deal-post" :data-post-status="dealPost.status">
                <h2 :id="`${uid}-post`" class="m-0 flex flex-wrap items-center gap-1.5 text-sm font-semibold">
                    {{ t('business.post.title') }}
                    <PointShopTag :tone="postTone">{{ t(`business.post.status.${dealPost.status}`) }}</PointShopTag>
                    <PointShopTag v-if="dealPost.status === 'POSTED' && dealPost.view" :tone="viewTone" data-testid="business-deal-post-view">{{ t(`business.post.view.${dealPost.view}`) }}</PointShopTag>
                </h2>

                <!-- 게시 전 -->
                <p v-if="dealPost.status === 'WAITING'" class="m-0 flex items-start gap-1.5 text-sm leading-relaxed">
                    <i class="pi pi-clock mt-1 text-xs text-muted-color" aria-hidden="true" />{{ waitingText }}
                </p>

                <!-- 게시 실패 — 운영팀이 확인 뒤 취소(전액 환불) -->
                <Message v-else-if="dealPost.status === 'FAILED'" severity="warn" size="small" :closable="false" data-testid="business-deal-post-failed">
                    <p class="m-0 font-semibold">{{ t('business.post.failedTitle') }}</p>
                    <!-- 예약 상태면 위 상태 칸이 같은 말을 한다 -->
                    <p v-if="app.status !== 'SCHEDULED'" class="m-0 mt-1 text-sm">{{ t('business.post.failedText') }}</p>
                    <p v-if="dealPost.failReason" class="m-0 mt-1 text-sm">{{ t('business.post.failReasonLabel') }} · {{ t(`business.post.failReason.${dealPost.failReason}`) }}</p>
                </Message>

                <!-- 숨김 — 긴급 내림·취소·환불 -->
                <Message v-else-if="dealPost.status === 'HIDDEN'" severity="secondary" size="small" :closable="false" data-testid="business-deal-post-hidden">
                    <p class="m-0 font-semibold">{{ t('business.post.hiddenTitle') }}</p>
                    <p class="m-0 mt-1 text-sm">{{ hiddenText }}</p>
                </Message>

                <!-- 게시됨 — 의심 · 종료 · 기간 종료 안내 -->
                <template v-else>
                    <Message v-if="dealPost.view === 'FLAGGED'" severity="warn" size="small" :closable="false" data-testid="business-deal-post-flagged">
                        <!-- 사업자 글은 종료 요청으로 자동 종료하지 않는다(`autoEndAt` 없음) — 운영팀이 유지·종료를 정한다 -->
                        <p class="m-0 font-semibold">{{ t('business.post.flaggedReview') }}</p>
                        <p class="m-0 mt-1 text-sm">{{ t('business.post.flaggedReviewHelp') }}</p>
                    </Message>
                    <div v-else-if="dealPost.view === 'ENDED'" class="flex flex-col gap-1 rounded-lg bg-surface-100 px-3 py-2 text-sm dark:bg-surface-800" data-testid="business-deal-post-ended">
                        <p class="m-0 flex flex-wrap items-center gap-x-2 font-medium">
                            <i class="pi pi-ban text-xs text-muted-color" aria-hidden="true" />{{ dealPost.endedBy ? t(`business.post.endedBy.${dealPost.endedBy}`) : t('business.post.view.ENDED') }}
                            <span v-if="dealPost.endedAt" class="text-xs font-normal text-muted-color">{{ t('business.post.endedAt', { at: time.full(dealPost.endedAt) }) }}</span>
                        </p>
                        <p class="m-0 text-xs text-muted-color">{{ t('business.post.endedNoRefund') }}</p>
                    </div>
                    <p v-else-if="dealPost.view === 'EXPIRED'" class="m-0 text-sm text-muted-color">{{ t('business.post.expiredText') }}</p>
                </template>

                <dl class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                    <dt class="text-muted-color">{{ t('business.post.board') }}</dt>
                    <dd class="m-0">{{ dealPost.boardName }}</dd>
                    <template v-if="dealPost.prefixName">
                        <dt class="text-muted-color">{{ t('business.post.prefix') }}</dt>
                        <dd class="m-0">{{ dealPost.prefixName }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ t('business.post.publishAt') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(dealPost.publishAt) }}</dd>
                    <template v-if="dealPost.endRequests">
                        <dt class="text-muted-color">{{ t('business.post.endRequests') }}</dt>
                        <dd class="m-0" data-testid="business-deal-post-requests">
                            <template v-if="dealPost.endRequests.total > 0">
                                <span class="font-semibold tabular-nums">{{ t('business.post.endRequestsTotal', { n: dealPost.endRequests.total }) }}</span>
                                <span v-if="requestCounts" class="text-xs text-muted-color"> · {{ requestCounts }}</span>
                            </template>
                            <span v-else class="text-muted-color">{{ t('business.post.endRequestsNone') }}</span>
                        </dd>
                    </template>
                    <template v-if="dealPost.placement">
                        <dt class="text-muted-color">{{ t('business.post.placement') }}</dt>
                        <dd class="m-0 flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1" data-testid="business-deal-post-placement" :data-placement-status="dealPost.placement.status">
                            {{ t(`business.post.placementProduct.${dealPost.placement.product}`) }}
                            <PointShopTag :tone="placementTone">{{ t(`business.post.placementStatus.${dealPost.placement.status}`) }}</PointShopTag>
                            <span v-if="dealPost.placement.status === 'STOPPED' && dealPost.placement.stopReason" class="text-xs text-muted-color">{{ t(`business.post.stopReason.${dealPost.placement.stopReason}`) }}</span>
                        </dd>
                    </template>
                </dl>

                <div v-if="dealPost.status === 'POSTED' && dealPost.postId" class="flex flex-col gap-1.5">
                    <div class="flex flex-wrap gap-2">
                        <Button as="router-link" :to="postLink" :label="t('business.post.viewPost')" icon="pi pi-external-link" size="small" severity="secondary" variant="outlined" data-testid="business-deal-post-link" />
                        <Button v-if="canEndDeal" type="button" :label="t('business.post.endSoldOut')" icon="pi pi-stop-circle" size="small" severity="danger" variant="outlined" :loading="endingDeal" data-testid="business-deal-end" @click="endDeal" />
                    </div>
                    <p v-if="canEndDeal" class="m-0 text-xs text-muted-color">{{ t('business.post.endHelp') }}</p>
                </div>
            </section>

            <!-- 성과(광고 성과 — 노출 자리가 있고 결제 뒤. 시작 전이면 한 줄, 사업자 센터 「진행 중 광고」 줄이 `#performance` 로 건너온다) -->
            <section
                v-if="statsSection !== 'NONE'"
                :id="UtilAdStats.ANCHOR"
                class="panel-card flex scroll-mt-24 flex-col gap-3 p-4"
                :aria-labelledby="`${uid}-stats`"
                data-testid="business-performance"
                :data-section="statsSection"
            >
                <h2 :id="`${uid}-stats`" class="m-0 text-sm font-semibold">{{ t('business.stats.title') }}</h2>
                <p v-if="statsSection === 'UPCOMING'" class="m-0 flex items-start gap-1.5 text-sm text-muted-color">
                    <i class="pi pi-chart-bar mt-1 text-xs" aria-hidden="true" />{{ t('business.stats.upcoming') }}
                </p>
                <BusinessAdStats v-else :stats="stats" :status="statsStatus" :error="statsError" :today="today" @retry="loadStats" />
            </section>

            <!-- 신청 내용 -->
            <section class="panel-card flex flex-col gap-3 p-4" :aria-labelledby="`${uid}-info`">
                <h2 :id="`${uid}-info`" class="m-0 text-sm font-semibold">{{ t('business.detail.info') }}</h2>
                <dl class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                    <dt class="text-muted-color">{{ t('business.detail.kind') }}</dt>
                    <dd class="m-0">{{ kindLabel }}</dd>
                    <dt class="text-muted-color">{{ t('business.detail.period') }}</dt>
                    <dd class="m-0 tabular-nums">{{ periodLabel }}</dd>
                    <template v-if="app.placement && app.placement !== 'NONE'">
                        <dt class="text-muted-color">{{ t('business.detail.placement') }}</dt>
                        <dd class="m-0">{{ t(`business.placement.${app.placement}`) }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ t('business.detail.paymentMethod') }}</dt>
                    <dd class="m-0">{{ t(`business.paymentMethod.${app.paymentMethod}`) }}</dd>
                    <dt class="text-muted-color">{{ t('business.detail.submittedAt') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(app.submittedAt) }}</dd>
                </dl>
                <BusinessPriceSummary v-if="app.status !== 'AWAITING_PAYMENT'" :amount="app.confirmed ?? app.estimate" :confirmed="app.confirmed !== null" :adjust-reason="app.adjustReason" />

                <!-- 소재(광고) -->
                <div v-if="app.creative && app.adType" class="flex flex-col gap-2">
                    <p class="m-0 flex items-center gap-1.5 text-sm font-semibold">
                        {{ t('business.detail.creativeTitle') }}
                        <PointShopTag tone="neutral">{{ t('business.detail.creativeVersion', { version: app.creative.version }) }}</PointShopTag>
                    </p>
                    <BusinessAdPreview :ad-type="app.adType" :image-url="app.creative.imageUrl" :link-url="app.creative.linkUrl" :alt-text="app.creative.altText ?? ''" :text="app.creative.text ?? ''" />
                    <p class="m-0 break-all text-xs text-muted-color">{{ app.creative.linkUrl }}<template v-if="app.creative.imageName"> · {{ app.creative.imageName }}</template></p>
                </div>

                <!-- 딜 정보(핫딜) -->
                <div v-if="app.deal" class="flex flex-col gap-1.5">
                    <p class="m-0 text-sm font-semibold">{{ t('business.detail.dealTitle') }}</p>
                    <HotdealThumb v-if="app.deal.imageUrl" :url="app.deal.imageUrl" class="aspect-square w-20" />
                    <p class="m-0 flex flex-wrap items-baseline gap-x-1.5 tabular-nums">
                        <span v-if="dealDiscount !== null" class="text-sm font-bold text-red-600 dark:text-red-400">{{ dealDiscount }}%</span>
                        <span class="text-base font-bold">{{ UtilBusinessMessages.won(t, app.deal.dealPrice) }}</span>
                        <span v-if="app.deal.listPrice" class="text-xs text-muted-color line-through">{{ UtilBusinessMessages.won(t, app.deal.listPrice) }}</span>
                    </p>
                    <p class="m-0 text-xs text-muted-color">
                        {{ app.deal.shopName }} · {{ t('business.deal.stock') }} {{ t(`business.deal.stockOption.${app.deal.stock}`) }}<template v-if="app.deal.endsOn"> · {{ t('business.deal.endsOn') }} {{ time.serviceDate(app.deal.endsOn) }}</template>
                    </p>
                    <a :href="app.deal.linkUrl" target="_blank" rel="sponsored nofollow noopener noreferrer" class="break-all text-xs text-primary hover:underline">{{ app.deal.linkUrl }}</a>
                    <p class="m-0 whitespace-pre-line text-sm">{{ app.deal.body }}</p>
                </div>
            </section>

            <!-- 처리 기록 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-history`">
                <h2 :id="`${uid}-history`" class="m-0 mb-3 text-sm font-semibold">{{ t('business.detail.historyTitle') }}</h2>
                <BusinessHistoryTimeline :history="history" />
            </section>

        </template>

        <PaymentFakeCheckoutDialog
            :visible="pay.fakeOpen.value"
            :checkout="pay.target.value?.checkout ?? null"
            :expires-at="pay.target.value?.expiresAt ?? ''"
            :busy="pay.fakeBusy.value"
            :error="pay.fakeError.value"
            @submit="pay.submitFake"
            @close="pay.closeFake"
        />
        <BusinessCreativeDialog
            v-if="app && app.adType"
            :visible="creativeOpen"
            :ad-type="app.adType"
            :creative="app.creative"
            :image="creativeImage"
            :image-busy="creativeImageBusy"
            :back-to-review="app.status !== 'SUBMITTED'"
            :busy="actionBusy"
            :error="creativeError"
            :field-errors="creativeFields"
            @pick-image="uploadCreativeImage"
            @remove-image="creativeImage = null"
            @submit="saveCreative"
            @close="creativeOpen = false"
        />
        <BusinessPauseDialog
            v-if="app && pauseInfo"
            :visible="pauseOpen"
            :end-label="endLabel"
            :left="Math.max(0, pausesLeft - 1)"
            :deal="app.kind === 'HOTDEAL_POST'"
            :busy="actionBusy"
            :error="pauseError"
            @submit="doPause"
            @close="pauseOpen = false"
        />
        <BusinessStopDialog
            v-if="app && stopEstimate"
            :visible="stopOpen"
            :refund-amount="stopEstimate.amount"
            :prorated-amount="stopEstimate.prorated"
            :fee-rate="stopEstimate.feeRate"
            :fee-amount="stopEstimate.fee"
            :total-days="stopEstimate.totalDays"
            :left-days="stopEstimate.leftDays"
            :from-paused="stopEstimate.fromPaused"
            :from-label="stopEstimate.fromLabel"
            :post-fee="postFee"
            :no-refund="noStopRefund"
            :busy="actionBusy"
            :error="stopError"
            @submit="doStop"
            @close="stopOpen = false"
        />
        <BusinessRefundDialog
            v-if="app"
            :visible="refundOpen"
            :running="app.phase === 'RUNNING'"
            :payment-method="app.paymentMethod"
            :refund-amount="refundAmount"
            :busy="actionBusy"
            :error="refundError"
            @submit="requestRefund"
            @close="refundOpen = false"
        />
    </div>
</template>

<script setup lang="ts">
import type { AdApplicationStats, BizApplicationDetail, BizCreativeRequest, BizPaymentStart, BusinessFile } from '~/composables/business/api/useBusinessApi.types';
import type { PaymentResult } from '~/composables/payment/api/usePaymentApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

/**
 * 신청 건 상세 `/business/applications/{id}` — 로그인 필요. 내 신청 건 목록·사업자 센터 홈의 할 일 카드·알림(결제 요청·기한 임박)이 온다.
 * 결제는 `payment` 모듈 한 길(`BIZ_APPLICATION`) — 결제 시작(`useBusinessApi.startPayment`) 뒤 공용 결제창 흐름(`usePaymentCheckout`)으로 완료하고 상세를 다시 읽는다(공구 결제 화면과 같은 방식).
 */
definePageMeta({ middleware: 'auth', meMenu: true });

const { t } = useI18n();
useSeoMeta({ title: () => t('business.detail.title'), description: () => t('business.detail.seoDescription'), robots: 'noindex' });

const uid = useId();
const route = useRoute();
const router = useRouter();
const api = useBusinessApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const now = useNowTicker();
const { user } = useAuth();

const id = computed(() => String(route.params.id ?? ''));
const app = ref<BizApplicationDetail | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const actionBusy = ref(false);
const justPaid = ref(false);

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        app.value = await api.application(id.value);
        status.value = 'ready';
    } catch (e) {
        app.value = null;
        loadError.value = e instanceof Error ? e.message : '';
        status.value = 'error';
    }
}

onMounted(async () => {
    // 포트원 모바일 리디렉션에서 돌아왔으면 이어 간다(같은 결제 번호일 때만) — 결과는 `onPaid` 가 상세를 다시 읽는다
    await pay.resumeRedirect();
    await load();
    // 사업자 센터 「진행 중 광고」 줄에서 왔으면 성과 칸으로(상세를 받은 뒤라 라우터의 해시 스크롤이 닿지 못한다)
    if (route.hash === `#${UtilAdStats.ANCHOR}`) {
        await nextTick();
        document.getElementById(UtilAdStats.ANCHOR)?.scrollIntoView({ block: 'start' });
    }
    // 홈의 [결제하기]로 왔으면 결제창을 바로 연다(쿼리는 지운다 — 새로고침에 다시 열리지 않게)
    if (route.query.pay) {
        void router.replace({ query: { ...route.query, pay: undefined } });
        if (app.value?.status === 'AWAITING_PAYMENT') void startPay();
    }
});

/** 처리 상태가 이미 바뀌었으면(409) 알리고 다시 읽는다 — 그 밖의 오류는 문구를 돌려준다 */
async function handleError(e: unknown, fallback: string): Promise<string> {
    if (e instanceof ApiError && e.code === 'BIZ_APPLICATION_STATE_CHANGED') {
        toast.show(t('business.detail.stateChanged'));
        await load();
        return '';
    }
    return e instanceof Error ? e.message : fallback;
}

const kindLabel = computed(() => {
    if (!app.value) return '';
    const kind = t(`business.kind.${app.value.kind}`);
    return app.value.adType && app.value.kind === 'AD_BANNER' ? `${kind} · ${t(`business.adType.${app.value.adType}`)}` : kind;
});
const periodLabel = computed(() =>
    app.value ? UtilBusinessMessages.period(t, time.value.serviceDate(app.value.startDate), time.value.serviceDate(app.value.endDate), app.value.startDate, app.value.endDate) : '',
);
/** 처리 기록 + 시각으로 계산한 노출 시작·종료(서버는 기록하지 않는다) */
const history = computed(() => (app.value ? UtilBusiness.withPhaseHistory(app.value.history, app.value.status, app.value.phase, app.value.startDate, app.value.endDate) : []));
/** 결제(입금)까지 마친 건인가 — 단계 표시의 멈춘 자리 */
const paid = computed(() => Boolean(app.value?.history.some((h) => h.action === 'PAID' || h.action === 'DEPOSIT_CONFIRMED')));
const dueSoon = computed(() => Boolean(app.value?.paymentDueAt && Date.parse(app.value.paymentDueAt) - now.value < UtilBusiness.DUE_WARN_MS));
const dealDiscount = computed(() => (app.value?.deal ? UtilBusiness.discountRate(app.value.deal.listPrice, app.value.deal.dealPrice) : null));
const retryPath = computed(() => (app.value && UtilBusiness.isAd(app.value.kind) ? UtilBusiness.AD_NEW_PATH : UtilBusiness.DEAL_NEW_PATH));

/* ── 신청 취소(결제 전) ── */
async function cancel(): Promise<void> {
    if (!app.value || actionBusy.value) return;
    const ok = await confirm({ title: t('business.detail.cancelConfirmTitle'), message: t('business.detail.cancelConfirmMessage'), confirmLabel: t('business.detail.cancel'), tone: 'danger' });
    if (!ok) return;
    actionBusy.value = true;
    try {
        app.value = await api.cancel(app.value.id);
        toast.success(t('business.detail.canceled'));
    } catch (e) {
        const message = await handleError(e, t('common.failed'));
        if (message) toast.error(message, 8000);
    } finally {
        actionBusy.value = false;
    }
}

/* ── 결제(온라인) — 결제 공용 흐름(가짜 결제창 · 포트원) ── */
const payStarting = ref(false);
const payError = ref('');
const pay = usePaymentCheckout<BizPaymentStart>({
    pendingKey: 'irobit-biz-application-payment-pending',
    redirectPath: (target) => UtilBusiness.applicationPath(target.orderRefId),
    // 포트원은 구매자 전화번호가 비면 결제창을 열지 않는다 — 서버가 결제 시작 응답에 사업자 상호·연락처·이메일을 싣는다(없을 때만 회원 값)
    customer: (target) => target.customer ?? { fullName: user.value?.nickname ?? '', phoneNumber: '', email: user.value?.email ?? '' },
    fakeCheckout: (paymentId, body) => api.fakeCheckout(paymentId, body),
    complete: (paymentId, body) => api.completePayment(paymentId, body),
    onResult: onPaid,
});

function onPaid(result: PaymentResult): void {
    if (result.accepted) {
        justPaid.value = true;
        toast.success(t('business.detail.paidTitle'));
    } else {
        payError.value = t('business.detail.payNotDone');
    }
    void load();
}

/** 결제창을 닫았거나 실패했을 때의 한 줄 */
const payNote = computed(() => {
    const note = pay.note.value;
    if (!note) return '';
    return note === 'CLOSED' ? t('business.detail.payCanceled') : t('business.detail.payFailed');
});

async function startPay(): Promise<void> {
    if (!app.value || payStarting.value || pay.busy.value) return;
    payStarting.value = true;
    payError.value = '';
    try {
        pay.open(await api.startPayment(app.value.id));
    } catch (e) {
        payError.value = await handleError(e, t('business.detail.payFailed'));
    } finally {
        payStarting.value = false;
    }
}

/* ── 소재 바꾸기(광고 — 결제 전) ── */
const creativeOpen = ref(false);
const creativeImage = ref<BusinessFile | null>(null);
const creativeImageBusy = ref(false);
const creativeError = ref('');
const creativeFields = ref<Record<string, string>>({});
/** 결제 전(심사 중 · 결제 대기 · 입금 확인 전 입금 대기)의 광고만 — 핫딜 글은 신청 화면에서 고친다 */
const canEditCreative = computed(() => {
    const a = app.value;
    if (!a || !a.adType || !UtilBusiness.isAd(a.kind)) return false;
    return a.status === 'SUBMITTED' || a.status === 'AWAITING_PAYMENT' || (a.status === 'AWAITING_DEPOSIT' && !a.deposit?.depositConfirmed);
});

function openCreative(): void {
    creativeImage.value = null;
    creativeError.value = '';
    creativeFields.value = {};
    creativeOpen.value = true;
}

async function uploadCreativeImage(file: File): Promise<void> {
    creativeImageBusy.value = true;
    creativeError.value = '';
    const rest = { ...creativeFields.value };
    delete rest.imageFileId;
    creativeFields.value = rest;
    try {
        // 배너 비율이 규격과 다르면 올리지 않고 이미지 칸에 알린다(±3%)
        const size = await UtilBusiness.imageSizeOf(file);
        const type = app.value?.adType;
        if (type && size && !UtilBusiness.bannerRatioOk(type, size.width, size.height)) {
            creativeFields.value = { ...rest, imageFileId: t('business.ad.imageRatio', { size: t(`business.adTypeSize.${type}`), width: size.width, height: size.height }) };
            return;
        }
        creativeImage.value = await api.uploadFile(file);
    } catch (e) {
        creativeError.value = e instanceof Error ? e.message : t('common.failed');
    } finally {
        creativeImageBusy.value = false;
    }
}

async function saveCreative(body: BizCreativeRequest): Promise<void> {
    if (!app.value) return;
    const wasSubmitted = app.value.status === 'SUBMITTED';
    actionBusy.value = true;
    creativeError.value = '';
    creativeFields.value = {};
    try {
        app.value = await api.updateCreative(app.value.id, { ...body });
        creativeOpen.value = false;
        toast.success(wasSubmitted ? t('business.detail.creativeSaved') : t('business.detail.creativeBackToReview'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields) creativeFields.value = { ...e.fields };
        else {
            creativeError.value = await handleError(e, t('common.failed'));
            if (!creativeError.value) creativeOpen.value = false;
        }
    } finally {
        actionBusy.value = false;
    }
}

/* ── 게시된 글(사업자 핫딜 2차) ── */
const dealPost = computed(() => (app.value?.kind === 'HOTDEAL_POST' ? (app.value.post ?? null) : null));
type PostTagTone = 'neutral' | 'primary' | 'success' | 'warn';
const postTone = computed<PostTagTone>(() => {
    const s = dealPost.value?.status;
    return s === 'POSTED' ? 'success' : s === 'FAILED' ? 'warn' : 'neutral';
});
const viewTone = computed<PostTagTone>(() => {
    const v = dealPost.value?.view;
    return v === 'ACTIVE' ? 'primary' : v === 'FLAGGED' ? 'warn' : 'neutral';
});
const placementTone = computed<PostTagTone>(() => {
    const s = dealPost.value?.placement?.status;
    return s === 'SHOWING' ? 'success' : s === 'STOPPED' ? 'warn' : 'neutral';
});
const waitingText = computed(() => {
    const p = dealPost.value;
    if (!p) return '';
    const at = time.value.full(p.publishAt);
    return p.prefixName ? t('business.post.waitingPrefixText', { at, board: p.boardName, prefix: p.prefixName }) : t('business.post.waitingText', { at, board: p.boardName });
});
const hiddenText = computed(() => {
    const s = app.value?.status;
    if (s === 'TAKEN_DOWN' && cause.value === 'ADVERTISER_PAUSE') return pausedEnded.value ? t('business.post.hiddenPausedEnded') : t('business.post.hiddenPaused');
    if (stoppedByMe.value) return t('business.post.hiddenStopped');
    if (s === 'TAKEN_DOWN') return t('business.post.hiddenTakenDown');
    if (s === 'CANCELED' || app.value?.refund?.status === 'APPROVED') return t('business.post.hiddenCanceled');
    return t('business.post.hiddenOther');
});
const requestCounts = computed(() => (dealPost.value?.endRequests ? UtilHotdealMessages.requestCounts(t, dealPost.value.endRequests) : ''));
/** 사업자 종료 — 게시되고 진행 중·의심일 때만(끝난 딜은 다시 열 수 없다) */
const canEndDeal = computed(() => dealPost.value?.status === 'POSTED' && (dealPost.value.view === 'ACTIVE' || dealPost.value.view === 'FLAGGED'));
const postLink = computed(() => `/posts/${dealPost.value?.postId ?? ''}`);

const endingDeal = ref(false);
async function endDeal(): Promise<void> {
    if (!app.value || endingDeal.value) return;
    const ok = await confirm({ title: t('business.post.endConfirmTitle'), message: t('business.post.endConfirmMessage'), confirmLabel: t('business.post.endSoldOut'), tone: 'danger' });
    if (!ok) return;
    endingDeal.value = true;
    try {
        const post = await api.endDeal(app.value.id);
        app.value = { ...app.value, post };
        toast.success(t('business.post.endDone'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'DEAL_ENDED') {
            // 그새 자동 종료·운영진 종료됐다 — 알리고 다시 읽는다
            toast.show(t('business.post.endStale'));
            await load();
        } else {
            const message = await handleError(e, t('common.failed'));
            if (message) toast.error(message, 8000);
        }
    } finally {
        endingDeal.value = false;
    }
}

/* ── 취소·환불 요청(예약·진행) ── */
const refundOpen = ref(false);
const refundError = ref('');
const refundAmount = computed(() => {
    const a = app.value;
    if (!a) return 0;
    const total = a.confirmed?.total ?? 0;
    return a.phase === 'RUNNING' ? UtilBusiness.proratedRefund(total, a.startDate, a.endDate, UtilBusiness.todayYmd(now.value)) : total;
});

async function requestRefund(reason: string): Promise<void> {
    if (!app.value) return;
    actionBusy.value = true;
    refundError.value = '';
    try {
        const result = await api.refundRequest(app.value.id, reason);
        app.value = result;
        refundOpen.value = false;
        toast.success(result.refund?.status === 'APPROVED' ? t('business.detail.refundAutoDone') : t('business.detail.refundRequested'));
    } catch (e) {
        refundError.value = await handleError(e, t('common.failed'));
        if (!refundError.value) refundOpen.value = false;
    } finally {
        actionBusy.value = false;
    }
}

/* ── 일시 정지·중단(광고주 요청 — biz-pause-contract) ── */
const cause = computed(() => app.value?.takedownCause ?? null);
const pauseInfo = computed(() => app.value?.pause ?? null);
/** 진행 중이라 [일시 정지]·[중단하고 환불 요청]을 보인다 */
const pausable = computed(() => (app.value ? UtilBusiness.runningPausable(app.value) : false));
const pausesLeft = computed(() => (pauseInfo.value ? Math.max(0, pauseInfo.value.limit - pauseInfo.value.used) : 0));
/** 정지한 채 끝 날짜가 지났다 — 환불 없이 끝남(화면이 끝 날짜와 지금을 비교) */
const pausedEnded = computed(() => (app.value ? UtilBusiness.pauseEnded(app.value.status, cause.value, app.value.endDate, now.value) : false));
/** 지금 정지 중(끝 전) — 중단 기준 = 멈춘 시각 */
const pausedNow = computed(() => app.value?.status === 'TAKEN_DOWN' && cause.value === 'ADVERTISER_PAUSE' && !pausedEnded.value);
/** 광고주 요청으로 중단한 건(승인 뒤 `CANCELED` 포함) */
const stoppedByMe = computed(() => cause.value === 'ADVERTISER_STOP' || app.value?.refund?.type === 'STOP_PRORATED');
const endLabel = computed(() => (app.value ? time.value.serviceDate(app.value.endDate) : ''));
const pausedSince = computed(() => (pauseInfo.value?.pausedAt ? time.value.full(pauseInfo.value.pausedAt) : ''));
const pausedSpan = computed(() => (pauseInfo.value && pauseInfo.value.pausedMinutes > 0 ? UtilBusinessMessages.minutes(t, pauseInfo.value.pausedMinutes) : ''));
const stoppedAt = computed(() => (pauseInfo.value?.stoppedAt ? time.value.full(pauseInfo.value.stoppedAt) : ''));
/** 중단 환불의 기준 시각 — 환불 줄의 `basisAt`(없으면 상세 `pause.refundFrom`) */
const refundFromLabel = computed(() => {
    const at = app.value?.refund?.basisAt ?? pauseInfo.value?.refundFrom ?? null;
    return at ? time.value.full(at) : '';
});
/** 중단 환불 내역(`STOP_PRORATED` — 서버 환불 줄 값 그대로). 승인 뒤에는 승인 금액이 따로 보인다 */
/** 환불이 두 줄 이상(중단 환불 + 추가 환불 등) — 이때 맨 위 종류·금액 줄은 줄 목록과 겹치므로 감춘다 */
const multiRefund = computed(() => (app.value?.refunds.length ?? 0) > 1);
const refundBreakdown = computed(() => {
    const r = app.value?.refund;
    if (!r || r.type !== 'STOP_PRORATED' || r.proratedAmount === null) return '';
    return UtilBusinessMessages.stopBreakdown(t, r.proratedAmount, r.feeRate ?? 0, r.feeAmount ?? 0, r.proposedAmount);
});

const pauseOpen = ref(false);
const pauseError = ref('');
function openPause(): void {
    pauseError.value = '';
    pauseOpen.value = true;
}

async function doPause(): Promise<void> {
    if (!app.value || actionBusy.value) return;
    actionBusy.value = true;
    pauseError.value = '';
    try {
        app.value = await api.pause(app.value.id);
        pauseOpen.value = false;
        toast.success(t('business.pause.pausedDone'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'BIZ_PAUSE_LIMIT') {
            // 그새 횟수를 다 썼다 — 알리고 다시 읽는다(중단만 남는다)
            pauseOpen.value = false;
            toast.error(e.message, 8000);
            await load();
        } else {
            pauseError.value = await handleError(e, t('common.failed'));
            if (!pauseError.value) pauseOpen.value = false;
        }
    } finally {
        actionBusy.value = false;
    }
}

async function resume(): Promise<void> {
    if (!app.value || actionBusy.value) return;
    const ok = await confirm({ title: t('business.pause.resumeConfirmTitle'), message: t('business.pause.resumeConfirmMessage', { end: endLabel.value }), confirmLabel: t('business.pause.resume') });
    if (!ok || !app.value) return;
    actionBusy.value = true;
    try {
        app.value = await api.resume(app.value.id);
        toast.success(t('business.pause.resumedDone'));
    } catch (e) {
        const message = await handleError(e, t('common.failed'));
        if (message) toast.error(message, 8000);
    } finally {
        actionBusy.value = false;
    }
}

const stopOpen = ref(false);
const stopError = ref('');
/** 환불 예상액(서버 `BizPricing.stop` 과 같은 식 — 일할 − 수수료, 비율은 그 건에 고정된 `pause.feeRate`) — 기준 = 정지 중이면 멈춘 시각, 아니면 지금 */
const stopEstimate = computed(() => {
    const a = app.value;
    const p = pauseInfo.value;
    if (!a || !p) return null;
    const pausedAt = pausedNow.value ? p.pausedAt : null;
    const from = pausedAt ?? new Date(now.value).toISOString();
    return {
        ...UtilBusiness.stopRefund(p.proratableTotal, a.startDate, a.endDate, from, p.feeRate),
        feeRate: p.feeRate,
        fromPaused: pausedAt !== null,
        fromLabel: pausedAt ? time.value.full(pausedAt) : '',
    };
});
/** 핫딜 글의 돌려드리지 않는 게시비 = 확정 합계 − 일할 대상(노출 자리 줄) */
const postFee = computed(() => {
    const a = app.value;
    const p = pauseInfo.value;
    if (!a || !p || a.kind !== 'HOTDEAL_POST') return null;
    return Math.max(0, (a.confirmed?.total ?? a.amount.total) - p.proratableTotal);
});
/** 돌려드릴 금액이 없다(핫딜 글 — 노출 자리 없음) */
const noStopRefund = computed(() => app.value?.kind === 'HOTDEAL_POST' && (pauseInfo.value?.proratableTotal ?? 0) <= 0);

function openStop(): void {
    stopError.value = '';
    stopOpen.value = true;
}

async function doStop(reason: string): Promise<void> {
    if (!app.value || actionBusy.value) return;
    actionBusy.value = true;
    stopError.value = '';
    try {
        const result = await api.stop(app.value.id, reason);
        app.value = result;
        stopOpen.value = false;
        toast.success(result.refund ? t('business.pause.stoppedDone') : t('business.pause.stoppedDoneNoRefund'));
    } catch (e) {
        stopError.value = await handleError(e, t('common.failed'));
        if (!stopError.value) stopOpen.value = false;
    } finally {
        actionBusy.value = false;
    }
}

/* ── 성과(광고 성과 — 노출·클릭) ── */
/** 오늘(한국 날짜) — 집계 하루는 표시 시간대와 무관 */
const today = computed(() => UtilAdStats.today(now.value));
const statsSection = computed(() => (app.value ? UtilAdStats.section(app.value, paid.value, today.value) : 'NONE'));
/** 성과를 셀 기간 — 시작 날짜 ~ min(끝 날짜, 오늘) */
const statsRange = computed(() => (app.value && statsSection.value === 'SHOW' ? UtilAdStats.range(app.value.startDate, app.value.endDate, today.value) : null));
const stats = ref<AdApplicationStats | null>(null);
const statsStatus = ref<'loading' | 'ready' | 'error'>('loading');
const statsError = ref('');
let statsSeq = 0;

async function loadStats(): Promise<void> {
    const range = statsRange.value;
    if (!app.value || !range) return;
    const seq = ++statsSeq;
    statsStatus.value = 'loading';
    statsError.value = '';
    try {
        const result = await api.stats(app.value.id, { from: range.from, to: range.to });
        if (seq !== statsSeq) return;
        stats.value = result;
        statsStatus.value = 'ready';
    } catch (e) {
        if (seq !== statsSeq) return;
        stats.value = null;
        statsError.value = e instanceof Error ? e.message : '';
        statsStatus.value = 'error';
    }
}

// 건·기간이 정해지거나 바뀔 때만 다시 받는다(정지·재개처럼 상세만 바뀌면 그대로 — 자정을 넘기면 기간 끝이 하루 늘어 다시 받는다)
watch(
    () => (statsRange.value && app.value ? `${app.value.id}|${statsRange.value.from}|${statsRange.value.to}` : ''),
    (key) => {
        if (key) void loadStats();
    },
);
</script>
