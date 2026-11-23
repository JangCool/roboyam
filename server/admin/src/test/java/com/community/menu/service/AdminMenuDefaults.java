package com.community.menu.service;

import com.community.menu.dto.AdminMenuNode;
import java.util.List;
import java.util.stream.Stream;

/** 시험용 — 관리자 메뉴 기본 구성({@link AdminScreenRegistry#defaults()})에서 묶음·부모 항목을 이름 키로 찾는다(부모 항목은 묶음 안에 있다) */
public final class AdminMenuDefaults {

    private AdminMenuDefaults() {
    }

    /** 이 모드의 최상위 묶음 이름 키(차례대로 — 낱개 화면은 null) */
    public static List<String> sections(String mode) {
        return AdminScreenRegistry.defaults().get(mode).stream().map(AdminMenuNode::labelKey).toList();
    }

    /** 묶음이나 묶음 안의 부모 항목 */
    public static AdminMenuNode group(String mode, String labelKey) {
        return AdminScreenRegistry.defaults().get(mode).stream().flatMap(AdminMenuDefaults::withChildren).filter(node -> labelKey.equals(node.labelKey())).findFirst().orElseThrow();
    }

    /** 바로 아래 화면 키(부모 항목 자리는 그 이름 키) */
    public static List<String> children(AdminMenuNode node) {
        return node.children().stream().map(child -> child.targetKey() == null ? child.labelKey() : child.targetKey()).toList();
    }

    private static Stream<AdminMenuNode> withChildren(AdminMenuNode node) {
        return Stream.concat(Stream.of(node), node.children().stream());
    }
}
