package com.community.operator.repository.master;

import com.community.operator.entity.OperatorInvitationEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface OperatorInvitationRepository extends RoboyamRepository<OperatorInvitationEntity, Long> {

    /** 아직 수락하지 않은 초대 — 같은 이메일에는 하나뿐이다(유일 인덱스) */
    Optional<OperatorInvitationEntity> findByEmailAndConsumedAtIsNull(String email);

    /** 수락하지 않은 초대 전부(기한이 지난 것 포함), 최근 초대가 앞 */
    List<OperatorInvitationEntity> findByConsumedAtIsNullOrderByCreatedAtDescIdDesc();

    /** 토큰 해시(SHA-256 16진)로 찾는다 — 링크 확인. 수락한 초대도 찾는다 */
    Optional<OperatorInvitationEntity> findByTokenHash(String tokenHash);

    /** 토큰 해시로 찾아 행을 쓰기 잠금으로 읽는다 — 같은 초대를 동시에 수락하는 요청을 줄 세운다. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OperatorInvitationEntity> findWithLockByTokenHash(String tokenHash);
}
