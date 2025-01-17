/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */
 
/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.opensearch.hadoop.integration.hive;

import java.util.List;

import org.opensearch.hadoop.rest.RestUtils;
import org.opensearch.hadoop.util.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.opensearch.hadoop.util.TestUtils.docEndpoint;
import static org.opensearch.hadoop.util.TestUtils.resource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import static org.opensearch.hadoop.integration.hive.HiveSuite.provisionOpenSearchLib;
import static org.opensearch.hadoop.integration.hive.HiveSuite.server;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class AbstractHiveExtraTests {

    @Before
    public void before() throws Exception {
        provisionOpenSearchLib();
        HiveSuite.before();
    }

    @After
    public void after() throws Exception {
        HiveSuite.after();
    }

    @Test
    public void testQuery() throws Exception {
        String resource = resource("cars", "transactions", TestUtils.getOpenSearchClusterInfo().getMajorVersion());

        if (!RestUtils.exists(resource)) {
            RestUtils.bulkData(resource, "cars-bulk.txt");
            RestUtils.refresh("cars");
        }

        String drop = "DROP TABLE IF EXISTS cars2";
        String create = "CREATE EXTERNAL TABLE cars2 ("
                + "color STRING,"
                + "price BIGINT,"
                + "sold TIMESTAMP, "
                + "alias STRING) "
                + HiveSuite.tableProps(resource, null, "'opensearch.mapping.names'='alias:&c'");

        String query = "SELECT * from cars2";
        String count = "SELECT count(*) from cars2";

        server.execute(drop);
        server.execute(create);
        List<String> result = server.execute(query);
        System.out.println("Cars Result: " + result);
        assertEquals(6, result.size());
        assertTrue(result.get(0).contains("foobar"));
        server.execute("ANALYZE TABLE cars2 COMPUTE STATISTICS"); // Hive caches counts on external data and so it needs to be updated.
        result = server.execute(count);
        System.out.println("Count Result: " + result);
        assertEquals("6", result.get(0));
    }

    @Test
    public void testDate() throws Exception {
        String resource = "hive-date-as-long";
        RestUtils.touch("hive-date-as-long");
        RestUtils.putMapping("hive-date-as-long", "data", "org/opensearch/hadoop/hive/hive-date-typeless-mapping.json");

        String docEndpoint = docEndpoint(resource, "data", TestUtils.getOpenSearchClusterInfo().getMajorVersion());

        RestUtils.postData(docEndpoint + "/1", "{\"type\" : 1, \"&t\" : 1407239910771}".getBytes());

        RestUtils.refresh("hive-date-as-long");

        String drop = "DROP TABLE IF EXISTS nixtime";
        String create = "CREATE EXTERNAL TABLE nixtime ("
                + "type     BIGINT,"
                + "dte     TIMESTAMP)"
                + HiveSuite.tableProps("hive-date-as-long", null, "'opensearch.mapping.names'='dte:&t'");

        String query = "SELECT * from nixtime WHERE type = 1";

        String string = RestUtils.get(docEndpoint + "/1");
        assertThat(string, containsString("140723"));

        server.execute(drop);
        server.execute(create);
        List<String> result = server.execute(query);

        assertThat(result.size(), is(1));
        assertThat(result.toString(), containsString("2014-08-05"));
    }
}