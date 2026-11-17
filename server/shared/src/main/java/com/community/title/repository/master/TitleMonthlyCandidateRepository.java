package com.community.title.repository.master;

import com.community.title.entity.TitleMonthlyCandidateEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;
import java.util.Optional;

/** 이달의 작가 후보로 넣은 글({@code title_monthly_candidates}, V240) */
public interface TitleMonthlyCandidateRepository extends IrobitRepository<TitleMonthlyCandidateEntity, Long> {

    Optional<TitleMonthlyCandidateEntity> findByMonthAndPostId(String month, Long postId);

    List<TitleMonthlyCandidateEntity> findByMonthOrderByAddedAtAsc(String month);

    /** 이 글들 중 그 달 후보로 넣은 것(관리자 글 목록의 {@code monthlyCandidate}) */
    List<TitleMonthlyCandidateEntity> findByMonthAndPostIdIn(String month, java.util.Collection<Long> postIds);
}
