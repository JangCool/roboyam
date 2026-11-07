package com.community.portone.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.portone.entity.QPortOneSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 포트원 설정 표({@code portone_settings}, shared V84) 읽기·쓰기 — {@code PortOneSettingsService} 와 local 시드({@code PortOneLocalSeeder})가 부른다.
 * 값은 암호문({@code v1:})으로만 오간다. 트랜잭션은 부르는 서비스의 것을 따른다.
 *
 * <p>Native SQL 로 남긴 것: 값 저장의 {@code INSERT … ON CONFLICT DO UPDATE}·local 시드의 {@code ON CONFLICT DO NOTHING}(칸마다 한 줄 — 처음에는 줄이 없고,
 * api·admin 이 같이 떠도 한 번만 들어가야 한다). {@code JdbcTemplate} 이라 부르는 쪽 트랜잭션이 있으면 참여하고, 없으면(기동 때 시드 {@code PortOneLocalSeeder}) 한 문장으로 바로 커밋한다.
 */
@Repository
public class PortOneSettingStore extends RoboyamRepositoryQDSL {

    /**
     * 칸 한 줄. {@code valueCipher} 가 null 이면 지운 칸(바꾼 시각·사람은 남는다).
     *
     * @param updatedByName 바꾼 운영자 이름(운영자가 지워졌거나 없으면 null)
     */
    public record Row(String key, String valueCipher, Instant updatedAt, Long updatedBy, String updatedByName) {

        public boolean configured() {
            return valueCipher != null;
        }

        @Override
        public String toString() {
            return "Row[key=" + key + ", configured=" + configured() + ", updatedAt=" + updatedAt + "]"; // 암호문도 싣지 않는다
        }
    }

    private static final QPortOneSettingEntity S = QPortOneSettingEntity.portOneSettingEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    private final JdbcTemplate jdbc;

    public PortOneSettingStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 모든 칸(지운 칸 포함) */
    public List<Row> findAll() {
        return query.select(Projections.constructor(Row.class, S.settingKey, S.valueCipher, S.updatedAt, S.updatedBy, OPERATOR.operatorName)).from(S)
                .leftJoin(OPERATOR).on(OPERATOR.id.eq(S.updatedBy)).orderBy(S.settingKey.asc()).fetch();
    }

    /** 칸 값을 저장한다(없으면 만든다) */
    public void upsert(String key, String valueCipher, long updatedBy, Instant now) {
        jdbc.update("INSERT INTO portone_settings (key, value_cipher, updated_at, updated_by) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (key) DO UPDATE SET value_cipher = EXCLUDED.value_cipher, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by",
                key, valueCipher, Timestamp.from(now), updatedBy);
    }

    /**
     * local 시드 — 그 칸의 줄이 없을 때만 넣는다(바꾼 사람 없음). api·admin 이 같이 떠도 한 번만 들어간다.
     *
     * @return 넣었으면 true
     */
    public boolean seedIfAbsent(String key, String valueCipher, Instant now) {
        return jdbc.update("INSERT INTO portone_settings (key, value_cipher, updated_at, updated_by) VALUES (?, ?, ?, NULL) ON CONFLICT (key) DO NOTHING",
                key, valueCipher, Timestamp.from(now)) == 1;
    }

    /**
     * 설정된 칸의 값을 지운다(줄은 남기고 값만 NULL — 「마지막 변경」이 남는다).
     *
     * @return 지웠으면 true(설정돼 있지 않았으면 false)
     */
    public boolean clear(String key, long updatedBy, Instant now) {
        return query.update(S).setNull(S.valueCipher).set(S.updatedAt, now).set(S.updatedBy, updatedBy).where(S.settingKey.eq(key), S.valueCipher.isNotNull())
                .execute() == 1;
    }
}
