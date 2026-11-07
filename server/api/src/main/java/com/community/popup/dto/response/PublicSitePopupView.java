package com.community.popup.dto.response;

import java.util.List;

/**
 * 공개 팝업 한 장 — user-web {@code PublicSitePopup} 과 같은 칸. 지금 종류가 아닌 칸은 null(IMAGE 면 제목·내용·단추, TEXT 면 그림·대체 글).
 *
 * @param version 내용 표식 — 보이는 칸이 바뀌면 바뀐다(「보지 않기」 기록이 있어도 다시 보인다)
 * @param devices {@code PC}·{@code MOBILE}
 */
public record PublicSitePopupView(String id, String version, String kind, String imageUrl, String imageAlt, String link, String heading, String body,
                                  String buttonLabel, String target, List<String> devices, String hideOption) {
}
