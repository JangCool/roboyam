package com.community.menu.service;

import com.community.menu.dto.MenuBoardRef;
import com.community.menu.dto.MenuNode;
import com.community.menu.dto.MenuViewer;
import com.community.menu.dto.response.MenuBoardView;
import com.community.menu.dto.response.MenuGroupNodeView;
import com.community.menu.dto.response.MenuItemNodeView;
import com.community.menu.dto.response.MenuNodeView;
import com.community.menu.dto.response.MenuResponse;
import com.community.menu.target.MenuResolveContext;
import com.community.menu.target.MenuTargetRegistry;
import com.community.menu.target.MenuTargetResolver;
import com.community.menu.target.ResolvedTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 트리를 <b>보는 사람 기준</b>으로 걸러 공개 응답 모양({@link MenuResponse})으로 만든다 — 공개 {@code GET /api/menu} 와 관리자 미리보기가 같이 쓴다
 * (admin-web {@code UtilMenu.preview} 와 같은 규칙·차례).
 *
 * <ol>
 *   <li>표시 여부({@code visible}) — 숨긴 폴더는 아래 전부와 함께 빠진다.</li>
 *   <li>보이는 대상({@code audience}) — {@link #audienceAllows}.</li>
 *   <li>항목은 리졸버가 해석한다 — 모르는 유형·대상 없음·꺼진 게시판·판매 꺼짐·로그인 필요 화면을 비로그인이 봄이면 빠진다.</li>
 *   <li>빈 폴더 제거(아래에서 위로).</li>
 * </ol>
 * 모바일 탭은 {@code SIDE} 그룹에서만, 남은 항목 중 깊이 우선 차례로 최대 10개다.
 */
@Component
public class MenuViewComposer {

    private final MenuTargetRegistry registry;

    public MenuViewComposer(MenuTargetRegistry registry) {
        this.registry = registry;
    }

    public MenuResponse compose(List<MenuNode> nodes, boolean side, MenuResolveContext context, String version) {
        List<String> tabs = new ArrayList<>();
        List<MenuNodeView> out = filter(nodes, side, context, tabs);
        List<String> mobileTabs = tabs.size() > MenuLimits.MOBILE_TABS ? tabs.subList(0, MenuLimits.MOBILE_TABS) : tabs;
        return new MenuResponse(out, List.copyOf(mobileTabs), version);
    }

    /** 보이는 대상이 이 사람을 들이는가 — 운영진은 등급 조건과 무관하게 본다. 비로그인 전용(GUEST)은 로그인한 사람에게 안 보인다 */
    public static boolean audienceAllows(String audience, String minGrade, MenuViewer viewer, Map<String, Integer> gradeLevels) {
        if (audience == null || viewer == null) {
            return false;
        }
        return switch (audience) {
            case "ALL" -> true;
            case "GUEST" -> viewer.isGuest();
            case "MEMBER" -> !viewer.isGuest();
            case "STAFF" -> viewer.isStaff();
            case "GRADE_MIN" -> {
                if (viewer.isStaff()) {
                    yield true;
                }
                Integer level = minGrade == null ? null : gradeLevels.get(minGrade);
                yield !viewer.isGuest() && level != null && viewer.gradeLevel() != null && viewer.gradeLevel() >= level;
            }
            default -> false;
        };
    }

    private List<MenuNodeView> filter(List<MenuNode> nodes, boolean side, MenuResolveContext context, List<String> tabs) {
        List<MenuNodeView> out = new ArrayList<>();
        for (MenuNode node : nodes) {
            if (!node.visible() || !audienceAllows(node.audience(), node.minGrade(), context.viewer(), context.gradeLevels())) {
                continue;
            }
            if (node.isFolder()) {
                List<MenuNodeView> children = filter(node.children(), side, context, tabs);
                if (!children.isEmpty()) {
                    out.add(MenuGroupNodeView.of(idOf(node), node.label() == null ? "" : node.label(), node.nameEn(), node.collapsible(),
                            node.collapsible() && node.collapsed(), children));
                }
                continue;
            }
            MenuTargetResolver resolver = registry.find(node.targetType()).orElse(null);
            ResolvedTarget target = resolver == null ? null : resolver.resolve(node, context);
            if (target == null) {
                continue;
            }
            boolean ownLabel = node.label() != null;
            String label = ownLabel ? node.label() : target.defaultLabel() == null ? "" : target.defaultLabel();
            String nameEn = node.nameEn() != null ? node.nameEn() : ownLabel ? null : target.defaultNameEn();
            boolean mobileTab = side && node.mobileTab();
            String id = idOf(node);
            if (mobileTab && id != null) {
                tabs.add(id);
            }
            MenuBoardRef board = target.board();
            MenuBoardView boardView = board == null ? null : new MenuBoardView(Long.toString(board.id()), board.slug(), board.color(), board.investmentNotice());
            String key = "BUILTIN".equals(node.targetType()) ? node.targetKey() : null;
            out.add(new MenuItemNodeView(id, MenuNode.ITEM, node.targetType(), key, label, nameEn, target.path(), target.matchPrefix(), target.newTab(), mobileTab,
                    boardView, target.extra()));
        }
        return out;
    }

    private static String idOf(MenuNode node) {
        return node.id() == null ? null : Long.toString(node.id());
    }
}
