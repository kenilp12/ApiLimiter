# Deep Dive: Rate-Limiting Algorithms & Distributed Redis Lua Implementation

This document provides a technical analysis of the four rate-limiting algorithms implemented in the **API Rate Limiter** platform.

All four algorithms execute as **atomic Lua scripts inside Redis**, ensuring zero race conditions between distributed gateway nodes and maintaining deterministic $O(1)$ to $O(\log N)$ performance.

---

## 1. Fixed Window Counter

### Mathematical Model & Concept
Time is divided into discrete, static intervals of duration $T$ (e.g., 60 seconds). A single counter is maintained for each window.

$$\text{Window ID} = \left\lfloor \frac{\text{nowMs}}{T \times 1000} \right\rfloor$$

When a request arrives:
1. Increment the counter for the current Window ID: $C \leftarrow C + 1$.
2. If $C = 1$, set TTL to $T + 1$ seconds.
3. If $C \le \text{Limit}$, allow the request with $\text{remaining} = \text{Limit} - C$.
4. If $C > \text{Limit}$, block the request with HTTP 429.

### Redis Lua Script (`fixed_window.lua`)
```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local cost = tonumber(ARGV[3]) or 1

local current = redis.call('INCRBY', key, cost)
if current == cost then
    redis.call('EXPIRE', key, windowSeconds + 1)
end

local ttl = redis.call('TTL', key)
if ttl < 0 then ttl = windowSeconds end

if current <= limit then
    return { 1, limit - current, ttl, 0 }
else
    return { 0, 0, ttl, ttl }
end
```

### Trade-Offs
- **Pros**: Extremely low memory consumption (single Redis integer key per window), ultra-fast $O(1)$ atomic increment.
- **Cons**: Susceptible to the **Boundary Burst Problem**. A client can send $N$ requests in the last second of Window 1, and another $N$ requests in the first second of Window 2, effectively executing $2N$ requests within 2 seconds.

---

## 2. Sliding Window Log

### Mathematical Model & Concept
To eliminate the boundary burst spike of the fixed window, the sliding window log records the exact millisecond timestamp of every permitted request in a Redis Sorted Set (`ZSET`).

When a request arrives at timestamp $t_{\text{now}}$:
1. Prune all recorded timestamps older than $t_{\text{now}} - W$ (where $W$ is the sliding window duration in milliseconds):
   $$\text{ZREMRANGEBYSCORE}(key, -\infty, t_{\text{now}} - W)$$
2. Count remaining timestamps in the current window:
   $$C = \text{ZCARD}(key)$$
3. If $C + \text{cost} \le \text{Limit}$:
   - Add $t_{\text{now}}$ to the sorted set: $\text{ZADD}(key, t_{\text{now}}, \text{requestId})$.
   - Allow request with $\text{remaining} = \text{Limit} - (C + \text{cost})$.
4. Else:
   - Query the score of the oldest element: $t_{\text{oldest}} = \text{ZRANGE}(key, 0, 0)$.
   - Calculate exact $\text{Retry-After}$ seconds until $t_{\text{oldest}}$ exits the window:
     $$\text{retryAfterSeconds} = \left\lceil \frac{(t_{\text{oldest}} + W) - t_{\text{now}}}{1000} \right\rceil$$

### Redis Lua Script (`sliding_window.lua`)
```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local cost = tonumber(ARGV[3]) or 1
local nowMs = tonumber(ARGV[4])
local requestId = ARGV[5]

local clearBefore = nowMs - windowMs
redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)

local current = redis.call('ZCARD', key)

if current + cost <= limit then
    for i = 1, cost do
        redis.call('ZADD', key, nowMs, requestId .. ':' .. i)
    end
    redis.call('EXPIRE', key, math.ceil(windowMs / 1000) + 2)
    return { 1, limit - (current + cost), math.ceil(windowMs / 1000), 0 }
else
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retryAfter = 1
    if #oldest >= 2 then
        local waitMs = (tonumber(oldest[2]) + windowMs) - nowMs
        if waitMs > 0 then retryAfter = math.ceil(waitMs / 1000) end
    end
    return { 0, 0, retryAfter, retryAfter }
end
```

### Trade-Offs
- **Pros**: Microsecond accuracy, continuous rate limiting without boundary artifacts, exact `Retry-After` calculation.
- **Cons**: Higher memory consumption than counters, since each request timestamp is stored in the ZSET. Complexity is $O(\log N + M)$ where $M$ is the number of pruned elements.

---

## 3. Token Bucket

### Mathematical Model & Concept
A bucket has a fixed maximum capacity $C$ and is continuously refilled with tokens at a constant rate $r$ tokens per second.

When a request arrives at $t_{\text{now}}$ with cost $k$ tokens:
1. Compute elapsed time since last refill: $\Delta t = \max(0, t_{\text{now}} - t_{\text{last}})$.
2. Calculate refilled tokens:
   $$\text{tokens}_{\text{new}} = \min(C, \text{tokens} + \Delta t \times r)$$
3. If $\text{tokens}_{\text{new}} \ge k$:
   - Consume tokens: $\text{tokens} \leftarrow \text{tokens}_{\text{new}} - k$.
   - Save balance and updated timestamp $t_{\text{now}}$.
   - Allow request with $\text{remaining} = \lfloor \text{tokens} \rfloor$.
