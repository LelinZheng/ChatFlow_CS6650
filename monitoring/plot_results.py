#!/usr/bin/env python3
"""
Generates all graphs needed for the CS6650 Assignment 2 report.

Reads from ../results/v2/ and produces PNG files in ../results/v2/graphs/.

Input files (all optional — graphs are skipped if the file doesn't exist):
    summary_{N}w.txt              - per-run summary from the load test client
    throughput_10s_{N}w.csv       - 10-second throughput buckets from client
    summary_{N}w_{S}srv.txt       - summary renamed after each server-count run
    summary_256w_{C}c.txt         - summary renamed after each consumer-thread run

Output graphs:
    01_throughput_vs_workers.png      - bar chart: throughput by worker count (single server)
    02_latency_vs_workers.png         - line chart: mean + p95 latency by worker count
    03_throughput_over_time_{N}w.png  - throughput stability (queue profile) per run
    04_scalability_comparison.png     - 1 vs 2 vs 4 servers throughput
    05_consumer_thread_tuning.png     - throughput vs consumer thread count

Usage:
    pip install pandas matplotlib
    python3 plot_results.py [results_dir]

    results_dir defaults to ../results/v2 (relative to this script's location)

Naming convention for multi-run files:
    After each server-count test, copy and rename the summary:
        cp summary_256w.txt summary_256w_1srv.txt   (single server baseline)
        cp summary_256w.txt summary_256w_2srv.txt   (2-server LB run)
        cp summary_256w.txt summary_256w_4srv.txt   (4-server LB run)

    After each consumer-thread test, copy and rename the summary:
        cp summary_256w.txt summary_256w_10c.txt
        cp summary_256w.txt summary_256w_20c.txt
        cp summary_256w.txt summary_256w_40c.txt
        cp summary_256w.txt summary_256w_80c.txt
"""

import os
import re
import sys
import pandas as pd
import matplotlib
matplotlib.use('Agg')  # headless — saves PNG without needing a display
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker

# ── Config ─────────────────────────────────────────────────────────────────────

SCRIPT_DIR  = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DIR = os.path.join(SCRIPT_DIR, "..", "results", "v2")
RESULTS_DIR = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DIR
GRAPHS_DIR  = os.path.join(RESULTS_DIR, "graphs")

os.makedirs(GRAPHS_DIR, exist_ok=True)

WORKER_COUNTS = [64, 128, 256, 512]

# ── Helpers ────────────────────────────────────────────────────────────────────

def summary_path(n):
    return os.path.join(RESULTS_DIR, f"summary_{n}w.txt")

def buckets_path(n):
    return os.path.join(RESULTS_DIR, f"throughput_10s_{n}w.csv")

def out(name):
    path = os.path.join(GRAPHS_DIR, name)
    print(f"  Saved: {path}")
    return path

def parse_summary(path):
    """Parse a summary_Nw.txt file into a dict of key metrics."""
    data = {}
    try:
        with open(path) as f:
            text = f.read()
    except FileNotFoundError:
        return None

    patterns = {
        "workers":       r"workers=(\d+)",
        "throughput":    r"throughput msg/s=([\d.]+)",
        "timeSec":       r"timeSec=([\d.]+)",
        "ok":            r"^OK=(\d+)",
        "failed":        r"failed=(\d+)",
        "connections":   r"connections=(\d+)",
        "reconnections": r"reconnections=(\d+)",
        "mean":          r"mean=([\d.]+)",
        "median":        r"median=([\d.]+)",
        "p95":           r"p95=([\d.]+)",
        "p99":           r"p99=([\d.]+)",
        "min_lat":       r"min=([\d.]+)",
        "max_lat":       r"max=([\d.]+)",
    }
    for key, pat in patterns.items():
        m = re.search(pat, text, re.MULTILINE)
        if m:
            data[key] = float(m.group(1))
    return data if data else None

