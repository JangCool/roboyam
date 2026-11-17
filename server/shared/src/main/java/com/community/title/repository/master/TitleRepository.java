package com.community.title.repository.master;

import com.community.title.entity.TitleEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 칭호 정의({@code titles}, V240) — 몇 줄뿐이라 목록은 통째로 읽는다 */
public interface TitleRepository extends IrobitRepository<TitleEntity, Long> {

    Optional<TitleEntity> findByTitleKey(String titleKey);

    /**
     * 칭호 행을 잠근다 — 이달의 작가 한 달 최대 인원 검사·명예 작가 즉시 지급이 같은 칭호의 수여끼리 줄을 서게 한다. {@code member_titles} 가 이 행을 FK 로 가리키지만 넣는 흐름(수여·승인·자동)이
     * 모두 먼저 이 행을 잠그거나 유일 색인으로 막으므로 {@code FOR NO KEY UPDATE} 로 결과가 같다(repository.md 「잠금 세기」).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TitleEntity> findWithLockByTitleKey(String titleKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TitleEntity> findWithLockById(Long id);

    List<TitleEntity> findAllByOrderBySortOrderAscIdAsc();
}
