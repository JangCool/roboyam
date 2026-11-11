package com.community.ad.repository.master;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.bizapplication.entity.QBizApplicationItemEntity;
import com.community.bizapplication.entity.QBizCreativeVersionEntity;
import com.community.bizapplication.entity.QBizDealPostEntity;
import com.community.hotdeal.entity.QPostDealEntity;
import com.community.post.entity.PostEntity;
import com.community.post.entity.QPostEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공개 광고 노출 읽기·집계(ad-contract 「서버 구현 결정」 「공개 노출 쿼리 후보」, V118) — 장부는 신청 건(biz_applications) 그대로다.
 *
 * <ul>
 *   <li>지금 나가는 건 = 그 상품 자리 줄이 있고 {@code status = 'SCHEDULED' AND start_at <= now < end_at} + 그 건의 <b>승인된</b> 소재 버전 중 최신
 *       ({@code approved_at IS NOT NULL} 인 가장 높은 버전 — 상관 서브쿼리 {@code max(version)}). 승인된 버전이 없으면 빠진다. 긴급 내림({@code TAKEN_DOWN})·종료·결제 대기는
 *       상태·시각으로 빠진다.</li>
 *   <li>집계는 한 문장 — 하루 1번 판정 표({@code ad_view_marks})에 {@code ON CONFLICT DO NOTHING} 으로 넣고, 들어갔을 때만 일별 집계({@code ad_daily_stats})에 +1.</li>
 * </ul>
 *
 * <p>읽기는 QueryDSL 이다. Native SQL 로 남긴 것과 이유: {@link #count} — 판정 넣기와 집계 upsert 를 한 문장({@code WITH … INSERT … ON CONFLICT DO NOTHING RETURNING} +
 * {@code ON CONFLICT DO UPDATE})으로 해야 동시에 와도 하루 한 번만 센다. 판정 표는 한국 날짜 하루 파티션(V194)이라 그 날 조각이 없으면 만들고 한 번 다시 쓴다.
 */
@Repository
public class PublicAdStore extends RoboyamRepositoryQDSL {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private static final QBizApplicationEntity A = QBizApplicationEntity.bizApplicationEntity;
    private static final QBizApplicationItemEntity I = QBizApplicationItemEntity.bizApplicationItemEntity;
    private static final QBizCreativeVersionEntity C = QBizCreativeVersionEntity.bizCreativeVersionEntity;
    private static final QBizDealPostEntity B = QBizDealPostEntity.bizDealPostEntity;
    private static final QPostEntity P = QPostEntity.postEntity;
    private static final QPostDealEntity D = QPostDealEntity.postDealEntity;

    /** 지금 나가는 건 하나 — 소재는 최신 승인 버전의 내용(JSON) */
    public record Running(long applicationId, BizProduct product, int weight, String advertiserName, int version, Map<String, Object> content) {
    }

    /** PostgreSQL 「행에 맞는 조각이 없다」(check_violation) — 판정 표의 그 날 조각이 없을 때 */
    private static final String NO_PARTITION = "23514";

    private final JdbcTemplate jdbc;

    public PublicAdStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 그 상품들에서 지금 나가는 건(신청 건 차례) */
    public List<Running> running(Collection<BizProduct> products, Instant now) {
        if (products.isEmpty()) {
            return List.of();
        }
        QBizCreativeVersionEntity v = new QBizCreativeVersionEntity("v");
        StringExpression advertiser = A.companyName.coalesce(A.advertiserName);
        return query.select(A.id, I.product, A.weight, advertiser, C.version, C.content).from(A)
                .join(I).on(I.applicationId.eq(A.id), I.product.in(products))
                .join(C).on(C.applicationId.eq(A.id), C.approvedAt.isNotNull(),
                        C.version.eq(JPAExpressions.select(v.version.max()).from(v).where(v.applicationId.eq(A.id), v.approvedAt.isNotNull())))
                .where(A.status.eq(BizApplicationStatus.SCHEDULED), A.startAt.loe(now), A.endAt.gt(now)).orderBy(A.id.asc()).fetch().stream()
                .map(t -> new Running(t.get(A.id), t.get(I.product), n(t.get(A.weight)), t.get(advertiser), n(t.get(C.version)),
                        JSON.readValue(t.get(C.content), MAP)))
                .toList();
    }

    /** 핫딜 노출 자리 건의 글 — 진행 중(저장 ACTIVE · 마감 전, 의심 포함)이고 공개(지움·예약 삭제 지남·숨김 아님)인 사업자 글만 */
    public record DealPost(long applicationId, long postId, String title, long boardId, Long prefixId, String storeName, long dealPrice, Long normalPrice,
                           String imageUrl) {
    }

