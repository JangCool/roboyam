package com.community.groupbuy.service;

import org.springframework.stereotype.Service;

/**
 * 발송·요청 쪽 1분 워커 한 바퀴(워커 키 {@value #WORKER_KEY}) — 에스크로 대기열(등록·구매 확정, 결정 14·64), 반품비 부족분을 하루 안에 결제하지 않은 요청 닫기(결정 68),
 * 공급 불가 선택 기한이 지난 주문을 「나머지만 받기」로(결정 53), 붙지 않은 요청 사진 정리(24시간). 「언제 돌릴지」는 admin 의 {@code GroupBuyStateScheduler}.
 */
@Service
public class GroupBuyFulfillmentWorker {

    public static final String WORKER_KEY = "groupbuy-fulfillment";

    private final GroupBuyEscrowWorker escrow;
    private final GroupBuyRequestService requests;
    private final GroupBuySupplyService supply;

    public GroupBuyFulfillmentWorker(GroupBuyEscrowWorker escrow, GroupBuyRequestService requests, GroupBuySupplyService supply) {
        this.escrow = escrow;
        this.requests = requests;
        this.supply = supply;
    }

    /** @return 이번 바퀴에 다룬 건수 */
    public int runOnce() {
        int n = escrow.runOnce();
        n += requests.closeUnpaid();
        n += supply.applyDefaults();
        n += requests.cleanupLoosePhotos().size();
        return n;
    }
}
