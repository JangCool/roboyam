package com.community.menu.service;

import com.community.cache.definition.CacheNames;
import com.community.menu.dto.MenuNode;
import com.community.menu.dto.MenuBoardRef;
import com.community.menu.dto.MenuPageRef;
import org.springframework.cache.CacheManager;
import com.community.board.repository.master.BoardStaffRepository;
import com.community.menu.dto.MenuViewer;
import com.community.menu.dto.response.MenuResponse;
import com.community.menu.entity.MenuSetEntity;
import com.community.menu.exception.MenuSetNotFoundException;
import com.community.menu.repository.master.MenuSetRepository;
import com.community.menu.target.MenuResolveContext;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 사이트 메뉴 {@code GET /api/menu?set=main} — 그룹의 트리를 <b>보는 사람 기준</b>으로 걸러 준다({@link MenuViewComposer}, 관리자 미리보기와 같은 조립기).
 *
 * <ul>
 *   <li>없는 그룹 404 {@code MENU_SET_NOT_FOUND}, 꺼진 그룹은 빈 {@code nodes}. 모바일 탭은 {@code SIDE} 그룹에서만.</li>
 *   <li>보는 사람: 로그인하지 않았거나 회원이 없어졌으면 {@code GUEST}. 로그인 회원은 등급 설정의 서열로 {@code GRADE_MIN} 을 판정한다.
 *       {@code STAFF}(운영진) = 게시판 매니저·스텝을 하나라도 맡은 회원이다 — 운영자는 공개 사이트에 로그인하지 않으므로(관리자 앱 계정이 따로다)
 *       공개 사이트의 운영진은 게시판 담당 회원뿐이다(게시판 쓰기 자격 {@code STAFF} 와 같은 뜻).</li>
 *   <li>그룹·트리·게시판·등급 서열·가젯 페이지는 캐시 {@code menu.public}(기본 300초 — 관리자 메뉴·게시판·등급·가젯 페이지 저장은 세대 번호로 몇 초 안에)에 두고,
 *       보는 사람 판정과 판매 스위치는 요청마다 읽는다. 판매가 꺼지면 공동구매 항목이 빠진다({@link MenuSales}).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PublicMenuService {

    private final MenuSetRepository sets;
    private final MenuTreeStore store;
    private final MenuSnapshots snapshots;
    private final MenuViewComposer composer;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final BoardStaffRepository boardStaff;
    private final MenuSales sales;
    private final CacheManager caches;

    public PublicMenuService(MenuSetRepository sets, MenuTreeStore store, MenuSnapshots snapshots, MenuViewComposer composer, UserRepository users,
                             ProfileRepository profiles, BoardStaffRepository boardStaff, MenuSales sales, CacheManager caches) {
        this.caches = caches;
        this.sets = sets;
        this.store = store;
        this.snapshots = snapshots;
        this.composer = composer;
        this.users = users;
        this.profiles = profiles;
        this.boardStaff = boardStaff;
        this.sales = sales;
    }

    /**
     * @param setKey 메뉴 그룹 코드
     * @param userId 로그인한 회원 ID(비로그인 null)
     */
    public MenuResponse menu(String setKey, Long userId) {
        if (setKey == null) {
            throw new MenuSetNotFoundException();
        }
        // 캐시 menu.public(항목 키 = 그룹 코드) — 보는 사람과 무관한 재료(그룹·트리·게시판·등급 서열·가젯 페이지)만 담는다. 없는 그룹도 「없음」으로 담는다
        Shared shared = caches.getCache(CacheNames.MENU_PUBLIC).get(setKey, () -> shared(setKey));
        if (shared == null) {
            throw new MenuSetNotFoundException();
        }
        if (!shared.enabled()) {
            return new MenuResponse(List.of(), List.of(), shared.version());
        }
        // 보는 사람(등급·운영진) 판정과 판매 스위치(돈 기능 — 요청마다 지금 값)는 꺼낸 뒤 — 거르는 규칙은 캐시 전과 같다(같은 조립기)
        MenuResolveContext context = new MenuResolveContext(shared.boards(), shared.levels(), sales.enabled(), viewerOf(userId, shared.levels()), shared.pages());
        return composer.compose(shared.nodes(), shared.side(), context, shared.version());
    }

    /** 그룹 하나의 공용 재료 — 없는 그룹은 null */
    private Shared shared(String setKey) {
        MenuSetEntity set = sets.findById(setKey).orElse(null);
        if (set == null) {
            return null;
        }
        String version = Long.toString(set.getVersion());
        if (!set.isEnabled()) {
            return new Shared(version, false, false, List.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, Integer> levels = Map.copyOf(MenuSnapshots.levels(snapshots.grades()));
        return new Shared(version, true, MenuLimits.isSide(set.getPlacement()), List.copyOf(store.load(set.getSetKey())), levels, Map.copyOf(snapshots.boards()),
                Map.copyOf(snapshots.pages()));
    }

    /** 캐시에 담는 그룹 하나의 재료 */
    private record Shared(String version, boolean enabled, boolean side, List<MenuNode> nodes, Map<String, Integer> levels, Map<Long, MenuBoardRef> boards,
                          Map<Long, MenuPageRef> pages) {
    }

    private MenuViewer viewerOf(Long userId, Map<String, Integer> levels) {
        if (userId == null || users.findById(userId).filter(user -> !user.isGone()).isEmpty()) {
            return MenuViewer.guest();
        }
        if (boardStaff.countByUserId(userId) > 0) {
            return new MenuViewer(MenuViewer.STAFF, null);
        }
        Integer level = profiles.findById(userId).map(ProfileEntity::getGradeKey).map(levels::get).orElse(null);
        return new MenuViewer(MenuViewer.MEMBER, level);
    }
}
