package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.bizapplication.entity.QBizDealPostEntity;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.entity.BusinessStatus;
import com.community.business.entity.QBusinessProfileEntity;
import com.community.business.entity.QBusinessServiceEntity;
import com.community.hotdeal.entity.QPostDealEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.post.entity.QPostEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사업자 핫딜 게시 결과(V123 {@code biz_deal_posts}) — 신청 건 하나에 한 줄(PK). 넣기는 {@code ON CONFLICT DO NOTHING} 이라 다시 돌아도 두 번 생기지 않고, 이긴 쪽만 글을 만든다
 * (biz-hotdeal-contract 규칙 1).
 *
 * <p>QueryDSL 이 기본이다. Native SQL 로 남긴 것과 이유: 게시 결과 줄 넣기 둘({@link #claimPosted} · {@link #claimFailed} — {@code ON CONFLICT DO NOTHING}: 먼저 넣은 쪽이 이기고
 * 진 쪽은 오류 없이 0 줄을 받는다. JPA 넣기로는 진 쪽이 유일 위반으로 업무 트랜잭션 전체가 롤백 전용이 된다).
 */
@Repository
public class BizDealPostStore extends RoboyamRepositoryQDSL {

    public static final String POSTED = "POSTED";
    public static final String FAILED = "FAILED";
    /** 서비스 줄 {@code HOTDEAL} 정지로 실패 — 줄이 다시 승인되면 다시 고른다(결정 10) */
    public static final String SERVICE_SUSPENDED = "SERVICE_SUSPENDED";

    private static final QBizDealPostEntity B = QBizDealPostEntity.bizDealPostEntity;
    private static final QBizApplicationEntity A = QBizApplicationEntity.bizApplicationEntity;
    private static final QBusinessProfileEntity BP = QBusinessProfileEntity.businessProfileEntity;
    private static final QBusinessServiceEntity BS = QBusinessServiceEntity.businessServiceEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QPostDealEntity DEAL = QPostDealEntity.postDealEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;

    /** 게시 결과 한 줄 — {@code hiddenAt} 은 이 흐름이 숨긴 시각(그 글의 숨김 시각과 같을 때만 되돌리기가 푼다) */
    public record PostRow(long applicationId, Long postId, String status, String failReason, Instant hiddenAt, Instant createdAt) {

        public boolean posted() {
            return POSTED.equals(status);
        }
    }

    private static final Expression<PostRow> ROW = Projections.constructor(PostRow.class, B.applicationId, B.postId, B.status, B.failReason, B.hiddenAt, B.createdAt);

    private final JdbcTemplate jdbc;

    public BizDealPostStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<PostRow> find(long applicationId) {
        return Optional.ofNullable(query.select(ROW).from(B).where(B.applicationId.eq(applicationId)).fetchFirst());
    }

    public Map<Long, PostRow> findAll(Collection<Long> applicationIds) {
        Map<Long, PostRow> out = new HashMap<>();
        if (!applicationIds.isEmpty()) {
            query.select(ROW).from(B).where(B.applicationId.in(applicationIds)).fetch().forEach(r -> out.put(r.applicationId(), r));
        }
        return out;
    }

    /** 게시 성공 줄을 넣는다 — 이미 줄이 있으면(다른 실행이 먼저) false. Native SQL 인 이유는 클래스 설명 */
    public boolean claimPosted(long applicationId, long postId, Instant now) {
        return jdbc.update("INSERT INTO biz_deal_posts (application_id, post_id, status, created_at, updated_at) VALUES (?, ?, 'POSTED', ?, ?) ON CONFLICT DO NOTHING",
                applicationId, postId, Timestamp.from(now), Timestamp.from(now)) == 1;
    }

    /** 게시 실패 줄을 넣는다 — 이미 줄이 있으면 false. Native SQL 인 이유는 클래스 설명 */
    public boolean claimFailed(long applicationId, String reason, Instant now) {
        return jdbc.update("INSERT INTO biz_deal_posts (application_id, status, fail_reason, created_at, updated_at) VALUES (?, 'FAILED', ?, ?, ?) ON CONFLICT DO NOTHING",
                applicationId, reason, Timestamp.from(now), Timestamp.from(now)) == 1;
    }

    public void setHidden(long applicationId, Instant hiddenAt, Instant now) {
        query.update(B).set(B.hiddenAt, hiddenAt).set(B.updatedAt, now).where(B.applicationId.eq(applicationId), B.status.eq(POSTED)).execute();
    }

    /**
     * 게시할 차례 — 예약 · 핫딜 글 · 시작 ≤ 지금 &lt; 끝 · 게시 결과 없음, 또는 서비스 정지({@code SERVICE_SUSPENDED})로 실패했는데 지금은 신원·서비스 줄 {@code HOTDEAL} 이 둘 다 승인
     * (결정 10 — 정지를 풀면 다시 올린다. 아직 정지면 고르지 않아 알림이 되풀이되지 않는다). ID 키셋 — 처리하면 결과 줄이 생기거나 바뀌어 다시 고르지 않는다.
     */
    public List<Long> due(Instant now, long afterId, int limit) {
        QBizDealPostEntity b = new QBizDealPostEntity("b");
        return query.select(A.id).from(A)
                .where(A.kind.eq(BizApplicationKind.HOTDEAL_POST), A.status.eq(BizApplicationStatus.SCHEDULED), A.startAt.loe(now), A.endAt.gt(now), A.id.gt(afterId),
                        JPAExpressions.selectOne().from(b).where(b.applicationId.eq(A.id)).notExists()
                                .or(JPAExpressions.selectOne().from(b).join(BP).on(BP.id.eq(A.businessProfileId), BP.status.eq(BusinessStatus.APPROVED))
                                        .join(BS).on(BS.profileId.eq(BP.id), BS.service.eq(BusinessServiceKind.HOTDEAL), BS.status.eq(BusinessServiceStatus.APPROVED))
                                        .where(b.applicationId.eq(A.id), b.status.eq(FAILED), b.failReason.eq(SERVICE_SUSPENDED)).exists()))
                .orderBy(A.id.asc()).limit(limit).fetch();
    }

    /** 서비스 정지로 실패한 줄을 지운다(다시 판정하기 전 — 신청 건 행을 잠근 뒤) — 지웠으면 true */
    public boolean clearServiceSuspended(long applicationId) {
        return query.delete(B).where(B.applicationId.eq(applicationId), B.status.eq(FAILED), B.failReason.eq(SERVICE_SUSPENDED)).execute() == 1;
    }

    /**
     * 판매자 숨김(V127 — 광고주 일시 정지·중단) 한 줄 — 까닭(ADVERTISER_PAUSE·ADVERTISER_STOP) · 숨긴 시각 · 처리한 사람 표시 이름(운영자 이름 또는 사업자 닉네임 — 없으면 상호) ·
     * 운영자 대신 처리였나
     */
    public record SellerHidden(String cause, Instant at, String by, boolean byOperator) {
    }

    /** 글 ID → 판매자 숨김(지금 판매자 숨김 까닭으로 숨겨진 사업자 글만 — 관리자 핫딜 목록·상세 {@code sellerHidden}) */
    public Map<Long, SellerHidden> sellerHidden(Collection<Long> postIds) {
        Map<Long, SellerHidden> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        query.select(POST.id, POST.hiddenReason, POST.hiddenAt, A.takedownActor, O.operatorName, PR.nickname, A.companyName, A.advertiserName).from(POST)
                .join(DEAL).on(DEAL.postId.eq(POST.id)).join(A).on(A.id.eq(DEAL.bizApplicationId))
                .leftJoin(O).on(A.takedownActor.eq(BizActorType.OPERATOR), O.id.eq(A.takenDownBy)).leftJoin(PR).on(PR.userId.eq(A.userId))
                .where(POST.id.in(postIds), POST.hiddenAt.isNotNull(), POST.hiddenUntil.isNull(), POST.hiddenReason.in("SELLER_PAUSED", "SELLER_STOPPED"))
                .fetch().forEach(t -> {
                    boolean operator = t.get(A.takedownActor) == BizActorType.OPERATOR;
                    String member = t.get(PR.nickname) != null ? t.get(PR.nickname) : t.get(A.companyName) != null ? t.get(A.companyName) : t.get(A.advertiserName);
                    out.put(t.get(POST.id), new SellerHidden("SELLER_PAUSED".equals(t.get(POST.hiddenReason)) ? "ADVERTISER_PAUSE" : "ADVERTISER_STOP",
                            t.get(POST.hiddenAt), operator ? t.get(O.operatorName) : member, operator));
                });
        return out;
    }

    /** 사업자 신원이 승인 상태인가(정지 · 승인 취소가 아님) — 게시 직전 재판정 */
    public boolean businessApproved(AppRow app) {
        if (app.businessProfileId() == null) {
            return false;
        }
        return query.select(BP.status).from(BP).where(BP.id.eq(app.businessProfileId())).fetchFirst() == BusinessStatus.APPROVED;
    }

    /** 서비스 줄 {@code HOTDEAL} 이 승인 상태인가(결정 10 — 신원은 승인이어도 이 줄만 정지일 수 있다) */
    public boolean hotdealServiceApproved(AppRow app) {
        if (app.businessProfileId() == null) {
            return false;
        }
        return query.select(BS.status).from(BS).where(BS.profileId.eq(app.businessProfileId()), BS.service.eq(BusinessServiceKind.HOTDEAL)).fetchFirst()
                == BusinessServiceStatus.APPROVED;
    }
}
