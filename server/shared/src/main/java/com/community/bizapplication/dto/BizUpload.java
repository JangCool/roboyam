package com.community.bizapplication.dto;

/** 올린 파일 하나(계약서 사본) — 이름은 표시용, 형식은 내용 첫 바이트로 판정한다 */
public record BizUpload(String originalName, byte[] data) {

    @Override
    public String toString() {
        return "BizUpload[name=" + originalName + ", bytes=" + (data == null ? 0 : data.length) + "]";
    }
}
