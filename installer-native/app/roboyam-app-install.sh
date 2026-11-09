#!/usr/bin/env bash
# ============================================================================
# 로보얌 앱 서버 설치 — Java 25 + api(·admin) jar + systemd + Caddy (Docker 없이)
#
# 대상   : Rocky Linux 9 (aarch64 — EC2 t4g), root
# 구성   : WAS1 = api + admin (--with-admin)   WAS2 = api 만
# 만드는 것
#   계정      roboyam (로그인 없음) — 두 앱이 이 계정으로 돈다
#   Java      Eclipse Temurin 25 JRE (Adoptium 저장소 — 서버 빌드의 javaVersion=25, 운영 이미지 eclipse-temurin:25-jre 와 같은 배포판)
#   jar       /opt/roboyam/<앱>/current.jar → releases/… (roboyam-deploy.sh 가 바꾼다)
#   환경 파일 /etc/roboyam/roboyam.env  두 앱·두 서버 공통(DB 접속·암호화 키 — prod compose 와 같은 변수 이름)
#             /etc/roboyam/api.env      서버마다 다른 값(NODE_ID·포트·풀 크기·-Xmx)
#             /etc/roboyam/admin.env    관리자 앱 값(ADMIN_NODE_ID·포트·풀 크기·-Xmx)
#   systemd   roboyam-api (127.0.0.1:8080) · roboyam-admin (127.0.0.1:8081) — 자동 재시작
#   Caddy     443 만 연다. Cloudflare Origin CA 인증서(자동 인증서 끔), 호스트별 api/admin 분기,
#             Cloudflare IP 가 아닌 직접 접속은 끊고, /actuator 는 readiness·liveness 만 내보낸다
#   스왑      --swap 을 주면 /swapfile
#
# 사용법 (WAS1 — 처음 설치, 공통 환경 파일을 새로 만든다)
#   ./roboyam-app-install.sh --with-admin --node-id 1 --admin-node-id 3 \
#       --api-jar roboyam-api.jar --admin-jar roboyam-admin.jar \
#       --db-host 10.0.1.20 --domain roboyam.com --domain www.roboyam.com --admin-domain admin.roboyam.com \
#       --origin-cert origin.pem --origin-key origin.key --swap 2G
# 사용법 (WAS2 — WAS1 의 /etc/roboyam/roboyam.env 를 복사해 와서)
#   ./roboyam-app-install.sh --node-id 2 --shared-env roboyam.env --api-jar roboyam-api.jar \
#       --domain roboyam.com --domain www.roboyam.com --origin-cert origin.pem --origin-key origin.key
# Cloudflare IP 대역만 새로 받아 Caddy 에 반영
#   ./roboyam-app-install.sh --refresh-cloudflare-ips
#
# 옵션
#       --node-id N            이 서버 api 의 Snowflake 번호(0~1023, 서버마다 다르게 — 필수)
#       --with-admin           관리자 앱도 이 서버에 설치한다
#       --admin-node-id N      관리자 앱의 Snowflake 번호 (기본 3 — api 서버들과 달라야 한다)
#       --api-jar FILE         api jar (이미 설치돼 있으면 생략 가능)
#       --admin-jar FILE       admin jar (--with-admin 이고 이미 설치돼 있으면 생략 가능)
#       --shared-env FILE      공통 환경 파일을 이 파일로 둔다(WAS2 — WAS1 것을 그대로 복사)
#       --db-host IP           공통 환경 파일을 새로 만들 때 DB 서버 사설 IP
#       --db-name NAME         (기본 roboyam)    --db-user NAME (기본 roboyam)
#       --domain HOST          공개 사이트 호스트(여러 번 — 첫 값이 PUBLIC_URL)
#       --admin-domain HOST    관리자 앱 호스트(ADMIN_URL — --with-admin 이면 Caddy 에도 연다)
#       --admin-allow CIDR     관리자 앱을 이 방문자 IP 대역에서만 연다(여러 번, 선택)
#       --origin-cert FILE     Cloudflare Origin CA 인증서(PEM)   --origin-key FILE  그 개인 키
#       --api-xmx SIZE         api 힙 (기본: 메모리에 맞춰 — 2GB 서버: admin 같이 768m / api 만 1024m)
#       --admin-xmx SIZE       admin 힙 (기본: 2GB 서버 384m)
#       --api-pool N           api DB_POOL_SIZE (기본 20)   --admin-pool N  ADMIN 풀 (기본 5)
#       --swap SIZE            스왑 파일 크기(예 2G). 이미 있으면 건드리지 않는다
#       --no-caddy             Caddy 를 설치·설정하지 않는다
#       --cf-ips-file FILE     Cloudflare IP 대역을 인터넷 대신 이 파일(줄마다 CIDR)에서 읽는다
#       --refresh-cloudflare-ips  Cloudflare IP 대역만 다시 받아 Caddy 를 다시 읽힌다
#       --no-start             설치만 하고 앱을 띄우지 않는다
#       --dry-run              계획만 보여 주고 끝낸다
#   -h, --help                 도움말
#
# 비밀값
#   공통 환경 파일을 새로 만들 때만 만든다: ROBOYAM_CRYPTO_KEY · ROBOYAM_DATA_KEY(1:…) · ROBOYAM_INDEX_KEY · ROBOYAM_INSTALL_TOKEN
#   DB 비밀번호는 프롬프트(무인: 환경 변수 ROBOYAM_DB_PASSWORD)로 받아 jar 안의 SecretCipherTool 로 DB_PASSWORD=ENC(...) 로 둔다.
#   이미 /etc/roboyam/roboyam.env 가 있으면 그대로 둔다(다시 실행해도 키가 바뀌지 않는다).
# ============================================================================
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/common.sh
. "${SCRIPT_DIR}/../lib/common.sh"
trap on_error ERR

