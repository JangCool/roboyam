#!/usr/bin/env bash
# =============================================================================
# init-env.sh — 개발 서버의 .env 를 처음 한 번 만든다 (비밀은 사람이 입력하지 않고 무작위로 만든다)
# =============================================================================
#
# 만드는 비밀(installer/roboyam-install.sh 와 같은 방식)
#   - DB 비밀번호(POSTGRES_PASSWORD 평문 — postgres 컨테이너용) + 같은 비밀번호의 암호문 DB_PASSWORD=ENC(...)(앱용)
#   - ROBOYAM_CRYPTO_KEY(DB 비밀번호 암호화) · ROBOYAM_DATA_KEY(개인정보 칸, 버전 1) · ROBOYAM_INDEX_KEY(중복 확인 해시)
#   - ROBOYAM_INSTALL_TOKEN(브라우저 /install 첫 화면에 넣는다)
# 암호문은 앱 이미지 안의 SecretCipherTool 로 만든다 → 앱 이미지가 ECR 에 먼저 올라가 있어야 한다(release.sh --push-only).
#
# 키 백업: 기본으로 비밀 4개를 SSM Parameter Store(/roboyam/dev/*, SecureString)에 복사한다 — 서버를 잃어도 키가 남는다.
#   ROBOYAM_DATA_KEY · ROBOYAM_INDEX_KEY 를 잃으면 DB 백업이 있어도 개인정보 칸을 영영 읽을 수 없다.
#
# 사용법(서버에서, 처음 한 번):
#   /opt/roboyam/dev/scripts/init-env.sh \
#     --domain dev.example.com --admin-domain admin.dev.example.com \
#     --registry 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com --tag 20261027-1200-abc1234 \
#     --backup-s3 s3://roboyam-dev-backup-123456789012/roboyam-dev
#
# 옵션
#   --domain <도메인>        공개 사이트 도메인(DEV_DOMAIN)
#   --admin-domain <도메인>  관리자 앱 도메인(ADMIN_DOMAIN)
#   --registry <호스트>      ECR 레지스트리(<계정>.dkr.ecr.<리전>.amazonaws.com)
#   --tag <태그>             처음 띄울 이미지 태그(release.sh 가 출력한다)
#   --backup-s3 <s3://...>   백업을 올릴 S3 위치(BACKUP_S3_URI)
#   --no-ssm                 SSM Parameter Store 에 키를 복사하지 않는다(직접 안전한 곳에 백업할 때)
#
# 이미 .env 가 있으면 아무것도 하지 않는다(키를 새로 만들면 기존 데이터를 읽을 수 없다).
# ⚠ 실제 서버에서 아직 시험하지 않았다(문법만 확인).
# =============================================================================
set -Eeuo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

DOMAIN="" ADMIN_DOMAIN="" REGISTRY="" TAG="" BACKUP_S3="" USE_SSM=1
while [ $# -gt 0 ]; do
    case "$1" in
        --domain)       DOMAIN=${2:-}; shift 2 ;;
        --admin-domain) ADMIN_DOMAIN=${2:-}; shift 2 ;;
        --registry)     REGISTRY=${2:-}; shift 2 ;;
        --tag)          TAG=${2:-}; shift 2 ;;
        --backup-s3)    BACKUP_S3=${2:-}; shift 2 ;;
        --no-ssm)       USE_SSM=0; shift ;;
        -h|--help)      sed -n '/^# 사용법/,/^# 이미/p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *)              fail "알 수 없는 옵션: $1 (도움말: $0 --help)" ;;
    esac
done
need() { [ -n "$2" ] || fail "$1 값이 필요합니다 (도움말: $0 --help)"; }
need --domain "$DOMAIN"; need --admin-domain "$ADMIN_DOMAIN"; need --registry "$REGISTRY"; need --tag "$TAG"; need --backup-s3 "$BACKUP_S3"
case "$BACKUP_S3" in s3://*) ;; *) fail "--backup-s3 는 s3:// 로 시작해야 합니다: $BACKUP_S3" ;; esac
[ "$DOMAIN" != "$ADMIN_DOMAIN" ] || fail "공개 사이트와 관리자 앱은 다른 도메인이어야 합니다(세션 쿠키가 도메인별로 갈린다)."

[ ! -e "$ENV_FILE" ] || fail "$ENV_FILE 이 이미 있습니다. 키를 새로 만들면 기존 데이터를 읽을 수 없으므로 덮어쓰지 않습니다."
[ -f "$DEV_DIR/compose.yml" ] || fail "$DEV_DIR/compose.yml 이 없습니다. 로컬에서 release.sh 를 먼저 돌려 파일을 올리세요."
command -v openssl > /dev/null || fail "openssl 이 없습니다."

API_IMAGE="$REGISTRY/roboyam-api:$TAG"
ADMIN_IMAGE="$REGISTRY/roboyam-admin:$TAG"

