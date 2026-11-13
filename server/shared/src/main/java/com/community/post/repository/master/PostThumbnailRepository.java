package com.community.post.repository.master;

import com.community.post.entity.PostThumbnailEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.Collection;
import java.util.List;

/** 글의 대표 썸네일 선택(V206 {@code post_thumbnails}) — 행이 없는 글은 자동. 바꾸는 쪽은 글 행을 먼저 잠근 트랜잭션 안에서 읽고 고친다(글쓴이 고치기 · 운영자 조치가 줄을 선다) */
public interface PostThumbnailRepository extends IrobitRepository<PostThumbnailEntity, Long> {

    /** 여러 글의 선택(목록 한 쪽) */
    List<PostThumbnailEntity> findByPostIdIn(Collection<Long> postIds);
}
