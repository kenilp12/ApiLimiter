-- Token Bucket Rate Limiter
-- KEYS[1]: rate limit key, e.g. rl:tb:<identifier>
-- ARGV[1]: bucket capacity (max burst size)
-- ARGV[2]: refill rate (tokens per second)
-- ARGV[3]: request cost (tokens consumed, default 1)
-- ARGV[4]: current timestamp in milliseconds

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
    local deltaMs = math.max(0, nowMs - lastRefillMs)
    local tokensToAdd = (deltaMs / 1000.0) * refillRate
    tokens = math.min(capacity, tokens + tokensToAdd)
    lastRefillMs = nowMs
end

if tokens >= cost then
    tokens = tokens - cost
    redis.call('HMSET', key, 'tokens', tostring(tokens), 'lastRefillMs', tostring(lastRefillMs))
    local expireSeconds = math.ceil((capacity / math.max(0.1, refillRate)) * 2) + 10
    redis.call('EXPIRE', key, expireSeconds)
    local remaining = math.floor(tokens)
    local resetSeconds = math.ceil((capacity - tokens) / math.max(0.1, refillRate))
    return { 1, remaining, resetSeconds, 0 }
else
    redis.call('HMSET', key, 'tokens', tostring(tokens), 'lastRefillMs', tostring(lastRefillMs))
    local missingTokens = cost - tokens
    local retryAfterSeconds = math.max(1, math.ceil(missingTokens / math.max(0.1, refillRate)))
    local resetSeconds = math.ceil((capacity - tokens) / math.max(0.1, refillRate))
    return { 0, math.floor(tokens), resetSeconds, retryAfterSeconds }
end
