-- Leaky Bucket Rate Limiter
-- KEYS[1]: rate limit key, e.g. rl:lb:<identifier>
-- ARGV[1]: bucket capacity (max queue size before overflow)
-- ARGV[2]: leak rate (requests processed/leaked per second)
-- ARGV[3]: request cost (water volume added, default 1)
-- ARGV[4]: current timestamp in milliseconds

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
    local deltaMs = math.max(0, nowMs - lastLeakMs)
    local leaked = (deltaMs / 1000.0) * leakRate
    water = math.max(0.0, water - leaked)
    lastLeakMs = nowMs
end

if (water + cost) <= capacity then
    water = water + cost
    redis.call('HMSET', key, 'water', tostring(water), 'lastLeakMs', tostring(lastLeakMs))
    local expireSeconds = math.ceil((capacity / math.max(0.1, leakRate)) * 2) + 10
    redis.call('EXPIRE', key, expireSeconds)
    local remaining = math.floor(capacity - water)
    local resetSeconds = math.ceil(water / math.max(0.1, leakRate))
    return { 1, remaining, resetSeconds, 0 }
else
    redis.call('HMSET', key, 'water', tostring(water), 'lastLeakMs', tostring(lastLeakMs))
    local excess = (water + cost) - capacity
    local retryAfterSeconds = math.max(1, math.ceil(excess / math.max(0.1, leakRate)))
    local resetSeconds = math.ceil(water / math.max(0.1, leakRate))
    return { 0, 0, resetSeconds, retryAfterSeconds }
end
