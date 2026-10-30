-- Fixed Window Counter Rate Limiter
-- KEYS[1]: rate limit key, e.g. rl:fw:<identifier>:<window_bucket>
-- ARGV[1]: max limit
-- ARGV[2]: window size in seconds
-- ARGV[3]: request cost (tokens/requests count)
-- ARGV[4]: current timestamp in milliseconds

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local cost = tonumber(ARGV[3]) or 1
local nowMs = tonumber(ARGV[4])

local current = redis.call('INCRBY', key, cost)
if current == cost then
    redis.call('EXPIRE', key, windowSeconds + 1)
end

local ttl = redis.call('TTL', key)
if ttl <= 0 then
    ttl = windowSeconds > 0 and windowSeconds or 1
end

if current <= limit then
    local remaining = limit - current
    return { 1, remaining, ttl, 0 }
else
    return { 0, 0, ttl, ttl }
end