# ----------------------------------------------------------------------------
# 기본값
# ----------------------------------------------------------------------------
NODE_ID=""
WITH_ADMIN=0
ADMIN_NODE_ID=3
API_JAR=""
ADMIN_JAR=""
SHARED_ENV_SRC=""
DB_HOST=""
DB_NAME=roboyam
DB_USER=roboyam
DOMAINS=()
ADMIN_DOMAIN=""
ADMIN_ALLOW=()
ORIGIN_CERT=""
ORIGIN_KEY=""
API_XMX=""
ADMIN_XMX=""
API_POOL=20
ADMIN_POOL=5
SWAP_SIZE=""
WITH_CADDY=1
CF_IPS_FILE=""
REFRESH_CF_ONLY=0
START_APPS=1
DRY_RUN=0

JAVA_PKG_CANDIDATES=(temurin-25-jre-headless temurin-25-jre)
API_PORT=8080
ADMIN_PORT=8081

BASE_DIR=/opt/roboyam
ENV_DIR=/etc/roboyam
SHARED_ENV="${ENV_DIR}/roboyam.env"
MEDIA_DIR=/var/lib/roboyam/media
ADDRESS_IMPORT_DIR=/var/lib/roboyam/address-import
CADDY_DIR=/etc/caddy
CADDY_CERT_DIR="${CADDY_DIR}/certs"
CF_ENV="${CADDY_DIR}/cloudflare-ips.env"
LOG_FILE="/var/log/roboyam-app-install-${STAMP}.log"

usage() { usage_from_header "$0"; }
usage_error() { printf '[사용법 오류] %s\n\n' "$*" >&2; usage >&2; exit 2; }

valid_node_id() { [[ "$1" =~ ^[0-9]+$ ]] && [ "$1" -le 1023 ]; }
valid_host() { [[ "$1" =~ ^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$ ]]; }
valid_size() { [[ "$1" =~ ^[0-9]+[mMgG]$ ]]; }

abs_path() { echo "$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"; }

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --node-id)        NODE_ID="${2:?--node-id 값이 필요합니다}"; shift 2 ;;
            --with-admin)     WITH_ADMIN=1; shift ;;
            --admin-node-id)  ADMIN_NODE_ID="${2:?--admin-node-id 값이 필요합니다}"; shift 2 ;;
            --api-jar)        API_JAR="${2:?--api-jar 값이 필요합니다}"; shift 2 ;;
            --admin-jar)      ADMIN_JAR="${2:?--admin-jar 값이 필요합니다}"; shift 2 ;;
            --shared-env)     SHARED_ENV_SRC="${2:?--shared-env 값이 필요합니다}"; shift 2 ;;
            --db-host)        DB_HOST="${2:?--db-host 값이 필요합니다}"; shift 2 ;;
            --db-name)        DB_NAME="${2:?--db-name 값이 필요합니다}"; shift 2 ;;
            --db-user)        DB_USER="${2:?--db-user 값이 필요합니다}"; shift 2 ;;
            --domain)         DOMAINS+=("${2:?--domain 값이 필요합니다}"); shift 2 ;;
            --admin-domain)   ADMIN_DOMAIN="${2:?--admin-domain 값이 필요합니다}"; shift 2 ;;
            --admin-allow)    ADMIN_ALLOW+=("${2:?--admin-allow 값이 필요합니다}"); shift 2 ;;
            --origin-cert)    ORIGIN_CERT="${2:?--origin-cert 값이 필요합니다}"; shift 2 ;;
            --origin-key)     ORIGIN_KEY="${2:?--origin-key 값이 필요합니다}"; shift 2 ;;
            --api-xmx)        API_XMX="${2:?--api-xmx 값이 필요합니다}"; shift 2 ;;
            --admin-xmx)      ADMIN_XMX="${2:?--admin-xmx 값이 필요합니다}"; shift 2 ;;
            --api-pool)       API_POOL="${2:?--api-pool 값이 필요합니다}"; shift 2 ;;
            --admin-pool)     ADMIN_POOL="${2:?--admin-pool 값이 필요합니다}"; shift 2 ;;
            --swap)           SWAP_SIZE="${2:?--swap 값이 필요합니다}"; shift 2 ;;
            --no-caddy)       WITH_CADDY=0; shift ;;
            --cf-ips-file)    CF_IPS_FILE="${2:?--cf-ips-file 값이 필요합니다}"; shift 2 ;;
            --refresh-cloudflare-ips) REFRESH_CF_ONLY=1; shift ;;
            --no-start)       START_APPS=0; shift ;;
            --dry-run)        DRY_RUN=1; shift ;;
            -h|--help)        usage; exit 0 ;;
            *)                usage_error "알 수 없는 옵션: $1" ;;
        esac
    done

    [ -z "$CF_IPS_FILE" ] || { [ -f "$CF_IPS_FILE" ] || usage_error "--cf-ips-file 파일이 없습니다: ${CF_IPS_FILE}"; }
    [ "$REFRESH_CF_ONLY" -eq 1 ] && return 0

    valid_node_id "$NODE_ID" || usage_error "--node-id 는 0~1023 숫자여야 합니다(서버마다 다르게)."
    if [ "$WITH_ADMIN" -eq 1 ]; then
        valid_node_id "$ADMIN_NODE_ID" || usage_error "--admin-node-id 는 0~1023 숫자여야 합니다."
        [ "$ADMIN_NODE_ID" != "$NODE_ID" ] || usage_error "--admin-node-id 가 --node-id 와 같습니다(Snowflake ID 가 겹친다)."
    fi
    [[ "$DB_NAME" =~ ^[a-z_][a-z0-9_]*$ ]] || usage_error "--db-name 이 올바르지 않습니다: ${DB_NAME}"
    [[ "$DB_USER" =~ ^[a-z_][a-z0-9_]*$ ]] || usage_error "--db-user 가 올바르지 않습니다: ${DB_USER}"
    [ -z "$DB_HOST" ] || is_ipv4 "$DB_HOST" || valid_host "$DB_HOST" || usage_error "--db-host 가 올바르지 않습니다: ${DB_HOST}"
    [[ "$API_POOL" =~ ^[0-9]+$ ]] && [[ "$ADMIN_POOL" =~ ^[0-9]+$ ]] || usage_error "--api-pool · --admin-pool 은 숫자여야 합니다."
    [ -z "$API_XMX" ] || valid_size "$API_XMX" || usage_error "--api-xmx 형식: 768m · 1g"
    [ -z "$ADMIN_XMX" ] || valid_size "$ADMIN_XMX" || usage_error "--admin-xmx 형식: 384m · 1g"
    [ -z "$SWAP_SIZE" ] || [[ "$SWAP_SIZE" =~ ^[0-9]+[MG]$ ]] || usage_error "--swap 형식: 2G · 1024M"

    local h
    for h in "${DOMAINS[@]}" ${ADMIN_DOMAIN:+"$ADMIN_DOMAIN"}; do
        valid_host "$h" || usage_error "호스트 이름이 올바르지 않습니다: ${h}"
    done
    for h in "${ADMIN_ALLOW[@]}"; do
        is_cidr4 "$h" || is_cidr6 "$h" || usage_error "--admin-allow 는 CIDR 이어야 합니다: ${h}"
    done

    [ -z "$API_JAR" ] || { [ -f "$API_JAR" ] || usage_error "--api-jar 파일이 없습니다: ${API_JAR}"; API_JAR="$(abs_path "$API_JAR")"; }
    [ -z "$ADMIN_JAR" ] || { [ -f "$ADMIN_JAR" ] || usage_error "--admin-jar 파일이 없습니다: ${ADMIN_JAR}"; ADMIN_JAR="$(abs_path "$ADMIN_JAR")"; }
    [ -z "$SHARED_ENV_SRC" ] || { [ -f "$SHARED_ENV_SRC" ] || usage_error "--shared-env 파일이 없습니다: ${SHARED_ENV_SRC}"; }
    [ "$WITH_ADMIN" -eq 1 ] || [ -z "$ADMIN_JAR" ] || usage_error "--admin-jar 는 --with-admin 과 같이 줍니다."

    if [ "$WITH_CADDY" -eq 1 ]; then
        [ "${#DOMAINS[@]}" -gt 0 ] || usage_error "--domain 이 필요합니다(Caddy 가 받을 공개 사이트 호스트)."
        [ "$WITH_ADMIN" -eq 0 ] || [ -n "$ADMIN_DOMAIN" ] || usage_error "--with-admin 이면 --admin-domain 이 필요합니다."
        if [ -z "$ORIGIN_CERT" ] || [ -z "$ORIGIN_KEY" ]; then
            [ -f "${CADDY_CERT_DIR}/origin.pem" ] && [ -f "${CADDY_CERT_DIR}/origin.key" ] ||
                usage_error "--origin-cert · --origin-key 가 필요합니다(Cloudflare Origin CA 인증서)."
        else
            [ -f "$ORIGIN_CERT" ] || usage_error "--origin-cert 파일이 없습니다: ${ORIGIN_CERT}"
            [ -f "$ORIGIN_KEY" ] || usage_error "--origin-key 파일이 없습니다: ${ORIGIN_KEY}"
        fi
    fi
}

