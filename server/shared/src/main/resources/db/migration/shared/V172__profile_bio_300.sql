-- 자기소개를 한 줄 100자에서 줄바꿈 포함 300자로 늘린다(사용자 2026-11-08). 서버 검사 MyProfileService.BIO_MAX = 300
ALTER TABLE profiles ALTER COLUMN bio TYPE varchar(300);
