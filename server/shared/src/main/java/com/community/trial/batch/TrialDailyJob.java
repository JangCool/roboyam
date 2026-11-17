package com.community.trial.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.trial.entity.TrialApplicationEntity;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageReportEntity;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialEntity;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.trial.entity.TrialPhotoEntity;
import com.community.trial.entity.TrialSettingEntity;
import com.community.trial.entity.TrialStatus;
import com.community.trial.entity.TrialStockReceiptEntity;
import com.community.trial.repository.master.TrialApplicationRepository;
import com.community.trial.repository.master.TrialDamageReportRepository;
import com.community.trial.repository.master.TrialParticipantRepository;
import com.community.trial.repository.master.TrialPhotoRepository;
import com.community.trial.repository.master.TrialQueries;
import com.community.trial.repository.master.TrialRepository;
import com.community.trial.repository.master.TrialStockReceiptRepository;
import com.community.trial.service.PurchaseProofService;
import com.community.trial.service.TrialPayments;
import com.community.trial.service.TrialPromiseService;
import com.community.trial.service.TrialSettingsService;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 체험단 하루 정리 Java 배치 {@code trial-daily}(trial-group-contract 「구현 결정」 11) — 관리자 서버가 매일 00:20(한국 시간)에 돈다. 건마다 짧은 트랜잭션이고 한 건이 실패해도
 * 다음 건으로 넘어간다(실패 수는 건너뜀으로 남는다). 두 번 돌아도 같은 결과다(각 줄의 「했음」 칸·상태를 본다).
 *
 * <ol>
 *   <li>신청 마감이 지난 모집 중 → 뽑는 중</li>
 *   <li>받음 자동 — 송장 뒤 7일 안 누르지 않으면 송장 + 7일을 받은 때로(송장 기준 배송 완료)</li>
 *   <li>사용기 기한 넘김 — 신청 금지(설정 90일) · 넘김 두 번(설정)이면 리뷰어 칭호 회수 · 알림</li>
 *   <li>반납 독촉(기한 당일·7일 뒤 — 설정) · 미반납 확정(14일 — 설정: 영구 금지 + 리뷰어 회수 + 광고주에게 이름·연락처 제공 기록)</li>
 *   <li>파손 이의 기한(7일)이 지난 신고 → 운영자 확인할 일로</li>
 *   <li>구매 인증 사진 파기(확인 뒤 설정 일수)</li>
 *   <li>입고 보관 기한(설정 60일) — 반송 없으면 관리자 알림(착불 반송 안내) 한 번</li>
 *   <li>주소 사본 파기 — 안 뽑힘·취소 · 제공형 받음 · 대여형 반납 뒤 7일(열린 파손 신고 없음) · 미반납·과실 제공 뒤 30일 · 끝난 모집</li>
 *   <li>사진 보관 기한(V243 — 모집 끝·취소 뒤, 열린 파손 신고가 있으면 판정 뒤 설정 일수) — 바이트 파기 · 지운 글의 대기 구매 인증 자동 반려</li>
 *   <li>붙지 않은 사진(올린 뒤 하루) 정리 · 모든 줄이 판정된 진행 중 모집 → 끝남</li>
 * </ol>
 */
@Component
public class TrialDailyJob implements BatchJob {

    private static final Logger log = LoggerFactory.getLogger(TrialDailyJob.class);

    /** 송장 뒤 받음 자동 일수(계약 고정) */
    public static final int AUTO_RECEIVE_DAYS = 7;
    /** 대여형 반납 뒤 주소 사본을 지우기까지(파손 신고를 받을 여유) */
    public static final int RETURN_PURGE_DAYS = 7;
    /** 광고주에게 이름·연락처를 넘긴 뒤 사본을 지우기까지 */
    public static final int PROVIDED_PURGE_DAYS = 30;
    /** 커서로 훑는 한 쪽 크기 */
    static final int PAGE = 500;

    private final TrialRepository trials;
    private final TrialApplicationRepository applications;
    private final TrialParticipantRepository participants;
    private final TrialDamageReportRepository damages;
    private final TrialStockReceiptRepository stocks;
    private final TrialPhotoRepository photos;
    private final TrialQueries queries;
    private final TrialSettingsService settings;
    private final TrialPromiseService promise;
    private final PurchaseProofService proofs;
    private final MemberNotificationPublisher notices;
    private final NotificationPublisher operators;
    private final Messages messages;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final TrialPayments payments;

