package com.community.address.dto.response;

import com.community.address.dto.AddressProvider;

/**
 * 회원 화면 우편번호 찾기 설정 — 비밀값이 없어 로그인 없이 준다.
 *
 * @param scriptUrl 다음 우편번호 스크립트 주소(관리자가 비워 두면 공식 주소)
 */
public record AddressSearchConfigResponse(AddressProvider provider, String scriptUrl) {
}
