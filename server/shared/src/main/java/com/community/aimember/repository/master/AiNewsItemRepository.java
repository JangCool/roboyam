package com.community.aimember.repository.master;

import com.community.aimember.entity.AiNewsItemEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 뉴스 수집 항목({@code ai_news_items}, V228) */
public interface AiNewsItemRepository extends IrobitRepository<AiNewsItemEntity, Long> {

    /** 이미 받은 링크(넣기 전에 거른다 — 링크는 고유) */
    @Query("select i.link from AiNewsItemEntity i where i.link in :links")
    List<String> findExistingLinks(@Param("links") Collection<String> links);

    /** 아직 쓰지 않은 최근 항목(받은 시각 최신부터) — 소재 고르기 */
    List<AiNewsItemEntity> findByUsedAtIsNullAndFetchedAtAfterAndSourceIdInOrderByFetchedAtDescIdDesc(Instant since, Collection<Long> sourceIds, Pageable page);

    /** 아직 쓰지 않은 항목 수(관리자 화면) */
    long countByUsedAtIsNull();

    /** 보관 기간이 지난 항목 지우기 — 지운 수 */
    @Modifying
    @Query("delete from AiNewsItemEntity i where i.fetchedAt < :before")
    int deleteFetchedBefore(@Param("before") Instant before);
}
