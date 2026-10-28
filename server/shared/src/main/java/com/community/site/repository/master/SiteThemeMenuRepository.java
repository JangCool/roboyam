package com.community.site.repository.master;

import com.community.site.entity.SiteThemeMenuEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 테마별 메뉴 설정({@code site_theme_menus}) — 키는 테마 id */
public interface SiteThemeMenuRepository extends RoboyamRepository<SiteThemeMenuEntity, String> {

    /** 저장할 때 같은 테마의 저장을 줄 세운다(행이 없으면 빈 값 — 새로 넣는 쪽은 기본 키 충돌로 막힌다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SiteThemeMenuEntity> findWithLockByTheme(String theme);
}
