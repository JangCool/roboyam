package com.community.ad.repository.master;

import com.community.ad.entity.QAdDailyStatEntity;
import com.community.ad.entity.QAdAdvertiserEntity;
import com.community.ad.service.AdAdvertiserService;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.bizapplication.entity.QBizApplicationItemEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 운영자 광고 성과 읽기(ad-performance-contract 규칙 9·10) — 합계·건별. 건의 유형 = 한도가 있는 상품 줄(핫딜 게시 {@code HOTDEAL_POST} 가 아닌 첫 줄 — 상품 이름 차례,
 * {@code BizApplicationReader.capacityProduct} 와 같다 — 그런 줄이 없는 건은 빠진다). 광고주 거르기는 광고주 키(ad-advertiser-contract 규칙 1·11): {@code adv-<id>} = 그 직접 등록
 * 광고주의 건, {@code biz-<id>} = 그 사업자 신원의 모든 건(붙은 광고주 줄의 건 포함). 사업자에 붙은 광고주 줄의 {@code adv-} 별칭은 서비스가 {@code biz-} 로 풀어서 넘긴다.
 * QueryDSL 만 쓴다 — 건의 유형은 상품 이름이 가장 앞선 줄({@code min(product)}) 상관 서브쿼리다(예전 {@code JOIN LATERAL … ORDER BY product LIMIT 1} 과 같은 값).
 */
@Repository
public class AdStatsAdminStore extends RoboyamRepositoryQDSL {

    private static final QAdDailyStatEntity S = QAdDailyStatEntity.adDailyStatEntity;
    private static final QBizApplicationEntity A = QBizApplicationEntity.bizApplicationEntity;
    private static final QBizApplicationItemEntity I = QBizApplicationItemEntity.bizApplicationItemEntity;

    /** 건 · 날짜 · 자리 합(버전은 합친다) */
    public record SumRow(long applicationId, BizProduct product, LocalDate day, String placement, long impressions, long clicks) {
    }

    /** 건별 표 후보 — 범위 합(0 포함) */
    public record BookingSum(long applicationId, BizProduct product, long impressions, long clicks) {
    }

    public AdStatsAdminStore(JPAQueryFactory query) {
        super(query);
    }

    /** 그 건(신청 건 ID 식)의 유형 — 핫딜 게시가 아닌 상품 줄 중 이름이 가장 앞선 것, 없으면 null */
    static JPQLQuery<BizProduct> typeOf(NumberPath<Long> applicationId) {
        return JPAExpressions.select(I.product.min()).from(I).where(I.applicationId.eq(applicationId), I.product.ne(BizProduct.HOTDEAL_POST));
    }

    /** 범위 {@code [from, to]}(끝 포함)의 집계 — 유형·광고주 거르기(null = 안 거름) */
    public List<SumRow> sums(LocalDate from, LocalDate to, BizProduct type, AdAdvertiserService.Ref advertiser) {
        JPQLQuery<BizProduct> product = typeOf(S.applicationId);
        NumberExpression<Long> impressions = sum(S.impressions);
        NumberExpression<Long> clicks = sum(S.clicks);
        JPAQuery<?> select = query.from(S).join(A).on(A.id.eq(S.applicationId)).where(S.day.between(from, to), product.isNotNull());
        filters(select, product, type, advertiser);
        return select.select(S.applicationId, product, S.day, S.placement, impressions, clicks).groupBy(S.applicationId, S.day, S.placement).fetch().stream()
                .map(t -> new SumRow(t.get(S.applicationId), t.get(product), t.get(S.day), t.get(S.placement), n(t.get(impressions)), n(t.get(clicks))))
                .toList();
    }

    /**
     * 건별 표 후보 — 기간이 범위와 겹치고 시작한({@code start_at <= now}) 결제 뒤 상태(예약 · 내림 · 승인 뒤 취소) 건 + 범위 합(0 포함).
     */
    public List<BookingSum> bookings(LocalDate from, LocalDate to, Instant rangeStart, Instant rangeEnd, BizProduct type, AdAdvertiserService.Ref advertiser,
                                     Instant now) {
        JPQLQuery<BizProduct> product = typeOf(A.id);
        QAdDailyStatEntity x = new QAdDailyStatEntity("x");
        JPQLQuery<Long> impressions = JPAExpressions.select(sum(x.impressions)).from(x).where(x.applicationId.eq(A.id), x.day.between(from, to));
        JPQLQuery<Long> clicks = JPAExpressions.select(sum(x.clicks)).from(x).where(x.applicationId.eq(A.id), x.day.between(from, to));
        JPAQuery<?> select = query.from(A).where(A.status.in(BizApplicationStatus.SCHEDULED, BizApplicationStatus.TAKEN_DOWN)
                        .or(A.status.eq(BizApplicationStatus.CANCELED).and(A.approvedAt.isNotNull())), A.startAt.loe(now), A.startAt.lt(rangeEnd), A.endAt.gt(rangeStart),
                product.isNotNull());
        filters(select, product, type, advertiser);
        return select.select(A.id, product, impressions, clicks).fetch().stream()
                .map(t -> new BookingSum(t.get(A.id), t.get(product), n(t.get(impressions)), n(t.get(clicks)))).toList();
    }

    private static void filters(JPAQuery<?> select, JPQLQuery<BizProduct> product, BizProduct type, AdAdvertiserService.Ref advertiser) {
        if (type != null) {
            select.where(product.eq(type));
        }
        if (advertiser != null && advertiser.advertiserId() != null) {
            select.where(A.advertiserId.eq(advertiser.advertiserId()));
        } else if (advertiser != null && advertiser.businessProfileId() != null) {
            // 사업자 키 = 그 사업자의 모든 건(신청 건 · 사업자를 고른 운영자 등록 건 · 붙은 광고주 줄의 건 — ad-advertiser-contract 규칙 11)
            QAdAdvertiserEntity adv = new QAdAdvertiserEntity("adv");
            BooleanExpression attached = A.businessProfileId.isNull()
                    .and(A.advertiserId.in(JPAExpressions.select(adv.id).from(adv).where(adv.businessProfileId.eq(advertiser.businessProfileId()))));
            select.where(A.businessProfileId.eq(advertiser.businessProfileId()).or(attached));
        }
    }

    private static NumberExpression<Long> sum(NumberPath<Long> value) {
        return Expressions.numberTemplate(Long.class, "sum({0})", value);
    }

    private static long n(Long value) {
        return value == null ? 0 : value;
    }
}
