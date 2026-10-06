#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo "  Running Tailor Cards Trade Evaluation Harness (45 scenarios)..."
echo "=========================================================="

./mvnw test -Dtest=TradeEvaluationRunnerTest

echo ""
echo "Evaluation complete! Report written to: evaluation/report.md"
