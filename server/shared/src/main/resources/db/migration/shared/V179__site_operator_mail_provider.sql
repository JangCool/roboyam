-- 운영 주체에 「메일 발송 사업자」 칸을 더한다(terms-operator-contract, 2026-11-10) — 개인정보처리방침 처리 위탁 항목의 자리표시 {{메일발송사업자}} 가 이 값으로 바뀐다.
--   · 선택 칸(최대 50자, 예: Amazon Web Services (Amazon SES)). 빈 칸이면 null — 본문에 그 자리표시를 쓴 약관은 게시를 거절한다(관리자 서버 TermsAdminService).
--   · 호스팅 사업자(hosting_provider)와 같은 모양. 형식 검사는 관리자 서버(SiteOperatorService)가 한다.

ALTER TABLE site_operator
    ADD COLUMN mail_provider varchar(50);
