package com.community.menu.service;

import com.community.board.repository.master.BoardStaffRepository;
import com.community.grade.entity.GradeEntity;
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
 *   <li>캐시를 두지 않는다(기존 캐시 수단이 없다) — 요청마다 그룹·트리·게시판·등급을 읽는다.</li>
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

    public PublicMenuService(MenuSetRepository sets, MenuTreeStore store, MenuSnapshots snapshots, MenuViewComposer composer, UserRepository users,
                             ProfileRepository profiles, BoardStaffRepository boardStaff) {
        this.sets = sets;
        this.store = store;
        this.snapshots = snapshots;
        this.composer = composer;
        this.users = users;
        this.profiles = profiles;
        this.boardStaff = boardStaff;
    }

    /**
     * @param setKey 메뉴 그룹 코드
     * @param userId 로그인한 회원 ID(비로그인 null)
     */
    public MenuResponse menu(String setKey, Long userId) {
        MenuSetEntity set = setKey == null ? null : sets.findById(setKey).orElse(null);
        if (set == null) {
            throw new MenuSetNotFoundException();
        }
        String version = Long.toString(set.getVersion());
        if (!set.isEnabled()) {
            return new MenuResponse(List.of(), List.of(), version);
        }
        List<GradeEntity> grades = snapshots.grades();
        Map<String, Integer> levels = MenuSnapshots.levels(grades);
        MenuResolveContext context = new MenuResolveContext(snapshots.boards(), levels, MenuSales.enabled(), viewerOf(userId, levels));
        return composer.compose(store.load(set.getSetKey()), MenuLimits.isSide(set.getPlacement()), context, version);
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
