package com.community.installation.repository.master;

import com.community.installation.entity.InstallationEntity;
import com.community.installation.entity.InstallationStatus;
import com.community.installation.entity.QInstallationEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 설치 상태 읽기·쓰기. <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>상태·토큰 해시는 <b>엔티티가 아니라 열 하나</b>로 읽는다 — 영속성 컨텍스트에 남은 옛 엔티티가 아니라 DB 의 지금 값을 본다.
 * 쓰기는 모두 {@code status = PENDING} 조건이 붙은 한 문장이라, 설치가 끝난 뒤에는 토큰을 새로 만들거나 완료를 다시 표시할 수 없다.
 * 행은 마이그레이션(V4)이 만들어 두므로 여기서 만들지 않는다.
 */
@Repository
public class InstallationRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QInstallationEntity INSTALLATION = QInstallationEntity.installationEntity;

    public InstallationRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 지금 상태. 행이 없으면(지워졌으면) 비어 있다 */
    public Optional<InstallationStatus> findStatus() {
        return Optional.ofNullable(query.select(INSTALLATION.status).from(INSTALLATION)
                .where(INSTALLATION.id.eq(InstallationEntity.ROW_ID)).fetchOne());
    }

    /** 저장된 설치 토큰 해시. 없으면(아직 만들지 않았거나 설치가 끝났으면) 비어 있다 */
    public Optional<String> findTokenHash() {
        return Optional.ofNullable(query.select(INSTALLATION.tokenHash).from(INSTALLATION)
                .where(INSTALLATION.id.eq(InstallationEntity.ROW_ID)).fetchOne());
    }

    /**
     * 행을 <b>쓰기 잠금</b>으로 읽는다({@code FOR NO KEY UPDATE} — Hibernate PostgreSQL 방언의 {@code PESSIMISTIC_WRITE}).
     * 설치 실행이 이것으로 줄을 세워 <b>동시에 두 번 호출돼도 한 번만 설치</b>된다 — 두 번째는 첫 번째가 끝날 때까지 기다렸다가 완료된 상태를 본다.
     */
    public Optional<InstallationEntity> lockRow() {
        return Optional.ofNullable(query.selectFrom(INSTALLATION).where(INSTALLATION.id.eq(InstallationEntity.ROW_ID))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne());
    }

    /**
     * 설치 토큰 해시를 바꾼다(설치 전에만). 서버를 다시 띄우면 새 토큰이 만들어지고 이전 것은 무효가 된다.
     *
     * @param tokenHash SHA-256 16진 64자. 토큰 원문을 넘기지 않는다
     * @return 바뀐 행 수 — 설치가 이미 끝났거나 행이 없으면 0
     */
    public long updateTokenHash(String tokenHash) {
        return query.update(INSTALLATION).set(INSTALLATION.tokenHash, tokenHash)
                .where(INSTALLATION.id.eq(InstallationEntity.ROW_ID), INSTALLATION.status.eq(InstallationStatus.PENDING))
                .execute();
    }

    /**
     * 고른 게시판 구성의 적용(또는 건너뜀)을 표시한다 — 표시가 없을 때만 먹는다(한 번만 적용하게).
     *
     * @return 바뀐 행 수 — 이미 표시돼 있었으면 0
     */
    public long markBoardPresetApplied(Instant appliedAt) {
        return query.update(INSTALLATION).set(INSTALLATION.boardPresetAppliedAt, appliedAt)
                .where(INSTALLATION.id.eq(InstallationEntity.ROW_ID), INSTALLATION.boardPresetAppliedAt.isNull())
                .execute();
    }

    /**
     * 설치 완료를 표시한다(토큰 해시를 지운다). 설치 전일 때만 먹는 조건부 UPDATE 다.
     *
     * @param boardPreset 설치 때 고른 기본 게시판 구성의 아이디
     * @return 바뀐 행 수 — 이미 완료였으면 0
     */
    public long markCompleted(long rootOperatorId, Instant installedAt, String boardPreset) {
        return query.update(INSTALLATION)
                .set(INSTALLATION.status, InstallationStatus.COMPLETED)
                .set(INSTALLATION.installedAt, installedAt)
                .set(INSTALLATION.rootOperatorId, rootOperatorId)
                .set(INSTALLATION.boardPreset, boardPreset)
                .setNull(INSTALLATION.tokenHash)
                .where(INSTALLATION.id.eq(InstallationEntity.ROW_ID), INSTALLATION.status.eq(InstallationStatus.PENDING))
                .execute();
    }
}
