-- KEYS[1] 验证码 Key
-- KEYS[2] 重发冷却 Key
-- ARGV[1] 验证码 HMAC
-- ARGV[2] 签发 ID
-- ARGV[3] 有效期秒数
-- ARGV[4] 冷却秒数

if redis.call('EXISTS', KEYS[2]) == 1 then
    return -1
end

if redis.call('HGET', KEYS[1], 'state') == 'RESERVED' then
    return -2
end

redis.call('DEL', KEYS[1])

redis.call('HSET', KEYS[1],
    'hash', ARGV[1],
    'issueId', ARGV[2],
    'failures', 0,
    'state', 'READY',
    'owner', ''
)

redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
redis.call('SET', KEYS[2], '1', 'EX', tonumber(ARGV[4]))

return 1