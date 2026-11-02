package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 사업자 핫딜 글의 딜이 바뀌었을 때 신청 건 처리 기록을 남긴다(biz-hotdeal-contract 규칙 6·결정 11·12) — 사업자 종료 {@code DEAL_ENDED_BY_BUSINESS} · 종료 요청 자동 종료
 * {@code DEAL_AUTO_ENDED}(사업자 글은 더 자동 종료하지 않는다 — 옛 기록 읽기용) · 종료 요청 확인 필요 {@code DEAL_REVIEW_NEEDED} · 운영자 유지 {@code DEAL_KEPT_BY_OPERATOR} ·
 * 운영진 종료·재개 {@code DEAL_ENDED_BY_OPERATOR}·{@code DEAL_REOPENED_BY_OPERATOR}. 신청 건 상태는 바꾸지 않고 환불도 없다(기간은 흐른다). 회원 핫딜 공용 길
 * ({@code DealEndService}·{@code DealStateService})이 부른다 — 회원 글이면 아무것도 하지 않는다.
 */
@Component
public class BizDealHistory {

    private final BizApplicationStore store;
    private final BizApplicationNotices notices;
    private final SnowflakeIdGenerator ids;

    public BizDealHistory(BizApplicationStore store, BizApplicationNotices notices, SnowflakeIdGenerator ids) {
        this.store = store;
        this.notices = notices;
        this.ids = ids;
    }

    /**
     * @param auto 종료 요청 자동 종료(배치)면 true, 작성자(사업자) 종료면 false
     */
    public void ended(PostDealEntity d, boolean auto, Instant now) {
        if (!business(d)) {
            return;
        }
        store.insertEvent(ids.nextId(), d.getBizApplicationId(), auto ? BizEventAction.DEAL_AUTO_ENDED : BizEventAction.DEAL_ENDED_BY_BUSINESS,
                auto ? BizActorType.SYSTEM : BizActorType.MEMBER, auto ? null : d.getAuthorId(), null, now);
    }

    /** 종료 요청이 문턱에 닿았다 — 처리 기록 + 운영자 알림 {@code HOTDEAL_BUSINESS_REVIEW} + 사업자 알림 「운영팀 확인 중」(결정 11) */
    public void reviewNeeded(PostDealEntity d, Instant now) {
        if (!business(d)) {
            return;
        }
        store.insertEvent(ids.nextId(), d.getBizApplicationId(), BizEventAction.DEAL_REVIEW_NEEDED, BizActorType.SYSTEM, null, null, now);
        notices.businessReview(d.getPostId());
        member(d, BizApplicationNotices.Kind.DEAL_REVIEW, null);
    }

    /** 운영자 [유지] — 처리 기록(사유) + 사업자 알림 */
    public void keptByOperator(PostDealEntity d, long operatorId, String reason, Instant now) {
        if (!business(d)) {
            return;
        }
        store.insertEvent(ids.nextId(), d.getBizApplicationId(), BizEventAction.DEAL_KEPT_BY_OPERATOR, BizActorType.OPERATOR, operatorId, reason, now);
        member(d, BizApplicationNotices.Kind.DEAL_KEPT, reason);
    }

    /**
     * 운영진 종료 — 처리 기록(사유). 사업자 알림은 핫딜 알림 {@code DEAL_MANAGER_ENDED}(작성자 = 사업자, 사유)가 이미 간다.
     *
     * @param role {@link PostDealEntity#ROLE_OPERATOR} 면 행위자 운영자, 게시판 매니저·스텝이면 회원
     */
    public void managerEnded(PostDealEntity d, long actorId, String role, String reason, Instant now) {
        if (!business(d)) {
            return;
        }
        store.insertEvent(ids.nextId(), d.getBizApplicationId(), BizEventAction.DEAL_ENDED_BY_OPERATOR, actorType(role), actorId, reason, now);
    }

    /** 운영진 재개 — 처리 기록(사유) + 사업자 알림 */
    public void managerReopened(PostDealEntity d, long actorId, String role, String reason, Instant now) {
        if (!business(d)) {
            return;
        }
        store.insertEvent(ids.nextId(), d.getBizApplicationId(), BizEventAction.DEAL_REOPENED_BY_OPERATOR, actorType(role), actorId, reason, now);
        member(d, BizApplicationNotices.Kind.DEAL_REOPENED, reason);
    }

    private void member(PostDealEntity d, BizApplicationNotices.Kind kind, String note) {
        store.find(d.getBizApplicationId()).ifPresent(app -> notices.member(app, kind, 0, note));
    }

    private static boolean business(PostDealEntity d) {
        return d.isBusiness() && d.getBizApplicationId() != null;
    }

    private static BizActorType actorType(String role) {
        return PostDealEntity.ROLE_OPERATOR.equals(role) ? BizActorType.OPERATOR : BizActorType.MEMBER;
    }
}
