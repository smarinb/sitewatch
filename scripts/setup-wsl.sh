#!/usr/bin/env bash
# Prepares a fresh WSL2 Ubuntu 24.04 for Sitewatch development.
# Installs: Docker Engine, JDK 21 (SDKMAN), Node 24 (nvm), Terraform, AWS CLI v2, Ansible, gh, utilities.
# Idempotent: safe to re-run.
set -euo pipefail

sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg unzip zip git jq make lsb-release software-properties-common

# --- Docker Engine (no Docker Desktop) ---
if ! command -v docker >/dev/null; then
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  sudo usermod -aG docker "$USER"
fi

# Enable systemd in WSL so Docker starts automatically
if ! grep -q "systemd=true" /etc/wsl.conf 2>/dev/null; then
  printf '[boot]\nsystemd=true\n' | sudo tee -a /etc/wsl.conf >/dev/null
  echo ">> systemd enabled: run 'wsl --shutdown' from Windows and reopen Ubuntu."
fi

# --- Terraform + GitHub CLI ---
if ! command -v terraform >/dev/null; then
  curl -fsSL https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp.gpg
  echo "deb [signed-by=/usr/share/keyrings/hashicorp.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" \
    | sudo tee /etc/apt/sources.list.d/hashicorp.list >/dev/null
fi
if ! command -v gh >/dev/null; then
  curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo tee /usr/share/keyrings/githubcli.gpg >/dev/null
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli.gpg] https://cli.github.com/packages stable main" \
    | sudo tee /etc/apt/sources.list.d/github-cli.list >/dev/null
fi
sudo apt-get update
sudo apt-get install -y terraform gh ansible

# --- AWS CLI v2 ---
if ! command -v aws >/dev/null; then
  tmp=$(mktemp -d)
  curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "$tmp/awscli.zip"
  unzip -q "$tmp/awscli.zip" -d "$tmp"
  sudo "$tmp/aws/install"
  rm -rf "$tmp"
fi

# --- JDK 21 via SDKMAN ---
if [ ! -d "$HOME/.sdkman" ]; then
  curl -fsSL "https://get.sdkman.io?rcupdate=true" | bash
fi
set +u
# shellcheck disable=SC1091
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.9-tem </dev/null || sdk install java "$(sdk list java | grep -oE '21\.[0-9.]+-tem' | head -1)" </dev/null || true
set -u

# --- Node 24 via nvm ---
if [ ! -d "$HOME/.nvm" ]; then
  curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
fi
set +u
export NVM_DIR="$HOME/.nvm"
# shellcheck disable=SC1091
source "$NVM_DIR/nvm.sh"
nvm install 24
nvm alias default 24
set -u

echo
echo "Done. Close and reopen the terminal, then check:"
echo "  docker run --rm hello-world && java -version && node -v && terraform -version && aws --version && ansible --version"
