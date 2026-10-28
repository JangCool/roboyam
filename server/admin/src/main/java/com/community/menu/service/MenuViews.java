package com.community.menu.service;

import com.community.menu.dto.MenuNode;
import com.community.menu.dto.request.MenuNodeRequest;
import com.community.menu.dto.response.MenuFolderView;
import com.community.menu.dto.response.MenuItemView;
import com.community.menu.dto.response.MenuSetView;
import com.community.menu.dto.response.MenuTreeNodeView;
import com.community.menu.entity.MenuSetEntity;
import com.community.menu.target.MenuResolveContext;
import com.community.menu.target.MenuTargetRegistry;
import com.community.menu.target.MenuTargetResolver;
import com.community.menu.target.MenuTargetStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 메뉴 그룹·트리 ↔ 관리자 화면 모양. 요청 노드 → {@link MenuNode}(검사 전), 저장된 트리 → 응답 노드(항목 상태 포함), 감사 기록용 트리 요약 */
@Component
public class MenuViews {

    private final MenuTargetRegistry registry;

    public MenuViews(MenuTargetRegistry registry) {
        this.registry = registry;
    }

    public static MenuSetView setView(MenuSetEntity set, long nodeCount) {
        return new MenuSetView(set.getSetKey(), set.getName(), set.getNameEn(), set.getPlacement(), set.isEnabled(), set.getDescription(), set.isSystem(),
                nodeCount, set.getUpdatedAt());
    }

    /** 저장된 트리 → 응답 노드. 항목에는 서버가 본 상태를 싣는다 */
    public List<MenuTreeNodeView> nodes(List<MenuNode> nodes, MenuResolveContext context) {
        List<MenuTreeNodeView> out = new ArrayList<>();
        for (MenuNode node : nodes) {
            String id = node.id() == null ? null : Long.toString(node.id());
            if (node.isFolder()) {
                out.add(new MenuFolderView(id, MenuNode.FOLDER, node.label(), node.nameEn(), node.visible(), node.audience(), node.minGrade(),
                        node.collapsible(), node.collapsed(), node.autoAdd(), node.divider(), nodes(node.children(), context)));
                continue;
            }
            MenuTargetResolver resolver = registry.find(node.targetType()).orElse(null);
            MenuTargetStatus status = resolver == null ? MenuTargetStatus.UNKNOWN_TYPE : resolver.status(node, context);
            out.add(new MenuItemView(id, MenuNode.ITEM, node.label(), node.nameEn(), node.visible(), node.audience(), node.minGrade(), node.targetType(),
                    node.targetId(), node.targetKey(), node.settings(), node.mobileTab(), node.divider(), node.description(),
                    new MenuItemView.Resolved(status.name())));
        }
        return out;
    }

    /** 요청 노드 → 검사 전 노드. ID 는 숫자가 아니면 null(새 노드), 빠진 참/거짓 칸은 기본값(보임 켬, 나머지 끔) */
    public static List<MenuNode> fromRequest(List<MenuNodeRequest> nodes) {
        List<MenuNode> out = new ArrayList<>();
        if (nodes == null) {
            return out;
        }
        for (MenuNodeRequest node : nodes) {
            if (node == null) {
                // 빈 자리 — 종류가 없는 노드로 넘겨 검사가 그 경로에 오류를 건다
                out.add(new MenuNode(null, null, null, null, true, null, null, false, false, false, null, null, null, null, false, List.of()));
                continue;
            }
            out.add(new MenuNode(MenuResolveContext.parseId(node.id()), node.kind(), node.label(), node.nameEn(), !Boolean.FALSE.equals(node.visible()),
                    node.audience(), node.minGrade(), Boolean.TRUE.equals(node.collapsible()), Boolean.TRUE.equals(node.collapsed()),
                    Boolean.TRUE.equals(node.autoAdd()), node.targetType(), node.targetId(), node.targetKey(), node.settings(),
                    Boolean.TRUE.equals(node.mobileTab()), fromRequest(node.children()), node.divider(), node.description()));
        }
        return out;
    }

    /** 감사 기록용 트리 요약 — 한 줄에 노드 하나(들여쓰기 = 단계) */
    public static List<String> outline(List<MenuNode> nodes) {
        List<String> lines = new ArrayList<>();
        outline(nodes, 0, lines);
        return lines;
    }

    private static void outline(List<MenuNode> nodes, int depth, List<String> lines) {
        for (MenuNode node : nodes) {
            StringBuilder line = new StringBuilder("  ".repeat(depth));
            if (node.isFolder()) {
                line.append('[').append(node.label()).append(']');
                if (node.autoAdd()) {
                    line.append(" [autoAdd]");
                }
            } else {
                if (node.label() != null) {
                    line.append(node.label()).append(" → ");
                }
                line.append(node.targetType()).append(':').append(target(node));
                if (node.mobileTab()) {
                    line.append(" [tab]");
                }
            }
            if (!node.visible()) {
                line.append(" [hidden]");
            }
            if (!"ALL".equals(node.audience())) {
                line.append(" [").append(node.audience()).append(node.minGrade() == null ? "" : " " + node.minGrade()).append(']');
            }
            lines.add(line.toString());
            outline(node.children(), depth + 1, lines);
        }
    }

    private static String target(MenuNode node) {
        if (node.targetId() != null) {
            return node.targetId();
        }
        if (node.targetKey() != null) {
            return node.targetKey();
        }
        String path = MenuSettingsJson.text(node.settings(), "path");
        if (path != null) {
            return path;
        }
        String href = MenuSettingsJson.text(node.settings(), "href");
        return href == null ? "" : href;
    }
}
