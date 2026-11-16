package com.community.post.repository.master;

import com.community.post.entity.PostFeatureEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 우수 글 선정({@code post_features}, V238) — 선정·취소는 글 행을 잠근 관리자 트랜잭션에서(같은 글의 선정이 그 잠금에서 줄을 선다) */
public interface PostFeatureRepository extends IrobitRepository<PostFeatureEntity, Long> {

    /** 그 글의 살아 있는(취소되지 않은) 선정 */
    Optional<PostFeatureEntity> findByPostIdAndUnfeaturedAtIsNull(Long postId);

    /** 이 글들 중 살아 있는 선정 — 관리자 글 목록·상세의 {@code featured} */
    List<PostFeatureEntity> findByPostIdInAndUnfeaturedAtIsNull(Collection<Long> postIds);
}
