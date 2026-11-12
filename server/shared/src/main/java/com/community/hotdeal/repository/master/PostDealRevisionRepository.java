package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.PostDealRevisionEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;

public interface PostDealRevisionRepository extends IrobitRepository<PostDealRevisionEntity, Long> {

    /** 글의 딜 칸 수정 이력 — 최근 것부터(관리자 서랍 「수정 이력」) */
    List<PostDealRevisionEntity> findByPostIdOrderByCreatedAtDescIdDesc(Long postId);
}
