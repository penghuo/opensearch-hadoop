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
package org.opensearch.integration.storm;

import java.util.Map;

import org.opensearch.hadoop.rest.RestUtils;
import org.opensearch.hadoop.util.unit.TimeValue;
import org.opensearch.storm.OpenSearchSpout;
import org.junit.Test;

import org.apache.storm.topology.TopologyBuilder;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

public class AbstractSpoutSimpleRead extends AbstractStormSpoutTests {

    public AbstractSpoutSimpleRead(Map conf, String index) {
        super(conf, index);
    }

    @Test
    public void testSimpleRead() throws Exception {
        String target = index + "/basic-read";

        RestUtils.touch(index);
        RestUtils.postData(target, "{\"message\" : \"Hello World\",\"message_date\" : \"2014-05-25\"}".getBytes());
        RestUtils.postData(target, "{\"message\" : \"Goodbye World\",\"message_date\" : \"2014-05-25\"}".getBytes());
        RestUtils.refresh(index);

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("opensearch-spout", new TestSpout(new OpenSearchSpout(target)));
        builder.setBolt("test-bolt", new CapturingBolt()).shuffleGrouping("opensearch-spout");

        MultiIndexSpoutStormSuite.run(index + "simple", builder.createTopology(), AbstractStormSuite.COMPONENT_HAS_COMPLETED);

        AbstractStormSuite.COMPONENT_HAS_COMPLETED.waitFor(1, TimeValue.timeValueSeconds(10));

        assertTrue(RestUtils.exists(target));
        String results = RestUtils.get(target + "/_search?");
        assertThat(results, containsString("Hello"));
        assertThat(results, containsString("Goodbye"));

        System.out.println(CapturingBolt.CAPTURED);
        assertThat(CapturingBolt.CAPTURED.size(), is(2));
    }
}