package com.community.popup.repository.master;

import com.community.popup.entity.SitePopupEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 팝업(V162) — 목록은 저장한 차례(같으면 먼저 만든 것) */
public interface SitePopupRepository extends IrobitRepository<SitePopupEntity, Long> {

    List<SitePopupEntity> findAllByOrderBySortOrderAscIdAsc();

    /** 노출을 켠 팝업(공개 목록의 후보 — 기간은 부르는 쪽이 지금 시각으로 거른다) */
    List<SitePopupEntity> findAllByVisibleTrueOrderBySortOrderAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SitePopupEntity> findWithLockById(Long id);

    /** 모두 쓰기 잠금 — 만들기(맨 끝 차례)·차례 바꾸기를 줄 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SitePopupEntity> findAllWithLockByOrderBySortOrderAscIdAsc();
}
