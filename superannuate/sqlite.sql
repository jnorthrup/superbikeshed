-- rm /tmp/sa.sqlite ;time sqlite3 /tmp/sa.sqlite <sqlite.sql ;echo -e ".mode column\n.headers on\nselect * from  superannuated1909 limit 2;" |sqlite3 /tmp/sa.sqlite ; ls -lh superannuated1909.fwf  /tmp/sa.sqlite


CREATE TABLE fixed_width_table
(
    full_string CHAR
);

.import superannuated1909.fwf fixed_width_table
CREATE TABLE superannuated1909
(
    'SalesNo'     INT,
    'SalesAreaID' TEXT,
    'date'        TEXT,
    'PluNo'       TEXT,
    'ItemName'    TEXT,
    'Quantity'    FLOAT,
    'Amount'      FLOAT,
    'TransMode'   TEXT
);

-- CREATE TABLE superannuated1909 as
INSERT INTO superannuated1909
SELECT CAST(SUBSTR(full_string, 1 + 0, 11 - 0) as NUMERIC)  AS 'SalesNo',
       RTRIM(SUBSTR(full_string, 1 + 11, 15 - 11))          AS 'SalesAreaID',
       DATE(SUBSTR(full_string, 1 + 15, 25 - 15))           AS 'date',
       RTRIM(SUBSTR(full_string, 1 + 25, 40 - 25))          AS 'PluNo',
       RTRIM(SUBSTR(full_string, 1 + 40, 60 - 40))          AS 'ItemName',
       CAST(SUBSTR(full_string, 1 + 60, 82 - 60) as float)  AS 'Quantity',
       CAST(SUBSTR(full_string, 1 + 82, 103 - 82) as float) AS 'Amount',
       RTRIM(SUBSTR(full_string, 1 + 103, 108 - 103))       AS 'TransMode'
FROM fixed_width_table;

DROP TABLE fixed_width_table;
VACUUM;
--
-- jdbc2json  
--  TYPES=['"VIEW"'] ../../jdbc2json/bin/jdbctocouchdbbulk.sh http://127.0.0.1:5984/foo1_ jdbc:sqlite:/tmp/sa.sqlite

-- bitbanging
--  BULKSIZE=5 LIMIT=10 TERSE=true  ../../jdbc2json/bin/jdbctocouchdbbulk.sh http://127.0.0.1:9989/foo1_ jdbc:sqlite:/tmp/sa.sqlite

