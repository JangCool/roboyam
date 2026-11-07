package com.community.ad.repository.master;

import com.community.ad.entity.QAdAdvertiserEntity;
import com.community.ad.repository.master.AdAdvertiserStore.AdvertiserRow;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.bizapplication.entity.QBizDepositEntity;
import com.community.bizapplication.entity.QBizRefundEntity;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.entity.BusinessStatus;
import com.community.business.entity.QBusinessProfileEntity;
import com.community.business.entity.QBusinessServiceEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 광고주 관리 읽기(admin-web {@code /ad-advertisers}, docs/features/ad-advertiser-contract.md) — 광고주 줄 · 목록에 나오는 사업자(규칙 2) · 신청 건 가벼운 줄(광고주 키 · 건 수 ·
 * 돈 · 최근 광고일 재료). 쓰기는 shared {@code AdAdvertiserStore}. QueryDSL 만 쓴다.
 */
@Repository
public class AdAdvertiserAdminStore extends RoboyamRepositoryQDSL {

    private static final QBizApplicationEntity A = QBizApplicationEntity.bizApplicationEntity;
    private static final QAdAdvertiserEntity ADV = new QAdAdvertiserEntity("adv");
    private static final QAdAdvertiserEntity AD = QAdAdvertiserEntity.adAdvertiserEntity;
    private static final QBizDepositEntity D = QBizDepositEntity.bizDepositEntity;
    private static final QBizRefundEntity R = QBizRefundEntity.bizRefundEntity;
    private static final QBusinessProfileEntity P = QBusinessProfileEntity.businessProfileEntity;
    private static final QBusinessServiceEntity S = QBusinessServiceEntity.businessServiceEntity;

    /**
     * 신청 건 가벼운 줄 — 광고주 키 재료(건의 사업자 신원 · 건 광고주 줄 · 그 광고주 줄의 사업자 신원)와 결제·환불.
     *
     * @param paid     결제액 — 신청 건 상세 {@code refundSummary.paid} 와 같은 원천({@code BizRefundSummary.paidOf}: 온라인 = 결제 완료면 승인 합계, 수동 = 입금 확인 금액), 결제 전 null
     * @param refunded 승인된 환불 합({@code BizRefundSummary.of} 의 {@code refunded} — 결제 전이면 0)
     */
    public record AppLite(long id, Long businessProfileId, Long advertiserId, Long advertiserProfileId, String status, Instant startAt, Instant endAt,
                          String paymentMethod, Long paid, long refunded) {

        /** 광고주 키(규칙 1) — 건의 사업자 신원 → 건 광고주 줄의 사업자 신원 → 직접 등록 광고주 · 없으면 null */
        public String key() {
            Long profile = businessProfileId != null ? businessProfileId : advertiserProfileId;
            if (profile != null) {
                return "biz-" + profile;
            }
            return advertiserId == null ? null : "adv-" + advertiserId;
        }
    }

    /** 목록에 나오는 사업자 신원(규칙 2) — 상호·등록번호 끝 4자리·신원 상태·광고 서비스 줄 상태(없으면 OFF)·연락처 암호문(가린 값을 만들 때만 푼다) */
    public record BusinessRow(long id, String companyName, String bizNoLast4, String status, String adService, Instant createdAt, String ownerNameEnc, String phoneEnc,
                              String emailEnc) {
    }

    /** 승인된 환불의 합(결제 전 건은 서비스가 0 으로 본다) */
    private static final Expression<Long> REFUNDED = JPAExpressions.select(Expressions.numberTemplate(Long.class, "sum({0})", R.approvedAmount)).from(R)
            .where(R.applicationId.eq(A.id), R.status.eq(BizRefundStatus.APPROVED), R.approvedAmount.isNotNull());

    private static final Expression<AdvertiserRow> ADVERTISER = Projections.constructor(AdvertiserRow.class, AD.id, AD.name, AD.businessProfileId, P.companyName,
            P.bizNoLast4, AD.contactNameMasked, AD.contactPhoneMasked, AD.emailMasked, AD.memo, AD.createdAt, AD.archivedAt, AD.contactPurgedAt);

    public AdAdvertiserAdminStore(JPAQueryFactory query) {
        super(query);
    }

    /** 광고주 키가 있는 모든 신청 건(목록) */
    public List<AppLite> apps() {
        return apps(A.businessProfileId.isNotNull().or(A.advertiserId.isNotNull()));
    }

