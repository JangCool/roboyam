# installer-native — 운영 서버 원스톱 설치 (Docker 없이)

AWS 서울 EC2 3대(Rocky Linux 9, aarch64)에 로보얌을 **yum/dnf + systemd** 로 올린다.
**DB 서버에서 한 번 실행하면** DB 를 먼저 설치하고, 앱 서버 두 대에 SSH 로 묶음(jar·스크립트·인증서)을 보내 풀고 설치·기동까지 한다.
Docker 설치형(`installer/`)·compose(`docker/`)와 별개다.

> ⚠ 실서버에서 아직 시험하지 않았다(`bash -n` 문법 검사만). 처음은 `--dry-run` 으로 점검부터 한다.

## 구성

```
방문자 ─HTTPS→ Cloudflare (DNS·CDN·로드밸런싱·방문자 인증서, SSL Full (strict))
                 ├─ roboyam.com ──→ 로드밸런싱 풀 [WAS1, WAS2]   상태 확인 /actuator/health/readiness
                 └─ admin.roboyam.com ──→ WAS1
WAS1 (t4g.small, 공인 IP)  Caddy:443 → api :8080 (NODE_ID 1) + admin :8081 (NODE_ID 3)
WAS2 (t4g.small, 공인 IP)  Caddy:443 → api :8080 (NODE_ID 2)
DB   (t4g.medium, 공인 IP) PostgreSQL 18 + pg_cron :5432 — 원스톱 스크립트를 여기서 돌린다
                           └─ SSH(사설 IP) → WAS1, WAS2
```

## 접속 경로 — bastion 없음 (사용자 결정 2026-11-09)

- **세 대 모두 탄력적 IP(고정 공인 IP)** 를 붙인다. WAS 는 Cloudflare 가 들어오는 주소라 꼭 필요하고, DB 는 내 PC 에서 바로 SSH 로 들어가고 `dnf`(설치·보안 업데이트)·R2 백업 업로드로 밖에 나가려고 붙인다.
- 공인 IP 가 있어도 아래 보안 그룹이 내 IP·앱 서버 말고는 다 막는다. bastion 서버·NAT 게이트웨이(월 $40 이상)는 쓰지 않는다.
- 원스톱 스크립트는 DB 서버 안에서 **VPC 사설 IP** 로 앱 서버에 붙는다(`install.conf` 에는 WAS 의 사설 IP).
- 비용: 공인 IPv4 개당 시간당 $0.005 ≈ **월 $3.6**, 3개 ≈ 월 $11. 2025-07-15 이후 만든 계정은 크레딧(6개월) 방식이라 크레딧이 남은 동안은 크레딧에서 빠진다(콘솔 › 결제 및 비용 관리 › 프리 티어에서 확인).
- EC2 의 퍼블릭 DNS 이름(`ec2-…compute.amazonaws.com`)은 공인 IP 의 별명이라 공인 IP 없이는 생기지 않는다.
- 나중에 22 를 닫고 싶으면: EC2 Instance Connect Endpoint(무료, 들어가는 길만)나 SSM Session Manager.

SSH 키: 앱 서버용 `.pem` 을 DB 서버 `/root/.ssh/` 에 두거나(600), 키를 서버에 두기 싫으면 `ssh -A` 로 들어와 `sudo --preserve-env=SSH_AUTH_SOCK ./roboyam-install.sh` 로 에이전트를 넘긴다(`install.conf` 의 `SSH_KEY` 를 비운다).

## 실행 순서

1. **묶음 만들기** — 로컬 PC(저장소 루트, Git Bash):
   ```bash
   installer-native/make-package.sh --build
   ```
   → `installer-native/dist/roboyam-native-<커밋>-<날짜>.tar.gz` (스크립트 + `application/` jar 2개)
2. **DB 서버로 올리고 풀기**
   ```bash
   scp installer-native/dist/roboyam-native-*.tar.gz rocky@<DB 공인 IP>:
   ssh rocky@<DB 공인 IP>
   tar xzf roboyam-native-*.tar.gz && cd roboyam-native
   cp install.conf.example install.conf && vi install.conf      # 앱 서버 사설 IP·도메인·SSH 키·이미지 저장소(R2)
   # certs/ 에 Cloudflare Origin CA 인증서 origin.pem · origin.key 를 넣는다
   ```
   R2 는 그 전에 만들어 둔다(아래 「올린 이미지」).
3. **점검 → 설치**
   ```bash
   sudo ./roboyam-install.sh --dry-run
   sudo ./roboyam-install.sh
   ```
   DB 비밀번호(와 `S3_ACCESS_KEY` 를 줬으면 S3 비밀 키)를 한 번 묻는다. 그다음은 자동이다:
   `[1/3]` DB 설치 → `[2/3]` WAS1 묶음 전송·설치·admin→api 기동(readiness 확인) → DB 배치 등록 확인 → `[3/3]` WAS1 의 `roboyam.env` 를 WAS2 로 넘겨 설치·기동 → 앱 서버의 임시 폴더 삭제.
   끝에 설치 마법사 주소·토큰이 나온다. **WAS1 의 `/etc/roboyam/roboyam.env` 를 서버 밖에 백업**한다.
