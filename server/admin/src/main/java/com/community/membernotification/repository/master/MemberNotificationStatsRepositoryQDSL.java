package com.community.membernotification.repository.master;

import com.community.membernotification.entity.QMemberNotificationEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 회원 이벤트 관리의 발송 수 — 회원 알림 표({@code member_notifications})를 종류별로 센다. <b>트랜잭션은 부르는 서비스가 연다.</b> */
@Repository
public class MemberNotificationStatsRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QMemberNotificationEntity NOTIFICATION = QMemberNotificationEntity.memberNotificationEntity;

    public MemberNotificationStatsRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** {@code since} 뒤에 만든 알림 행 수 — 종류 → 수. 묶인 댓글 알림은 한 줄을 한 건으로 센다(회원 알림함의 줄 수) */
    public Map<String, Long> countByTypeSince(Instant since) {
        Map<String, Long> counts = new HashMap<>();
        for (Tuple row : query.select(NOTIFICATION.type, NOTIFICATION.count()).from(NOTIFICATION).where(NOTIFICATION.createdAt.goe(since))
                .groupBy(NOTIFICATION.type).fetch()) {
            counts.put(row.get(NOTIFICATION.type), row.get(NOTIFICATION.count()));
        }
        return counts;
    }
}
