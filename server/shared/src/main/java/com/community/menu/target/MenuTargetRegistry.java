package com.community.menu.target;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 등록된 연결 유형(리졸버) 모음 — Spring 이 모아 준 {@link MenuTargetResolver} 를 유형 코드로 찾는다.
 * 모르는 유형(뒤 버전이 만든 행, 코드에서 뺀 유형)은 {@link #find} 가 비어 있고 부르는 쪽이 공개 응답에서 뺀다. 같은 유형 코드가 둘이면 기동을 막는다.
 */
@Component
public class MenuTargetRegistry {

    /** 화면 차례(admin-web {@code UtilMenu.TARGET_TYPES}) */
    private static final List<String> ORDER = List.of("BOARD", "BUILTIN", "GADGET_PAGE", "PATH", "URL");

    /** 준비 중 유형 — 서버 리졸버가 없고 화면에 흐린 줄로만 보인다(고정 페이지는 CMS 기능이 생길 때) */
    public static final List<String> SOON_TYPES = List.of("PAGE");

    private final Map<String, MenuTargetResolver> byType;
    private final List<MenuTargetResolver> ordered;

    public MenuTargetRegistry(List<MenuTargetResolver> resolvers) {
        Map<String, MenuTargetResolver> map = new LinkedHashMap<>();
        for (MenuTargetResolver resolver : resolvers) {
            if (map.put(resolver.type(), resolver) != null) {
                throw new IllegalStateException("메뉴 연결 유형 코드가 겹친다: " + resolver.type());
            }
        }
        List<MenuTargetResolver> sorted = new ArrayList<>(map.values());
        sorted.sort((a, b) -> Integer.compare(rank(a.type()), rank(b.type())));
        this.byType = Map.copyOf(map);
        this.ordered = List.copyOf(sorted);
    }

    /** 유형 코드로 찾는다 — 모르는 유형이면 비어 있다 */
    public Optional<MenuTargetResolver> find(String type) {
        return type == null ? Optional.empty() : Optional.ofNullable(byType.get(type));
    }

    /** 등록된 모든 리졸버(화면 차례) */
    public List<MenuTargetResolver> all() {
        return ordered;
    }

    private static int rank(String type) {
        int index = ORDER.indexOf(type);
        return index < 0 ? ORDER.size() : index;
    }
}