4. **Cloudflare** — SSL 「Full (strict)」, 로드밸런싱 풀(WAS1·WAS2 공인 IP, 상태 확인 `GET /actuator/health/readiness` 200, Host `roboyam.com`), `admin` 은 WAS1.
5. **배포(이후)** — 새 묶음을 DB 서버에 풀고:
   ```bash
   sudo ./roboyam-install.sh --deploy
   ```
   DB 백업 → WAS1 admin → WAS1 api → WAS2 api 를 한 대씩 바꾸고 재기동한다. readiness 가 안 오면 그 서버는 이전 jar 로 되돌리고 거기서 멈춘다(DB 이관은 되돌리지 않는다).

한 단계만 다시: `--only db | was1 | was2`. 서버 한 대에서 손으로 하려면 각 스크립트를 직접 쓴다(아래 표, `--help`).

| 파일 | 하는 일 |
|---|---|
| `roboyam-install.sh` | **원스톱** — DB 서버에서 설치·배포 전체 |
| `make-package.sh` | 로컬에서 묶음 만들기(`--build` 로 빌드까지) |
| `install.conf.example` | 설정 예시 |
| `db/roboyam-db-install.sh` | DB: PGDG PostgreSQL 18·pg_cron, C.UTF-8, 메모리 자동 계산, pg_hba(앱 서버만), 앱 계정(superuser 아님)·확장·`cron` 권한, 백업 cron |
| `db/roboyam-db-backup.sh` · `backup-hook.example.sh` | 백업(`pg_dump -Fc`, 최근 N개) · R2 업로드 훅 예시 |
| `app/roboyam-app-install.sh` | 앱: Temurin 25, systemd(api·admin), 환경 파일(`DB_PASSWORD=ENC(...)`·이미지 저장소 `ROBOYAM_STORAGE_*`), Caddy(Origin CA·Cloudflare IP 만), 스왑 |
| `app/roboyam-deploy.sh` | 앱 서버에서 jar 교체 → 재시작 → readiness → 실패 시 되돌리기 |
| `lib/common.sh` | 공통 함수 |

## 올린 이미지 — Cloudflare R2 (S3 호환)

운영(prod) 기본 저장소는 **S3 호환 버킷**이다(`ROBOYAM_STORAGE_TYPE=s3`). 앱 서버 두 대와 관리자 앱이 같은 버킷을 읽고 쓰므로 WAS1 에서 올린 이미지를 WAS2 도 보인다.
같은 설정으로 AWS S3·MinIO 로 바꿀 수 있다(endpoint·region 만 다르다). 설정·내보내기 방식은 `docs/features/media-storage-contract.md`.

1. **버킷** — Cloudflare › R2 › 버킷 만들기(예 `roboyam-media`, 위치 자동 또는 APAC).
2. **공개 주소** — 버킷 › 설정 › 사용자 도메인에 `media.roboyam.com` 연결(Cloudflare CDN 이 캐시한다 — 객체에 1년 immutable 캐시 머리글이 붙는다). `r2.dev` 주소는 속도 제한이 있어 운영에 쓰지 않는다.
3. **API 토큰** — R2 › API 토큰 관리 › 「객체 읽기 및 쓰기」, **이 버킷만**. Access Key ID 는 `install.conf` 의 `S3_ACCESS_KEY`, Secret Access Key 는 설치할 때 입력한다(WAS1 에서 `ENC(...)` 로 암호화).
4. `install.conf`: `S3_ENDPOINT=https://<계정 ID>.r2.cloudflarestorage.com`, `S3_REGION=auto`, `S3_BUCKET`, `S3_PUBLIC_URL=https://media.roboyam.com`.

앱은 설정이 틀리면(버킷·지역 없음, 키 한쪽만) **뜨지 않는다**(로그에 칸 이름). 버킷에 닿지 못하는 것은 경고만 남기고 뜬 뒤 올리기·읽기가 503 이다.
앱은 `/media/**` 도 계속 연다 — 공개 주소 없이 쓰거나, 예전에 글 본문에 저장된 `/media/…` 주소를 버킷에서 읽어 준다.
서버 한 대 구성이면 `STORAGE=local`(앱 서버의 `/var/lib/roboyam/media`).

**나중에 값 바꾸기**: WAS1 의 `/etc/roboyam/roboyam.env` 의 `ROBOYAM_STORAGE_*` 를 고쳐 WAS2 에도 같게 복사하고 두 서버의 앱을 재기동한다. 비밀 키 암호문 만들기:
```bash
key="$(sudo sed -n 's/^ROBOYAM_CRYPTO_KEY=//p' /etc/roboyam/roboyam.env)"
sudo env ROBOYAM_CRYPTO_KEY="$key" java -cp /opt/roboyam/api/current.jar -Dloader.main=com.community.zconfiguration.crypto.SecretCipherTool \
    org.springframework.boot.loader.launch.PropertiesLauncher encrypt; unset key
```
(값은 프롬프트로 받는다. env 파일은 `ENC(...)` 괄호 때문에 셸로 `source` 하지 않는다.)

