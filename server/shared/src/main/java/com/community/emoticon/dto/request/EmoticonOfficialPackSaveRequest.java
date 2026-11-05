package com.community.emoticon.dto.request;

import java.util.List;

/**
 * 운영 팩 만들기·고치기 본문 — admin-web {@code EmoticonOfficialPackSaveRequest}. 로보티콘은 {@code id}(이 팩의 로보티콘 ID 또는 올린 그림 ID)를 차례대로, 이름표와 함께.
 * 대표 이미지 {@code coverId} 는 지금 대표의 그림 ID 또는 올린 대표 이미지 ID(필수). 검사는 서비스가 모은다.
 */
public record EmoticonOfficialPackSaveRequest(String name, String description, List<String> tags, List<Item> items, String coverId) {

    public record Item(String id, String alt) {
    }
}