echo "[1/4] 앱 이미지 받기 ($API_IMAGE)"
ecr_login "$REGISTRY"
docker pull -q "$API_IMAGE" > /dev/null || fail "이미지를 받지 못했습니다: $API_IMAGE (release.sh 로 올렸는지, 태그가 맞는지 확인)"

echo "[2/4] 비밀 만들기"
DB_PLAIN=$(openssl rand -hex 24)
CRYPTO_KEY=$(openssl rand -base64 32)
DATA_KEY=$(openssl rand -base64 32)
INDEX_KEY=$(openssl rand -base64 32)
INSTALL_TOKEN=$(openssl rand -hex 16)
for k in "$CRYPTO_KEY" "$DATA_KEY" "$INDEX_KEY"; do [ "${#k}" -eq 44 ] || fail "키 생성에 실패했습니다(openssl rand -base64 32 가 44자가 아님)."; done

# 평문은 표준 입력으로만 넘긴다(인자 · 로그에 남지 않는다). 키는 환경 변수 이름만 넘겨 docker 가 값을 읽게 한다
DB_ENC=$(printf '%s' "$DB_PLAIN" | ROBOYAM_CRYPTO_KEY="$CRYPTO_KEY" docker run --rm -i -e ROBOYAM_CRYPTO_KEY --entrypoint java "$API_IMAGE" \
    -Dloader.main=com.community.zconfiguration.crypto.SecretCipherTool \
    -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher encrypt 2> /dev/null | tr -d '\r') || true
case "$DB_ENC" in 'ENC(v1:'*')') ;; *) fail "DB 비밀번호 암호화에 실패했습니다(앱 이미지의 SecretCipherTool)." ;; esac

echo "[3/4] $ENV_FILE 쓰기"
umask 077
cat > "$ENV_FILE" <<EOF
# 개발 서버 환경 변수 — init-env.sh 가 만들었다($(date '+%Y-%m-%d %H:%M')). 항목 설명은 .env.example 을 본다.
# 이 파일은 서버 밖으로 꺼내지 않는다. 키 백업은 SSM Parameter Store(/roboyam/dev/*)에 있다(--no-ssm 이 아니었다면).
DEV_DOMAIN=$DOMAIN
ADMIN_DOMAIN=$ADMIN_DOMAIN
ROBOYAM_IMAGE=$API_IMAGE
ROBOYAM_ADMIN_IMAGE=$ADMIN_IMAGE
DB_USERNAME=roboyam
POSTGRES_PASSWORD=$DB_PLAIN
DB_PASSWORD=$DB_ENC
ROBOYAM_CRYPTO_KEY=$CRYPTO_KEY
ROBOYAM_DATA_KEY=1:$DATA_KEY
ROBOYAM_DATA_KEY_PREVIOUS=
ROBOYAM_INDEX_KEY=$INDEX_KEY
NODE_ID=1
ADMIN_NODE_ID=2
ROBOYAM_INSTALL_TOKEN=$INSTALL_TOKEN
ADMIN_URL=https://$ADMIN_DOMAIN
PUBLIC_URL=https://$DOMAIN
JAVA_OPTS=-Xmx1g
ADMIN_JAVA_OPTS=-Xmx512m
BACKUP_S3_URI=${BACKUP_S3%/}
EOF
chmod 600 "$ENV_FILE"

echo "[4/4] 키 백업"
if [ "$USE_SSM" = 1 ]; then
    region=$(printf '%s' "$REGISTRY" | sed -n 's/^[0-9]*\.dkr\.ecr\.\([a-z0-9-]*\)\.amazonaws\.com$/\1/p')
    tmp=$(mktemp)
    trap 'rm -f "$tmp"' EXIT
    for pair in "ROBOYAM_CRYPTO_KEY=$CRYPTO_KEY" "ROBOYAM_DATA_KEY=1:$DATA_KEY" "ROBOYAM_INDEX_KEY=$INDEX_KEY" "POSTGRES_PASSWORD=$DB_PLAIN"; do
        name=${pair%%=*}
        printf '%s' "${pair#*=}" > "$tmp"   # 값은 파일로 넘긴다(명령 인자로 프로세스 목록에 보이지 않게)
        if aws ssm put-parameter --region "$region" --name "/roboyam/dev/$name" --type SecureString \
                --value "file://$tmp" > /dev/null 2>&1; then
            note "SSM /roboyam/dev/$name 저장"
        else
            note "⚠ SSM /roboyam/dev/$name 저장 실패(이미 있거나 권한 없음) — 이 키는 직접 백업하세요"
        fi
    done
else
    note "SSM 백업을 건너뜀 — $ENV_FILE 의 ROBOYAM_*_KEY 를 서버 밖 안전한 곳에 직접 백업하세요"
fi

echo
echo "완료. 다음:"
echo "  1) /opt/roboyam/dev/scripts/deploy.sh $TAG      # 띄우기"
echo "  2) 브라우저 https://$DOMAIN/install 에서 설치 마법사 — 설치 토큰: $INSTALL_TOKEN"
