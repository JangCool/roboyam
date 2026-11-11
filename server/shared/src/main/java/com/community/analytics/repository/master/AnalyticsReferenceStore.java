package com.community.analytics.repository.master;

import com.community.aimember.entity.QAiMemberProfileEntity;
import com.community.home.entity.QGadgetPageEntity;
import com.community.operator.entity.QOperatorMemberAccountEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 방문 통계가 메인 DB 에서 읽는 기준 목록(site-analytics-contract 「3단계」) — 공개 사이트 서버가 서버 메모리에 짧게 들고 쓴다(신호마다 DB 를 읽지 않게).
 *
 * <ul>
 *   <li><b>뺄 계정</b>: 운영자 회원 계정({@code operator_member_accounts.user_id} — 「로보얌운영팀」 포함) · AI 회원({@code ai_member_profiles.user_id}). 이 회원의 방문 신호·
 *       사이트 검색어는 방문 통계 전체에서 버리고, 게시판 활동(글·댓글·가입 — DB 배치 {@code R__batch_38})과 대시보드 「회원 현황」 오늘 가입에서도 뺀다
 *       ({@link #countedMember} — 두 표 모두 {@code user_id} 유일 인덱스).</li>
 *   <li><b>가젯 페이지 키</b>: 방문 주소 {@code /p/[key]} 가 실제 있는 페이지인지 볼 목록({@code gadget_pages.page_key}).</li>
 * </ul>
 */
@Repository
public class AnalyticsReferenceStore extends RoboyamRepositoryQDSL {

    private static final QOperatorMemberAccountEntity OPERATOR_MEMBER = QOperatorMemberAccountEntity.operatorMemberAccountEntity;
    private static final QAiMemberProfileEntity AI_MEMBER = QAiMemberProfileEntity.aiMemberProfileEntity;
    private static final QGadgetPageEntity PAGE = QGadgetPageEntity.gadgetPageEntity;

    public AnalyticsReferenceStore(JPAQueryFactory query) {
        super(query);
    }

    /** 통계에서 뺄 회원 번호(운영자 회원 계정 + AI 회원) — 둘 다 작은 표라 통째로 읽는다 */
    public Set<Long> excludedMemberIds() {
        Set<Long> out = new HashSet<>(query.select(OPERATOR_MEMBER.userId).from(OPERATOR_MEMBER).fetch());
        out.addAll(query.select(AI_MEMBER.userId).from(AI_MEMBER).fetch());
        return out;
    }

    /** 있는 가젯 페이지의 키 */
    public List<String> gadgetPageKeys() {
        return query.select(PAGE.pageKey).from(PAGE).fetch();
    }

    /**
     * 「통계에 세는 회원」 조건 — 운영자 회원 계정·AI 회원이 아니다(두 표의 {@code user_id} 유일 인덱스로 NOT EXISTS). DB 배치 {@code R__batch_38}(게시판 활동·가입)의 SQL 과
     * 같은 기준이다 — 한쪽을 고치면 같이 고친다.
     */
    public static BooleanExpression countedMember(NumberExpression<Long> userId) {
        return JPAExpressions.selectOne().from(OPERATOR_MEMBER).where(OPERATOR_MEMBER.userId.eq(userId)).notExists()
                .and(JPAExpressions.selectOne().from(AI_MEMBER).where(AI_MEMBER.userId.eq(userId)).notExists());
    }
}
