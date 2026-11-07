package com.community.publicdata.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.publicdata.entity.QPublicDataSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공공데이터포털 설정 표({@code public_data_settings}, shared V125) 읽기·쓰기 — {@code PublicDataSettingsService} 만 부른다. 값은 암호문({@code v1:})으로만 오간다.
 * 트랜잭션은 부르는 서비스의 것을 따른다.
 *
 * <p>Native SQL(JDBC)로 남긴 것: 값 저장·연결 시험 기록의 {@code INSERT … ON CONFLICT (key) DO UPDATE}(칸마다 한 줄 — 처음 저장할 때 줄이 없다).
 * {@code JdbcTemplate} 이라 부르는 쪽 트랜잭션이 있으면 같은 연결로 참여하고, 없으면(연결 시험 {@code PublicDataSettingsService#test} 는 국세청을 부르므로 트랜잭션 밖) 한 문장으로 바로 커밋한다 — 예전과 같다.
 */
@Repository
public class PublicDataSettingStore extends RoboyamRepositoryQDSL {

    /**
     * 칸 한 줄. {@code valueCipher} 가 null 이면 지웠거나 저장한 적 없는 칸.
     *
     * @param updatedByName 마지막으로 저장·지운 운영자 이름(없거나 지워졌으면 null)
     * @param testedByName  마지막으로 연결 시험한 운영자 이름
     */
    public record Row(String key, String valueCipher, Instant updatedAt, String updatedByName, String testedResult, Instant testedAt, String testedByName) {

        public boolean configured() {
            return valueCipher != null;
        }

        @Override
        public String toString() {
            return "Row[key=" + key + ", configured=" + configured() + ", updatedAt=" + updatedAt + "]"; // 암호문도 싣지 않는다
        }
    }

    private static final QPublicDataSettingEntity S = QPublicDataSettingEntity.publicDataSettingEntity;

    private final JdbcTemplate jdbc;

    public PublicDataSettingStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<Row> find(String key) {
        QOperatorEntity u = new QOperatorEntity("updater");
        QOperatorEntity t = new QOperatorEntity("tester");
        return Optional.ofNullable(query.select(Projections.constructor(Row.class, S.settingKey, S.valueCipher, S.updatedAt, u.operatorName, S.testedResult, S.testedAt,
                        t.operatorName))
                .from(S).leftJoin(u).on(u.id.eq(S.updatedBy)).leftJoin(t).on(t.id.eq(S.testedBy)).where(S.settingKey.eq(key)).fetchOne());
    }

    /** 암호문만(조회할 때마다 읽는다 — 이름을 잇지 않는다) */
    public Optional<String> cipher(String key) {
        return Optional.ofNullable(query.select(S.valueCipher).from(S).where(S.settingKey.eq(key), S.valueCipher.isNotNull()).fetchOne());
    }

    /** 값을 저장한다(없으면 만든다) — 옛 키로 한 연결 시험 결과는 지운다 */
    public void upsert(String key, String valueCipher, long updatedBy, Instant now) {
        jdbc.update("INSERT INTO public_data_settings (key, value_cipher, updated_at, updated_by) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (key) DO UPDATE SET value_cipher = EXCLUDED.value_cipher, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by, "
                        + "tested_result = NULL, tested_at = NULL, tested_by = NULL",
                key, valueCipher, Timestamp.from(now), updatedBy);
    }

    /**
     * 설정된 값을 지운다(줄은 남기고 값만 NULL — 「마지막 변경」이 남는다). 연결 시험 결과도 지운다.
     *
     * @return 지웠으면 true(설정돼 있지 않았으면 false)
     */
    public boolean clear(String key, long updatedBy, Instant now) {
        return query.update(S).setNull(S.valueCipher).set(S.updatedAt, now).set(S.updatedBy, updatedBy).setNull(S.testedResult).setNull(S.testedAt).setNull(S.testedBy)
                .where(S.settingKey.eq(key), S.valueCipher.isNotNull()).execute() == 1;
    }

    /** 마지막 연결 시험을 남긴다(줄이 없으면 만든다 — 값 없이) */
    public void recordTest(String key, String result, long testedBy, Instant now) {
        jdbc.update("INSERT INTO public_data_settings (key, tested_result, tested_at, tested_by) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (key) DO UPDATE SET tested_result = EXCLUDED.tested_result, tested_at = EXCLUDED.tested_at, tested_by = EXCLUDED.tested_by",
                key, result, Timestamp.from(now), testedBy);
    }
}
