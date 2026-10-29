# AWS 개발 서버 구축 절차 (1단계: EC2 한 대)

EC2 한 대에 공개 사이트(app)·관리자 앱(admin)·PostgreSQL·Caddy(HTTPS)를 docker compose로 띄운다.
사용자가 늘면 [docker/prod](../prod/compose.yml) 구성(ALB + 앱 서버 2대 + RDS)으로 넘어간다. 앱 이미지와 환경 변수 이름은 같다.

> ⚠ 이 절차와 스크립트는 실제 AWS에서 아직 끝까지 시험하지 않았다(스크립트 문법만 확인). 처음 따라 할 때 막히는 곳은 이 문서를 고친다.

## 구성과 비용

| 항목 | 값 |
|---|---|
| 리전 | 서울 `ap-northeast-2` |
| 서버 | EC2 `t4g.medium`(Graviton arm64, 2 vCPU · 4GB) · Ubuntu 24.04 · gp3 30GB · 스왑 2GB |
| 주소 | 탄력적 IP 1개 → Route 53 A 레코드 2개(공개 사이트, 관리자 앱) |
| 이미지 | ECR `roboyam-api` · `roboyam-admin` (20개 넘으면 오래된 것부터 지움) |
| 백업 | 매일 04:00 `pg_dump` + 올린 이미지 → S3(30일 보관) / EBS 스냅샷 매일(7개 보관) |
| 비밀 | 서버 `/opt/roboyam/dev/.env`(권한 600) + 키 사본은 SSM Parameter Store `/roboyam/dev/*` |
| 메일 | Amazon SES SMTP |

