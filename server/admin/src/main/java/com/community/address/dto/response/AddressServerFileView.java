package com.community.address.dto.response;

import java.time.Instant;

/** 서버 폴더의 주소 DB 파일 하나(이름만 — 서버 경로는 내보내지 않는다) */
public record AddressServerFileView(String name, long size, Instant modifiedAt) {
}
