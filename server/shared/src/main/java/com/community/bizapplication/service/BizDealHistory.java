package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 사업자 핫딜 글의 딜이 끝났을 때 신청 건 처리 기록을 남긴다(biz-hotdeal-contract 규칙 6) — 사업자 종료 {@code DEAL_ENDED_BY_BUSINESS} · 종료 요청 자동 종료 {@code DEAL_AUTO_ENDED}.
 * 신청 건 상태는 바꾸지 않고 환불도 없다(기간은 흐른다). 회원 핫딜 종료 공용 길({@code DealEndService})이 부른다 — 회원 글이면 아무것도 하지 않는다.
 */
@Component
public class BizDealHistory {

    private final BizApplicationStore store;
    private final SnowflakeIdGenerator ids;

    public BizDealHistory(BizApplicationStore store, SnowflakeIdGenerator ids) {
        this.store = store;
        this.ids = ids;
    }

    /**
     * @param auto 종료 요청 자동 종료(배치)면 true, 작성자(사업자) 종료면 false
     */
    public void ended(PostDealEntity d, boolean auto, Instant now) {
        if (!d.isBusiness() || d.getBizApplicationId() == null) {
            return;
        }
        store.insertEvent(ids.nextId(), d.getBizApplicationId(), auto ? BizEventAction.DEAL_AUTO_ENDED : BizEventAction.DEAL_ENDED_BY_BUSINESS,
                auto ? BizActorType.SYSTEM : BizActorType.MEMBER, auto ? null : d.getAuthorId(), null, now);
    }
}
