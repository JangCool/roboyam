package com.community.home.service;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 기본 가젯(시드·가젯 페이지 처음 배치)이 쓰는 Tabler Icons outline 몇 개의 그림 조각 — 제목 아이콘(gadget-page-contract 「가젯 제목 아이콘」)의 {@code iconTabler} 와 같은 모양.
 * 값은 Tabler Icons 3.49.0(MIT, admin-web 의존성 {@code @tabler/icons})의 {@code tabler-nodes-outline.json} 에서 path 만 그대로 옮겼다(관리자 고르기 창 {@code UtilIconCatalog.pathOf} 와 같은 규칙).
 * 관리자가 고르는 아이콘은 이 표와 상관없다 — 여기는 서버가 만드는 기본값에 쓸 것만 둔다(2026-11-10 「가젯마다 어울리는 아이콘」).
 */
public final class GadgetTablerIcons {

    /** 인기글 — 불꽃 */
    public static final String FLAME = "flame";
    /** 영상·짤 갤러리 — 사진과 영상 */
    public static final String PHOTO_VIDEO = "photo-video";
    /** 소모임 — 여러 사람 */
    public static final String USERS_GROUP = "users-group";

    private static final Map<String, List<String>> PATHS = Map.of(
            FLAME, List.of("M12 10.941c2.333 -3.308 .167 -7.823 -1 -8.941c0 3.395 -2.235 5.299 -3.667 6.706c-1.43 1.408 -2.333 3.294 -2.333 5.588c0 3.704 3.134 6.706 7 6.706c3.866 0 7 -3.002 7 -6.706c0 -1.712 -1.232 -4.403 -2.333 -5.588c-2.084 3.353 -3.257 3.353 -4.667 2.235"),
            PHOTO_VIDEO, List.of("M9 15h-3a3 3 0 0 1 -3 -3v-6a3 3 0 0 1 3 -3h6a3 3 0 0 1 3 3v3", "M9 12a3 3 0 0 1 3 -3h6a3 3 0 0 1 3 3v6a3 3 0 0 1 -3 3h-6a3 3 0 0 1 -3 -3l0 -6",
                    "M3 12l2.296 -2.296a2.41 2.41 0 0 1 3.408 0l.296 .296", "M14 13.5v3l2.5 -1.5l-2.5 -1.5", "M7 6v.01"),
            USERS_GROUP, List.of("M10 13a2 2 0 1 0 4 0a2 2 0 0 0 -4 0", "M8 21v-1a2 2 0 0 1 2 -2h4a2 2 0 0 1 2 2v1", "M15 5a2 2 0 1 0 4 0a2 2 0 0 0 -4 0", "M17 10h2a2 2 0 0 1 2 2v1",
                    "M5 5a2 2 0 1 0 4 0a2 2 0 0 0 -4 0", "M3 13v-1a2 2 0 0 1 2 -2h2"));

    private GadgetTablerIcons() {
    }

    /** 이 표에 있는 이름인가 */
    public static boolean isKnown(String name) {
        return name != null && PATHS.containsKey(name);
    }

    /**
     * 꾸미기에 Tabler 아이콘을 입힌다 — {@code iconSource} TABLER · {@code iconTabler{name, paths[{d}]}}. 표에 없는 이름이면 그대로 돌려준다.
     * PrimeIcons 이름({@code icon})은 건드리지 않는다(출처를 PRIME 으로 되돌리면 그 아이콘이 보인다).
     */
    public static ObjectNode apply(ObjectNode style, String name) {
        List<String> paths = PATHS.get(name);
        if (paths == null) {
            return style;
        }
        ObjectNode tabler = HomeLayoutJson.object().put("name", name);
        ArrayNode list = tabler.putArray("paths");
        paths.forEach(d -> list.addObject().put("d", d));
        style.put("iconSource", GadgetStyles.ICON_TABLER);
        style.set("iconTabler", tabler);
        return style;
    }
}
