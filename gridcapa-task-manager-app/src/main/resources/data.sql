INSERT INTO parameter (id, name, display_order, parameter_type, section_title, value)
    VALUES ('TEST_ID_1', 'TestName1', 1, 'INT', 'First section', '25')
    ON CONFLICT (id) DO
    UPDATE SET name = 'TestName1', display_order = 1, parameter_type = 'INT', section_title = 'First section', value = '25';

INSERT INTO parameter (id, name, display_order, parameter_type, section_title, value)
    VALUES ('TEST_ID_2', 'TestName2', 3, 'BOOLEAN', 'First section', 'true')
    ON CONFLICT (id) DO
    UPDATE SET name = 'TestName2', display_order = 3, parameter_type = 'BOOLEAN', section_title = 'First section', value = 'true';

INSERT INTO parameter (id, name, display_order, parameter_type, section_title, value)
    VALUES ('TEST_ID_3', 'TestName3', 12, 'STRING', 'Second section', 'Strange thing')
    ON CONFLICT (id) DO
    UPDATE SET name = 'TestName3', display_order = 12, parameter_type = 'STRING', section_title = 'Second section', value = 'Strange thing';