def save(fig, name):
    fig.savefig(out(name), dpi=150, bbox_inches="tight")
    plt.close(fig)

# ── Collect per-run summaries ──────────────────────────────────────────────────

summaries = {}
for n in WORKER_COUNTS:
    d = parse_summary(summary_path(n))
    if d:
        summaries[n] = d

if not summaries:
    print("No summary_*w.txt files found in", RESULTS_DIR)
    print("Run the load test first, then re-run this script.")
    sys.exit(0)

print(f"Found summary data for workers: {sorted(summaries.keys())}")
print(f"Writing graphs to: {GRAPHS_DIR}\n")

workers_with_data = sorted(summaries.keys())
labels = [f"{n}w" for n in workers_with_data]

# ── Graph 1: Throughput vs Worker Count (bar chart) ───────────────────────────

values = [summaries[n]["throughput"] for n in workers_with_data]

fig, ax = plt.subplots(figsize=(8, 5))
bars = ax.bar(labels, values, color="#4C72B0", width=0.5, edgecolor="white")
ax.bar_label(bars, fmt="%.0f", padding=4, fontsize=10)
ax.set_title("Throughput vs Client Thread Count (Single Server)", fontsize=14, fontweight="bold")
ax.set_xlabel("Client Threads")
ax.set_ylabel("Throughput (msg/s)")
ax.yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, _: f"{x:,.0f}"))
ax.grid(axis="y", alpha=0.3)
ax.set_ylim(0, max(values) * 1.2)
save(fig, "01_throughput_vs_workers.png")

# ── Graph 2: Latency vs Worker Count (mean + p95) ─────────────────────────────

means = [summaries[n].get("mean", 0) for n in workers_with_data]
p95s  = [summaries[n].get("p95",  0) for n in workers_with_data]

fig, ax = plt.subplots(figsize=(8, 5))
ax.plot(labels, means, marker="o", linewidth=2, label="Mean latency", color="#4C72B0")
ax.plot(labels, p95s,  marker="s", linewidth=2, label="p95 latency",  color="#DD8452", linestyle="--")
for i, (m, p) in enumerate(zip(means, p95s)):
    ax.annotate(f"{m:.1f}ms", (labels[i], m), textcoords="offset points", xytext=(0, 8),
                ha="center", fontsize=9, color="#4C72B0")
    ax.annotate(f"{p:.1f}ms", (labels[i], p), textcoords="offset points", xytext=(0, -14),
                ha="center", fontsize=9, color="#DD8452")
ax.set_title("Latency vs Client Thread Count", fontsize=14, fontweight="bold")
ax.set_xlabel("Client Threads")
ax.set_ylabel("Latency (ms)")
ax.legend()
ax.grid(alpha=0.3)
ax.set_ylim(0, max(p95s) * 1.4)
save(fig, "02_latency_vs_workers.png")

# ── Graph 3: Throughput over time per run (queue profile) ─────────────────────

for n in workers_with_data:
    path = buckets_path(n)
    if not os.path.exists(path):
        print(f"  Skipping throughput-over-time for {n}w (throughput_10s_{n}w.csv not found)")
        continue

    df = pd.read_csv(path)
    if df.empty:
        continue

    t0 = df["bucketStartMillis"].iloc[0]
    df["elapsed_s"] = (df["bucketStartMillis"] - t0) / 1000.0

    fig, ax = plt.subplots(figsize=(12, 5))
    ax.plot(df["elapsed_s"], df["throughputMsgPerSec"], linewidth=1.5, color="#4C72B0")
    ax.fill_between(df["elapsed_s"], df["throughputMsgPerSec"], alpha=0.15, color="#4C72B0")
    ax.axhline(df["throughputMsgPerSec"].mean(), color="red", linestyle="--",
               linewidth=1, label=f"Mean: {df['throughputMsgPerSec'].mean():.0f} msg/s")
    ax.set_title(f"Throughput Over Time — {n} workers (queue profile)", fontsize=13, fontweight="bold")
    ax.set_xlabel("Elapsed Time (s)")
    ax.set_ylabel("Throughput (msg/s)")
    ax.yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, _: f"{x:,.0f}"))
    ax.legend()
    ax.grid(alpha=0.3)
    save(fig, f"03_throughput_over_time_{n}w.png")

