package com.community.address.dto.response;

import java.util.List;

/**
 * 서버 폴더에서 적재 — 고를 수 있는 파일.
 *
 * @param enabled 서버 폴더가 설정돼 있는가({@code ROBOYAM_ADDRESS_IMPORT_DIR}). false 면 화면은 이 방법을 보이지 않는다
 */
public record AddressServerFilesView(boolean enabled, List<AddressServerFileView> files) {
}