    public TrialDailyJob(TrialRepository trials, TrialApplicationRepository applications, TrialParticipantRepository participants, TrialDamageReportRepository damages,
                         TrialStockReceiptRepository stocks, TrialPhotoRepository photos, TrialQueries queries, TrialSettingsService settings, TrialPromiseService promise,
                         PurchaseProofService proofs, MemberNotificationPublisher notices, NotificationPublisher operators, Messages messages,
                         ObjectProvider<Clock> clocks, PlatformTransactionManager transactions, TrialPayments payments) {
        this.trials = trials;
        this.applications = applications;
        this.participants = participants;
        this.damages = damages;
        this.stocks = stocks;
        this.photos = photos;
        this.queries = queries;
        this.settings = settings;
        this.promise = promise;
        this.proofs = proofs;
        this.notices = notices;
        this.operators = operators;
        this.messages = messages;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
        this.tx = new TransactionTemplate(transactions);
        this.payments = payments;
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.TRIAL_DAILY;
    }

    /** 한 번 돈 결과 — 단계마다 처리한 수 */
    public record Summary(int closedApply, int autoReceived, int reviewLate, int reminded, int unreturned, int damageAwaiting, int proofsRejected, int proofsPurged,
                          int stockNotified,
                          int addressesPurged, int photosPurged, int photosCleaned, int closed, int failed) {

        int total() {
            return closedApply + autoReceived + reviewLate + reminded + unreturned + damageAwaiting + proofsRejected + proofsPurged + stockNotified + addressesPurged
                    + photosPurged + photosCleaned + closed;
        }
    }

    @Override
    public BatchResult run(BatchContext context) {
        Summary s = runOnce();
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        step.record(s.total() + s.failed(), s.total(), s.failed());
        return BatchResult.of(new MessageRef("batch.result.trialDaily", String.valueOf(s.autoReceived()), String.valueOf(s.reviewLate()), String.valueOf(s.reminded()),
                String.valueOf(s.unreturned()), String.valueOf(s.addressesPurged()), String.valueOf(s.proofsPurged())));
    }

    /** 배치 본체(시험이 바로 부른다) */
    public Summary runOnce() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        TrialSettingEntity.Values cfg = settings.current();
        int[] failed = {0};

        int closedApply = each(queries.recruitingEnded(now), failed, id -> trials.findWithLockById(id).filter(t -> t.getStatus() == TrialStatus.RECRUITING)
                .ifPresent(t -> t.changeStatus(TrialStatus.SELECTING, now)));

        int autoReceived = each(queries.autoReceiveDue(now.minus(Duration.ofDays(AUTO_RECEIVE_DAYS))), failed, id -> participants.findWithLockById(id)
                .filter(p -> p.shipped() && !p.received()).ifPresent(p -> {
                    TrialEntity t = trials.findById(p.getTrialId()).orElseThrow();
                    p.receive(TrialParticipantEntity.RECEIVED_BY_AUTO, p.getShippedAt().plus(Duration.ofDays(AUTO_RECEIVE_DAYS)), t.getReviewDays(), t.getReturnDays());
                }));

        int reviewLate = each(queries.reviewLateDue(now), failed, id -> participants.findWithLockById(id)
                .filter(p -> p.getReviewPostId() == null && p.getReviewLateAt() == null && p.getReviewDueAt() != null && p.getReviewDueAt().isBefore(now))
                .ifPresent(p -> promise.reviewLate(p, trials.findById(p.getTrialId()).orElseThrow(), cfg, now)));

        int[] reminded = {0};
        int unreturned = 0;
        long cursor = 0;
        // 때가 된 줄만 질의에서 거르고 ID 커서로 끝까지(M4-3 — 아직 때가 안 된 줄이 앞쪽 천 건을 막지 않게)
        for (List<TrialParticipantEntity> page = queries.returnOpen(now, cfg.returnRemindFirstDays(), cfg.returnRemindSecondDays(), cfg.unreturnedDays(), cursor, PAGE);
             !page.isEmpty();
             page = queries.returnOpen(now, cfg.returnRemindFirstDays(), cfg.returnRemindSecondDays(), cfg.unreturnedDays(), cursor, PAGE)) {
            for (TrialParticipantEntity open : page) {
                Long id = open.getId();
                cursor = id;
                Instant due = open.getReturnDueAt();
                try {
                    Integer result = tx.execute(status -> {
                        TrialParticipantEntity p = participants.findWithLockById(id).orElseThrow();
                        if (p.returned() || p.getUnreturnedAt() != null) {
                            return 0;
                        }
                        TrialEntity t = trials.findById(p.getTrialId()).orElseThrow();
                        if (!now.isBefore(due.plus(Duration.ofDays(cfg.unreturnedDays())))) {
                            promise.unreturned(p, t, now);
                            return 2;
                        }
                        if (p.getReturnRemindSecondAt() == null && !now.isBefore(due.plus(Duration.ofDays(cfg.returnRemindSecondDays())))) {
                            p.markRemindSecond(now);
                            if (p.getReturnRemindFirstAt() == null) {
                                p.markRemindFirst(now);
                            }
                            remind(p, t, cfg.unreturnedDays());
                            return 1;
                        }
                        if (p.getReturnRemindFirstAt() == null && !now.isBefore(due.plus(Duration.ofDays(cfg.returnRemindFirstDays())))) {
                            p.markRemindFirst(now);
                            remind(p, t, cfg.unreturnedDays());
                            return 1;
                        }
                        return 0;
                    });
                    if (result != null && result == 1) {
                        reminded[0]++;
                    } else if (result != null && result == 2) {
                        unreturned++;
                    }
                } catch (RuntimeException e) {
                    failed[0]++;
                    log.warn("체험단 반납 판정 실패 participantId={}", id, e);
                }
            }
        }

