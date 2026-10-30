package com.community.business.service;

import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.entity.BusinessStatus;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.repository.master.BusinessStore;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 때의 사업자 신원(F8) — <b>공개 탈퇴 API 가 아직 없어 부르는 곳이 없다</b>. 탈퇴 API(커머스 「탈퇴 대비」와 함께)가 생기면 탈퇴 전에 {@link #check}, 탈퇴 트랜잭션 안에서
 * {@link #withdraw} 를 부른다. 결제 전·시작 전 신청 건은 탈퇴 때 자동 취소·환불(신청 건 훅)하고 진행 중 건만 막는다(「남은 기간 포기」 동의로 풀 수 있음).
 */
@Service
public class BusinessWithdrawal {

    private final BusinessProfileRepository profiles;
    private final BusinessServiceRows serviceRows;
    private final BusinessStore store;
    private final ObjectProvider<BusinessApplicationHook> hooks;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public BusinessWithdrawal(BusinessProfileRepository profiles, BusinessServiceRows serviceRows, BusinessStore store, ObjectProvider<BusinessApplicationHook> hooks,
                              SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.profiles = profiles;
        this.serviceRows = serviceRows;
        this.store = store;
        this.hooks = hooks;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 탈퇴할 수 있는가 — 사업자 신원이 없거나 이미 탈퇴면 막지 않는다 */
    @Transactional(readOnly = true)
    public BusinessApplicationHook.WithdrawalCheck check(long userId) {
        return profiles.findByUserId(userId).filter(p -> p.getStatus() != BusinessStatus.WITHDRAWN)
                .map(p -> hook().withdrawalCheck(p.getId())).orElse(BusinessApplicationHook.WithdrawalCheck.FREE);
    }

    /** 탈퇴 — 신원 탈퇴 · 서비스 줄 닫기 · 신청 건 정리(훅). 부르는 쪽(탈퇴 API)이 {@link #check} 와 포기 동의를 먼저 본다 */
    @Transactional
    public void withdraw(long userId, boolean forfeitRunning) {
        BusinessProfileEntity p = profiles.findWithLockByUserId(userId).filter(found -> found.getStatus() != BusinessStatus.WITHDRAWN).orElse(null);
        if (p == null) {
            return;
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        hook().onWithdrawn(p.getId(), forfeitRunning);
        p.withdraw(now);
        serviceRows.sync(p.getId(), BusinessServiceStatus.OFF, null, now);
        store.insertHistory(ids.nextId(), p.getId(), BusinessStore.WITHDRAWN, BusinessStore.MEMBER, userId, forfeitRunning ? "FORFEIT" : null, now);
    }

    private BusinessApplicationHook hook() {
        return hooks.getIfAvailable(() -> BusinessApplicationHook.NONE);
    }
}
