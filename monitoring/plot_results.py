#!/usr/bin/env python3
"""
Generates all graphs for the CS6650 Assignment 2 report.
Reads from ../results/v2/ and saves PNGs to ../results/v2/graphs/.

Graphs produced:
    01_throughput_vs_workers.png       - 64/128/256/512 workers @ 20 consumer threads
    02_latency_vs_workers.png          - mean + p95 latency for same runs
    03_throughput_over_time_Nw.png     - queue profile per worker-count run
    04_consumer_thread_tuning.png      - 10/20/40/80 consumer threads @ 128 workers
    05_scalability_comparison.png      - 1 / 2 / 4 servers @ 128w 20c
    06_throughput_over_time_servers.png- queue profile: 1 vs 2 vs 4 servers overlaid
    07_throughput_over_time_1M.png     - 4-server 1M stress test queue profile

Usage:
    python3 monitoring/plot_results.py
"""

import os
import re
import sys
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker

# ── Paths ──────────────────────────────────────────────────────────────────────

SCRIPT_DIR  = os.path.dirname(os.path.abspath(__file__))
RESULTS_DIR = os.path.join(SCRIPT_DIR, "..", "results", "v2")
GRAPHS_DIR  = os.path.join(RESULTS_DIR, "graphs")
os.makedirs(GRAPHS_DIR, exist_ok=True)

def p(filename):
    return os.path.join(RESULTS_DIR, filename)

def out(name):
    path = os.path.join(GRAPHS_DIR, name)
    print(f"  Saved: {path}")
    return path

# ── Parser ─────────────────────────────────────────────────────────────────────

def parse_summary(filename):
    try:
        with open(p(filename)) as f:
            text = f.read()
    except FileNotFoundError:
        print(f"  WARNING: {filename} not found, skipping.")
        return None

    patterns = {
        "throughput":    r"throughput msg/s=([\d.]+)",
        "timeSec":       r"timeSec=([\d.]+)",
        "ok":            r"^OK=(\d+)",
        "failed":        r"failed=(\d+)",
        "mean":          r"mean=([\d.]+)",
        "median":        r"median=([\d.]+)",
        "p95":           r"p95=([\d.]+)",
        "p99":           r"p99=([\d.]+)",
    }
    data = {}
    for key, pat in patterns.items():
        m = re.search(pat, text, re.MULTILINE)
        if m:
            data[key] = float(m.group(1))
    return data if data else None

def load_buckets(filename):
    path = p(filename)
    if not os.path.exists(path):
        print(f"  WARNING: {filename} not found, skipping.")
        return None
    df = pd.read_csv(path)
    if df.empty:
        return None
    t0 = df["bucketStartMillis"].iloc[0]
    df["elapsed_s"] = (df["bucketStartMillis"] - t0) / 1000.0
    return df

def save(fig, name):
    fig.savefig(out(name), dpi=150, bbox_inches="tight")
    plt.close(fig)

def fmt(ax):
    ax.yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, _: f"{x:,.0f}"))
    ax.grid(alpha=0.3)

# ── Data definitions ───────────────────────────────────────────────────────────

# Graph 1 & 2: worker count tuning (fixed 20 consumer threads)
WORKER_RUNS = [
    (64,  "summary_64w_20c.txt"),
    (128, "summary_128w_20c.txt"),
    (256, "summary_256w_20c.txt"),
    (512, "summary_512w_20c.txt"),
]

# Graph 3: throughput-over-time for each worker count
WORKER_BUCKETS = [
    (64,  "throughput_10s_64w_20c.csv"),
    (128, "throughput_10s_128w_20c.csv"),
    (256, "throughput_10s_256w_20c.csv"),
    (512, "throughput_10s_512w_20c.csv"),
]

# Graph 4: consumer thread tuning (fixed 128 workers)
CONSUMER_RUNS = [
    (10, "summary_128w_10c.txt"),
    (20, "summary_128w_20c.txt"),
    (40, "summary_128w_40c.txt"),
    (80, "summary_128w_80c.txt"),
]

# Graph 5 & 6: server scaling (fixed 128w 20c)
SERVER_RUNS = [
    (1, "summary_128w_20c.txt"),
    (2, "summary_128w_20c_2servers.txt"),
    (4, "summary_128w_20c_4servers.txt"),
]
SERVER_BUCKETS = [
    (1, "throughput_10s_128w_20c.csv"),
    (2, "throughput_10s_128w_20c_2servers.csv"),
    (4, "throughput_10s_128w_20c_4servers.csv"),
]

