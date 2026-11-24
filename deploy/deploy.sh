#!/usr/bin/env bash
# Deploys one commit of Sitewatch on this server. Run as root, by CI through SSM Run Command or by hand:
#
#   sudo bash deploy.sh <git-sha-40-hex> <image-tag>
#
# It fetches the compose file for that exact commit, refreshes the secrets and the origin certificate from SSM,
# starts the new images and waits until they are healthy. If they are not, it goes back to the previous version.
set -euo pipefail

sha="${1:?usage: deploy.sh <git-sha> <image-tag>}"
tag="${2:?usage: deploy.sh <git-sha> <image-tag>}"

# The arguments end up in URLs and in a shell: accept nothing but what they should look like.
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid git sha: $sha" >&2; exit 2; }
[[ "$tag" =~ ^[A-Za-z0-9_.-]{1,64}$ ]] || { echo "Invalid image tag: $tag" >&2; exit 2; }

repo="${SITEWATCH_REPO:-smarinb/sitewatch}"
dir="${SITEWATCH_DIR:-/opt/sitewatch}"
region="${AWS_REGION:-us-east-1}"
export PATH="$PATH:/snap/bin" # the AWS CLI is a snap, and SSM runs commands with a minimal PATH
export SITEWATCH_DIR="$dir"

compose=(docker compose --project-directory "$dir" -f "$dir/docker-compose.yml" --env-file "$dir/secrets.env")

log() { echo "[deploy $(date -u +%H:%M:%S)] $*"; }

install -d -m 0755 "$dir"
install -d -m 0700 "$dir/certs"

log "Fetching the compose file and Caddyfile of $sha"
raw="https://raw.githubusercontent.com/$repo/$sha/deploy"
for file in docker-compose.prod.yml Caddyfile; do
  curl -fsSL --retry 3 --max-time 30 "$raw/$file" -o "$dir/$file.new"
  [ -s "$dir/$file.new" ] || { echo "$file is empty" >&2; exit 1; }
done

log "Refreshing secrets and the origin certificate from SSM"
/usr/local/bin/sitewatch-fetch-secrets
for pair in origin-cert:origin.pem origin-key:origin.key; do
  aws ssm get-parameter --region "$region" --name "/sitewatch/tls/${pair%%:*}" --with-decryption \
    --query Parameter.Value --output text > "$dir/certs/${pair##*:}.new"
  [ -s "$dir/certs/${pair##*:}.new" ] || { echo "${pair%%:*} is empty" >&2; exit 1; }
  chmod 0600 "$dir/certs/${pair##*:}.new"
  mv "$dir/certs/${pair##*:}.new" "$dir/certs/${pair##*:}"
done

# Validate before replacing what is running.
IMAGE_TAG="$tag" docker compose --project-directory "$dir" -f "$dir/docker-compose.prod.yml.new" \
  --env-file "$dir/secrets.env" config -q
mv "$dir/docker-compose.prod.yml.new" "$dir/docker-compose.yml"
mv "$dir/Caddyfile.new" "$dir/Caddyfile"

previous="$(cat "$dir/current-tag" 2>/dev/null || true)"

start() {
  IMAGE_TAG="$1" "${compose[@]}" pull --quiet
  IMAGE_TAG="$1" "${compose[@]}" up -d --remove-orphans --wait --wait-timeout 180
}

log "Starting $tag (previous: ${previous:-none})"
if start "$tag"; then
  echo "$tag" > "$dir/current-tag"
  docker image prune -af --filter "until=168h" > /dev/null
  log "Deployed $tag"
  exit 0
fi

log "FAILED to start $tag"
IMAGE_TAG="$tag" "${compose[@]}" logs --tail 60 api || true
if [ -n "$previous" ]; then
  log "Rolling back to $previous"
  if start "$previous"; then
    log "Rolled back to $previous"
  else
    log "ROLLBACK FAILED too"
  fi
fi
exit 1
