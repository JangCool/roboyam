package com.community.title.repository.master;

import com.community.title.entity.MemberTitleEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 회원이 받은 칭호({@code member_titles}, V240) */
public interface MemberTitleRepository extends IrobitRepository<MemberTitleEntity, Long> {

    /** 그 회원·칭호·기간의 살아 있는 줄 */
    Optional<MemberTitleEntity> findByUserIdAndTitleIdAndPeriodAndRevokedAtIsNull(Long userId, Long titleId, String period);

    /** 그 회원·칭호의 살아 있는 줄 전부(이달의 작가는 달마다 한 줄) */
    List<MemberTitleEntity> findByUserIdAndTitleIdAndRevokedAtIsNull(Long userId, Long titleId);

    /** 그 회원의 살아 있는 줄 전부 */
    List<MemberTitleEntity> findByUserIdAndRevokedAtIsNull(Long userId);

    /** 그 칭호·기간의 살아 있는 줄 수(이달의 작가 한 달 인원) — 칭호 행을 잠근 트랜잭션에서 */
    long countByTitleIdAndPeriodAndRevokedAtIsNull(Long titleId, String period);

    /** 그 칭호·기간의 살아 있는 줄(이달의 작가 뽑기 화면의 「이미 받은 회원」) */
    List<MemberTitleEntity> findByTitleIdAndPeriodAndRevokedAtIsNullOrderByGrantedAtAsc(Long titleId, String period);

    /** 그 회원·칭호의 살아 있는 줄 수(명예 작가 = 이달의 작가 횟수) */
    long countByUserIdAndTitleIdAndRevokedAtIsNull(Long userId, Long titleId);

    /** 운영자가 회수한 줄이 있나(자동 칭호를 배치가 다시 주지 않는다) */
    boolean existsByUserIdAndTitleIdAndRevokedAtIsNotNullAndRevokedByIsNotNull(Long userId, Long titleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MemberTitleEntity> findWithLockById(Long id);
}
