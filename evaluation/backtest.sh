#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

CSV_FILE="${1:-evaluation/past_trades.csv}"

if [ ! -f "$CSV_FILE" ]; then
  echo "Error: CSV file not found: $CSV_FILE"
  echo "Usage: ./evaluation/backtest.sh [path/to/past_trades.csv]"
  exit 1
fi

echo "=========================================================="
echo "  Replaying historical trades from: $CSV_FILE"
echo "=========================================================="

./mvnw test -Dtest=TradeBacktesterTest -Dbacktest.csv="$CSV_FILE"