    /** 그 신청 건들의 글(V123 biz_deal_posts POSTED) — 진행 중·공개인 것만(biz-hotdeal-contract 규칙 6·7) */
    public Map<Long, DealPost> dealPosts(Collection<Long> applicationIds, Instant now) {
        Map<Long, DealPost> out = new HashMap<>();
        if (applicationIds.isEmpty()) {
            return out;
        }
        query.select(Projections.constructor(DealPost.class, B.applicationId, P.id, P.title, P.boardId, P.prefixId, D.storeName, D.dealPrice, D.normalPrice,
                        D.imageUrl)).from(B).join(P).on(P.id.eq(B.postId)).join(D).on(D.postId.eq(P.id))
                .where(B.applicationId.in(applicationIds), B.status.eq("POSTED"), D.source.eq("BUSINESS"), D.status.eq("ACTIVE"),
                        D.endsAt.isNull().or(D.endsAt.gt(now)), P.deletedAt.isNull(), P.status.eq(PostEntity.PUBLISHED),
                        P.deleteScheduledAt.isNull().or(P.deleteScheduledAt.gt(now)), P.hiddenAt.isNull().or(P.hiddenUntil.isNotNull().and(P.hiddenUntil.loe(now))))
                .fetch().forEach(d -> out.put(d.applicationId(), d));
        return out;
    }

    /**
     * 하루 1번 집계 — 같은 날·방문자·종류·건·버전·자리가 처음이면 일별 집계에 +1. Native SQL 인 이유는 클래스 설명.
     *
     * <p>판정 표는 V194 부터 한국 날짜 하루 파티션이다(조각은 DB 배치 {@code ad-view-mark-cleanup} 이 오늘~모레를 미리 만든다 — R__batch_33). 그 날 조각이 없으면 PostgreSQL 이
     * 23514 로 거절하므로 조각을 한 번 만든 뒤({@code ad_ensure_view_mark_partitions} — 잠금 아래 한 번만) 다시 넣는다. 트랜잭션 안에서 불리면 <b>JDBC 세이브포인트</b>로 그 문장만
     * 되돌린다(세이브포인트가 없으면 실패한 문장이 업무 트랜잭션 전체를 못 쓰게 한다). 안의 문장은 JDBC 뿐이다(세이브포인트 안에서 JPA 질의가 실패하면 롤백 전용이 된다 — repository.md).
     * {@code RETURNING} 은 상수 {@code 1} 이다 — 파티션 표는 시스템 칸({@code xmax}·{@code ctid})을 돌려주지 못한다(0A000).
     *
     * @param kind {@code I} 노출 · {@code C} 클릭
     * @return 셌으면 true(이미 센 것이면 false)
     */
    public boolean count(char kind, LocalDate day, String visitorHash, long applicationId, int version, String placement) {
        Integer n = jdbc.execute((ConnectionCallback<Integer>) con -> {
            Savepoint savepoint = con.getAutoCommit() ? null : con.setSavepoint();
            try {
                int counted = insertMark(con, kind, day, visitorHash, applicationId, version, placement);
                if (savepoint != null) {
                    con.releaseSavepoint(savepoint);
                }
                return counted;
            } catch (SQLException e) {
                if (savepoint != null) {
                    con.rollback(savepoint);
                }
                if (!NO_PARTITION.equals(e.getSQLState())) {
                    throw e;
                }
                try (PreparedStatement ps = con.prepareStatement("SELECT ad_ensure_view_mark_partitions(?)")) {
                    ps.setDate(1, Date.valueOf(day));
                    ps.execute();
                }
                return insertMark(con, kind, day, visitorHash, applicationId, version, placement); // 조각이 없어 거절됐다 — 한 번 만들고 다시
            }
        });
        return n != null && n > 0;
    }

    private static int insertMark(Connection con, char kind, LocalDate day, String visitorHash, long applicationId, int version, String placement)
            throws SQLException {
        String column = kind == 'C' ? "clicks" : "impressions";
        try (PreparedStatement ps = con.prepareStatement(
                "WITH mark AS (INSERT INTO ad_view_marks (day, visitor_hash, kind, application_id, version, placement) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT DO NOTHING RETURNING 1) "
                        + "INSERT INTO ad_daily_stats (application_id, version, day, placement, impressions, clicks, updated_at) "
                        + "SELECT ?, ?, ?, ?, ?, ?, now() FROM mark "
                        + "ON CONFLICT (application_id, version, day, placement) DO UPDATE SET " + column + " = ad_daily_stats." + column + " + 1, updated_at = now()")) {
            ps.setDate(1, Date.valueOf(day));
            ps.setString(2, visitorHash);
            ps.setString(3, String.valueOf(kind));
            ps.setLong(4, applicationId);
            ps.setInt(5, version);
            ps.setString(6, placement);
            ps.setLong(7, applicationId);
            ps.setInt(8, version);
            ps.setDate(9, Date.valueOf(day));
            ps.setString(10, placement);
            ps.setInt(11, kind == 'C' ? 0 : 1); // impressions
            ps.setInt(12, kind == 'C' ? 1 : 0); // clicks
            return ps.executeUpdate();
        }
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }
}
