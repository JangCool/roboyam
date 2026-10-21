-- 초기 스키마. 테이블은 개발 단계별로 V2__... 로 추가한다 (docs/data-model.md).
--
-- pg_trgm: 글 검색(제목+본문+작성자)에 쓴다 (docs/decisions.md 검색).
-- RDS 에서는 마스터 사용자(rds_superuser)가 만들 수 있다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
