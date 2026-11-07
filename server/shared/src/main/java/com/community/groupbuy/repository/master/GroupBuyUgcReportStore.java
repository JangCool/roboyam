package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyUgcReportEntity;
import com.community.groupbuy.entity.QGroupBuyQuestionEntity;
import com.community.groupbuy.entity.QGroupBuyReviewEntity;
import com.community.groupbuy.entity.QGroupBuyUgcReportEntity;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 공구 질문·리뷰 신고({@code group_buy_ugc_reports}, 2b-6) — {@code GroupBuyUgcReportService} 안에 있던 SQL 을 옮겼다. 모두 QueryDSL 이다({@link GroupBuyStoreSupport}).
 */
@Repository
public class GroupBuyUgcReportStore extends GroupBuyStoreSupport {

    private static final QGroupBuyUgcReportEntity X = QGroupBuyUgcReportEntity.groupBuyUgcReportEntity;
    private static final QGroupBuyQuestionEntity Q = QGroupBuyQuestionEntity.groupBuyQuestionEntity;
    private static final QGroupBuyReviewEntity R = QGroupBuyReviewEntity.groupBuyReviewEntity;

    /** 열린 신고의 사유별 수 */
    public record ReasonCount(String reason, long count) {
    }

    public GroupBuyUgcReportStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /** 신고할 수 있는 질문(지우지·숨기지 않은 · 남의 비밀글이 아닌)의 작성자 */
    public Optional<Long> reportableQuestionAuthor(long questionId, long reporterId) {
        return Optional.ofNullable(query.select(Q.userId).from(Q)
                .where(Q.id.eq(questionId), Q.deletedAt.isNull(), Q.hiddenAt.isNull(), Q.secret.isFalse().or(Q.userId.eq(reporterId))).fetchFirst());
    }

    /** 신고할 수 있는 리뷰(지우지·숨기지 않은)의 작성자 */
    public Optional<Long> reportableReviewAuthor(long reviewId) {
        return Optional.ofNullable(query.select(R.userId).from(R).where(R.id.eq(reviewId), R.deletedAt.isNull(), R.hiddenAt.isNull()).fetchFirst());
    }

    /** 신고를 넣는다 — 같은 사람이 같은 대상을 이미 신고했으면 {@link org.springframework.dao.DuplicateKeyException}({@code ux_gbur_once}) */
    public void insertReport(long id, String type, long targetId, long reporterId, String reason, String detail, Instant now) {
        insert(GroupBuyUgcReportEntity.open(id, type, targetId, reporterId, reason, detail, now));
    }

    /** 그 대상의 열린 신고를 처리됨으로 — 바꾼 수 */
    public int resolve(String type, long targetId, long operatorId, Instant now) {
        return (int) query.update(X).set(X.status, "RESOLVED").set(X.resolvedAt, now).set(X.resolvedBy, operatorId)
                .where(X.targetType.eq(type), X.targetId.eq(targetId), X.status.eq("OPEN")).execute();
    }

    /** 열린 신고 — 사유별 수(많은 것 먼저, 같으면 사유 이름 차례) */
    public List<ReasonCount> openReasons(String type, long targetId) {
        NumberExpression<Long> count = X.count();
        return query.select(X.reason, count).from(X).where(X.targetType.eq(type), X.targetId.eq(targetId), X.status.eq("OPEN")).groupBy(X.reason)
                .orderBy(count.desc(), X.reason.asc()).fetch().stream().map(t -> new ReasonCount(t.get(0, String.class), n(t.get(1, Long.class)))).toList();
    }
}
