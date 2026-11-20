package com.community.jjal.repository.master;

import com.community.jjal.entity.JjalFileEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface JjalFileRepository extends IrobitRepository<JjalFileEntity, Long> {

    Optional<JjalFileEntity> findByPublicId(String publicId);

    List<JjalFileEntity> findAllByPublicIdIn(Collection<String> publicIds);

    Optional<JjalFileEntity> findBySha256(String sha256);

    /**
     * 같은 해시의 파일을 쓰기 잠금으로 — 올리기가 그 파일을 가리키는 보관함 줄을 넣기 전에 잡는다. 정리 배치도 지우기 전에 같은 행을 잠그므로(같은 잠금) 둘이 엇갈리지 않는다.
     * 결과가 같은 근거: 이 표를 FK 로 가리키는 줄(보관함 줄·쓰임)을 넣는 흐름 중 올리기·담기는 늘 이 행을 먼저 잠그고, 글·댓글 저장의 쓰임은 보관함에 있는 짤(줄이 있어 정리 대상이 아님)이나
     * 이미 쓰던 짤만 넣는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JjalFileEntity> findWithLockBySha256(String sha256);

    /** 쓰기 잠금으로 읽기(담기·신고 조치·정리 배치) — 근거는 {@link #findWithLockBySha256} */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JjalFileEntity> findWithLockById(Long id);
}
