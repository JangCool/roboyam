package com.community.withdrawal.service;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPhase;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.service.BizApplicationOps;
import com.community.bizapplication.service.BizPricing;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.BusinessStatus;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.withdrawal.repository.master.WithdrawalQueries;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업자 회원의 탈퇴 화면 정보(commerce.md 17-1) — 진행 중 광고·핫딜 신청 건마다 이름·끝·포기하면 잃는 금액(남은 기간 일할 — {@link BizPricing#prorate}, 이미 환불한 몫은 뺀다)과
 * 환불 대기 금액 합(포기와 무관하게 환불된다). 진행 중 판정은 {@code BizApplicationHookImpl#withdrawalCheck} 와 같다(예약이고 시작 ≤ 지금 < 끝, 또는 끝나지 않은 내림).
 */
@Component
@Transactional(readOnly = true)
public class WithdrawalBusinessInfo {

    /** 진행 중 건 하나 */
    public record Running(long id, String kind, String title, Instant endsAt, long forfeitAmount) {
    }

    /** 사업자 회원의 진행 중 건과 금액 — 사업자가 아니면 {@link #NONE} */
    public record Info(List<Running> running, long forfeitTotal, long refundWaitingTotal) {

        public static final Info NONE = new Info(List.of(), 0, 0);

        public boolean hasRunning() {
            return !running.isEmpty();
        }
    }

    private static final Set<BizApplicationStatus> LIVE = EnumSet.of(BizApplicationStatus.SCHEDULED, BizApplicationStatus.TAKEN_DOWN);

    private final BusinessProfileRepository profiles;
    private final BizApplicationStore applications;
    private final BizRefundStore refunds;
    private final BizApplicationOps ops;
    private final WithdrawalQueries queries;

    public WithdrawalBusinessInfo(BusinessProfileRepository profiles, BizApplicationStore applications, BizRefundStore refunds, BizApplicationOps ops, WithdrawalQueries queries) {
        this.profiles = profiles;
        this.applications = applications;
        this.refunds = refunds;
        this.ops = ops;
        this.queries = queries;
    }

    public Info of(long userId, Instant now) {
        Optional<BusinessProfileEntity> profile = profiles.findByUserId(userId).filter(p -> p.getStatus() != BusinessStatus.WITHDRAWN);
        if (profile.isEmpty()) {
            return Info.NONE;
        }
        long profileId = profile.get().getId();
        List<Running> running = new ArrayList<>();
        for (AppRow app : applications.byProfile(profileId, LIVE)) {
            if (!running(app, now)) {
                continue;
            }
            long paid = ops.paidAmount(app);
            long forfeit = Math.max(0, Math.min(BizPricing.prorate(paid, app.startAt(), app.endAt(), now).proposed(), paid - refunds.approvedTotal(app.id())));
            running.add(new Running(app.id(), app.kind().service().name(), app.title(), app.endAt(), forfeit));
        }
        return new Info(List.copyOf(running), running.stream().mapToLong(Running::forfeitAmount).sum(), queries.bizRefundWaitingTotal(profileId));
    }

    /** 진행 중 — 예약이고 시작 ≤ 지금 < 끝, 또는 끝나지 않은 내림({@code BizApplicationHookImpl#running} 과 같다) */
    static boolean running(AppRow a, Instant now) {
        if (a.status() == BizApplicationStatus.TAKEN_DOWN) {
            return a.endAt().isAfter(now);
        }
        return BizPhase.of(a.startAt(), a.endAt(), now) == BizPhase.RUNNING;
    }
}