**로컬 폴더 → 버킷 옮기기**(로컬 저장소로 운영하다 바꿀 때 한 번): 키(폴더 경로)가 같으므로 폴더를 그대로 올리면 DB 를 고칠 것이 없다. 앱 서버마다(이미지를 올린 서버 모두) [rclone](https://rclone.org/s3/#cloudflare-r2) 으로:
```bash
rclone copy /var/lib/roboyam/media r2:roboyam-media --exclude '.upload-*' --header-upload 'Cache-Control: public, max-age=31536000, immutable' --progress
```
그 뒤 `ROBOYAM_STORAGE_TYPE=s3` 로 바꾸고 재기동. 옮긴 뒤 새 이미지 주소는 공개 주소(`https://media…`)이고, 예전 글 본문의 `/media/…` 는 앱이 버킷에서 읽어 준다.

메모리(자동): DB 2GB → shared_buffers 512MB·effective_cache_size 1536MB·work_mem 4MB, 4GB → 1GB·3GB·5MB.
앱 2GB 서버 → api+admin 768m/384m, api 만 1024m.

## 보안 그룹

| 서버 | 포트 | 출처 |
|---|---|---|
| WAS1·WAS2 | 443 | **Cloudflare IP 대역만**(https://www.cloudflare.com/ips/) |
| WAS1·WAS2 | 22 | **DB 서버 보안 그룹**(원스톱 스크립트) + 내 IP(문제 볼 때) |
| DB | 5432 | **WAS1·WAS2 보안 그룹만** |
| DB | 22 | **내 IP 만** |

R2 는 보안 그룹이 아니라 API 토큰으로 막는다 — 토큰은 **이 버킷의 객체 읽기·쓰기만**(계정 전체·관리자 권한 금지), 비밀 키는 `roboyam.env` 에 `ENC(...)` 로만 둔다.
AWS S3 로 옮기면 키 대신 **EC2 IAM 역할**(그 버킷의 `s3:GetObject`·`PutObject`·`DeleteObject`·`ListBucket`)을 붙이고 `ROBOYAM_STORAGE_S3_ACCESS_KEY`·`SECRET_KEY` 를 비운다.

80 은 열지 않는다. Caddy 도 Cloudflare 가 아닌 접속은 끊고, `/actuator` 는 readiness·liveness 만 내보낸다.
Cloudflare IP 가 바뀌면 앱 서버에서 `sudo bash app/roboyam-app-install.sh --refresh-cloudflare-ips` + 보안 그룹 갱신.

## 운영 전 확인

- DB: `sudo -u postgres psql -d roboyam -c "select cardinality(show_trgm('한글')) > 0"` → `t`, `select jobname, schedule, active from cron.job`, 관리자 › 배치에서 DB 배치가 「사용 불가」가 아닌지.
- 앱: `NODE_ID` 가 1·2·3 으로 서로 다른지, 두 서버 `roboyam.env` 가 같은지(`sudo sha256sum /etc/roboyam/roboyam.env`).
- 접속 수: 2 × 20 + 5 + 여유 < 200.
- 백업: `sudo /opt/roboyam/db/bin/roboyam-db-backup.sh` 로 R2 업로드 확인, **복원 연습** 한 번, EBS 스냅샷(DLM)도 따로.
- 방문자 IP: 로그·로그인 잠금에 실제 IP 가 찍히는지(Caddy 가 `X-Forwarded-For` 를 방문자 IP 하나로 넘긴다 — `roboyam.proxy.trusted-hops: 1`).
- **올린 이미지**: 기동 로그에 `이미지 저장소: S3 호환 버킷 bucket=…` 이 찍히고 「버킷에 닿지 못했다」 경고가 없는지. 관리자 화면에서 로고를 바꾸고 WAS1·WAS2 양쪽(로드밸런싱으로 번갈아)에서 보이는지, 이미지 주소가 `https://media…` 인지, 그 주소의 응답에 `cf-cache-status: HIT`(두 번째부터)가 붙는지. `STORAGE=local` 로 두 대를 돌리면 이미지가 깨진다.
- aarch64 지원: PostgreSQL 18·pg_cron·TimescaleDB 는 PGDG, Java 25 는 Adoptium, Caddy 는 COPR `@caddy/caddy` 로 설치한다.
- TimescaleDB 는 기본 미설치(지금 앱이 쓰지 않는다). 켤 때는 `install.conf` 에 `DB_EXTRA_ARGS="--timescale"` — PGDG `timescaledb_18` 을 설치하고, 검증에서 판(`timescaledb.license` = timescale 이면 압축·연속 집계 정책까지)을 알려 준다. Timescale 자체 저장소는 el9 aarch64 가 비어 있어 쓰지 않는다.
