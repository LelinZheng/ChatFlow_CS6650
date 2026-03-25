"""
Graph 3: Batch optimization — client throughput and avg flush latency per config.
Hardcoded from batch-optimization summary.txt results.
Run from the load-tests/ directory:
    python3 plot_batch_optimization.py
"""

import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np
import os

configs      = ["A1\nb=100 f=100ms", "A2\nb=100 f=500ms", "A3\nb=500 f=500ms",
                "A4\nb=1000 f=1000ms", "A5\nb=5000 f=1000ms"]
throughput   = [2064.10, 2183.21, 2130.09, 1980.68, 2111.49]   # msg/s
flush_lat    = [6,        7,        20,       34,       140]      # ms

x = np.arange(len(configs))
width = 0.38

fig, ax1 = plt.subplots(figsize=(11, 5))

bars = ax1.bar(x - width/2, throughput, width, label="Client Throughput (msg/s)",
               color="#42A5F5", edgecolor="white")
ax1.set_ylabel("Client Throughput (msg/s)", fontsize=12)
ax1.set_ylim(1800, 2300)
ax1.yaxis.set_major_formatter(ticker.FuncFormatter(lambda v, _: f"{int(v):,}"))

# Annotate bars
for bar in bars:
    ax1.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 5,
             f"{bar.get_height():,.0f}", ha="center", va="bottom", fontsize=9)

ax2 = ax1.twinx()
ax2.bar(x + width/2, flush_lat, width, label="Avg Flush Latency (ms)",
        color="#EF5350", edgecolor="white", alpha=0.85)
ax2.set_ylabel("Avg Flush Latency (ms)", fontsize=12, color="#EF5350")
ax2.tick_params(axis="y", labelcolor="#EF5350")
ax2.set_ylim(0, 180)

ax1.set_xticks(x)
ax1.set_xticklabels(configs, fontsize=10)
ax1.set_title("Batch Configuration Optimization — 500K Messages, 256 Workers",
              fontsize=13, fontweight="bold")

# Highlight winner
ax1.get_xticklabels()[1].set_fontweight("bold")
ax1.get_xticklabels()[1].set_color("#1565C0")

lines1, labels1 = ax1.get_legend_handles_labels()
lines2, labels2 = ax2.get_legend_handles_labels()
ax1.legend(lines1 + lines2, labels1 + labels2, loc="upper right", fontsize=10)

ax1.grid(axis="y", linestyle="--", alpha=0.4)
fig.text(0.5, 0.01, "★ A2 selected: highest throughput with lowest flush latency",
         ha="center", fontsize=10, color="#1565C0")

plt.tight_layout(rect=[0, 0.04, 1, 1])
out = "graphs/batch_optimization.png"
os.makedirs("graphs", exist_ok=True)
plt.savefig(out, dpi=150)
print(f"Saved: {out}")
plt.close()
