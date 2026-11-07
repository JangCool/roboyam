package com.community.rewardevent.dto;

/**
 * 보상 줄(admin-web {@code RewardLine}) — 요청·응답 같은 모양. 종류마다 쓰는 칸이 다르다(안 쓰는 칸은 null). 요청의 {@code id} 는 화면이 붙인 임시 값이어도 된다(서버가 새 번호를 준다),
 * 품목·팩의 이름·종류·사용 방식·기간은 서버가 품목·팩에서 다시 채운다.
 */
public record RewardLineDto(String id, String kind, Integer points, String itemId, String itemName, String itemKind, String itemUsage, Integer itemDurationDays,
                            Integer quantity, String packId, String packName, String manualName, String manualMethod) {
}
