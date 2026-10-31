package com.community.bizapplication.dto;

/** 계약서 사본 열람 결과 — 사업자등록증 열람과 같은 모양({@code url} = data URL) */
public record BizContractFile(String url, String mimeType, String fileName) {

    @Override
    public String toString() {
        return "BizContractFile[" + fileName + "]";
    }
}