# ── Graph 4: Scalability comparison (1 / 2 / 4 servers) ──────────────────────
# Looks for summary_{N}w_{S}srv.txt — copy and rename after each server-count run.

scalability = {}
for srv in [1, 2, 4]:
    for n in [256, 512, 128, 64]:
        path = os.path.join(RESULTS_DIR, f"summary_{n}w_{srv}srv.txt")
        if os.path.exists(path):
            d = parse_summary(path)
            if d:
                scalability[srv] = d
                break

if len(scalability) >= 2:
    srv_keys   = sorted(scalability)
    srv_labels = [f"{s} server{'s' if s > 1 else ''}" for s in srv_keys]
    srv_values = [scalability[s]["throughput"] for s in srv_keys]

    fig, ax = plt.subplots(figsize=(7, 5))
    bars = ax.bar(srv_labels, srv_values,
                  color=["#4C72B0", "#55A868", "#DD8452"][:len(srv_keys)],
                  width=0.5, edgecolor="white")
    ax.bar_label(bars, fmt="%.0f", padding=4, fontsize=10)
    base = srv_values[0]
    for i, v in enumerate(srv_values):
        ax.annotate(f"×{v/base:.1f}",
                    (srv_labels[i], v + max(srv_values) * 0.03),
                    ha="center", fontsize=9, color="gray")
    ax.set_title("Throughput Scalability: 1 vs 2 vs 4 Servers", fontsize=13, fontweight="bold")
    ax.set_ylabel("Throughput (msg/s)")
    ax.yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, _: f"{x:,.0f}"))
    ax.grid(axis="y", alpha=0.3)
    ax.set_ylim(0, max(srv_values) * 1.25)
    save(fig, "04_scalability_comparison.png")
else:
    print("  Skipping scalability graph — need summary_*w_1srv.txt, _2srv.txt, _4srv.txt")
    print("  After each server-count run: cp summary_256w.txt summary_256w_Nsrv.txt")

# ── Graph 5: Consumer thread tuning ───────────────────────────────────────────
# Looks for summary_256w_{C}c.txt — copy and rename after each consumer-thread run.

consumer_runs = {}
for c in [10, 20, 40, 80]:
    path = os.path.join(RESULTS_DIR, f"summary_256w_{c}c.txt")
    if os.path.exists(path):
        d = parse_summary(path)
        if d:
            consumer_runs[c] = d

if len(consumer_runs) >= 2:
    c_keys   = sorted(consumer_runs)
    c_labels = [f"{c}" for c in c_keys]
    c_values = [consumer_runs[c]["throughput"] for c in c_keys]

    fig, ax = plt.subplots(figsize=(8, 5))
    bars = ax.bar(c_labels, c_values, color="#8172B2", width=0.5, edgecolor="white")
    ax.bar_label(bars, fmt="%.0f", padding=4, fontsize=10)
    ax.set_title("Throughput vs Consumer Thread Count (256 client workers)", fontsize=13, fontweight="bold")
    ax.set_xlabel("Consumer Threads")
    ax.set_ylabel("Throughput (msg/s)")
    ax.yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, _: f"{x:,.0f}"))
    ax.grid(axis="y", alpha=0.3)
    ax.set_ylim(0, max(c_values) * 1.2)
    save(fig, "05_consumer_thread_tuning.png")
else:
    print("  Skipping consumer tuning graph — need summary_256w_10c.txt, _20c.txt, etc.")
    print("  After each consumer-thread run: cp summary_256w.txt summary_256w_Nc.txt")

print("\nDone.")
