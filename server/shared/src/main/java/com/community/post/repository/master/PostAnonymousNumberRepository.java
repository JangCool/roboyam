package com.community.post.repository.master;

import com.community.post.entity.PostAnonymousNumberEntity;
import com.community.post.entity.PostAnonymousNumberId;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 익명 글 안의 회원 번호(V184 {@code post_anonymous_numbers}) — 글 ID 로 읽는다 */
public interface PostAnonymousNumberRepository extends RoboyamRepository<PostAnonymousNumberEntity, PostAnonymousNumberId> {

    /** 글 하나의 번호 전부 */
    List<PostAnonymousNumberEntity> findByPostId(Long postId);

    /** 여러 글의 번호(글이 섞인 목록) */
    List<PostAnonymousNumberEntity> findByPostIdIn(Collection<Long> postIds);

    Optional<PostAnonymousNumberEntity> findByPostIdAndUserId(Long postId, Long userId);

    /** 글 안의 가장 큰 번호(없으면 0) — 글 단위 잠금 아래에서만 읽고 + 1 로 다음 번호를 매긴다 */
    @Query("select coalesce(max(n.number), 0) from PostAnonymousNumberEntity n where n.postId = :postId")
    int maxNumber(@Param("postId") Long postId);
}
