"""
Graph 2: DB CPU % and Memory used over time — Test 3 endurance.
Uses test3-endurance/db_stats.log (13-column format).
Run from the load-tests/ directory:
    python3 plot_db_resources.py
"""

import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import io
import os

with open("test3-endurance/db_stats.log") as f:
    lines = [l for l in f if not l.startswith("nohup")]
df = pd.read_csv(io.StringIO("".join(lines)))

df["timestamp"] = pd.to_datetime(df["timestamp"], utc=True)
df["cpu_pct"] = pd.to_numeric(df["cpu_pct"], errors="coerce")
df["mem_used_mb"] = pd.to_numeric(df["mem_used_mb"], errors="coerce")

# Normalize to elapsed minutes from test start
t0 = df["timestamp"].iloc[0]
df["elapsed_min"] = (df["timestamp"] - t0).dt.total_seconds() / 60

fig, ax1 = plt.subplots(figsize=(12, 5))

color_cpu = "#E53935"
color_mem = "#1E88E5"

ax1.set_xlabel("Elapsed Time (minutes)", fontsize=12)
ax1.set_ylabel("CPU Usage (%)", color=color_cpu, fontsize=12)
ax1.plot(df["elapsed_min"], df["cpu_pct"], color=color_cpu, linewidth=1.5, label="CPU %")
ax1.tick_params(axis="y", labelcolor=color_cpu)
ax1.set_ylim(0, max(df["cpu_pct"].dropna().max() * 1.2, 20))

ax2 = ax1.twinx()
ax2.set_ylabel("Memory Used (MB)", color=color_mem, fontsize=12)
ax2.plot(df["elapsed_min"], df["mem_used_mb"], color=color_mem, linewidth=1.5,
         linestyle="--", label="Mem Used (MB)")
ax2.tick_params(axis="y", labelcolor=color_mem)

# Combined legend
lines1, labels1 = ax1.get_legend_handles_labels()
lines2, labels2 = ax2.get_legend_handles_labels()
ax1.legend(lines1 + lines2, labels1 + labels2, loc="upper left", fontsize=10)

ax1.set_title("DB EC2 — CPU & Memory During Test 3 Endurance (5M messages)", fontsize=13, fontweight="bold")
ax1.grid(axis="x", linestyle="--", alpha=0.4)

plt.tight_layout()
out = "graphs/db_resources_test3.png"
os.makedirs("graphs", exist_ok=True)
plt.savefig(out, dpi=150)
print(f"Saved: {out}")
plt.close()
