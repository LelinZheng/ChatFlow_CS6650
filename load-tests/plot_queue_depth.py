"""
Graph 4: RabbitMQ queue depth over time — all 3 tests.
Uses queue_stats.log (total_unacked column).
Run from the load-tests/ directory:
    python3 plot_queue_depth.py
"""

import pandas as pd
import matplotlib.pyplot as plt
import io
import os

TESTS = [
    ("test1-baseline/queue_stats.log",  "Test 1 — Baseline (500K)", "#2196F3"),
    ("test2-stress/queue_stats.log",    "Test 2 — Stress (1M)",     "#FF9800"),
    ("test3-endurance/queue_stats.log", "Test 3 — Endurance (5M)",  "#4CAF50"),
]

fig, ax = plt.subplots(figsize=(12, 5))

for path, label, color in TESTS:
    with open(path) as f:
        lines = [l for l in f if not l.startswith("nohup")]
    df = pd.read_csv(io.StringIO("".join(lines)))
    df["timestamp"] = pd.to_datetime(df["timestamp"], utc=True)
    df["total_unacked"] = pd.to_numeric(df["total_unacked"], errors="coerce").fillna(0)

    t0 = df["timestamp"].iloc[0]
    df["elapsed_min"] = (df["timestamp"] - t0).dt.total_seconds() / 60

    ax.plot(df["elapsed_min"], df["total_unacked"], label=label, color=color, linewidth=1.4)
    # Mark peak
    peak_idx = df["total_unacked"].idxmax()
    peak_val = df.loc[peak_idx, "total_unacked"]
    peak_t   = df.loc[peak_idx, "elapsed_min"]
    if peak_val > 0:
        ax.annotate(f"peak={int(peak_val)}",
                    xy=(peak_t, peak_val),
                    xytext=(peak_t + 0.5, peak_val + 3),
                    fontsize=8, color=color,
                    arrowprops=dict(arrowstyle="->", color=color, lw=0.8))

ax.set_xlabel("Elapsed Time (minutes)", fontsize=12)
ax.set_ylabel("Total Unacknowledged Messages", fontsize=12)
ax.set_title("RabbitMQ Queue Depth Over Time (Unacked Messages)", fontsize=13, fontweight="bold")
ax.legend(fontsize=10)
ax.set_ylim(bottom=0)
ax.grid(axis="y", linestyle="--", alpha=0.5)
fig.text(0.5, 0.01,
         "Ready messages stayed at 0 throughout — consumer fully kept pace with ingest rate",
         ha="center", fontsize=9, color="#555")

plt.tight_layout(rect=[0, 0.04, 1, 1])
out = "graphs/queue_depth.png"
os.makedirs("graphs", exist_ok=True)
plt.savefig(out, dpi=150)
print(f"Saved: {out}")
plt.close()
