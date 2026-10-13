#!/usr/bin/env bash
# Buylist assistant evaluation.
#   ./evaluation/run_buylist_eval.sh                 live run (needs ANTHROPIC_API_KEY)
#   ./evaluation/run_buylist_eval.sh --repeats 3     each case 3 times, majority vote
#   ./evaluation/run_buylist_eval.sh --baseline      save this run as the baseline
#   ./evaluation/run_buylist_eval.sh --replay        re-score the last run, no model calls
#   ./evaluation/run_buylist_eval.sh --category sealed | --cases id1,id2
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

ARGS=("-Dtest=BuylistAssistantEvalTest" "-Dsurefire.failIfNoSpecifiedTests=false")
MODE="live"
while [ $# -gt 0 ]; do
    case "$1" in
        --repeats) ARGS+=("-Deval.repeats=$2"); shift ;;
        --baseline) ARGS+=("-Deval.updateBaseline=true") ;;
        --replay) MODE="replay" ;;
        --category) ARGS+=("-Deval.category=$2"); shift ;;
        --cases) ARGS+=("-Deval.cases=$2"); shift ;;
        --model) ARGS+=("-Deval.model=$2"); shift ;;
        --allow-regressions) ARGS+=("-Deval.allowRegressions=true") ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
    shift
done

if [ "$MODE" = "replay" ]; then
    if [ ! -f evaluation/buylist/results.json ]; then
        echo "No evaluation/buylist/results.json yet: do a live run first."
        exit 1
    fi
    ARGS+=("-Deval.replay=true")
else
    if [ -z "$ANTHROPIC_API_KEY" ]; then
        echo "ANTHROPIC_API_KEY is not set. Export it, or use --replay to re-score the last run."
        exit 1
    fi
    ARGS+=("-Deval.live=true")
fi

LOG="$(mktemp)"
set +e
./mvnw -q test "${ARGS[@]}" > "$LOG" 2>&1
STATUS=$?
set -e
grep -E "^EVAL" "$LOG" || tail -40 "$LOG"
rm -f "$LOG"
echo ""
echo "Report: evaluation/buylist/report$( [ "$MODE" = "replay" ] && echo "-replay" ).md"
if [ $STATUS -ne 0 ]; then
    echo "Evaluation failed: regressions against baseline.json (see the report)."
fi
exit $STATUS