        int damageAwaiting = each(damages.findTop500ByStatusAndObjectionDueAtBefore(TrialDamageStatus.REPORTED, now).stream().map(TrialDamageReportEntity::getId).toList(),
                failed, id -> damages.findWithLockById(id).filter(d -> d.getStatus() == TrialDamageStatus.REPORTED).ifPresent(TrialDamageReportEntity::await));

        // 구매 인증 — 지운 글의 대기 인증 자동 반려(L3·M4-4) · 확인 뒤 보관 일수가 지난 사진 파기. 묶음이 꽉 차면 다시(하루 상한 없이 끝까지)
        int proofsRejected = repeat(failed, "지운 글 구매 인증 자동 반려", () -> proofs.rejectForDeletedPosts(now));
        int proofsPurged = repeat(failed, "구매 인증 사진 파기", () -> proofs.purgeDue(now.minus(Duration.ofDays(cfg.proofPhotoDays()))));

        int stockNotified = each(queries.stockKeepDue(now.minus(Duration.ofDays(cfg.stockKeepDays()))).stream().map(TrialStockReceiptEntity::getId).toList(), failed,
                id -> stocks.findById(id).filter(r -> r.getKeepNotifiedAt() == null && r.getReturnShippedAt() == null).ifPresent(r -> {
                    r.markKeepNotified(now);
                    String trialId = Long.toString(r.getTrialId());
                    operators.publish(AdminNotificationEvent.TRIAL_STOCK_KEEP_DUE, NotificationContext.empty().dedupe(trialId).link("id", trialId)
                            .args(trials.findById(r.getTrialId()).map(TrialEntity::getTitle).orElse("")));
                }));

        // 주소 사본 파기 — 후보를 ID 커서로 끝까지 훑고(M4-3), 때가 된 줄만 잠가 지운다
        int addressesPurged = 0;
        long appCursor = 0;
        for (List<Long> page = queries.purgeCandidates(appCursor, PAGE); !page.isEmpty(); page = queries.purgeCandidates(appCursor, PAGE)) {
            appCursor = page.get(page.size() - 1);
            List<Long> due = new ArrayList<>();
            try {
                List<Long> ids = page;
                tx.executeWithoutResult(status -> applications.findAllById(ids).forEach(a -> {
                    if (a.getAddressPurgedAt() == null && purgeDue(a, now)) {
                        due.add(a.getId());
                    }
                }));
            } catch (RuntimeException e) {
                failed[0]++;
                log.warn("체험단 주소 사본 파기 후보 판정 실패 afterId={}", appCursor, e);
                continue;
            }
            addressesPurged += each(due, failed, id -> applications.findWithLockById(id).filter(a -> a.getAddressPurgedAt() == null && purgeDue(a, now))
                    .ifPresent(a -> a.purgeAddress(now)));
        }

        // 체험단 사진 보관 기한(점검 D3) — 모집이 끝나거나 취소된 뒤(열린 파손 신고가 있으면 판정 뒤) 설정 일수가 지난 사진의 바이트를 지운다(줄은 남는다)
        int photosPurged = 0;
        Instant photoCutoff = now.minus(Duration.ofDays(cfg.photoKeepDays()));
        long photoCursor = 0;
        for (List<Long> page = queries.photoPurgeCandidates(photoCutoff, photoCursor, PAGE); !page.isEmpty();
             page = queries.photoPurgeCandidates(photoCutoff, photoCursor, PAGE)) {
            photoCursor = page.get(page.size() - 1);
            photosPurged += each(page, failed, id -> photos.findById(id).filter(p -> !p.purged()).ifPresent(p -> p.purge(now)));
        }

