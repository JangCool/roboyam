package com.community.nicknameimage.repository.master;

import com.community.nicknameimage.entity.NicknameImageRequestEntity;
import com.community.nicknameimage.entity.NicknameImageStatus;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 닉네임 그림 신청 — 회원 화면(api)과 운영자 검토(admin)가 같이 쓴다 */
public interface NicknameImageRequestRepository extends RoboyamRepository<NicknameImageRequestEntity, Long> {

    /** 회원의 그 상태 신청(대기·적용 중은 회원당 하나 — 부분 유일 색인) */
    Optional<NicknameImageRequestEntity> findFirstByUserIdAndStatus(Long userId, NicknameImageStatus status);

    /** 회원의 가장 최근 신청 */
    Optional<NicknameImageRequestEntity> findFirstByUserIdOrderByIdDesc(Long userId);

    /** 회원의 그 상태 신청들(반려 파일 정리) */
    List<NicknameImageRequestEntity> findByUserIdAndStatus(Long userId, NicknameImageStatus status);

    /** 다시 신청을 막는 시각 제한(반려·운영 해제) 중 가장 늦은 것 — {@code reapply_at > now} */
    Optional<NicknameImageRequestEntity> findFirstByUserIdAndReapplyAtAfterOrderByReapplyAtDesc(Long userId, Instant now);

    /** 오늘(한국 시간 0시 뒤) 올린 수 — 하루 한도 */
    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, Instant since);

    /** 처리하려는 신청을 잠근다(승인·반려·해제가 겹쳐도 한쪽만 이긴다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NicknameImageRequestEntity> findWithLockById(Long id);
}
