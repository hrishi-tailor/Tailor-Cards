#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo "  Running Tailor Cards Trade Evaluation Harness..."
echo "=========================================================="

STUB_ARG=""
if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "Notice: ANTHROPIC_API_KEY not set in environment."
    echo "Running extraction in CI stub mode (-Deval.stub=true)."
    STUB_ARG="-Deval.stub=true"
fi

./mvnw test -Dtest=TradeEvaluationRunnerTest $STUB_ARG

echo ""
echo "Evaluation complete! Report written to: evaluation/report.md"