# Graph 7: 1M stress test
STRESS_BUCKETS_FILE = "throughput_10s_128w_20c_4servers_1M.csv"
STRESS_SUMMARY_FILE = "summary_128w_20c_4servers_1M.txt"

COLORS = ["#4C72B0", "#55A868", "#DD8452", "#8172B2"]

# ── Graph 1: Throughput vs Worker Count ───────────────────────────────────────

print("Graph 1: Throughput vs Worker Count")
data1 = [(n, parse_summary(f)) for n, f in WORKER_RUNS]
data1 = [(n, d) for n, d in data1 if d]

if data1:
    labels = [f"{n}w" for n, _ in data1]
    values = [d["throughput"] for _, d in data1]

    fig, ax = plt.subplots(figsize=(8, 5))
    bars = ax.bar(labels, values, color="#4C72B0", width=0.5, edgecolor="white")
    ax.bar_label(bars, fmt="%.0f", padding=4, fontsize=10)
    ax.set_title("Throughput vs Client Thread Count (20 consumer threads)", fontsize=13, fontweight="bold")
    ax.set_xlabel("Client Threads")
    ax.set_ylabel("Throughput (msg/s)")
    ax.set_ylim(0, max(values) * 1.2)
    fmt(ax)
    save(fig, "01_throughput_vs_workers.png")

# ── Graph 2: Latency vs Worker Count ──────────────────────────────────────────

print("Graph 2: Latency vs Worker Count")
if data1:
    means = [d.get("mean", 0) for _, d in data1]
    p95s  = [d.get("p95",  0) for _, d in data1]

    fig, ax = plt.subplots(figsize=(8, 5))
    ax.plot(labels, means, marker="o", linewidth=2, label="Mean latency", color="#4C72B0")
    ax.plot(labels, p95s,  marker="s", linewidth=2, label="p95 latency",  color="#DD8452", linestyle="--")
    for i, (m, p95) in enumerate(zip(means, p95s)):
        ax.annotate(f"{m:.1f}ms", (labels[i], m), textcoords="offset points",
                    xytext=(0, 8),   ha="center", fontsize=9, color="#4C72B0")
        ax.annotate(f"{p95:.1f}ms", (labels[i], p95), textcoords="offset points",
                    xytext=(0, -14), ha="center", fontsize=9, color="#DD8452")
    ax.set_title("Latency vs Client Thread Count", fontsize=13, fontweight="bold")
    ax.set_xlabel("Client Threads")
    ax.set_ylabel("Latency (ms)")
    ax.set_ylim(0, max(p95s) * 1.4)
    ax.legend()
    fmt(ax)
    save(fig, "02_latency_vs_workers.png")

# ── Graph 3: Throughput over time per worker count ────────────────────────────

print("Graph 3: Throughput over time (per worker count)")
for n, filename in WORKER_BUCKETS:
    df = load_buckets(filename)
    if df is None:
        continue
    mean_tput = df["throughputMsgPerSec"].mean()
    fig, ax = plt.subplots(figsize=(12, 5))
    ax.plot(df["elapsed_s"], df["throughputMsgPerSec"], linewidth=1.5, color="#4C72B0")
    ax.fill_between(df["elapsed_s"], df["throughputMsgPerSec"], alpha=0.15, color="#4C72B0")
    ax.axhline(mean_tput, color="red", linestyle="--", linewidth=1,
               label=f"Mean: {mean_tput:.0f} msg/s")
    ax.set_title(f"Throughput Over Time — {n} workers, 20 consumer threads", fontsize=13, fontweight="bold")
    ax.set_xlabel("Elapsed Time (s)")
    ax.set_ylabel("Throughput (msg/s)")
    ax.legend()
    fmt(ax)
    save(fig, f"03_throughput_over_time_{n}w.png")

# ── Graph 4: Consumer Thread Tuning ───────────────────────────────────────────

print("Graph 4: Consumer thread tuning")
data4 = [(c, parse_summary(f)) for c, f in CONSUMER_RUNS]
data4 = [(c, d) for c, d in data4 if d]

