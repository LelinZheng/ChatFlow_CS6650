import pandas as pd
import matplotlib.pyplot as plt
from datetime import datetime

# Load data from repo root
df = pd.read_csv('main_metrics.csv')

# Plot latency distribution
plt.figure(figsize=(10, 6))
plt.hist(df[df['statusCode'] == 'OK']['latencyMs'], bins=50)
plt.xlabel('Latency (ms)')
plt.ylabel('Frequency')
plt.title('Message Latency Distribution')
plt.show()

# Plot throughput over time
buckets = pd.read_csv('throughput_10s.csv')

# Convert milliseconds timestamp to datetime
buckets['timestamp'] = pd.to_datetime(buckets['bucketStartMillis'], unit='ms')

# Create the plot
plt.figure(figsize=(12, 6))
plt.plot(buckets['timestamp'], buckets['throughputMsgPerSec'], linewidth=2)
plt.xlabel('Time')
plt.ylabel('Throughput (msg/s)')
plt.title('Throughput Over Time (10-second buckets)')
plt.xticks(rotation=45)
plt.tight_layout()  # Prevents label cutoff
plt.grid(True, alpha=0.3)  # Optional: adds grid for easier reading
plt.show()