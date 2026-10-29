package com.community.menu.service;

import com.community.menu.dto.MenuNode;
import com.community.menu.target.MenuTargetRegistry;
import com.community.menu.target.MenuTargetResolver;
import com.community.menu.target.MenuValidationContext;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 공개 메뉴 트리 검사·정리 — admin-web {@code UtilMenu.validate} 와 같은 규칙. 위반을 <b>칸별로 모아</b> 400 {@code VALIDATION} 으로 던진다
 * (칸 경로 {@code nodes[1].children[2].label}, 트리 전체 문제는 {@code nodes}).
 *
 * <ul>
 *   <li>폴더: 이름 필수 · 깊이 ≤ 3({@code kind}) · 자동 추가는 {@code SIDE} 그룹만({@code autoAdd}). 접힌 채 시작은 접기를 켰을 때만 남긴다.
 *       선택 연결({@code targetType} 이 비면 연결 없음 — 연결 칸과 {@code linkClick} 을 버린다)은 항목과 같은 리졸버 정리·검사를 거치되
 *       「그룹 안에서 한 번」 중복 규칙에 세지 않고 로그인 필요 화면의 보이는 대상 규칙도 걸지 않는다. 화면이 모르는 유형은 400({@code targetType}),
 *       {@code linkClick} 은 빠지면 {@code NAVIGATE_OPEN}, 목록 밖은 400({@code linkClick}).</li>
 *   <li>항목: 하위 노드 금지({@code children}) · 깊이 ≤ 4({@code kind}) · 모바일 탭은 {@code SIDE} 그룹만({@code mobileTab}) · 유형별 검사는 리졸버
 *       (이름 필수 여부·대상·주소·로그인 필요 화면의 보이는 대상) · 게시판·앱 화면은 그룹 안에서 한 번.
 *       모르는 유형은 유형 코드 모양·이름 길이만 보고 나머지 칸은 받은 그대로 보존한다.</li>
 *   <li>공통: 영문명 · 보이는 대상 값 · {@code GRADE_MIN} 의 등급(등급 목록에 있는 키).</li>
 *   <li>트리: 노드 ≤ 80 · 모바일 탭 ≤ 10 · 자동 추가 폴더 하나 — 새로 켠 폴더가 하나면 이미 켜져 있던 것을 끄고, 새로 켠 것이 둘 이상이면 400.</li>
 * </ul>
 */
@Component
public class MenuTreeValidator {

    private final MenuTargetRegistry registry;

