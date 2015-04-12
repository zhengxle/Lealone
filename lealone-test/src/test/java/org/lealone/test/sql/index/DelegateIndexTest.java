/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.test.sql.index;

import org.junit.Test;
import org.lealone.test.sql.TestBase;

public class DelegateIndexTest extends TestBase {
    @Test
    public void run() {
        executeUpdate("DROP TABLE IF EXISTS DelegateIndexTest");
        executeUpdate("CREATE TABLE IF NOT EXISTS DelegateIndexTest(date_time TIMESTAMP primary key, intcol INT)");

        executeUpdate("INSERT INTO DelegateIndexTest(date_time, intcol) VALUES('1970-01-01 00:00:01.0', 12)");

        sql = "select * from DelegateIndexTest where date_time='1970-01-01 00:00:01.0'";
        printResultSet();
    }
}