4. Else:
   - Calculate token deficit: $D = k - \text{tokens}_{\text{new}}$.
   - Compute retry duration: $\text{retryAfter} = \lceil D / r \rceil$.
   - Reject request with HTTP 429.

### Redis Lua Script (`token_bucket.lua`)
```lua
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local cost = tonumber(ARGV[3]) or 1
local nowMs = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'lastRefillMs')
local tokens = tonumber(data[1])
local lastRefillMs = tonumber(data[2])

if not tokens or not lastRefillMs then
    tokens = capacity
    lastRefillMs = nowMs
else
    local deltaSec = math.max(0, nowMs - lastRefillMs) / 1000.0
    tokens = math.min(capacity, tokens + deltaSec * refillRate)
    lastRefillMs = nowMs
end

if tokens >= cost then
    tokens = tokens - cost
    redis.call('HMSET', key, 'tokens', tostring(tokens), 'lastRefillMs', tostring(lastRefillMs))
    redis.call('EXPIRE', key, math.ceil(capacity / refillRate) * 2 + 10)
    return { 1, math.floor(tokens), math.ceil((capacity - tokens) / refillRate), 0 }
else
    redis.call('HMSET', key, 'tokens', tostring(tokens), 'lastRefillMs', tostring(lastRefillMs))
    local retryAfter = math.max(1, math.ceil((cost - tokens) / refillRate))
    return { 0, math.floor(tokens), math.ceil((capacity - tokens) / refillRate), retryAfter }
end
```

### Trade-Offs
- **Pros**: Perfectly handles bursty traffic while guaranteeing a fixed long-term average consumption rate. Extremely memory-efficient (Redis Hash with two float/integer fields). $O(1)$ time complexity.
- **Cons**: Requires careful tuning of both burst capacity $C$ and continuous refill rate $r$.

---

## 4. Leaky Bucket

### Mathematical Model & Concept
Unlike Token Bucket (which holds tokens to consume), Leaky Bucket models a virtual queue holding pending units of work. The bucket leaks (discharges) work at a constant rate $L$ per second.

When a request of cost $k$ arrives at $t_{\text{now}}$:
1. Compute elapsed time: $\Delta t = \max(0, t_{\text{now}} - t_{\text{last}})$.
2. Calculate leaked water:
   $$\text{water}_{\text{current}} = \max(0, \text{water} - \Delta t \times L)$$
3. If $\text{water}_{\text{current}} + k \le C$ (where $C$ is maximum queue capacity):
   - Add water: $\text{water} \leftarrow \text{water}_{\text{current}} + k$.
   - Save water level and timestamp $t_{\text{now}}$.
   - Allow request with $\text{remaining} = \lfloor C - \text{water} \rfloor$.
4. Else:
   - Calculate overflow excess: $E = (\text{water}_{\text{current}} + k) - C$.
   - Compute time until enough water leaks: $\text{retryAfter} = \lceil E / L \rceil$.
   - Reject request with HTTP 429.

### Redis Lua Script (`leaky_bucket.lua`)
```lua
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local leakRate = tonumber(ARGV[2])
local cost = tonumber(ARGV[3]) or 1
local nowMs = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'water', 'lastLeakMs')
local water = tonumber(data[1])
local lastLeakMs = tonumber(data[2])

if not water or not lastLeakMs then
    water = 0.0
    lastLeakMs = nowMs
else
    local deltaSec = math.max(0, nowMs - lastLeakMs) / 1000.0
    water = math.max(0.0, water - deltaSec * leakRate)
    lastLeakMs = nowMs
end

if (water + cost) <= capacity then
    water = water + cost
    redis.call('HMSET', key, 'water', tostring(water), 'lastLeakMs', tostring(lastLeakMs))
    redis.call('EXPIRE', key, math.ceil(capacity / leakRate) * 2 + 10)
    return { 1, math.floor(capacity - water), math.ceil(water / leakRate), 0 }
else
    redis.call('HMSET', key, 'water', tostring(water), 'lastLeakMs', tostring(lastLeakMs))
    local retryAfter = math.max(1, math.ceil(((water + cost) - capacity) / leakRate))
    return { 0, 0, math.ceil(water / leakRate), retryAfter }
end
```

### Trade-Offs
- **Pros**: Strictly enforces a constant outflow rate, eliminating sudden spikes and smoothing downstream load. $O(1)$ state complexity in Redis.
- **Cons**: Less forgiving for bursty interactive applications compared to Token Bucket.

---

## 5. Summary Comparison Matrix

| Feature | Fixed Window | Sliding Window | Token Bucket | Leaky Bucket |
| :--- | :--- | :--- | :--- | :--- |
| **Data Structure** | Redis String | Redis ZSET | Redis Hash | Redis Hash |
| **Time Complexity**| $O(1)$ | $O(\log N)$ | $O(1)$ | $O(1)$ |
| **Space Complexity**| $O(1)$ | $O(N)$ | $O(1)$ | $O(1)$ |
| **Burst Tolerance** | Low (Boundary Spike) | Low | High (Up to Capacity) | Smoothed Outflow |
| **State Fields** | Integer counter | Timestamp scores | `tokens`, `lastRefill` | `water`, `lastLeak` |
| **Best Used For** | Simple rate quotas | Exact SLA compliance | Public APIs with bursts | Queue pacing, background jobs |
