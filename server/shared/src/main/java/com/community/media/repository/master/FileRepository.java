package com.community.media.repository.master;

import com.community.media.entity.FileEntity;
import com.community.media.entity.FileStatus;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface FileRepository extends RoboyamRepository<FileEntity, Long> {

    /** 행을 쓰기 잠금으로 읽는다 — 같은 파일을 두 곳에서 지정하는 요청을 줄 세울 때. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FileEntity> findWithLockById(Long id);

    /** 올리고 나서 오래 지정되지 않은 파일(오래된 순). 정리 배치가 쓴다 */
    List<FileEntity> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(FileStatus status, Instant before);
}
