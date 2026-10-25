-- US-35: notifications are now scoped by the active user role.
-- The existing rows were demo/test data, so they are discarded before the
-- application recreates user_notification with the NOT NULL recipient_role column.
TRUNCATE TABLE user_notification;