    /** 사업자 키의 모든 건 — 건의 신원이 그 사업자이거나 건 광고주 줄이 그 사업자에 붙었다 */
    public List<AppLite> businessApps(long profileId) {
        return apps(A.businessProfileId.eq(profileId).or(A.businessProfileId.isNull().and(ADV.businessProfileId.eq(profileId))));
    }

    /** 직접 등록 광고주 키의 모든 건 */
    public List<AppLite> advertiserApps(long advertiserId) {
        return apps(A.advertiserId.eq(advertiserId).and(A.businessProfileId.isNull()).and(ADV.businessProfileId.isNull()));
    }

    /** 모든 광고주 줄(직접 등록 + 사업자에 붙은 것) */
    public List<AdvertiserRow> advertisers() {
        return query.select(ADVERTISER).from(AD).leftJoin(P).on(P.id.eq(AD.businessProfileId)).orderBy(AD.id.asc()).fetch();
    }

    /** 목록에 나오는 사업자 전부 */
    public List<BusinessRow> businesses() {
        return businessSelect().where(listed()).orderBy(P.id.asc()).fetch().stream().map(AdAdvertiserAdminStore::business).toList();
    }

    /** 목록에 나오는 사업자 한 곳 — 아니면 비어 있다(상세 404) */
    public Optional<BusinessRow> business(long profileId) {
        return Optional.ofNullable(businessSelect().where(P.id.eq(profileId), listed()).fetchFirst()).map(AdAdvertiserAdminStore::business);
    }

    /** 결제 · 환불 — {@code BizRefundSummary.paidOf} 와 같은 판정(온라인 = 결제 완료면 승인 합계, 수동 = 입금 확인 금액). 결제 전 건의 환불은 세지 않는다 */
    private List<AppLite> apps(BooleanExpression where) {
        return query.select(A.id, A.businessProfileId, A.advertiserId, ADV.businessProfileId, A.status, A.startAt, A.endAt, A.paymentMethod, A.paidAt,
                        A.approvedTotal, D.amount, REFUNDED).from(A)
                .leftJoin(ADV).on(ADV.id.eq(A.advertiserId)).leftJoin(D).on(D.applicationId.eq(A.id)).where(where).fetch().stream()
                .map(t -> {
                    BizPaymentMethod method = t.get(A.paymentMethod);
                    Long paid = method == BizPaymentMethod.ONLINE && t.get(A.paidAt) != null ? t.get(A.approvedTotal) : method == BizPaymentMethod.MANUAL ? t.get(D.amount)
                            : null;
                    Long refunded = t.get(REFUNDED);
                    BizApplicationStatus status = t.get(A.status);
                    return new AppLite(t.get(A.id), t.get(A.businessProfileId), t.get(A.advertiserId), t.get(ADV.businessProfileId), status == null ? null : status.name(),
                            t.get(A.startAt), t.get(A.endAt), method == null ? null : method.name(), paid, paid == null || refunded == null ? 0 : refunded);
                }).toList();
    }

    /** 목록에 나오는 사업자 — 광고주 줄이 붙었거나 · 신청 건(종류 무관)이 있거나 · AD 서비스 줄이 있는 신원 */
    private static BooleanExpression listed() {
        QAdAdvertiserEntity x = new QAdAdvertiserEntity("x");
        QBizApplicationEntity b = new QBizApplicationEntity("b");
        return JPAExpressions.selectOne().from(x).where(x.businessProfileId.eq(P.id)).exists()
                .or(JPAExpressions.selectOne().from(b).where(b.businessProfileId.eq(P.id)).exists()).or(S.id.isNotNull());
    }

    private JPAQuery<Tuple> businessSelect() {
        return query.select(P.id, P.companyName, P.bizNoLast4, P.status, S.status, P.createdAt, P.erasedAt, P.ownerNameEnc, P.phoneEnc, P.emailEnc).from(P)
                .leftJoin(S).on(S.profileId.eq(P.id), S.service.eq(BusinessServiceKind.AD));
    }

    /** 연락처 암호문은 파기되지 않은 신원만(파기된 신원은 null) */
    private static BusinessRow business(Tuple t) {
        boolean erased = t.get(P.erasedAt) != null;
        BusinessStatus status = t.get(P.status);
        BusinessServiceStatus ad = t.get(S.status);
        return new BusinessRow(t.get(P.id), t.get(P.companyName), t.get(P.bizNoLast4), status == null ? null : status.name(), ad == null ? "OFF" : ad.name(),
                t.get(P.createdAt), erased ? null : t.get(P.ownerNameEnc), erased ? null : t.get(P.phoneEnc), erased ? null : t.get(P.emailEnc));
    }
}
