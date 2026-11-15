package com.community.post.repository.master;

import com.community.post.entity.PostTagEntity;
import com.community.post.entity.PostTagId;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.Collection;
import java.util.List;

/** 글 태그(V215 {@code post_tags}) — 바꾸는 쪽({@code PostTagWriter})은 글 행을 잠근(또는 방금 넣은) 트랜잭션 안에서 읽고 고친다 */
public interface PostTagRepository extends IrobitRepository<PostTagEntity, PostTagId> {

    /** 한 글의 태그(단 차례) */
    List<PostTagEntity> findByPostIdOrderBySortOrderAsc(long postId);

    /** 여러 글의 태그(차례는 부르는 쪽이 정한다) */
    List<PostTagEntity> findByPostIdIn(Collection<Long> postIds);
}