if len(data4) >= 2:
    c_labels = [str(c) for c, _ in data4]
    c_values = [d["throughput"] for _, d in data4]

    fig, ax = plt.subplots(figsize=(8, 5))
    bars = ax.bar(c_labels, c_values, color="#8172B2", width=0.5, edgecolor="white")
    ax.bar_label(bars, fmt="%.0f", padding=4, fontsize=10)
    ax.set_title("Throughput vs Consumer Thread Count (128 client workers)", fontsize=13, fontweight="bold")
    ax.set_xlabel("Consumer Threads")
    ax.set_ylabel("Throughput (msg/s)")
    ax.set_ylim(0, max(c_values) * 1.2)
    fmt(ax)
    save(fig, "04_consumer_thread_tuning.png")

# ── Graph 5: Scalability comparison ───────────────────────────────────────────

print("Graph 5: Scalability comparison (1 / 2 / 4 servers)")
data5 = [(s, parse_summary(f)) for s, f in SERVER_RUNS]
data5 = [(s, d) for s, d in data5 if d]

if len(data5) >= 2:
    s_labels = [f"{s} server{'s' if s > 1 else ''}" for s, _ in data5]
    s_values = [d["throughput"] for _, d in data5]
    base = s_values[0]

    fig, ax = plt.subplots(figsize=(7, 5))
    bars = ax.bar(s_labels, s_values,
                  color=COLORS[:len(data5)], width=0.5, edgecolor="white")
    ax.bar_label(bars, fmt="%.0f", padding=4, fontsize=10)
    for i, v in enumerate(s_values):
        ax.annotate(f"×{v/base:.1f}",
                    (s_labels[i], v + max(s_values) * 0.03),
                    ha="center", fontsize=9, color="gray")
    ax.set_title("Throughput Scalability: 1 vs 2 vs 4 Servers (128w 20c)", fontsize=13, fontweight="bold")
    ax.set_ylabel("Throughput (msg/s)")
    ax.set_ylim(0, max(s_values) * 1.25)
    fmt(ax)
    save(fig, "05_scalability_comparison.png")

# ── Graph 6: Throughput over time — 1 vs 2 vs 4 servers overlaid ─────────────

print("Graph 6: Throughput over time overlaid (1 / 2 / 4 servers)")
fig, ax = plt.subplots(figsize=(12, 5))
plotted = False
for (s, filename), color in zip(SERVER_BUCKETS, COLORS):
    df = load_buckets(filename)
    if df is None:
        continue
    ax.plot(df["elapsed_s"], df["throughputMsgPerSec"], linewidth=1.5,
            color=color, label=f"{s} server{'s' if s > 1 else ''}")
    plotted = True

if plotted:
    ax.set_title("Throughput Over Time: 1 vs 2 vs 4 Servers (128w 20c)", fontsize=13, fontweight="bold")
    ax.set_xlabel("Elapsed Time (s)")
    ax.set_ylabel("Throughput (msg/s)")
    ax.legend()
    fmt(ax)
    save(fig, "06_throughput_over_time_servers.png")
else:
    plt.close(fig)

# ── Graph 7: 4-server 1M stress test ──────────────────────────────────────────

print("Graph 7: 1M stress test throughput over time")
df7 = load_buckets(STRESS_BUCKETS_FILE)
if df7 is not None:
    d7  = parse_summary(STRESS_SUMMARY_FILE)
    mean_tput = df7["throughputMsgPerSec"].mean()

    fig, ax = plt.subplots(figsize=(14, 5))
    ax.plot(df7["elapsed_s"], df7["throughputMsgPerSec"], linewidth=1.5, color="#DD8452")
    ax.fill_between(df7["elapsed_s"], df7["throughputMsgPerSec"], alpha=0.15, color="#DD8452")
    ax.axhline(mean_tput, color="red", linestyle="--", linewidth=1,
               label=f"Mean: {mean_tput:.0f} msg/s")
    title = "Stress Test (1M messages) — 4 Servers, 128 Workers, 20 Consumer Threads"
    if d7:
        title += f"\n OK={int(d7.get('ok',0)):,}  throughput={d7.get('throughput',0):.0f} msg/s"
    ax.set_title(title, fontsize=12, fontweight="bold")
    ax.set_xlabel("Elapsed Time (s)")
    ax.set_ylabel("Throughput (msg/s)")
    ax.legend()
    fmt(ax)
    save(fig, "07_throughput_over_time_1M.png")

print("\nDone.")
