#!/usr/bin/env bash
# Reinicia un proceso de EventCart (laboratorio de fallos, solo dev).
# Uso: restart.sh <api|consumer|worker>
set -euo pipefail

profile="${1:?usage: restart.sh <api|consumer|worker>}"
dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$dir/stop.sh" "$profile"
"$dir/start.sh" "$profile"
