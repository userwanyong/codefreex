-- Seed data for P5 integration tests

MERGE INTO app (id, app_name, description, init_prompt, code_gen_type, status, deploy_key, user_id, is_public, is_featured, priority, view_count, like_count, edit_time, create_time, update_time, is_delete)
VALUES (1001, 'TestApp1', 'Test application 1', 'build a todo app', 'html_single', 'draft', 'DK_TEST_001', 2001, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

MERGE INTO app (id, app_name, description, init_prompt, code_gen_type, status, deploy_key, user_id, is_public, is_featured, priority, view_count, like_count, edit_time, create_time, update_time, is_delete)
VALUES (1002, 'TestApp2', 'Test application 2', 'build a calculator', 'html_single', 'draft', 'DK_TEST_002', 2002, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
