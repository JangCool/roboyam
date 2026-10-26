package com.community.group.repository.master;

import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupStatus;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 소모임 — 복합 조건·집계는 {@link GroupRepositoryQDSL} */
public interface GroupRepository extends RoboyamRepository<GroupEntity, Long> {

    /** 정원·멤버 수·상태를 바꾸기 전에 행을 잠근다(트랜잭션 안에서만) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GroupEntity> findWithLockById(Long id);

    /** 이 회원이 모임장인 소모임 수(그 상태들) — 1인 상한 */
    long countByLeaderIdAndStatusIn(Long leaderId, Collection<GroupStatus> statuses);

    List<GroupEntity> findByLeaderIdAndStatusIn(Long leaderId, Collection<GroupStatus> statuses);

    /** 같은 이름(이름 키)이 그 상태들에 있나 */
    boolean existsByNameKeyAndStatusIn(String nameKey, Collection<GroupStatus> statuses);

    /** 내 개설 신청(신청 중·반려) — 최근이 위 */
    List<GroupEntity> findByCreatorIdAndStatusInOrderByRequestedAtDesc(Long creatorId, Collection<GroupStatus> statuses);

    /** 그 시각 뒤에 이 회원이 낸 개설 신청 수(하루 한도 — 상태 무관) */
    long countByCreatorIdAndRequestedAtAfter(Long creatorId, Instant after);

    Optional<GroupEntity> findFirstByCreatorIdAndRequestedAtAfterOrderByRequestedAtAsc(Long creatorId, Instant after);

    long countByStatus(GroupStatus status);

    /** 승인 대기 — 오래된 신청이 위 */
    List<GroupEntity> findByStatusOrderByRequestedAtAscIdAsc(GroupStatus status);

    /** 그 게시판의 소모임이 하나라도 있나(게시판 지우기 막기 — 상태 무관) */
    boolean existsByBoardId(Long boardId);
}
