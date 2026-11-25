-- 单 Key 固定窗口；达到限额后不再累加，也不延长窗口。
local count = tonumber(redis.call('GET', KEYS[1]) or '0')
if count >= tonumber(ARGV[1]) then
    return 0
end
count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end
return 1
