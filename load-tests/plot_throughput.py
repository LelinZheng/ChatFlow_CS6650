"""
Graph 1: Throughput over time — all 3 tests on one chart.
Uses throughput_10s.csv (bucketStartMillis, count, throughputMsgPerSec).
Run from the load-tests/ directory:
    python3 plot_throughput.py
"""

import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import os

TESTS = [
    ("test1-baseline/throughput_10s.csv", "Test 1 — Baseline (500K)", "#2196F3"),
    ("test2-stress/throughput_10s.csv",   "Test 2 — Stress (1M)",     "#FF9800"),
    ("test3-endurance/throughput_10s.csv","Test 3 — Endurance (5M)",  "#4CAF50"),
]

fig, ax = plt.subplots(figsize=(12, 5))

for path, label, color in TESTS:
    df = pd.read_csv(path)
    # Normalize time to seconds from start of each test
    t0 = df["bucketStartMillis"].iloc[0]
    df["elapsed_sec"] = (df["bucketStartMillis"] - t0) / 1000
    ax.plot(df["elapsed_sec"], df["throughputMsgPerSec"], label=label, color=color, linewidth=1.2)

ax.set_xlabel("Elapsed Time (seconds)", fontsize=12)
ax.set_ylabel("Throughput (msg/s)", fontsize=12)
ax.set_title("WebSocket Send Throughput Over Time", fontsize=14, fontweight="bold")
ax.legend(fontsize=10)
ax.yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, _: f"{int(x):,}"))
ax.grid(axis="y", linestyle="--", alpha=0.5)
ax.set_ylim(bottom=0)

plt.tight_layout()
out = "graphs/throughput_over_time.png"
os.makedirs("graphs", exist_ok=True)
plt.savefig(out, dpi=150)
print(f"Saved: {out}")
plt.close()