# ----------------------------------------------------------------------------
# 힙 계산 — 두 앱이 한 서버면 합이 넘지 않게 따로 준다
#   표기 용량 G(GB) 기준: api+admin = G×384m / G×192m (2GB → 768m/384m, 4GB → 1536m/768m)
#                        api 만    = G×512m           (2GB → 1024m,     4GB → 2048m)
#   힙 밖(메타스페이스·스레드·코드 캐시)이 JVM 마다 250~350MB 더 든다 — 2GB 에 두 앱이면 --swap 을 권한다
# ----------------------------------------------------------------------------
compute_heap() {
    local g; g="$(mem_nominal_gb)"
    MEM_GB="$g"
    if [ "$WITH_ADMIN" -eq 1 ]; then
        [ -n "$API_XMX" ] || API_XMX="$((g * 384))m"
        [ -n "$ADMIN_XMX" ] || ADMIN_XMX="$((g * 192))m"
    else
        [ -n "$API_XMX" ] || API_XMX="$((g * 512))m"
    fi
    return 0
}

size_mb() {   # 768m / 1g → MB
    local v="${1,,}"
    case "$v" in
        *g) echo $(( ${v%g} * 1024 )) ;;
        *m) echo "${v%m}" ;;
    esac
}

check_heap_budget() {
    local total heap=0
    total="$(mem_total_mb)"
    heap=$(size_mb "$API_XMX")
    [ "$WITH_ADMIN" -eq 1 ] && heap=$((heap + $(size_mb "$ADMIN_XMX")))
    local jvms=1; [ "$WITH_ADMIN" -eq 1 ] && jvms=2
    local est=$((heap + jvms * 300 + 300))
    log "메모리 ${total}MB — 힙 합 ${heap}MB, 추정 사용 ${est}MB (JVM 밖 ${jvms}×300 + OS·Caddy 300)"
    if [ "$est" -gt "$total" ]; then
        if [ -z "$SWAP_SIZE" ] && ! swapon --show=NAME --noheadings 2>/dev/null | grep . > /dev/null; then
            warn "추정 사용이 메모리보다 큽니다. --swap 2G 를 주거나 --api-xmx/--admin-xmx 를 낮추세요."
        else
            warn "추정 사용이 메모리보다 큽니다(스왑으로 버팁니다). 느려지면 힙을 낮추거나 서버를 키우세요."
        fi
    fi
}