    public MenuTreeValidator(MenuTargetRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param nodes         요청 트리(ID 는 부르는 쪽이 정리했다)
     * @param storedAutoAdd 지금 저장된 노드 ID → 자동 추가 켜짐(새로 켠 폴더 판정). 미리보기는 빈 맵
     * @return 정리한 트리(이름 다듬기·안 쓰는 칸 비우기·자동 추가 하나로)
     */
    public List<MenuNode> validate(List<MenuNode> nodes, MenuValidationContext context, Map<Long, Boolean> storedAutoAdd) {
        Walk walk = new Walk(context);
        List<MenuNode> out = walk.nodes(nodes == null ? List.of() : nodes, "nodes", 1);

        if (MenuNode.count(out) > MenuLimits.NODES) {
            walk.errors.putIfAbsent("nodes", new MessageRef("menu.tree.nodesLimit", String.valueOf(MenuLimits.NODES)));
        }
        if (walk.tabs > MenuLimits.MOBILE_TABS) {
            walk.errors.putIfAbsent("nodes", new MessageRef("menu.tree.tabsLimit", String.valueOf(MenuLimits.MOBILE_TABS)));
        }
        List<MenuNode> fresh = walk.autoAddFolders.stream().filter(f -> f.id() == null || !Boolean.TRUE.equals(storedAutoAdd.get(f.id()))).toList();
        if (fresh.size() > 1 || (fresh.isEmpty() && walk.autoAddFolders.size() > 1)) {
            walk.errors.putIfAbsent("nodes", new MessageRef("menu.tree.autoAddMulti"));
        }
        if (!walk.errors.isEmpty()) {
            throw new ValidationException(walk.errors);
        }
        if (walk.autoAddFolders.size() > 1) {
            MenuNode keep = fresh.get(0);
            return keepOnlyAutoAdd(out, keep);
        }
        return out;
    }

    /** 자동 추가를 {@code keep} 폴더 하나로 — 나머지 폴더는 끈다 */
    private static List<MenuNode> keepOnlyAutoAdd(List<MenuNode> nodes, MenuNode keep) {
        List<MenuNode> out = new ArrayList<>();
        for (MenuNode node : nodes) {
            if (!node.isFolder()) {
                out.add(node);
                continue;
            }
            MenuNode folder = node.autoAdd() && node != keep ? node.withAutoAdd(false) : node;
            out.add(folder.withChildren(keepOnlyAutoAdd(node.children(), keep)));
        }
        return out;
    }

    private final class Walk {

        final Map<String, MessageRef> errors = new LinkedHashMap<>();
        final Set<String> linked = new HashSet<>();
        final List<MenuNode> autoAddFolders = new ArrayList<>();
        final MenuValidationContext context;
        final boolean side;
        int tabs;

        Walk(MenuValidationContext context) {
            this.context = context;
            this.side = MenuLimits.isSide(context.placement());
        }

        List<MenuNode> nodes(List<MenuNode> nodes, String prefix, int depth) {
            List<MenuNode> out = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i++) {
                String path = prefix + "[" + i + "]";
                MenuFieldErrors fieldErrors = new MenuFieldErrors(path, errors);
                MenuNode node = nodes.get(i);
                if (node == null || (!node.isFolder() && !node.isItem())) {
                    fieldErrors.add("kind", new MessageRef("menu.node.kind"));
                    continue;
                }
                String audience = node.audience();
                if (!MenuLimits.AUDIENCES.contains(audience)) {
                    fieldErrors.add("audience", new MessageRef("menu.audience.choice"));
                }
                String minGrade = null;
                if ("GRADE_MIN".equals(audience)) {
                    minGrade = MenuRules.stripToNull(node.minGrade());
                    if (minGrade == null) {
                        fieldErrors.add("minGrade", new MessageRef("menu.grade.required"));
                    } else if (!context.gradeKeys().contains(minGrade)) {
                        fieldErrors.add("minGrade", new MessageRef("menu.grade.unknown"));
                    }
                }
                fieldErrors.add("nameEn", MenuRules.nameEnProblem(node.nameEn()));
                String label = MenuRules.stripToNull(node.label());
                String nameEn = MenuRules.stripToNull(node.nameEn());
                String divider = node.divider() == null ? MenuNode.DIVIDER_NONE : node.divider();
                if (!MenuLimits.DIVIDERS.contains(divider)) {
                    fieldErrors.add("divider", new MessageRef("menu.divider.choice"));
                }
                if (node.isFolder()) {
                    out.add(folder(node, path, depth, fieldErrors, label, nameEn, audience, minGrade, divider));
                } else {
                    fieldErrors.add("description", MenuRules.descriptionProblem(node.description()));
                    out.add(item(node, depth, fieldErrors, label, nameEn, audience, minGrade).withExtras(divider, MenuRules.stripToNull(node.description())));
                }
            }
            return out;
        }

        private MenuNode folder(MenuNode node, String path, int depth, MenuFieldErrors fieldErrors, String label, String nameEn, String audience,
                                String minGrade, String divider) {
            fieldErrors.add("label", MenuRules.labelProblem(node.label(), true, MenuLimits.LABEL_MAX));
            if (depth > MenuLimits.FOLDER_DEPTH) {
                fieldErrors.add("kind", new MessageRef("menu.node.folderDepth", String.valueOf(MenuLimits.FOLDER_DEPTH)));
            }
            if (node.autoAdd() && !side) {
                fieldErrors.add("autoAdd", new MessageRef("menu.autoAdd.side"));
            }
            MenuNode link = folderLink(node, path, audience, fieldErrors);
            String linkClick = null;
            if (link != null) {
                linkClick = node.linkClick() == null ? MenuNode.LINK_CLICK_NAVIGATE_OPEN : node.linkClick();
                if (!MenuLimits.LINK_CLICKS.contains(linkClick)) {
                    fieldErrors.add("linkClick", new MessageRef("siteThemeMenu.choice"));
                }
            }
            List<MenuNode> children = nodes(node.children(), path + ".children", depth + 1);
            MenuNode folder = new MenuNode(node.id(), MenuNode.FOLDER, label, nameEn, node.visible(), audience, minGrade, node.collapsible(),
                    node.collapsible() && node.collapsed(), side && node.autoAdd(), link == null ? null : link.targetType(),
                    link == null ? null : link.targetId(), link == null ? null : link.targetKey(), link == null ? null : link.settings(), false, children,
                    divider, null, linkClick);
            if (folder.autoAdd()) {
                autoAddFolders.add(folder);
            }
            return folder;
        }

        /**
         * 폴더의 선택 연결 — 항목과 같은 리졸버로 정리·검사하고 정리한 연결 칸(유형·ID·키·설정)을 가진 노드를 돌려준다. 연결이 없으면 null.
         * 「그룹 안에서 한 번」 중복에 세지 않고, 리졸버가 거는 보이는 대상 문제({@code audience} — 로그인 필요 화면)는 폴더에 걸지 않는다.
         */
        private MenuNode folderLink(MenuNode node, String path, String audience, MenuFieldErrors fieldErrors) {
            String type = MenuRules.stripToNull(node.targetType());
            if (type == null) {
                return null;
            }
            MenuTargetResolver resolver = registry.find(type).orElse(null);
            if (resolver == null) {
                // 폴더 연결은 화면이 아는 유형만 저장한다(모르는 유형 보존은 항목만)
                fieldErrors.add("targetType", new MessageRef("menu.target.type"));
                return null;
            }
            MenuNode base = new MenuNode(node.id(), MenuNode.ITEM, null, null, true, audience, null, false, false, false, type, node.targetId(),
                    node.targetKey(), node.settings(), false, List.of());
            MenuNode link = resolver.normalize(base);
            Map<String, MessageRef> found = new LinkedHashMap<>();
            resolver.validate(link, context, new MenuFieldErrors(path, found));
            String audienceField = path + ".audience";
            found.forEach((field, message) -> {
                if (!field.equals(audienceField)) {
                    errors.putIfAbsent(field, message);
                }
            });
            return link;
        }

        private MenuNode item(MenuNode node, int depth, MenuFieldErrors fieldErrors, String label, String nameEn, String audience, String minGrade) {
            if (!node.children().isEmpty()) {
                fieldErrors.add("children", new MessageRef("menu.node.children"));
            }
            if (depth > MenuLimits.ITEM_DEPTH) {
                fieldErrors.add("kind", new MessageRef("menu.node.itemDepth", String.valueOf(MenuLimits.ITEM_DEPTH)));
            }
            if (node.mobileTab() && !side) {
                fieldErrors.add("mobileTab", new MessageRef("menu.mobileTab.side"));
            }
            String type = MenuRules.stripToNull(node.targetType());
            MenuNode base = new MenuNode(node.id(), MenuNode.ITEM, label, nameEn, node.visible(), audience, minGrade, false, false, false, type, node.targetId(),
                    node.targetKey(), node.settings(), side && node.mobileTab(), List.of());
            MenuTargetResolver resolver = registry.find(type).orElse(null);
            MenuNode item;
            if (resolver != null) {
                item = resolver.normalize(base);
                fieldErrors.add("label", MenuRules.labelProblem(node.label(), resolver.labelRequired(), MenuLimits.LABEL_MAX));
                resolver.validate(item, context, fieldErrors);
                String linkId = resolver.linkId(item);
                if (linkId != null && !linked.add(linkId)) {
                    fieldErrors.add(resolver.targetField(), new MessageRef("menu.target.duplicate"));
                }
            } else {
                // 모르는 유형 — 받은 그대로 보존한다(유형 코드 모양·칸 크기만 본다)
                item = base;
                if (type == null || !MenuLimits.TARGET_TYPE.matcher(type).matches()) {
                    fieldErrors.add("targetType", new MessageRef("menu.target.type"));
                }
                fieldErrors.add("label", MenuRules.labelProblem(node.label(), false, MenuLimits.LABEL_MAX));
                if (node.targetId() != null && node.targetId().length() > MenuLimits.TARGET_VALUE_MAX) {
                    fieldErrors.add("targetId", new MessageRef("menu.target.length", String.valueOf(MenuLimits.TARGET_VALUE_MAX)));
                }
                if (node.targetKey() != null && node.targetKey().length() > MenuLimits.TARGET_VALUE_MAX) {
                    fieldErrors.add("targetKey", new MessageRef("menu.target.length", String.valueOf(MenuLimits.TARGET_VALUE_MAX)));
                }
            }
            if (item.mobileTab()) {
                tabs++;
            }
            return item;
        }
    }
}
