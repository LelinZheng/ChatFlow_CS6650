\timing on

-- Most active room and user (used as inputs for queries below)
SELECT room_id FROM messages GROUP BY room_id ORDER BY COUNT(*) DESC LIMIT 1;
SELECT user_id FROM messages GROUP BY user_id ORDER BY COUNT(*) DESC LIMIT 1;

-- Query 1: messages for a room in time range (LIMIT 1000, target < 100ms)
SELECT message_id, room_id, user_id, username, content, message_type, created_at
FROM messages
WHERE room_id = '1'
  AND created_at BETWEEN '2026-03-24T18:00:00Z'::timestamptz AND '2026-03-24T18:45:00Z'::timestamptz
ORDER BY created_at ASC
LIMIT 1000;

-- Query 2: user message history (target < 200ms)
SELECT message_id, room_id, user_id, username, content, message_type, created_at
FROM messages
WHERE user_id = (SELECT user_id FROM messages GROUP BY user_id ORDER BY COUNT(*) DESC LIMIT 1)
ORDER BY created_at DESC
LIMIT 1000;

-- Query 3: count active users in time window (target < 500ms)
SELECT COUNT(DISTINCT user_id)
FROM messages
WHERE created_at BETWEEN '2026-03-24T18:00:00Z'::timestamptz AND '2026-03-24T18:45:00Z'::timestamptz;

-- Query 4: rooms a user participated in with last activity (target < 50ms)
SELECT room_id, MAX(created_at) AS last_activity
FROM messages
WHERE user_id = (SELECT user_id FROM messages GROUP BY user_id ORDER BY COUNT(*) DESC LIMIT 1)
GROUP BY room_id
ORDER BY last_activity DESC;
