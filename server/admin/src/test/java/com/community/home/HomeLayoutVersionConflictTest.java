package com.community.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.CommerceAdminTestBase;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.HomeLayoutBodyView;
import com.community.home.dto.response.HomeLayoutView;
import com.community.home.exception.LayoutVersionConflictException;
import com.community.home.service.HomeLayoutAdminService;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.CommonException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * 배치 저장의 버전 확인(관리자 QA 2026-11-17 F-1) — 화면이 받은 {@code updatedAt} 을 {@code base} 로 보내면, 그사이 다른 저장이 있었을 때 409 {@code LAYOUT_VERSION_CONFLICT}.
 * 저장 본문이 「가젯 전체」라 낡은 화면이 저장하면 남이 넣은 가젯이 경고 없이 지워지던 문제다. {@code base} 를 안 보내면(옛 화면·시드 코드) 예전처럼 검사하지 않는다.
 * 다른 홈 시험(아이로빗·다음식·네이버식)과 겹치지 않게 기본 테마의 홈에서 시험한다.
 */
class HomeLayoutVersionConflictTest extends CommerceAdminTestBase {

    private static final String THEME = "default";

    @Autowired
    private HomeLayoutAdminService service;

    private AdminActor root;

    @BeforeEach
    void setUp() {
        long rootId = rootId();
        root = actor(rootId);
        // SQL 로 남김: ON CONFLICT upsert — 사이트 설정 행(저장이 잠그는 행)을 없을 때만 넣는다
        jdbc.update("INSERT INTO site_settings (id, site_name, public_url, updated_by) VALUES (1, '시험', 'http://localhost', ?) ON CONFLICT (id) DO NOTHING", rootId);
        // 이 시험은 저장한 적 없는 기본 배치에서 시작한다
        jdbc.update("DELETE FROM home_gadgets WHERE theme = ? AND page_id IS NULL AND NOT board_layout", THEME);
        jdbc.update("DELETE FROM home_layouts WHERE theme = ?", THEME);
    }

    private HomeLayoutSaveRequest request(HomeLayoutSaveRequest.Base base) {
        HomeLayoutBodyView defaults = service.defaults(THEME);
        List<HomeLayoutSaveRequest.Item> items = defaults.layout().items().stream()
                .map(it -> new HomeLayoutSaveRequest.Item(it.gadgetId(), it.x(), it.y(), it.w(), it.h())).toList();
        return new HomeLayoutSaveRequest(new HomeLayoutSaveRequest.Layout(defaults.layout().columns(), items),
                defaults.gadgets().stream().map(g -> new HomeLayoutSaveRequest.Gadget(g.id(), g.type(), null, null, null, null, null)).toList(), base);
    }

    private static HomeLayoutSaveRequest.Base base(Instant updatedAt) {
        return new HomeLayoutSaveRequest.Base(updatedAt);
    }

    @Test
    void 받은_뒤_다른_저장이_있으면_409() {
        assertThat(service.get(THEME).updatedAt()).isNull();
        // 두 탭이 저장한 적 없는 기본 배치를 받았다 — 앞 탭이 먼저 저장
        HomeLayoutView first = service.save(root, THEME, request(base(null)));
        assertThat(first.updatedAt()).isNotNull();

        // 뒤 탭(낡은 화면)은 막힌다
        assertThatThrownBy(() -> service.save(root, THEME, request(base(null))))
                .isInstanceOf(LayoutVersionConflictException.class)
                .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.save(root, THEME, request(base(first.updatedAt().minusSeconds(5)))))
                .isInstanceOf(LayoutVersionConflictException.class);
    }

    @Test
    void 받은_시각이_지금_저장과_같으면_저장되고_밀리초_아래는_보지_않는다() {
        HomeLayoutView first = service.save(root, THEME, request(base(null)));
        // 저장 응답의 시각(자바)과 다시 읽은 시각(DB)은 밀리초 아래 자릿수가 다를 수 있다 — 응답 시각으로 이어 저장해도 된다
        HomeLayoutView second = service.save(root, THEME, request(base(first.updatedAt())));
        HomeLayoutView third = service.save(root, THEME, request(base(service.get(THEME).updatedAt())));
        assertThat(third.updatedAt()).isAfterOrEqualTo(second.updatedAt());
    }

    @Test
    void base_를_안_보내면_예전처럼_검사하지_않는다() {
        service.save(root, THEME, request(base(null)));
        HomeLayoutView again = service.save(root, THEME, request(null));
        assertThat(again.updatedAt()).isNotNull();
    }
}
