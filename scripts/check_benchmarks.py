"""Fails CI when macrobenchmark medians exceed the performance budgets in docs/testing.md.

Usage: python scripts/check_benchmarks.py <directory containing *-benchmarkData.json>
"""

import json
import sys
from pathlib import Path

# (benchmark method, metric name) -> maximum allowed median.
BUDGETS = {
    ("a_voiceShortcutColdStartToRecording", "timeToInitialDisplayMs"): 500.0,
    ("a_voiceShortcutColdStartToRecording", "VoxLog.recordingStartSumMs"): 150.0,
    ("d_stopToSaved", "VoxLog.recordingStopSumMs"): 150.0,
    ("b_textShortcutColdStart", "timeToInitialDisplayMs"): 400.0,
}

# Emulators are slower and noisier than phones; CI allows this factor on top of the budget.
EMULATOR_TOLERANCE = 2.0


def load_results(directory: Path) -> dict[tuple[str, str], float]:
    medians: dict[tuple[str, str], float] = {}
    for path in directory.rglob("*benchmarkData.json"):
        for benchmark in json.loads(path.read_text(encoding="utf-8"))["benchmarks"]:
            for metric, values in benchmark["metrics"].items():
                medians[(benchmark["name"], metric)] = values["median"]
    return medians


def main() -> int:
    medians = load_results(Path(sys.argv[1]))
    if not medians:
        print("No benchmark results found.")
        return 1
    failures = 0
    for (benchmark, metric), budget in BUDGETS.items():
        median = medians.get((benchmark, metric))
        if median is None:
            print(f"MISSING  {benchmark}.{metric}")
            failures += 1
            continue
        limit = budget * EMULATOR_TOLERANCE
        status = "OK" if median <= limit else "OVER"
        failures += status == "OVER"
        print(f"{status:7}  {benchmark}.{metric}: median {median:.1f} ms (budget {budget:.0f}, CI limit {limit:.0f})")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
