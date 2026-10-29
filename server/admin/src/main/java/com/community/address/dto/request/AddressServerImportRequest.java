package com.community.address.dto.request;

import java.util.List;

/**
 * 서버 폴더의 파일로 적재 — 검사는 서비스가 한다({@code fields.fileNames}·{@code fields.charset}).
 *
 * @param fileNames 서버 폴더 목록({@code GET /api/admin/address/server-files})에 있는 이름 1~2개
 * @param charset   {@code MS949} | {@code UTF-8}
 */
public record AddressServerImportRequest(List<String> fileNames, String charset) {
}
