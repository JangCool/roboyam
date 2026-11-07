package com.community.popup.dto;

import java.time.Instant;

/**
 * 검사·정리를 마친 팝업 칸({@link com.community.popup.service.SitePopups#check}) — 만들기·고치기가 엔티티에 그대로 넣는다.
 * 글자 칸은 앞뒤 공백을 뗐고 비면 null 이다. 지금 종류가 아닌 칸도 그대로 담는다(종류를 되돌리면 다시 쓰인다).
 *
 * @param imageFileId 그림 파일 ID(형식만 본 값 — 올려 둔 파일인지는 저장하는 쪽이 확인한다)
 * @param showPc      PC(폭 768px 이상)에 보인다
 * @param showMobile  모바일에 보인다 — 둘 중 하나는 참
 */
public record SitePopupFields(String name, String kind, Long imageFileId, String imageAlt, String link, String heading, String body, String buttonLabel,
                              String target, boolean showPc, boolean showMobile, String hideOption, boolean visible, Instant showFrom, Instant showUntil) {
}
