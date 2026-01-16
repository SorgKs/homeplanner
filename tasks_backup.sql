/* WARNING: Script requires that SQLITE_DBCONFIG_DEFENSIVE be disabled */
PRAGMA foreign_keys=OFF;
BEGIN TRANSACTION;
CREATE TABLE tasks (
	id INTEGER NOT NULL, 
	title VARCHAR(255) NOT NULL, 
	description TEXT, 
	task_type VARCHAR(9) NOT NULL, 
	recurrence_type VARCHAR(15), 
	recurrence_interval INTEGER, 
	interval_days INTEGER, 
	reminder_time DATETIME NOT NULL, 
	alarm BOOLEAN NOT NULL, 
	alarm_time DATETIME, 
	enabled BOOLEAN NOT NULL, 
	completed BOOLEAN NOT NULL, 
	group_id INTEGER, 
	last_shown_at DATETIME, 
	created_at DATETIME NOT NULL, 
	updated_at DATETIME NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(group_id) REFERENCES groups (id)
);
INSERT INTO tasks VALUES(1,'Test Task Today','A test task for today','ONE_TIME',NULL,NULL,NULL,'2026-01-13 09:00:00.000000',0,NULL,1,0,NULL,NULL,'2026-01-13 23:16:13.074308','2026-01-13 23:16:13.074308');
INSERT INTO tasks VALUES(2,'Daily Recurring Task','A recurring task every day','RECURRING','DAILY',1,NULL,'2026-01-13 09:00:00.000000',0,NULL,1,0,NULL,NULL,'2026-01-13 23:16:13.359466','2026-01-13 23:16:13.359466');
INSERT INTO tasks VALUES(3,'Weekly Task','A task every week','RECURRING','WEEKLY',1,NULL,'2026-01-14 09:00:00.000000',0,NULL,1,0,NULL,NULL,'2026-01-13 23:16:13.904484','2026-01-13 23:16:13.904484');
INSERT INTO tasks VALUES(4,'Future One-time Task','A task in the future','ONE_TIME',NULL,NULL,NULL,'2026-01-14 09:00:00.000000',0,NULL,1,0,NULL,NULL,'2026-01-13 23:16:13.975752','2026-01-13 23:16:13.975752');
CREATE TABLE task_history (
	id INTEGER NOT NULL, 
	task_id INTEGER, 
	action VARCHAR(11) NOT NULL, 
	action_timestamp DATETIME NOT NULL, 
	iteration_date DATETIME, 
	meta_data TEXT, 
	comment TEXT, 
	PRIMARY KEY (id), 
	FOREIGN KEY(task_id) REFERENCES tasks (id) ON DELETE SET NULL
);
INSERT INTO task_history VALUES(1,1,'CREATED','2026-01-13 23:16:13.158161',NULL,'{"title": "Test Task Today", "description": "A test task for today", "task_type": "one_time", "recurrence_type": null, "recurrence_interval": null, "interval_days": null, "reminder_time": "2026-01-13T09:00:00", "alarm": false, "alarm_time": null, "group_id": null, "assigned_user_ids": []}','13 января в 09:00');
INSERT INTO task_history VALUES(2,2,'CREATED','2026-01-13 23:16:13.697869',NULL,'{"title": "Daily Recurring Task", "description": "A recurring task every day", "task_type": "recurring", "recurrence_type": "daily", "recurrence_interval": 1, "interval_days": null, "reminder_time": "2026-01-13T09:00:00", "alarm": false, "alarm_time": null, "group_id": null, "assigned_user_ids": []}','в 09:00 каждый день');
INSERT INTO task_history VALUES(3,3,'CREATED','2026-01-13 23:16:13.942762',NULL,'{"title": "Weekly Task", "description": "A task every week", "task_type": "recurring", "recurrence_type": "weekly", "recurrence_interval": 1, "interval_days": null, "reminder_time": "2026-01-14T09:00:00", "alarm": false, "alarm_time": null, "group_id": null, "assigned_user_ids": []}','каждый среда в 09:00');
INSERT INTO task_history VALUES(4,4,'CREATED','2026-01-13 23:16:14.015413',NULL,'{"title": "Future One-time Task", "description": "A task in the future", "task_type": "one_time", "recurrence_type": null, "recurrence_interval": null, "interval_days": null, "reminder_time": "2026-01-14T09:00:00", "alarm": false, "alarm_time": null, "group_id": null, "assigned_user_ids": []}','14 января в 09:00');
CREATE TABLE task_users (
	task_id INTEGER NOT NULL, 
	user_id INTEGER NOT NULL, 
	PRIMARY KEY (task_id, user_id), 
	FOREIGN KEY(task_id) REFERENCES tasks (id) ON DELETE CASCADE, 
	FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);
COMMIT;
