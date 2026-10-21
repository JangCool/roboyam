-- grade_rules 정리 — V9__grades.sql 이 등급 설정(grades)으로 데이터를 옮긴 뒤 이 표를 읽는 곳이 없다
-- (서버 코드·이후 마이그레이션 모두 참조하지 않고, 이 표를 가리키는 외래키·뷰도 없다). 데이터는 grades 에 이미 있다.
DROP TABLE grade_rules;
