package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.PostDealEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface PostDealRepository extends IrobitRepository<PostDealEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PostDealEntity> findWithLockByPostId(Long postId);
}