print_plan() {
    step "설치 계획"
    printf '   %-22s %s\n' "앱"            "api (NODE_ID ${NODE_ID}, 127.0.0.1:${API_PORT}, -Xmx${API_XMX}, 풀 ${API_POOL})"
    if [ "$WITH_ADMIN" -eq 1 ]; then
        printf '   %-22s %s\n' "" "admin (NODE_ID ${ADMIN_NODE_ID}, 127.0.0.1:${ADMIN_PORT}, -Xmx${ADMIN_XMX}, 풀 ${ADMIN_POOL})"
    fi
    printf '   %-22s %s\n' "Java"          "${JAVA_PKG_CANDIDATES[0]} (Adoptium)"
    if [ -f "$SHARED_ENV" ]; then
        printf '   %-22s %s\n' "공통 환경 파일" "${SHARED_ENV} — 이미 있음, 그대로 둔다"
    elif [ -n "$SHARED_ENV_SRC" ]; then
        printf '   %-22s %s\n' "공통 환경 파일" "${SHARED_ENV_SRC} 를 복사"
    else
        printf '   %-22s %s\n' "공통 환경 파일" "새로 만든다 (DB ${DB_HOST:-<--db-host 필요>}:5432/${DB_NAME}, 키 생성, DB 비밀번호 ENC)"
    fi
    if [ "$WITH_CADDY" -eq 1 ]; then
        printf '   %-22s %s\n' "Caddy 공개 사이트" "${DOMAINS[*]} → :${API_PORT}"
        [ "$WITH_ADMIN" -eq 1 ] && printf '   %-22s %s\n' "Caddy 관리자" "${ADMIN_DOMAIN} → :${ADMIN_PORT}${ADMIN_ALLOW[*]:+ (방문자 ${ADMIN_ALLOW[*]} 만)}"
        printf '   %-22s %s\n' "인증서" "${ORIGIN_CERT:-${CADDY_CERT_DIR}/origin.pem (있는 것)}"
    else
        printf '   %-22s %s\n' "Caddy" "설치하지 않음 (--no-caddy)"
    fi
    printf '   %-22s %s\n' "스왑" "${SWAP_SIZE:-만들지 않음}"
    printf '   %-22s %s\n' "앱 기동" "$([ "$START_APPS" -eq 1 ] && echo "예 (readiness 확인)" || echo "아니오 (--no-start)")"
}

# ----------------------------------------------------------------------------
# OS·Java
# ----------------------------------------------------------------------------
install_base() {
    log "기본 패키지"
    dnf install -y ca-certificates curl openssl tar gzip dnf-plugins-core
}

create_user() {
    if id roboyam > /dev/null 2>&1; then
        log "계정 roboyam : 이미 있음"
    else
        log "계정 roboyam 생성 (로그인 없음)"
        useradd --system --home-dir "$BASE_DIR" --no-create-home --shell /sbin/nologin roboyam
    fi
    mkdir -p "$BASE_DIR/bin" "$BASE_DIR/lib" "$ENV_DIR" "$MEDIA_DIR"
    chmod 755 "$BASE_DIR"
    chown root:roboyam "$ENV_DIR"; chmod 750 "$ENV_DIR"
    chown roboyam:roboyam "$MEDIA_DIR"; chmod 750 "$MEDIA_DIR"
    if [ "$WITH_ADMIN" -eq 1 ]; then
        mkdir -p "$ADDRESS_IMPORT_DIR"
        chown root:roboyam "$ADDRESS_IMPORT_DIR"; chmod 750 "$ADDRESS_IMPORT_DIR"
    fi
}

setup_swap() {
    [ -n "$SWAP_SIZE" ] || return 0
    if swapon --show=NAME --noheadings | grep . > /dev/null; then
        log "스왑 : 이미 있음 — 건드리지 않음 ($(swapon --show=NAME,SIZE --noheadings | tr '\n' ' '))"
        return 0
    fi
    log "스왑 /swapfile ${SWAP_SIZE}"
    local mb; mb=$(size_mb "${SWAP_SIZE,,}")
    fallocate -l "${mb}M" /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count="$mb" status=none
    chmod 600 /swapfile
    mkswap /swapfile > /dev/null
    swapon /swapfile
    grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap defaults 0 0' >> /etc/fstab
    # 메모리가 정말 모자랄 때만 스왑을 쓰게 한다
    printf 'vm.swappiness = 10\n' > /etc/sysctl.d/90-roboyam-swap.conf
    sysctl -q -p /etc/sysctl.d/90-roboyam-swap.conf
}

