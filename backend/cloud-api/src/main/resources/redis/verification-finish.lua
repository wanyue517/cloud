-- KEYS[1] 验证码 Key
-- ARGV[1] 请求 owner
-- ARGV[2] COMMIT / ROLLBACK

local key = KEYS[1]

if redis.call('HGET', key, 'state') ~= 'RESERVED' then
    return 0
end

if redis.call('HGET', key, 'owner') ~= ARGV[1] then
    return 0
end

if ARGV[2] == 'COMMIT' then
    redis.call('DEL', key)
    return 1
end

if ARGV[2] == 'ROLLBACK' then
    redis.call('HSET', key,
        'state', 'READY',
        'owner', ''
    )
    return 1
end

return 0