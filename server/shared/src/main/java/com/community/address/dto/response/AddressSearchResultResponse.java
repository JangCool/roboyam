package com.community.address.dto.response;

/**
 * 자체 주소 DB 검색 결과 한 줄 — user-web {@code AddressSearchResult} 와 같은 모양.
 *
 * @param postcode      새 우편번호 5자리
 * @param roadAddress   도로명 주소(시·도부터 건물 번호, 참고 항목 괄호까지)
 * @param jibunAddress  지번 주소(관련지번이 여럿이면 쉼표) — 지번 파일이 없던 줄은 빈 글자
 * @param buildingName  건물 이름 — 없으면 빈 글자
 * @param roadAddressEn 영문 도로명 주소 — 영문 파일을 같이 올렸을 때만, 아니면 null
 */
public record AddressSearchResultResponse(String postcode, String roadAddress, String jibunAddress, String buildingName, String roadAddressEn) {
}
