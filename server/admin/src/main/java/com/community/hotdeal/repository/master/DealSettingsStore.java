package com.community.hotdeal.repository.master;

import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.entity.DealSettingEntity;
import com.community.hotdeal.entity.QDealSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/**
 * 핫딜 설정 행({@code deal_settings}, id = 1) 읽기·쓰기 — 관리자 핫딜 설정 화면. 공개·관리자 서버의 짧은 사본은 shared {@code DealSettingsReader}(저장 뒤 {@code invalidate}).
 * 저장은 행을 잠가 읽고({@link #lock}) 엔티티를 고친다(변경 감지). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>Native SQL 로 남긴 것: 행이 지워졌을 때 기본값으로 다시 만드는 {@code INSERT … ON CONFLICT (id) DO NOTHING}(두 요청이 동시에 읽어도 한 줄).
 */
@Repository
public class DealSettingsStore extends RoboyamRepositoryQDSL {

    private static final QDealSettingEntity S = QDealSettingEntity.dealSettingEntity;

    private final EntityManager entityManager;

    public DealSettingsStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /** 저장된 값 + 마지막 저장(행이 V122 에서 만들어진 뒤 아무도 저장하지 않았으면 {@code updatedBy} null) */
    public record Stored(DealSettings settings, Instant updatedAt, Long updatedBy) {
    }

    public Stored read() {
        return read(false);
    }

    /** 행을 잠가 읽는다(동시 저장이 줄을 선다 — {@code FOR NO KEY UPDATE}) */
    public Stored lock() {
        return read(true);
    }

    /** 값을 바꾼다 — {@link #lock} 으로 읽은 행(변경 감지 — 다음 질의나 커밋 때 나간다) */
    public void update(DealSettings s, long operatorId, Instant now) {
        row(false).apply(s, operatorId, now);
    }

    private Stored read(boolean forUpdate) {
        DealSettingEntity row = row(forUpdate);
        return new Stored(row.settings(), row.getUpdatedAt(), row.getUpdatedBy());
    }

    private DealSettingEntity row(boolean forUpdate) {
        var select = query.selectFrom(S).where(S.id.eq(DealSettingEntity.ROW_ID));
        DealSettingEntity row = (forUpdate ? select.setLockMode(LockModeType.PESSIMISTIC_WRITE) : select).fetchOne();
        if (row == null) {
            // V122 가 행을 넣는다 — 지워졌으면 기본값으로 다시 만든다
            entityManager.createNativeQuery("INSERT INTO deal_settings (id, updated_at) VALUES (1, now()) ON CONFLICT (id) DO NOTHING").executeUpdate();
            return row(forUpdate);
        }
        return row;
    }
}
