package com.community.batch.service;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.WorkerHeartbeats;
import com.community.groupbuy.service.ParcelTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공구 배송 상태 자동 반영(commerce-groupbuy-contract 결정 90)의 「언제 돌릴지」 — 관리자 서버, 5분마다 한 바퀴({@value ParcelTrackingService#WORKER_KEY}). 줄마다의 조회 간격은
 * 설정 주기(기본 3시간)와 실패 간격 표가 정하므로 바퀴는 차례가 된 줄만 집는다. 한 바퀴의 일(N일 자동 배송완료 · 스마트택배 조회 · 반영)은 shared {@link ParcelTrackingService} 가 한다.
 * {@code GroupBuyStateScheduler} 와 같은 모양이다.
 *
 * <ul>
 *   <li>{@code irobit.batch.enabled=false} 인 서버는 돌지 않는다(api 서버에는 이 예약이 없다). 배치를 켠 서버가 여럿이어도 줄마다 {@code SKIP LOCKED} + 임대로 겹치지 않는다.</li>
 *   <li>바퀴마다 워커 기록({@code worker_heartbeats}) — 일이 없으면 간격마다 한 번만, 실패하면 가린 오류 한 줄.</li>
 * </ul>
 */
@Component
public class GroupBuyTrackingScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyTrackingScheduler.class);

    private final BatchProperties properties;
    private final ParcelTrackingService tracking;
    private final WorkerHeartbeats heartbeats;

    public GroupBuyTrackingScheduler(BatchProperties properties, ParcelTrackingService tracking, WorkerHeartbeats heartbeats) {
        this.properties = properties;
        this.tracking = tracking;
        this.heartbeats = heartbeats;
    }

    /** @return 이번 바퀴에 다룬 건수(배치를 끈 서버면 -1) */
    @Scheduled(initialDelayString = "${irobit.groupbuy.tracking-poll-millis:300000}", fixedDelayString = "${irobit.groupbuy.tracking-poll-millis:300000}")
    public int trackingTick() {
        if (!properties.enabled()) {
            return -1;
        }
        try {
            int handled = tracking.runOnce().total();
            heartbeats.success(ParcelTrackingService.WORKER_KEY, handled);
            return handled;
        } catch (RuntimeException e) {
            log.error("공구 배송 조회 워커 한 바퀴가 실패했다 원인={}", e.getClass().getSimpleName());
            heartbeats.failure(ParcelTrackingService.WORKER_KEY, e);
            return 0;
        }
    }
}
