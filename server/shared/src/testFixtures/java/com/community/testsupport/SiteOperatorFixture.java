package com.community.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 시험용 운영 주체 행({@code site_operator}, V159 — 사업자 정보의 정본). 커머스 시험이 사업자 정보를 채우거나 비울 때 쓴다(1행 표라 시험이 끝나면 {@link #clear} 로 되돌린다).
 */
public final class SiteOperatorFixture {

    private SiteOperatorFixture() {
    }

    /** 개인사업자 + 시험 사업자 정보(등록번호 체크섬이 맞는 번호, 통신판매업 신고번호·기관 포함) — 판매 켜기 자동 조건 BUSINESS_INFO·OPERATOR_STAGE 가 찬다 */
    public static void fillBusiness(JdbcTemplate jdbc) {
        jdbc.update("UPDATE site_operator SET stage = 'SOLE_PROPRIETOR', operator_name = '홍길동', contact_email = 'help@test.local', phone = '02-000-0000', "
                + "privacy_officer_same = true, privacy_officer_name = NULL, privacy_officer_email = NULL, hosting_provider = '시험 호스팅', business_name = '시험상회', "
                + "representative = '홍길동', registration_number = '123-45-67891', address = '서울특별시 강남구 1', mail_order_number = '2026-서울-0001', "
                + "mail_order_authority = '서울특별시 강남구', corporate_number = NULL WHERE id = 1");
    }

    /** 처음 값(개인 단계, 빈 칸) */
    public static void clear(JdbcTemplate jdbc) {
        jdbc.update("UPDATE site_operator SET stage = 'INDIVIDUAL', operator_name = '', contact_email = '', phone = NULL, privacy_officer_same = true, "
                + "privacy_officer_name = NULL, privacy_officer_email = NULL, hosting_provider = NULL, business_name = NULL, representative = NULL, "
                + "registration_number = NULL, address = NULL, mail_order_number = NULL, mail_order_authority = NULL, corporate_number = NULL WHERE id = 1");
    }
}
