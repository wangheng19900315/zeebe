/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow;

import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_VERSION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.util.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.data.ResourceType;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.util.StreamUtil;

public class CreateDeploymentTest
{
    private static final WorkflowDefinition WORKFLOW = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .endEvent()
            .done();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldCreateDeploymentWithBpmnXml()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .type(ValueType.DEPLOYMENT, Intent.CREATE)
                .command()
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resources", Collections.singletonList(deploymentResource(bpmnXml(WORKFLOW), "process.bpmn")))
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.position()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
        assertThat(resp.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(resp.intent()).isEqualTo(Intent.CREATED);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnDeployedWorkflowDefinitions()
    {
        // when
        ExecuteCommandResponse resp = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, WORKFLOW);

        // then
        List<Map<String, Object>> deployedWorkflows = (List<Map<String, Object>>) resp.getValue().get("deployedWorkflows");
        assertThat(deployedWorkflows).hasSize(1);
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_VERSION, 1);

        // when deploy the workflow definition a second time
        resp = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, WORKFLOW);

        // then the workflow definition version is increased
        deployedWorkflows = (List<Map<String, Object>>) resp.getValue().get("deployedWorkflows");
        assertThat(deployedWorkflows).hasSize(1);
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_VERSION, 2);
    }

    @Test
    public void shouldWriteWorkflowEvent()
    {
        // when
        final long deploymentKey = apiRule.topic().deploy(ClientApiRule.DEFAULT_TOPIC_NAME, WORKFLOW);

        // then
        final SubscribedRecord workflowEvent = apiRule.topic()
                .receiveEvents()
                .ofTypeWorkflow()
                .withIntent(Intent.CREATED)
                .getFirst();
        assertThat(workflowEvent.key()).isGreaterThanOrEqualTo(0L).isNotEqualTo(deploymentKey);
        assertThat(workflowEvent.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry("deploymentKey", deploymentKey)
            .containsEntry("bpmnXml", bpmnXml(WORKFLOW));
    }

    @Test
    public void shouldCreateDeploymentWithMultipleResources()
    {
        // given
        final WorkflowDefinition definition1 = Bpmn.createExecutableWorkflow("process1").startEvent().done();
        final WorkflowDefinition definition2 = Bpmn.createExecutableWorkflow("process2").startEvent().done();

        final List<Map<String, Object>> resources = Arrays.asList(deploymentResource(bpmnXml(definition1), "process1.bpmn"),
                                                                  deploymentResource(bpmnXml(definition2), "process2.bpmn"));

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .type(ValueType.DEPLOYMENT, Intent.CREATE)
                .command()
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resources", resources)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(resp.intent()).isEqualTo(Intent.CREATED);

        final List<SubscribedRecord> workflowEvents = apiRule.topic()
                .receiveEvents()
                .ofTypeWorkflow()
                .withIntent(Intent.CREATED)
                .limit(2)
                .collect(toList());

        assertThat(workflowEvents)
            .extracting(s -> s.value().get(PROP_WORKFLOW_BPMN_PROCESS_ID))
            .contains("process1", "process2");
    }

    @Test
    public void shouldCreateDeploymentResourceWithMultipleWorkflows() throws IOException
    {
        // given
        final InputStream resourceAsStream = getClass().getResourceAsStream("/workflows/collaboration.bpmn");

        // when
        final ExecuteCommandResponse resp = apiRule.topic()
                .deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
                                    StreamUtil.read(resourceAsStream),
                                    ResourceType.BPMN_XML.name(),
                                    "collaboration.bpmn");

        // then
        assertThat(resp.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(resp.intent()).isEqualTo(Intent.CREATED);

        final List<SubscribedRecord> workflowEvents = apiRule.topic()
                .receiveEvents()
                .ofTypeWorkflow()
                .withIntent(Intent.CREATED)
                .limit(2)
                .collect(toList());

        assertThat(workflowEvents)
            .extracting(s -> s.value().get(PROP_WORKFLOW_BPMN_PROCESS_ID))
            .contains("process1", "process2");
    }

    @Test
    public void shouldCreateDeploymentWithMultipleResourcesAndWorkflows() throws IOException
    {
        // given
        final WorkflowDefinition singleWorkflow = Bpmn.createExecutableWorkflow("singleProcess").startEvent().done();
        final InputStream multipleWorkflowsResource = getClass().getResourceAsStream("/workflows/collaboration.bpmn");

        final List<Map<String, Object>> resources = Arrays.asList(deploymentResource(bpmnXml(singleWorkflow), "process1.bpmn"),
                                                                  deploymentResource(StreamUtil.read(multipleWorkflowsResource), "collaboration.bpmn"));

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .type(ValueType.DEPLOYMENT, Intent.CREATE)
                .command()
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resources", resources)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(resp.intent()).isEqualTo(Intent.CREATED);

        final List<SubscribedRecord> workflowEvents = apiRule.topic()
                .receiveEvents()
                .ofTypeWorkflow()
                .withIntent(Intent.CREATED)
                .limit(3)
                .collect(toList());

        assertThat(workflowEvents)
            .extracting(s -> s.value().get(PROP_WORKFLOW_BPMN_PROCESS_ID))
            .contains("singleProcess", "process1", "process2");
    }

    @Test
    public void shouldRejectDeploymentIfTopicNotExists()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.topic().deployWithResponse("not-existing", WORKFLOW);

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(resp.intent()).isEqualTo(Intent.CREATE);
        assertThat((String) resp.getValue().get("errorMessage")).isEqualTo("No topic found with name not-existing");
    }

    @Test
    public void shouldRejectDeploymentIfNotValid()
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process").done();

        // when
        final ExecuteCommandResponse resp = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, definition);

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(resp.intent()).isEqualTo(Intent.CREATE);
        assertThat((String) resp.getValue().get("errorMessage")).contains("The process must contain at least one none start event.");
    }

    @Test
    public void shouldRejectDeploymentIfOneResourceIsNotValid()
    {
        // given
        final WorkflowDefinition invalidDefinition = Bpmn.createExecutableWorkflow("process").done();

        final List<Map<String, Object>> resources = Arrays.asList(deploymentResource(bpmnXml(WORKFLOW), "process1.bpmn"),
                                                                  deploymentResource(bpmnXml(invalidDefinition), "process2.bpmn"));

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .type(ValueType.DEPLOYMENT, Intent.CREATE)
                .command()
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resources", resources)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(resp.intent()).isEqualTo(Intent.CREATE);
        assertThat((String) resp.getValue().get("errorMessage"))
            .contains("Resource 'process2.bpmn':")
            .contains("The process must contain at least one none start event.");
    }

    @Test
    public void shouldRejectDeploymentIfNoResources()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .type(ValueType.DEPLOYMENT, Intent.CREATE)
                .command()
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resources", Collections.emptyList())
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(resp.intent()).isEqualTo(Intent.CREATE);
        assertThat((String) resp.getValue().get("errorMessage")).isEqualTo("Deployment doesn't contain a resource to deploy.");
    }

    @Test
    public void shouldRejectDeploymentIfNotParsable()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.topic()
                .deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
                                    "not a workflow".getBytes(UTF_8),
                                    ResourceType.BPMN_XML.name(),
                                    "invalid.bpmn");

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(resp.intent()).isEqualTo(Intent.CREATE);
        assertThat((String) resp.getValue().get("errorMessage"))
            .contains("Failed to deploy resource 'invalid.bpmn':")
            .contains("Failed to read BPMN model");
    }

    @Test
    public void shouldRejectDeploymentIfConditionIsInvalid()
    {
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("workflow")
                                     .startEvent()
                                     .exclusiveGateway()
                                     .sequenceFlow(s -> s.condition("foobar"))
                                         .endEvent()
                                     .sequenceFlow(s -> s.defaultFlow())
                                         .endEvent()
                                         .done();

        // when
        final ExecuteCommandResponse resp = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, definition);

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(resp.intent()).isEqualTo(Intent.CREATE);
        assertThat((String) resp.getValue().get("errorMessage")).contains("The condition 'foobar' is not valid");
    }

    @Test
    public void shouldCreateDeploymentWithYamlWorfklow() throws Exception
    {
        // given
        final File yamlFile = new File(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
        final String yamlWorkflow = Files.contentOf(yamlFile, UTF_8);

        // when
        final ExecuteCommandResponse resp = apiRule.topic()
                .deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME,
                                    yamlWorkflow.getBytes(UTF_8),
                                    ResourceType.YAML_WORKFLOW.name(),
                                    "simple-workflow.yaml");

        // then
        assertThat(resp.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(resp.intent()).isEqualTo(Intent.CREATED);

        final SubscribedRecord workflowEvent = apiRule.topic()
                .receiveEvents()
                .ofTypeWorkflow()
                .withIntent(Intent.CREATED)
                .getFirst();

        assertThat(workflowEvent.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "yaml-workflow")
            .containsEntry("deploymentKey", resp.key())
            .containsEntry("bpmnXml", bpmnXml(Bpmn.readFromYamlFile(yamlFile)));
    }

    @Test
    public void shouldCreateWorkflowOnAllPartitions()
    {
        // given
        final int partitions = 3;

        apiRule.createTopic("test", partitions);
        final List<Integer> partitionIds = apiRule.getPartitionsFromTopology("test");

        // when
        apiRule.topic().deploy("test", WORKFLOW);

        // then
        final List<Long> workflowKeys = new ArrayList<>();
        partitionIds.forEach(partitionId ->
        {
            final SubscribedRecord event = apiRule.topic(partitionId)
                    .receiveEvents()
                    .ofTypeWorkflow()
                    .withIntent(Intent.CREATED)
                    .getFirst();

            workflowKeys.add(event.key());
        });

        assertThat(workflowKeys)
            .hasSize(partitions)
            .containsOnly(workflowKeys.get(0));
    }

    @Test
    public void shouldAssignWorkflowVersionsPerTopic()
    {
        // given
        apiRule.createTopic("foo", 1);
        apiRule.createTopic("bar", 1);

        final int fooPartition = apiRule.getSinglePartitionId("foo");
        final int barPartition = apiRule.getSinglePartitionId("bar");

        // when
        apiRule.topic().deploy("foo", WORKFLOW);
        apiRule.topic().deploy("bar", WORKFLOW);

        // then
        final SubscribedRecord eventFoo = apiRule.topic(fooPartition).receiveEvents()
            .ofTypeWorkflow()
            .withIntent(Intent.CREATED)
            .getFirst();
        final SubscribedRecord eventBar = apiRule.topic(barPartition).receiveEvents()
            .ofTypeWorkflow()
            .withIntent(Intent.CREATED)
            .getFirst();

        assertThat(eventFoo.value().get("version")).isEqualTo(1);
        assertThat(eventBar.value().get("version")).isEqualTo(1);
    }

    private Map<String, Object> deploymentResource(final byte[] resource, String name)
    {
        final Map<String, Object> deploymentResource = new HashMap<>();
        deploymentResource.put("resource", resource);
        deploymentResource.put("resourceType", ResourceType.BPMN_XML);
        deploymentResource.put("resourceName", name);

        return deploymentResource;
    }

    private byte[] bpmnXml(final WorkflowDefinition definition)
    {
        return Bpmn.convertToString(definition).getBytes(UTF_8);
    }

}
