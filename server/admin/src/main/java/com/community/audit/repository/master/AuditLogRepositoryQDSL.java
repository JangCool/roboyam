package com.community.audit.repository.master;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRow;
import com.community.audit.dto.AuditSearchCriteria;
import com.community.audit.entity.QAuditLogEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.highlight.entity.QHighlightEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.QPostEntity;
import com.community.report.entity.QReportCaseEntity;
import com.community.role.entity.QRoleEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 감사 기록 읽기 — 조건·정렬·쪽 나누기는 DB 에서 한다(최근순: 발생 시각, 같으면 id). 행위자 이름은 운영자 표를 왼쪽 조인으로 함께 읽는다.
 * 쓰기는 {@code AuditLogService}(shared)가 한다 — 여기서는 고치거나 지우지 않는다. <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>색인(V21): 목록 {@code (occurred_at DESC, id DESC)}, 작업 {@code (action, occurred_at DESC, id DESC)}, 행위자 {@code (actor_id, occurred_at DESC)},
 * 실패·거부 {@code WHERE result <> 'SUCCESS'}, 내 로그인 이력 {@code (actor_id, occurred_at DESC, id DESC) WHERE action IN (로그인 성공·실패)}.
 */
@Repository
public class AuditLogRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QAuditLogEntity AUDIT = QAuditLogEntity.auditLogEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;
    private static final QRoleEntity ROLE = QRoleEntity.roleEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;

    public AuditLogRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 조건에 맞는 한 쪽 — 최근순 */
    public List<AuditRow> search(AuditSearchCriteria criteria) {
        return query.select(Projections.constructor(AuditRow.class, AUDIT.id, AUDIT.occurredAt, AUDIT.actorType, AUDIT.actorId, OPERATOR.operatorName, AUDIT.action,
                        AUDIT.result, AUDIT.targetType, AUDIT.targetId, AUDIT.ip, AUDIT.detail))
                .from(AUDIT).leftJoin(OPERATOR).on(OPERATOR.id.eq(AUDIT.actorId))
                .where(conditions(criteria))
                .orderBy(AUDIT.occurredAt.desc(), AUDIT.id.desc())
                .offset((long) criteria.page() * criteria.size()).limit(criteria.size())
                .fetch();
    }

    /** 조건에 맞는 줄 수 */
    public long count(AuditSearchCriteria criteria) {
        Long total = query.select(AUDIT.count()).from(AUDIT).where(conditions(criteria)).fetchOne();
        return total == null ? 0 : total;
    }

    /** 그 운영자 본인의 로그인 성공·실패 기록 — 최근순 {@code limit} 건 */
    public List<AuditRow> recentLogins(long operatorId, int limit) {
        List<Tuple> rows = query.select(AUDIT.id, AUDIT.occurredAt, AUDIT.actorType, AUDIT.actorId, AUDIT.action, AUDIT.result, AUDIT.targetType, AUDIT.targetId, AUDIT.ip,
                        AUDIT.detail)
                .from(AUDIT)
                .where(AUDIT.actorId.eq(operatorId), AUDIT.action.in(AuditActions.LOGIN_SUCCESS, AuditActions.LOGIN_FAILURE))
                .orderBy(AUDIT.occurredAt.desc(), AUDIT.id.desc())
                .limit(limit)
                .fetch();
        return rows.stream().map(row -> new AuditRow(row.get(AUDIT.id), row.get(AUDIT.occurredAt), row.get(AUDIT.actorType), row.get(AUDIT.actorId), null, row.get(AUDIT.action),
                row.get(AUDIT.result), row.get(AUDIT.targetType), row.get(AUDIT.targetId), row.get(AUDIT.ip), row.get(AUDIT.detail))).toList();
    }

    /** 운영자 이름(지금의 것) — 지워진 운영자는 결과에 없다 */
    public Map<Long, String> operatorNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (Tuple row : query.select(OPERATOR.id, OPERATOR.operatorName).from(OPERATOR).where(OPERATOR.id.in(ids)).fetch()) {
            names.put(row.get(OPERATOR.id), row.get(OPERATOR.operatorName));
        }
        return names;
    }

    /** 역할 이름(지금의 것) — 지워진 역할은 결과에 없다 */
    public Map<String, String> roleNames(Collection<String> roleIds) {
        Map<String, String> names = new HashMap<>();
        if (roleIds.isEmpty()) {
            return names;
        }
        for (Tuple row : query.select(ROLE.roleId, ROLE.roleName).from(ROLE).where(ROLE.roleId.in(roleIds)).fetch()) {
            names.put(row.get(ROLE.roleId), row.get(ROLE.roleName));
        }
        return names;
    }

    /** 회원 닉네임(지금의 것) — 없는 회원은 결과에 없다 */
    public Map<Long, String> memberNicknames(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        for (Tuple row : query.select(PROFILE.userId, PROFILE.nickname).from(PROFILE).where(PROFILE.userId.in(userIds)).fetch()) {
            names.put(row.get(PROFILE.userId), row.get(PROFILE.nickname));
        }
        return names;
    }

    /** 글 제목(지금의 것) — 없는 글·제목 없는 글은 결과에 없다 */
    public Map<Long, String> postTitles(Collection<Long> postIds) {
        Map<Long, String> titles = new HashMap<>();
        if (postIds.isEmpty()) {
            return titles;
        }
        QPostEntity post = QPostEntity.postEntity;
        for (Tuple row : query.select(post.id, post.title).from(post).where(post.id.in(postIds)).fetch()) {
            if (row.get(post.title) != null) {
                titles.put(row.get(post.id), row.get(post.title));
            }
        }
        return titles;
    }

    /** 댓글 본문(지금의 것 — 서식과 함께). 없는 댓글·파기한 본문은 결과에 없다 */
    public Map<Long, CommentText> commentTexts(Collection<Long> commentIds) {
        Map<Long, CommentText> texts = new HashMap<>();
        if (commentIds.isEmpty()) {
            return texts;
        }
        QCommentEntity comment = QCommentEntity.commentEntity;
        for (Tuple row : query.select(comment.id, comment.contentFormat, comment.content).from(comment).where(comment.id.in(commentIds)).fetch()) {
            if (row.get(comment.content) != null) {
                texts.put(row.get(comment.id), new CommentText(row.get(comment.contentFormat), row.get(comment.content)));
            }
        }
        return texts;
    }

    /** 하이라이트 → 대상 글 ID·하이라이트 제목. 없는 하이라이트는 결과에 없다 */
    public Map<Long, HighlightRef> highlights(Collection<Long> highlightIds) {
        Map<Long, HighlightRef> refs = new HashMap<>();
        if (highlightIds.isEmpty()) {
            return refs;
        }
        QHighlightEntity highlight = QHighlightEntity.highlightEntity;
        for (Tuple row : query.select(highlight.id, highlight.postId, highlight.title).from(highlight).where(highlight.id.in(highlightIds)).fetch()) {
            refs.put(row.get(highlight.id), new HighlightRef(row.get(highlight.postId), row.get(highlight.title)));
        }
        return refs;
    }

    /** 신고 사건 ID → 사건 번호. 없는 사건은 결과에 없다 */
    public Map<Long, Long> reportCaseNumbers(Collection<Long> caseIds) {
        Map<Long, Long> numbers = new HashMap<>();
        if (caseIds.isEmpty()) {
            return numbers;
        }
        QReportCaseEntity reportCase = QReportCaseEntity.reportCaseEntity;
        for (Tuple row : query.select(reportCase.id, reportCase.caseNo).from(reportCase).where(reportCase.id.in(caseIds)).fetch()) {
            numbers.put(row.get(reportCase.id), row.get(reportCase.caseNo));
        }
        return numbers;
    }

    /** 댓글 본문과 서식 */
    public record CommentText(ContentFormat format, String content) {
    }

    /** 하이라이트의 대상 글과 하이라이트 제목(지정 때 정한 표시 제목 — 없으면 null) */
    public record HighlightRef(Long postId, String title) {
    }

    private static BooleanExpression[] conditions(AuditSearchCriteria c) {
        return new BooleanExpression[] {
                c.from() == null ? null : AUDIT.occurredAt.goe(c.from()),
                c.to() == null ? null : AUDIT.occurredAt.loe(c.to()),
                actor(c),
                c.action() == null ? null : AUDIT.action.eq(c.action()),
                c.targetType() == null ? null : AUDIT.targetType.eq(c.targetType()),
                target(c),
                c.result() == null ? null : AUDIT.result.eq(c.result().name())
        };
    }

    /** 행위자: ID 가 같거나 이름에 검색어가 들어 있다. 이메일은 맞추지 않는다(조회 응답이 이메일을 내지 않으므로 검색으로 알아내게 하지 않는다) */
    private static BooleanExpression actor(AuditSearchCriteria c) {
        BooleanExpression byId = c.actorId() == null ? null : AUDIT.actorId.eq(c.actorId());
        BooleanExpression byName = c.actorName() == null ? null
                : AUDIT.actorId.in(JPAExpressions.select(OPERATOR.id).from(OPERATOR).where(OPERATOR.operatorName.containsIgnoreCase(c.actorName())));
        if (byId == null) {
            return byName;
        }
        return byName == null ? byId : byId.or(byName);
    }

    /**
     * 대상: 대상 ID 에 검색어가 들어 있거나, 대상 종류별로 지금의 이름에 들어 있다 — 운영자(이름) · 역할(이름) · 회원(닉네임) · 배치(서비스가 넘긴 키).
     * 기록에는 이름을 남기지 않아 지금의 이름으로 찾는다.
     */
    private static BooleanExpression target(AuditSearchCriteria c) {
        String term = c.target();
        if (term == null) {
            return null;
        }
        BooleanExpression match = AUDIT.targetId.containsIgnoreCase(term)
                .or(AUDIT.targetType.eq("OPERATOR").and(AUDIT.targetId.in(
                        JPAExpressions.select(OPERATOR.id.stringValue()).from(OPERATOR).where(OPERATOR.operatorName.containsIgnoreCase(term)))))
                .or(AUDIT.targetType.eq("ROLE").and(AUDIT.targetId.in(
                        JPAExpressions.select(ROLE.roleId).from(ROLE).where(ROLE.roleName.containsIgnoreCase(term)))))
                .or(AUDIT.targetType.eq("MEMBER").and(AUDIT.targetId.in(
                        JPAExpressions.select(PROFILE.userId.stringValue()).from(PROFILE).where(PROFILE.nickname.containsIgnoreCase(term)))));
        if (!c.batchKeys().isEmpty()) {
            match = match.or(AUDIT.targetType.eq("BATCH").and(AUDIT.targetId.in(c.batchKeys())));
        }
        return match;
    }
}
