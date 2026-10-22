package com.community.mail.repository.master;

import com.community.mail.dto.MailSettingHistoryRow;
import com.community.mail.entity.QMailSettingHistoryEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 메일 서버 설정 이력 조회 — 최신순(시각, 같으면 id 큰 쪽 먼저), 페이징은 DB 에서. 바꾼 운영자 이름은 왼쪽 조인으로 함께 읽는다 */
@Repository
public class MailSettingHistoryRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QMailSettingHistoryEntity HISTORY = QMailSettingHistoryEntity.mailSettingHistoryEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    public MailSettingHistoryRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    public List<MailSettingHistoryRow> page(int page, int size) {
        List<Tuple> rows = query.select(HISTORY, OPERATOR.operatorName).from(HISTORY).leftJoin(OPERATOR).on(OPERATOR.id.eq(HISTORY.operatorId))
                .orderBy(HISTORY.changedAt.desc(), HISTORY.id.desc())
                .offset((long) page * size).limit(size)
                .fetch();
        return rows.stream().map(row -> new MailSettingHistoryRow(row.get(HISTORY), row.get(OPERATOR.operatorName))).toList();
    }

    public long count() {
        Long total = query.select(HISTORY.count()).from(HISTORY).fetchOne();
        return total == null ? 0 : total;
    }
}