install_java() {
    if [ ! -f /etc/yum.repos.d/adoptium.repo ]; then
        log "Adoptium 저장소"
        cat > /etc/yum.repos.d/adoptium.repo <<'EOF'
[Adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/rhel/$releasever/$basearch
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
EOF
    fi
    local pkg=""
    for pkg in "${JAVA_PKG_CANDIDATES[@]}"; do
        if rpm -q "$pkg" > /dev/null 2>&1 || dnf install -y "$pkg"; then
            JAVA_PKG="$pkg"
            break
        fi
    done
    [ -n "${JAVA_PKG:-}" ] || fail "Temurin 25 를 설치하지 못했습니다(${JAVA_PKG_CANDIDATES[*]})."
    JAVA_BIN="$(rpm -ql "$JAVA_PKG" | grep -E '/bin/java$' | tail -n 1)"
    [ -x "$JAVA_BIN" ] || fail "java 실행 파일을 찾지 못했습니다(${JAVA_PKG})."
    local ver; ver="$("$JAVA_BIN" -version 2>&1)"; ver="${ver%%$'\n'*}"
    [[ "$ver" == *'"25'* ]] || fail "Java 25 가 아닙니다: ${ver}"
    log "Java : ${JAVA_BIN} (${ver})"
}

# ----------------------------------------------------------------------------
# 환경 파일
# ----------------------------------------------------------------------------
# DB 비밀번호를 jar 안의 SecretCipherTool 로 암호화한다(installer/roboyam-install.sh 와 같은 도구·방식)
encrypt_with_jar() {   # <jar> <키> — 표준 입력의 값을 ENC(...) 로
    ROBOYAM_CRYPTO_KEY="$2" "$JAVA_BIN" -Dloader.main=com.community.zconfiguration.crypto.SecretCipherTool \
        -cp "$1" org.springframework.boot.loader.launch.PropertiesLauncher encrypt 2>>"$LOG_FILE" | tail -n 1
}

any_jar() {
    if [ -n "$API_JAR" ]; then echo "$API_JAR"
    elif [ -n "$ADMIN_JAR" ]; then echo "$ADMIN_JAR"
    elif [ -L "${BASE_DIR}/api/current.jar" ]; then readlink -f "${BASE_DIR}/api/current.jar"
    elif [ -L "${BASE_DIR}/admin/current.jar" ]; then readlink -f "${BASE_DIR}/admin/current.jar"
    fi
}

write_shared_env() {
    if [ -f "$SHARED_ENV" ]; then
        log "공통 환경 파일 : 이미 있음 — 그대로 둔다 (${SHARED_ENV})"
        return 0
    fi
    if [ -n "$SHARED_ENV_SRC" ]; then
        log "공통 환경 파일 : ${SHARED_ENV_SRC} 복사"
        install -m 640 -o root -g roboyam "$SHARED_ENV_SRC" "$SHARED_ENV"
        return 0
    fi

    [ -n "$DB_HOST" ] || fail "공통 환경 파일을 새로 만들려면 --db-host 가 필요합니다(또는 --shared-env 로 WAS1 의 파일을 준다)."
    local jar; jar="$(any_jar)"
    [ -n "$jar" ] || fail "DB 비밀번호를 암호화하려면 jar 가 필요합니다(--api-jar)."

    step "공통 환경 파일 만들기"
    local crypto_key data_key index_key token pw enc
    crypto_key="$(openssl rand -base64 32)"
    data_key="$(openssl rand -base64 32)"
    index_key="$(openssl rand -base64 32)"
    token="${ROBOYAM_INSTALL_TOKEN:-$(rand_alnum 32)}"
    [ "${#crypto_key}" -eq 44 ] && [ "${#data_key}" -eq 44 ] && [ "${#index_key}" -eq 44 ] || fail "키 생성 실패(openssl rand)."
    [[ "$token" =~ ^[A-Za-z0-9._-]{8,128}$ ]] || fail "설치 토큰은 영숫자 . _ - 로 8~128자여야 합니다."

    pw="${ROBOYAM_DB_PASSWORD:-}"
    if [ -z "$pw" ]; then
        echo "   DB 서버(roboyam-db-install.sh)에서 정한 ${DB_USER} 비밀번호를 입력합니다."
        read_password_twice "DB 비밀번호" pw
    fi
    enc="$(printf '%s\n' "$pw" | encrypt_with_jar "$jar" "$crypto_key" || true)"
    pw=""; unset pw ROBOYAM_DB_PASSWORD
    [[ "$enc" == ENC\(v1:*\) ]] || fail "DB 비밀번호 암호화에 실패했습니다(jar 에 SecretCipherTool 이 있는지 확인). 로그: ${LOG_FILE}"

    local public_url="" admin_url=""
    [ "${#DOMAINS[@]}" -gt 0 ] && public_url="https://${DOMAINS[0]}"
    [ -n "$ADMIN_DOMAIN" ] && admin_url="https://${ADMIN_DOMAIN}"

    local tmp; tmp="$(mktemp "${SHARED_ENV}.XXXXXX")"
    cat > "$tmp" <<EOF
# 로보얌 앱 공통 환경 — 두 앱(api·admin)·두 서버(WAS1·WAS2)가 같은 값을 쓴다 (roboyam-app-install.sh 가 만든다)
# 변수 이름은 docker/prod/compose.yml · compose.admin.yml 과 같다. 셸로 source 하지 않는다(ENC(...) 의 괄호).
# 서버마다 다른 값(NODE_ID·포트·풀·힙)은 api.env · admin.env 에 있다.
# ⚠ 아래 키를 잃으면 복구할 수 없는 값이 있다 — 이 파일을 서버 밖 안전한 곳(비밀번호 관리자 등)에 따로 백업한다.

SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
DB_USERNAME=${DB_USER}
# 암호문 — 아래 ROBOYAM_CRYPTO_KEY 로 기동 때 푼다
DB_PASSWORD=${enc}
ROBOYAM_CRYPTO_KEY=${crypto_key}

# 개인정보 칸 암호화 키(<버전>:<base64>) · 키 교체 중 옛 키 · 중복 확인 해시 키(바꾸지 않는다)
ROBOYAM_DATA_KEY=1:${data_key}
ROBOYAM_DATA_KEY_PREVIOUS=
ROBOYAM_INDEX_KEY=${index_key}

# 설치 마법사(/install) 토큰 — 여러 서버가 같은 값. 설치가 끝나면 쓰이지 않는다
ROBOYAM_INSTALL_TOKEN=${token}

PUBLIC_URL=${public_url}
ADMIN_URL=${admin_url}

# 올린 이미지 폴더 — 이 서버의 로컬 디스크다. 서버 두 대가 같은 파일을 보려면 R2 저장소(후속)가 필요하다
ROBOYAM_STORAGE_PATH=${MEDIA_DIR}

# (선택) 필요하면 여기에 더한다 — 예: TURNSTILE_SECRET_KEY=
EOF
    chown root:roboyam "$tmp"; chmod 640 "$tmp"
    mv -f "$tmp" "$SHARED_ENV"
    NEW_SHARED_ENV=1
    log "공통 환경 파일 : ${SHARED_ENV} (DB_PASSWORD=ENC(...), 키 3개·설치 토큰 생성)"
}

write_app_env() {   # <api|admin>
    local app="$1" file="${ENV_DIR}/$1.env" node port pool xmx
    if [ "$app" = api ]; then node="$NODE_ID"; port="$API_PORT"; pool="$API_POOL"; xmx="$API_XMX"
    else node="$ADMIN_NODE_ID"; port="$ADMIN_PORT"; pool="$ADMIN_POOL"; xmx="$ADMIN_XMX"; fi
    local java_opts="-Xmx${xmx} -XX:+ExitOnOutOfMemoryError -XX:+UseStringDeduplication"

    if [ -f "$file" ]; then
        log "${file} : 값 갱신 (NODE_ID ${node}, 포트 ${port}, 풀 ${pool}, -Xmx${xmx})"
        env_set "$file" NODE_ID "$node"
        env_set "$file" SERVER_PORT "$port"
        env_set "$file" DB_POOL_SIZE "$pool"
        env_set "$file" JAVA_OPTS "$java_opts"
        return 0
    fi
    log "${file} 생성"
    {
        echo "# 로보얌 ${app} — 이 서버만의 값 (roboyam-app-install.sh 가 만든다. 공통 값은 roboyam.env)"
        echo "# Snowflake ID 서버 번호 — api 서버들·관리자 앱이 모두 달라야 한다"
        echo "NODE_ID=${node}"
        echo "# 127.0.0.1 에서만 받는다 — 바깥은 Caddy(443)가 받는다"
        echo "SERVER_ADDRESS=127.0.0.1"
        echo "SERVER_PORT=${port}"
        echo "# 서버 2대 × api 풀 + 관리자 풀 < DB max_connections(200)"
        echo "DB_POOL_SIZE=${pool}"
        echo "JAVA_OPTS=${java_opts}"
        if [ "$app" = admin ]; then
            echo "# 주소 DB 적재 폴더(행정안전부 zip — 설정 › 주소 검색)"
            echo "ROBOYAM_ADDRESS_IMPORT_DIR=${ADDRESS_IMPORT_DIR}"
        fi
    } > "$file"
    chown root:roboyam "$file"; chmod 640 "$file"
}

# ----------------------------------------------------------------------------
# systemd
# ----------------------------------------------------------------------------
write_unit() {   # <api|admin>
    local app="$1" unit="/etc/systemd/system/roboyam-$1.service"
    mkdir -p "${BASE_DIR}/${app}/releases"
    chown root:roboyam "${BASE_DIR}/${app}" "${BASE_DIR}/${app}/releases"
    log "systemd ${unit}"
    cat > "$unit" <<EOF
# 로보얌 ${app} — roboyam-app-install.sh 가 만든다. jar 교체는 ${BASE_DIR}/bin/roboyam-deploy.sh ${app} <jar>
[Unit]
Description=Roboyam ${app}
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=roboyam
Group=roboyam
WorkingDirectory=${BASE_DIR}/${app}
EnvironmentFile=${SHARED_ENV}
EnvironmentFile=${ENV_DIR}/${app}.env
ExecStart=${JAVA_BIN} \$JAVA_OPTS -jar ${BASE_DIR}/${app}/current.jar
# SIGTERM 으로 끝나면 143 — 정상 종료로 본다. graceful shutdown(30초)이 끝날 시간을 준다
SuccessExitStatus=143
TimeoutStopSec=45
Restart=always
RestartSec=5
LimitNOFILE=65536
UMask=0027

NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=full
ProtectHome=yes
ReadWritePaths=${MEDIA_DIR}

[Install]
WantedBy=multi-user.target
EOF
}

install_deploy_tool() {
    install -m 755 "${SCRIPT_DIR}/roboyam-deploy.sh" "${BASE_DIR}/bin/roboyam-deploy.sh"
    install -m 644 "${SCRIPT_DIR}/../lib/common.sh" "${BASE_DIR}/lib/common.sh"
    ln -sfn "${BASE_DIR}/bin/roboyam-deploy.sh" /usr/local/bin/roboyam-deploy
}

# ----------------------------------------------------------------------------
# Caddy
# ----------------------------------------------------------------------------
install_caddy() {
    if rpm -q caddy > /dev/null 2>&1; then
        log "Caddy : 이미 설치됨 ($(caddy version 2>/dev/null | head -n 1))"
        return 0
    fi
    log "Caddy 설치 (COPR @caddy/caddy)"
    dnf copr enable -y @caddy/caddy ||
        fail "COPR @caddy/caddy 를 켜지 못했습니다. https://caddyserver.com/docs/install 를 보고 손으로 설치한 뒤 다시 실행하세요."
    dnf install -y caddy
}

# Cloudflare IP 대역 — Caddy 가 이 대역에서 온 접속만 받고 CF-Connecting-IP 를 방문자 IP 로 믿는다
fetch_cloudflare_ips() {
    local raw
    if [ -n "$CF_IPS_FILE" ]; then
        raw="$(cat "$CF_IPS_FILE")"
    else
        raw="$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v4)"$'\n'"$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v6)" ||
            fail "Cloudflare IP 대역을 받지 못했습니다(--cf-ips-file 로 줄 수 있다)."
    fi
    local list=() line n4=0
    while IFS= read -r line; do
        line="${line//[[:space:]]/}"
        [ -n "$line" ] || continue
        if is_cidr4 "$line"; then list+=("$line"); n4=$((n4 + 1))
        elif is_cidr6 "$line"; then list+=("$line")
        else fail "Cloudflare IP 목록에 CIDR 이 아닌 줄이 있습니다: ${line}"
        fi
    done <<< "$raw"
    [ "$n4" -ge 5 ] || fail "Cloudflare IPv4 대역이 너무 적습니다(${n4}개) — 목록을 확인하세요."
    CF_IPS="${list[*]}"
}

write_cloudflare_env() {
    mkdir -p "$CADDY_DIR"
    printf '# Cloudflare IP 대역 (%s, roboyam-app-install.sh --refresh-cloudflare-ips 로 갱신)\nCF_IPS=%s\n' "$STAMP" "$CF_IPS" > "$CF_ENV"
    chmod 644 "$CF_ENV"
    mkdir -p /etc/systemd/system/caddy.service.d
    printf '[Service]\nEnvironmentFile=%s\n' "$CF_ENV" > /etc/systemd/system/caddy.service.d/roboyam.conf
    systemctl daemon-reload
}

install_origin_cert() {
    mkdir -p "$CADDY_CERT_DIR"
    if [ -n "$ORIGIN_CERT" ]; then
        log "Origin CA 인증서 → ${CADDY_CERT_DIR}"
        openssl x509 -in "$ORIGIN_CERT" -noout > /dev/null 2>&1 || fail "--origin-cert 가 PEM 인증서가 아닙니다: ${ORIGIN_CERT}"
        openssl pkey -in "$ORIGIN_KEY" -noout > /dev/null 2>&1 || fail "--origin-key 가 PEM 개인 키가 아닙니다: ${ORIGIN_KEY}"
        [ "$(openssl x509 -in "$ORIGIN_CERT" -noout -pubkey | openssl sha256)" = "$(openssl pkey -in "$ORIGIN_KEY" -pubout | openssl sha256)" ] ||
            fail "인증서와 개인 키가 짝이 아닙니다."
        install -m 644 -o root -g caddy "$ORIGIN_CERT" "${CADDY_CERT_DIR}/origin.pem"
        install -m 640 -o root -g caddy "$ORIGIN_KEY" "${CADDY_CERT_DIR}/origin.key"
    fi
    log "인증서 만료: $(openssl x509 -in "${CADDY_CERT_DIR}/origin.pem" -noout -enddate | cut -d= -f2)"
}

write_caddyfile() {
    local file="${CADDY_DIR}/Caddyfile"
    [ -f "$file" ] && cp -a "$file" "${file}.backup.${STAMP}"
    local public_hosts="" h
    for h in "${DOMAINS[@]}"; do public_hosts+="${public_hosts:+, }https://${h}"; done

    {
        cat <<'EOF'
# 로보얌 — installer-native/app/roboyam-app-install.sh 가 만든다(다시 실행하면 덮어쓴다)
# 방문자 → Cloudflare(HTTPS·CDN·로드밸런싱) → 이 Caddy(443, Origin CA 인증서) → 127.0.0.1 의 앱
# Cloudflare SSL 모드는 「Full (strict)」. {$CF_IPS} 는 cloudflare-ips.env(systemd drop-in)에서 온다.
{
	# 인증서는 Cloudflare Origin CA 파일을 쓴다 — 자동 발급·HTTP→HTTPS 넘김을 끈다(80 은 열지 않는다)
	auto_https off
	servers {
		# Cloudflare 에서 온 요청만 CF-Connecting-IP 를 방문자 IP 로 믿는다
		trusted_proxies static {$CF_IPS}
		client_ip_headers CF-Connecting-IP
	}
}

(roboyam_common) {
	tls /etc/caddy/certs/origin.pem /etc/caddy/certs/origin.key
	# Cloudflare 를 거치지 않은 직접 접속은 끊는다(보안 그룹과 이중으로)
	@direct not remote_ip {$CF_IPS}
	abort @direct
	# /actuator 는 Cloudflare 로드밸런싱 상태 확인용 readiness·liveness 만 내보낸다
	@actuator {
		path /actuator /actuator/*
		not path /actuator/health/readiness /actuator/health/liveness
	}
	respond @actuator 404
	encode zstd gzip
	header -Server
}

# 앱은 X-Forwarded-For 의 뒤에서 1번째(roboyam.proxy.trusted-hops: 1)를 방문자 IP 로 쓴다 —
# 그래서 Caddy 가 확인한 방문자 IP 한 개로 바꿔 넘긴다(요청자가 보낸 값은 버린다)
(roboyam_proxy) {
	reverse_proxy {args[0]} {
		header_up X-Forwarded-For {client_ip}
	}
}
EOF
        printf '\n# 공개 사이트 (api)\n%s {\n\timport roboyam_common\n\timport roboyam_proxy 127.0.0.1:%s\n}\n' "$public_hosts" "$API_PORT"
        if [ "$WITH_ADMIN" -eq 1 ]; then
            printf '\n# 관리자 앱 (admin) — 검색에 잡히지 않게 한다\nhttps://%s {\n\timport roboyam_common\n' "$ADMIN_DOMAIN"
            if [ "${#ADMIN_ALLOW[@]}" -gt 0 ]; then
                printf '\t@not_allowed not client_ip %s\n\trespond @not_allowed 403\n' "${ADMIN_ALLOW[*]}"
            fi
            printf '\theader X-Robots-Tag "noindex, nofollow"\n\timport roboyam_proxy 127.0.0.1:%s\n}\n' "$ADMIN_PORT"
        fi
    } > "$file"
    chmod 644 "$file"

    log "Caddyfile 검사"
    CF_IPS="$CF_IPS" caddy validate --config "$file" --adapter caddyfile > /dev/null ||
        fail "Caddyfile 검사 실패 — ${file} (이전 파일: ${file}.backup.${STAMP})"
}

open_firewall() {
    if systemctl is-active --quiet firewalld 2>/dev/null; then
        log "firewalld : https(443) 열기"
        firewall-cmd -q --permanent --add-service=https
        firewall-cmd -q --reload
    fi
}

reload_caddy() {
    systemctl enable caddy
    if systemctl is-active --quiet caddy; then
        systemctl reload caddy || systemctl restart caddy
    else
        systemctl restart caddy
    fi
    systemctl is-active --quiet caddy || { journalctl -u caddy -n 40 --no-pager || true; fail "Caddy 가 뜨지 않았습니다."; }
    log "Caddy 실행 중"
}

refresh_cloudflare_only() {
    require_root
    [ -f "${CADDY_DIR}/Caddyfile" ] || fail "Caddyfile 이 없습니다. 먼저 전체 설치를 하세요."
    fetch_cloudflare_ips
    write_cloudflare_env
    CF_IPS="$CF_IPS" caddy validate --config "${CADDY_DIR}/Caddyfile" --adapter caddyfile > /dev/null || fail "Caddyfile 검사 실패"
    # 환경 파일이 바뀌었으므로 reload 가 아니라 restart 로 새 값을 읽힌다
    systemctl restart caddy
    log "Cloudflare IP 대역 갱신 완료 ($(wc -w <<< "$CF_IPS")개)"
}

# ----------------------------------------------------------------------------
# 앱 배치·기동
# ----------------------------------------------------------------------------
deploy_app() {   # <api|admin> <jar 또는 빈 값>
    local app="$1" jar="$2"
    systemctl daemon-reload
    systemctl enable "roboyam-${app}" > /dev/null
    if [ -n "$jar" ]; then
        if [ "$START_APPS" -eq 1 ]; then
            "${BASE_DIR}/bin/roboyam-deploy.sh" "$app" "$jar"
        else
            mkdir -p "${BASE_DIR}/${app}/releases"
            local dest; dest="${BASE_DIR}/${app}/releases/roboyam-${app}-${STAMP}-$(sha256sum "$jar" | cut -c1-8).jar"
            install -m 644 -o root -g roboyam "$jar" "$dest"
            ln -sfn "$dest" "${BASE_DIR}/${app}/current.jar"
            log "${app} jar 배치 (띄우지 않음): $(basename "$dest")"
        fi
    elif [ -L "${BASE_DIR}/${app}/current.jar" ]; then
        if [ "$START_APPS" -eq 1 ]; then
            log "${app} : 지금 jar 로 다시 시작"
            systemctl restart "roboyam-${app}"
        fi
    else
        warn "${app} jar 가 없습니다 — 나중에 roboyam-deploy ${app} <jar> 로 올리세요."
    fi
}

print_result() {
    echo
    echo "============================================================"
    echo " 앱 서버 설치 완료"
    echo "============================================================"
    printf '   %-20s %s\n' "공통 환경"   "${SHARED_ENV}"
    printf '   %-20s %s\n' "서버별 환경" "${ENV_DIR}/api.env$([ "$WITH_ADMIN" -eq 1 ] && echo " · ${ENV_DIR}/admin.env")"
    printf '   %-20s %s\n' "배포"        "roboyam-deploy api <jar>$([ "$WITH_ADMIN" -eq 1 ] && echo " · roboyam-deploy admin <jar>")"
    printf '   %-20s %s\n' "로그"        "journalctl -u roboyam-api -f$([ "$WITH_ADMIN" -eq 1 ] && echo " · journalctl -u roboyam-admin -f")"
    printf '   %-20s %s\n' "설치 로그"   "${LOG_FILE}"
    if [ "${NEW_SHARED_ENV:-0}" -eq 1 ]; then
        echo
        echo "   ⚠ 키 백업: ${SHARED_ENV} 를 서버 밖 안전한 곳에 따로 보관하세요."
        echo "     ROBOYAM_CRYPTO_KEY 를 잃으면 DB_PASSWORD 암호문을 풀 수 없고(새 키로 다시 암호화하면 복구),"
        echo "     ROBOYAM_DATA_KEY 를 잃으면 저장된 배송지·환불 계좌 등을 영영 읽을 수 없습니다."
        echo "   WAS2 설치 때 이 파일을 복사해 --shared-env 로 줍니다(scp 후 바로 지운다)."
        echo "   설치 마법사 토큰: $(env_get "$SHARED_ENV" ROBOYAM_INSTALL_TOKEN)  (브라우저 /install 첫 화면)"
    fi
}

main() {
    parse_args "$@"

    if [ "$REFRESH_CF_ONLY" -eq 1 ]; then
        refresh_cloudflare_only
        exit 0
    fi

    require_root
    check_os
    compute_heap

    if [ "$DRY_RUN" -eq 1 ]; then
        echo "============================================================"
        echo " 로보얌 앱 서버 설치 — dry-run (아무것도 바꾸지 않는다)"
        echo "============================================================"
        print_plan
        check_heap_budget
        echo; log "dry-run 끝"
        exit 0
    fi

    install -m 600 /dev/null "$LOG_FILE"   # 설치 토큰 등이 찍히므로 root 만 읽게
    exec > >(tee -a "$LOG_FILE") 2>&1
    echo "============================================================"
    echo " 로보얌 앱 서버 설치 (api$([ "$WITH_ADMIN" -eq 1 ] && echo ' + admin'))"
    echo "============================================================"
    print_plan
    check_heap_budget

    step "OS·Java"
    install_base
    create_user
    setup_swap
    install_java

    step "환경 파일"
    write_shared_env
    write_app_env api
    [ "$WITH_ADMIN" -eq 1 ] && write_app_env admin

    step "systemd"
    install_deploy_tool
    write_unit api
    [ "$WITH_ADMIN" -eq 1 ] && write_unit admin
    systemctl daemon-reload

    if [ "$WITH_CADDY" -eq 1 ]; then
        step "Caddy"
        install_caddy
        fetch_cloudflare_ips
        write_cloudflare_env
        install_origin_cert
        write_caddyfile
        open_firewall
        reload_caddy
    fi

    step "앱 기동"
    # 관리자 앱이 DB 배치를 pg_cron 에 등록하므로 먼저 띄운다
    [ "$WITH_ADMIN" -eq 1 ] && deploy_app admin "$ADMIN_JAR"
    deploy_app api "$API_JAR"

    print_result
}

main "$@"
