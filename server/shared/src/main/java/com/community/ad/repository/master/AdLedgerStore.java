package com.community.ad.repository.master;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizSource;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.bizapplication.entity.QBizApplicationItemEntity;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * 광고 일정표의 장부 읽기(ad-contract) — 장부 = 신청 건({@code biz_applications}) 중 그 자리(상품) 줄이 있는 건. 유형 7개(배너 넷·한 줄·핫딜 홈 상단·말머리 상단)가 상품 이름 그대로다.
 * <b>장부에 드는 상태</b>: 자리를 차지하는 상태(결제·입금 대기 · 예약 · 내림) + 운영자 등록의 심사 대기(광고 심사 큐에도 있다) + 승인 뒤 취소된 건. 사업자 신청의 심사 대기·반려·만료·
 * 승인 전 취소는 심사 큐에만 있다. QueryDSL 만 쓴다.
 *
 * <p>공개 노출 조회({@code PublicAdStore})도 같은 모양이다: {@code status = 'SCHEDULED' AND start_at <= now < end_at} 인 그 상품 줄의 건 + 최신 승인 소재 버전,
 * 비중({@code weight}) 가중 무작위는 노출 쪽이 고른다.
 */
@Repository
public class AdLedgerStore extends RoboyamRepositoryQDSL {

    private static final QBizApplicationEntity A = QBizApplicationEntity.bizApplicationEntity;
    private static final QBizApplicationItemEntity I = QBizApplicationItemEntity.bizApplicationItemEntity;

    static final int MAX_ROWS = 2000;

    private final BizApplicationStore apps;

    public AdLedgerStore(JPAQueryFactory query, BizApplicationStore apps) {
        super(query);
        this.apps = apps;
    }

    /** 장부에 드는 건 */
    static BooleanExpression ledger() {
        return A.status.in(BizApplicationStatus.AWAITING_PAYMENT, BizApplicationStatus.AWAITING_DEPOSIT, BizApplicationStatus.SCHEDULED, BizApplicationStatus.TAKEN_DOWN)
                .or(A.status.eq(BizApplicationStatus.SUBMITTED).and(A.source.eq(BizSource.OPERATOR)))
                .or(A.status.eq(BizApplicationStatus.CANCELED).and(A.approvedAt.isNotNull()));
    }

    /**
     * 그 상품의 장부 — 기간이 {@code [from, to)} 와 겹치는 건(둘 다 null 이면 기간 무관), 검색(번호·광고주·상호·제목). 시작이 늦은 것 먼저, 최대 2,000건.
     */
    public List<AppRow> ledger(BizProduct product, Instant from, Instant to, String q) {
        JPAQuery<Long> select = withProduct(product).where(ledger());
        if (from != null && to != null) {
            select.where(A.startAt.lt(to), A.endAt.gt(from));
        }
        if (q != null && !q.isBlank()) {
            select.where(BizApplicationStore.search(q));
        }
        List<Long> ids = select.orderBy(A.startAt.desc(), A.id.desc()).limit(MAX_ROWS).fetch();
        Map<Long, AppRow> byId = new HashMap<>();
        apps.findAll(ids).forEach(r -> byId.put(r.id(), r));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /**
     * 그 상품의 지금 자리를 차지한 건(결제·입금 대기 · 끝나지 않은 예약 · 끝나지 않은 내림 — 일시 정지·중단·긴급 내림·사업자 정지, 되돌리거나 환불 승인 전까지 자리를 잡고 있다) — 유형 요약.
     * 자리를 차지하는 상태 목록은 한도 판정({@code BizSlotStore})과 같다.
     */
    public List<AppRow> live(BizProduct product, Instant now) {
        List<Long> ids = withProduct(product).where(A.status.in(BizApplicationStatus.OCCUPYING), A.endAt.gt(now)).limit(MAX_ROWS).fetch();
        return apps.findAll(ids);
    }

    private JPAQuery<Long> withProduct(BizProduct product) {
        return query.select(A.id).from(A).join(I).on(I.applicationId.eq(A.id), I.product.eq(product));
    }
}
