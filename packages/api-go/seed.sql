INSERT INTO users (floatplane_user_id, api_key) VALUES ('testuser', 'testuserapikey') ON CONFLICT DO NOTHING;
INSERT INTO device_sessions (id, floatplane_user_id, api_key, dpop_jkt, device_info) VALUES ('session1', 'testuser', 'testtoken', 'testjkt', 'Test Device') ON CONFLICT DO NOTHING;
