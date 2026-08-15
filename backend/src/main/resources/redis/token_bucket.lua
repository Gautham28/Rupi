-- Token bucket using Redis TIME for refill.
-- KEYS[1] = rate key
-- ARGV[1] = capacity
-- ARGV[2] = refill tokens per second
-- ARGV[3] = requested tokens
-- Returns: { allowed (0/1), remaining_tokens, retry_after_ms }

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_per_second = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local time = redis.call('TIME')
local now_ms = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

local data = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts = tonumber(data[2])

if tokens == nil then
  tokens = capacity
  ts = now_ms
end

local elapsed = math.max(0, now_ms - ts)
local refill = (elapsed / 1000.0) * refill_per_second
tokens = math.min(capacity, tokens + refill)
ts = now_ms

local allowed = 0
local retry_after_ms = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  local missing = requested - tokens
  retry_after_ms = math.ceil((missing / refill_per_second) * 1000)
end

redis.call('HSET', key, 'tokens', tokens, 'ts', ts)
local ttl_ms = math.ceil((capacity / refill_per_second) * 2000)
if ttl_ms < 2000 then
  ttl_ms = 2000
end
redis.call('PEXPIRE', key, ttl_ms)

-- Redis Lua numeric returns become Long in Java
return { allowed, math.floor(tokens), retry_after_ms }