예상 비용은 **월 $36.89**(AWS 가격 계산기, 2026-10-27 서울 온디맨드 — [견적](https://calculator.aws/#/estimate?id=67de7ed23004679ac5e7367f4c3b4fb0ce8375bf)). 부가세 10%를 더하면 약 5.7만 원이다(1,400원/$ 기준). Route 53(월 $0.5~)과 SES(1천 통당 $0.10)는 빠져 있다. 석 달 사용량을 본 뒤 1년 Compute Savings Plan으로 바꾸면 월 약 $27.5([견적](https://calculator.aws/#/estimate?id=8d1093cf69426f2598a1ecc3c0795f18b32a63d8))다.

## 파일

| 파일 | 어디서 | 하는 일 |
|---|---|---|
| `release.sh` | 로컬 PC | 화면·jar·arm64 이미지 빌드 → ECR 업로드 → 서버에 파일 복사 → 서버에서 `deploy.sh` |
| `scripts/server-setup.sh` | 서버, 처음 한 번 | Docker·AWS CLI·스왑·시간대·자동 보안 업데이트·로그 크기 제한·백업 cron |
| `scripts/init-env.sh` | 서버, 처음 한 번 | 비밀을 무작위로 만들어 `.env` 작성, 키를 SSM에 백업 |
| `scripts/deploy.sh` | 서버 | 태그를 바꿔 띄우고 기동 확인(되돌리기도 같은 명령) |
| `scripts/backup.sh` | 서버, 매일 cron | DB·올린 이미지를 S3에 백업 |
| `scripts/restore.sh` | 서버 | 백업으로 DB(와 올린 이미지) 되살리기 |
| `aws/*.json` | AWS 콘솔·CloudShell | IAM 정책, S3·ECR 수명 주기 규칙 |

---

## 1. 로컬 PC 준비

- pnpm(의존성 설치됨), JDK(`./gradlew`), Docker Desktop, Git Bash(Windows)
- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) 설치 후 `aws --version`이 되는지 확인
- 도메인 하나(예: `example.com`). 아래에서는 공개 사이트 `dev.example.com`, 관리자 앱 `admin.dev.example.com`으로 쓴다.

## 2. AWS 계정 기본 (콘솔)

1. **루트 계정에 MFA를 건다.** 루트 액세스 키는 만들지 않는다.
2. IAM에서 작업용 사용자(예: `jang-admin`)를 만들어 `AdministratorAccess`를 주고 MFA를 건다. 이후 콘솔은 이 사용자로 쓴다.
3. 콘솔 오른쪽 위 리전을 **아시아 태평양(서울)**으로 둔다.
4. **Billing → Budgets**에서 월 예산 `$45`, 실제 비용 80%·100%에 메일 알림을 건다.
5. 신규 계정 크레딧이 있으면 **Billing → Credits**에서 남은 금액과 만료일을 확인한다.

아래 CLI 명령은 콘솔 위쪽의 **CloudShell**(브라우저 안 터미널, 작업용 사용자 권한)에서 돌린다. 로컬 PC에 관리자 키를 두지 않기 위해서다.

## 3. ECR 저장소 · S3 백업 버킷 (CloudShell)

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=ap-northeast-2
BUCKET=roboyam-dev-backup-$ACCOUNT_ID

for repo in roboyam-api roboyam-admin; do
  aws ecr create-repository --region $REGION --repository-name $repo --image-scanning-configuration scanOnPush=true
  aws ecr put-lifecycle-policy --region $REGION --repository-name $repo --lifecycle-policy-text '{"rules":[{"rulePriority":1,"description":"keep last 20 images","selection":{"tagStatus":"any","countType":"imageCountMoreThan","countNumber":20},"action":{"type":"expire"}}]}'
done

aws s3api create-bucket --region $REGION --bucket $BUCKET --create-bucket-configuration LocationConstraint=$REGION
aws s3api put-public-access-block --bucket $BUCKET --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-lifecycle-configuration --bucket $BUCKET --lifecycle-configuration '{"Rules":[{"ID":"expire-backups-30d","Status":"Enabled","Filter":{},"Expiration":{"Days":30},"AbortIncompleteMultipartUpload":{"DaysAfterInitiation":7}}]}'

echo "ECR: $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com   버킷: $BUCKET"
```

출력된 ECR 주소와 버킷 이름을 적어 둔다. 수명 주기 규칙 원본은 `aws/ecr-lifecycle.json` · `aws/s3-backup-lifecycle.json`이다.

## 4. IAM: 서버 역할 · 배포용 사용자 (콘솔)

**서버 역할 `roboyam-dev-ec2`** — 서버가 AWS 키 없이 ECR·S3·SSM을 쓰게 한다.
1. IAM → 역할 → 역할 생성 → 신뢰할 엔터티: AWS 서비스 / **EC2**
2. 권한은 건너뛰고 이름 `roboyam-dev-ec2`로 만든다.
3. 만든 역할 → 권한 추가 → **인라인 정책 생성** → JSON에 [`aws/ec2-role-policy.json`](aws/ec2-role-policy.json)을 붙여넣는다. `ACCOUNT_ID`(12자리)와 `BACKUP_BUCKET`(3번의 버킷 이름)을 바꾼다. 정책 이름은 `roboyam-dev-ec2`.
4. (선택) 브라우저에서 서버 터미널을 열고 싶으면 관리형 정책 `AmazonSSMManagedInstanceCore`도 붙인다(Session Manager).

**배포용 사용자 `roboyam-release`** — 로컬 PC의 `release.sh`가 ECR에 이미지를 올릴 때만 쓴다.
1. IAM → 사용자 생성 `roboyam-release`(콘솔 접근 없음)
2. 인라인 정책으로 [`aws/release-user-policy.json`](aws/release-user-policy.json)을 붙인다(`ACCOUNT_ID`를 바꾼다).
3. 보안 자격 증명 → 액세스 키 만들기(CLI 용도)
4. 로컬 PC에서 `aws configure --profile roboyam-release`를 실행한다(리전 `ap-northeast-2`, 출력 `json`).

## 5. EC2 서버 만들기 (콘솔)

1. EC2 → **키 페어** 생성: `roboyam-dev`, `.pem`. 받은 파일을 `~/.ssh/roboyam-dev.pem`에 둔다.
2. EC2 → **보안 그룹** 생성 `roboyam-dev-web`
   - 인바운드: HTTP 80 · HTTPS 443 = `0.0.0.0/0` (Caddy 인증서 발급에 80도 필요)
   - 인바운드: SSH 22 = **내 IP**만 (집·사무실 IP가 바뀌면 여기를 고친다)
3. EC2 → **인스턴스 시작**
   - 이름 `roboyam-dev`, AMI **Ubuntu Server 24.04 LTS**, 아키텍처 **64비트(Arm)**
   - 인스턴스 유형 **t4g.medium**, 키 페어 `roboyam-dev`, 보안 그룹 `roboyam-dev-web`
   - 스토리지 **30 GiB gp3**
   - 고급 세부 정보 → IAM 인스턴스 프로파일 **`roboyam-dev-ec2`**, 메타데이터 버전 **V2만**(기본값)
   - 태그 `Backup` = `roboyam-dev` 추가(7번 스냅샷 정책이 이 태그로 고른다)
4. EC2 → **탄력적 IP** 할당 → 작업 → `roboyam-dev` 인스턴스에 연결. 이 IP를 적어 둔다.
   - 인스턴스를 지우면 탄력적 IP도 꼭 해제한다. 붙어 있지 않은 IP도 요금이 나간다.

## 6. 도메인 (Route 53)

- 도메인을 Route 53에서 샀으면 호스팅 영역이 이미 있다. 다른 곳에서 샀으면 Route 53에 호스팅 영역을 만들고, 그 NS 4개를 도메인 판매처에 넣는다.
- 호스팅 영역에 **A 레코드 2개**를 만든다: `dev.example.com` → 탄력적 IP, `admin.dev.example.com` → 탄력적 IP
- 확인: 로컬에서 `nslookup dev.example.com`이 탄력적 IP를 돌려준다.

## 7. EBS 스냅샷 자동화 (콘솔)

EC2 → **수명 주기 관리자(Data Lifecycle Manager)** → 정책 생성 → EBS 스냅샷 정책
- 대상: 인스턴스, 태그 `Backup` = `roboyam-dev`
- 일정: 매일 1회(예: 05:00 UTC = 14:00 한국시간, DB 백업과 겹치지 않게), **보관 7개**

디스크 통째 사본이라 서버를 잃었을 때 가장 빨리 되살린다(12번).

## 8. 서버 준비 (처음 한 번)

로컬 PC(Git Bash, 저장소 루트)에서:

```bash
scp -i ~/.ssh/roboyam-dev.pem docker/dev/scripts/server-setup.sh ubuntu@<탄력적IP>:~
ssh -i ~/.ssh/roboyam-dev.pem ubuntu@<탄력적IP> 'sudo bash ~/server-setup.sh'
```

끝나면 다시 접속해 `docker ps`가 sudo 없이 되고 `aws sts get-caller-identity`가 `roboyam-dev-ec2` 역할을 보여 주는지 확인한다.

## 9. 첫 배포

**① 로컬 설정 파일** `docker/dev/.env.release`(저장소에 들어가지 않는다):

```bash
ROBOYAM_ECR=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com
ROBOYAM_EC2_HOST=ubuntu@<탄력적IP>
ROBOYAM_EC2_KEY=~/.ssh/roboyam-dev.pem
AWS_PROFILE=roboyam-release
```

**② 이미지를 만들어 올린다**(배포는 아직 안 함). 마지막 줄에 태그가 나온다.

```bash
docker/dev/release.sh --push-only
```

**③ 서버에서 `.env`를 만든다**(비밀은 무작위로 만들어지고 키 사본은 SSM에 저장된다).

```bash
ssh -i ~/.ssh/roboyam-dev.pem ubuntu@<탄력적IP>
/opt/roboyam/dev/scripts/init-env.sh \
  --domain dev.example.com --admin-domain admin.dev.example.com \
  --registry 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com --tag <②의 태그> \
  --backup-s3 s3://roboyam-dev-backup-123456789012/roboyam-dev
```

마지막에 나오는 **설치 토큰**을 적어 둔다.

**④ 띄운다**(처음에는 Caddy가 HTTPS 인증서를 받느라 1~2분 더 걸린다).

```bash
/opt/roboyam/dev/scripts/deploy.sh <②의 태그>
```

**⑤ 설치 마법사:** 브라우저에서 `https://dev.example.com/install`을 열고 설치 토큰을 넣는다. 메일 서버는 11번 SES 값을 쓴다(SES 준비 전이면 나중에 관리자 화면의 메일 설정에서 바꾼다).

**⑥ 백업이 되는지 한 번 확인한다.**

```bash
/opt/roboyam/dev/scripts/backup.sh
aws s3 ls s3://roboyam-dev-backup-123456789012/roboyam-dev/db/
```

소셜 로그인을 쓰면 공급자 콘솔에 콜백 주소 `https://dev.example.com/api/auth/social/{kakao|naver|google}/callback`을 등록한다.

**관리자 테마 화면의 「실제 화면」**(관리자 앱이 공개 사이트를 iframe으로 띄운다)에 필요한 값은 둘이고, 화면 빌드에는 주소를 넣지 않는다(이미지는 환경마다 한 벌).

| 값 | 어디에 | 하는 일 |
|---|---|---|
| `ADMIN_URL` | 서버 `.env`(`init-env.sh`가 `https://<관리자 도메인>`으로 채운다) | 공개 사이트 앱이 `?adminPreview=1` 화면 응답에만 `Content-Security-Policy: frame-ancestors 'self' <이 origin>`을 붙이고 `X-Frame-Options`를 뺀다. 같은 응답 머리에 이 주소를 넣어 공개 화면이 그 주소의 메시지만 받는다. 비우면 「실제 화면」이 뜨지 않는다 |
| 사이트 공개 주소 | ⑤ 설치 마법사에서 정한 값(DB `site_settings.public_url`) | 관리자 화면이 iframe으로 띄울 공개 사이트 주소. 정하지 않았으면 「실제 화면」 자리에 안내만 보인다 |

그 밖의 화면·API·관리자 앱 응답은 지금처럼 `X-Frame-Options: DENY`다. 확인: `curl -sI "https://dev.example.com/?adminPreview=1" | grep -i -E 'frame|content-security'`. 개발 서버(`pnpm dev`)는 `NUXT_PUBLIC_USER_WEB_ORIGIN`(관리자 화면, 기본 `http://localhost:3100`)·`NUXT_PUBLIC_ADMIN_ORIGINS`(공개 화면, 기본 `http://localhost:3200,http://localhost:3201`)를 쓰고, 빌드한 화면은 이 둘을 비워 위 서버 값을 쓴다.

## 10. 평소 운영

| 하려는 일 | 명령 |
|---|---|
| 새 버전 배포 | 로컬: `docker/dev/release.sh` |
| 되돌리기 | 로컬: `docker/dev/release.sh --deploy <예전 태그>` (태그 목록: 서버에서 `deploy.sh --history`) |
| compose·Caddyfile만 바꿈 | 로컬: `docker/dev/release.sh --files-only` 후 서버: `deploy.sh`(태그 없이) |
| 로그 보기 | 서버: `cd /opt/roboyam/dev && docker compose logs -f --tail 100 app` |
| 손 백업 | 서버: `/opt/roboyam/dev/scripts/backup.sh` (배포 전·위험한 작업 전) |
| 백업 기록 | 서버: `tail /opt/roboyam/backups/backup.log` |

- 커밋하지 않은 상태로 `release.sh`를 돌리면 태그 끝에 `-dirty`가 붙는다. 그 이미지는 커밋으로 다시 만들 수 없으니 되도록 커밋한 뒤 배포한다.
- 보안 업데이트는 서버가 알아서 받는다(unattended-upgrades). 커널 업데이트 뒤 재부팅이 필요하면 접속할 때 `*** System restart required ***`가 보인다. 한가한 시간에 `sudo reboot`하면 컨테이너는 자동으로 다시 뜬다(`restart: unless-stopped`).

## 11. 메일 (Amazon SES)

1. SES(서울) → **자격 증명 생성** → 도메인 `example.com`. Route 53을 쓰면 DKIM 레코드가 자동으로 들어간다. "확인됨"이 될 때까지 기다린다.
2. SES → **SMTP 설정** → SMTP 자격 증명 생성. 사용자 이름과 비밀번호를 받는다(한 번만 보여 준다).
3. 설치 마법사나 관리자 화면의 메일 설정에 넣는다: 호스트 `email-smtp.ap-northeast-2.amazonaws.com`, 포트 `587`(STARTTLS), 보내는 주소 `no-reply@example.com`
4. 처음에는 **샌드박스**라 확인된 주소로만 보낼 수 있다. SES → 계정 대시보드 → **프로덕션 액세스 요청**(사용 목적: 가입 인증·비밀번호 재설정 메일)을 낸다. 승인까지 하루쯤 걸린다.

## 12. 서버를 잃었을 때

**A. 서버(디스크)가 망가졌다 → EBS 스냅샷으로 되살린다** (가장 빠름, 마지막 스냅샷 시점까지)
1. EC2 → 스냅샷에서 가장 최근 `roboyam-dev` 스냅샷 → **이미지(AMI) 생성** → 그 AMI로 5번과 같은 설정의 인스턴스를 시작한다.
2. 탄력적 IP를 새 인스턴스로 옮긴다. DNS는 그대로 둔다.
3. 스냅샷 이후의 데이터가 필요하면 B의 `restore.sh`로 그날 04:00 DB 백업을 덮어쓴다(둘 중 더 최근 것을 쓴다).

**B. 새 서버에 DB 백업만 되살린다**
1. 5·8·9번(①~④)으로 새 서버를 만든다. `init-env.sh`가 SSM 저장 실패 경고를 내는 것은 정상이다(예전 키가 이미 있다).
2. **새 `.env`의 `ROBOYAM_DATA_KEY` · `ROBOYAM_INDEX_KEY`를 예전 값으로 바꾼다.** 이 둘이 다르면 개인정보 칸을 읽을 수 없다.
   ```bash
   aws ssm get-parameter --region ap-northeast-2 --name /roboyam/dev/ROBOYAM_DATA_KEY --with-decryption --query Parameter.Value --output text
   aws ssm get-parameter --region ap-northeast-2 --name /roboyam/dev/ROBOYAM_INDEX_KEY --with-decryption --query Parameter.Value --output text
   ```
   나온 값을 `/opt/roboyam/dev/.env`에 넣는다.
3. 되살린다:
   ```bash
   aws s3 ls s3://<버킷>/roboyam-dev/db/
   /opt/roboyam/dev/scripts/restore.sh s3://<버킷>/roboyam-dev/db/roboyam-<날짜>.dump --media s3://<버킷>/roboyam-dev/media/media-<날짜>.tar.gz
   /opt/roboyam/dev/scripts/deploy.sh
   ```

**복원 연습:** 실제 사용자를 받기 전에 한 번 B를 해 본다. 같은 서버에서 `restore.sh <오늘 백업>`만 돌려도 절차를 확인할 수 있다.

## 13. 2단계로 넘어갈 때

순서대로 하나씩 한다(자세한 주의점은 [docker/prod/compose.yml](../prod/compose.yml) 머리말).
1. **DB → RDS PostgreSQL**: `backup.sh`의 덤프를 RDS에 `pg_restore`하고 `.env`의 `DB_URL`을 RDS 주소로 바꾼다. RDS는 로캘(UTF-8)과 pg_cron 파라미터 그룹을 만들 때 정한다. compose에서 postgres 서비스는 뺀다.
2. **올린 이미지 → EFS**: 서버가 2대가 되기 전에 `MEDIA_DIR`을 공유 디스크로 바꾼다.
3. **ALB + ACM + 앱 서버 2대**: prod compose로 옮기고 Caddy는 뺀다. 서버마다 `NODE_ID`가 달라야 한다.