        // 붙지 않은 사진(올린 뒤 하루) — 줄째 지운다(V243 부터 바이트가 DB 안에 있어 저장소 파일이 없다)
        int photosCleaned = 0;
        for (TrialPhotoEntity loose : photos.findTop200ByAttachedAtIsNullAndCreatedAtBefore(now.minus(Duration.ofDays(1)))) {
            try {
                tx.executeWithoutResult(status -> photos.findById(loose.getId()).filter(p -> p.getAttachedAt() == null).ifPresent(photos::delete));
                photosCleaned++;
            } catch (RuntimeException e) {
                failed[0]++;
                log.warn("체험단 붙지 않은 사진 정리 실패 photoId={}", loose.getId(), e);
            }
        }

        // 모든 줄이 판정된 진행 중 모집 → 끝남. 광고 신청 건도 함께 정리한다(L5 — 결제 끝 건은 닫은 시각) — 열린 결제 → 신청 건 → 모집 차례로 잠근다
        int closed = each(queries.runningAllJudged(), failed, id -> {
            TrialPayments.Locked l = payments.lockFor(id, "TRIAL_CLOSED");
            if (l.trial().getStatus() == TrialStatus.RUNNING) {
                payments.close(l.trial(), l.app(), null, now);
            }
        });

        return new Summary(closedApply, autoReceived, reviewLate, reminded[0], unreturned, damageAwaiting, proofsRejected, proofsPurged, stockNotified, addressesPurged,
                photosPurged, photosCleaned, closed, failed[0]);
    }

    private void remind(TrialParticipantEntity p, TrialEntity t, int unreturnedDays) {
        notices.trial(p.getUserId(), MemberNotificationType.TRIAL_RETURN_DUE, t.getTitle(),
                messages.get(new MessageRef("trial.notice.returnDue", String.valueOf(unreturnedDays))), "/me/trials");
    }

    /** 주소 사본을 지울 때가 됐나(계약 「구현 결정」 11 — 반납 확인 뒤 파기) */
    private boolean purgeDue(TrialApplicationEntity a, Instant now) {
        if (a.getStatus() == TrialApplicationStatus.NOT_SELECTED || a.getStatus() == TrialApplicationStatus.CANCELLED) {
            return true;
        }
        TrialEntity t = trials.findById(a.getTrialId()).orElse(null);
        if (t == null || t.getStatus() == TrialStatus.CANCELLED) {
            return true;
        }
        TrialParticipantEntity p = participants.findByApplicationId(a.getId()).orElse(null);
        if (p == null) {
            return t.getStatus() == TrialStatus.CLOSED;
        }
        if (p.cancelled() || (t.getStatus() == TrialStatus.CLOSED && !p.shipped())) {
            return true; // 운영자가 취소한 줄 · 끝내 보내지 않고 모집이 끝난 줄(V242 — D9)
        }
        if (p.getContactProvidedAt() != null) {
            return !now.isBefore(p.getContactProvidedAt().plus(Duration.ofDays(PROVIDED_PURGE_DAYS)));
        }
        boolean openDamage = damages.findByParticipantIdOrderByCreatedAtDesc(p.getId()).stream().anyMatch(d -> d.getStatus() != TrialDamageStatus.DECIDED);
        if (openDamage) {
            return false;
        }
        if (!t.lend()) {
            return p.received();
        }
        return p.returned() && !now.isBefore(p.getReturnedAt().plus(Duration.ofDays(RETURN_PURGE_DAYS)));
    }

    /** 한 묶음(200건)씩 짧은 트랜잭션으로 되풀이 — 묶음이 꽉 차지 않을 때까지(최대 1000번 — 막힘 방지). 처리한 수 */
    private int repeat(int[] failed, String what, IntSupplier batch) {
        int total = 0;
        for (int round = 0; round < 1000; round++) {
            Integer n;
            try {
                n = tx.execute(status -> batch.getAsInt());
            } catch (RuntimeException e) {
                failed[0]++;
                log.warn("{} 실패", what, e);
                break;
            }
            int done = n == null ? 0 : n;
            total += done;
            if (done < 200) {
                break;
            }
        }
        return total;
    }

    /** 건마다 짧은 트랜잭션 — 실패는 세고 다음 건으로 */
    private int each(List<Long> ids, int[] failed, Consumer<Long> work) {
        int done = 0;
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(status -> work.accept(id));
                done++;
            } catch (RuntimeException e) {
                failed[0]++;
                log.warn("체험단 하루 정리 한 건 실패 id={}", id, e);
            }
        }
        return done;
    }
}
