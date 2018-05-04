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
package io.zeebe.broker.incident;

import static io.zeebe.broker.test.MsgPackUtil.JSON_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_PAYLOAD;
import static io.zeebe.broker.test.MsgPackUtil.encodeMsgPack;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;

public class IncidentTest
{
    private static final String PROP_PAYLOAD = "payload";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testClient;

    private static final WorkflowDefinition WORKFLOW_INPUT_MAPPING = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("failingTask", t -> t.taskType("test")
                         .input("$.foo", "$.foo"))
            .done();

    private static final WorkflowDefinition WORKFLOW_OUTPUT_MAPPING = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("failingTask", t -> t.taskType("test")
                         .output("$.foo", "$.foo"))
            .done();

    private static final byte[] PAYLOAD;

    static
    {
        final MutableDirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            w.writeString(wrapString("foo"));
            w.writeString(wrapString("bar"));
        });
        PAYLOAD = new byte[buffer.capacity()];
        buffer.getBytes(0, PAYLOAD);
    }

    @Before
    public void init() throws Exception
    {
        testClient = apiRule.topic();
        apiRule.waitForTopic("default-topic", 1);
    }

    @Test
    public void shouldCreateIncidentForInputMappingFailure()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", failureEvent.key())
            .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentForOutputMappingFailure()
    {
        // given
        testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("test", MSGPACK_PAYLOAD);

        // then
        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_COMPLETING);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", failureEvent.key())
            .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldResolveIncidentForInputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord followUpEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_ACTIVATED);
        assertThat(followUpEvent.value()).containsEntry("payload", PAYLOAD);

        final SubscribedRecord incidentResolvedEvent = receiveFirstIncidentEvent(Intent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", followUpEvent.key())
            .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldResolveIncidentForOutputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("test", MSGPACK_PAYLOAD);

        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_COMPLETING);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord followUpEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_COMPLETED);
        assertThat(followUpEvent.value()).containsEntry("payload", PAYLOAD);

        final SubscribedRecord incidentResolvedEvent = receiveFirstIncidentEvent(Intent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", followUpEvent.key())
            .containsEntry("taskKey", -1);
    }


    @Test
    public void shouldCreateIncidentForInvalidResultOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("failingTask", t -> t.taskType("external")
                                       .input("$.string", "$"))
                          .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then incident is created
        final SubscribedRecord incidentEvent = receiveFirstIncidentCommand(Intent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Processing failed, since mapping will result in a non map object (json object).");
    }

    @Test
    public void shouldResolveIncidentForInvalidResultOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .input("$.string", "$"))
                          .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then incident is created
        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'string':{'obj':'test'}}");

        // then
        final SubscribedRecord followUpEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_ACTIVATED);

        final byte[] result = (byte[]) followUpEvent.value()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedRecord incidentResolvedEvent = receiveFirstIncidentEvent(Intent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Processing failed, since mapping will result in a non map object (json object).")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentForInvalidResultOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("failingTask", t -> t.taskType("external")
                                       .input("$.jsonObject", "$")
                                       .output("$.testAttr", "$"))
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':'test'}")));
        receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_ACTIVATED);

        // then incident is created
        final SubscribedRecord incidentEvent = receiveFirstIncidentCommand(Intent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Processing failed, since mapping will result in a non map object (json object).");
    }

    @Test
    public void shouldResolveIncidentForInvalidResultOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .input("$.jsonObject", "$")
                                       .output("$.testAttr", "$"))
                          .done());

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':'test'}")));
        receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_ACTIVATED);

        // then incident is created
        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_COMPLETING);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedRecord followUpEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) followUpEvent.value()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedRecord incidentResolvedEvent = receiveFirstIncidentEvent(Intent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Processing failed, since mapping will result in a non map object (json object).")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentForInAndOutputMappingAndNoTaskCompletePayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("failingTask", t -> t.taskType("external")
                                       .input("$.jsonObject", "$")
                                       .output("$.testAttr", "$"))
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then incident is created
        final SubscribedRecord incidentEvent = receiveFirstIncidentCommand(Intent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Could not apply output mappings: Task was completed without payload");
    }

    @Test
    public void shouldResolveIncidentForInAndOutputMappingAndNoTaskCompletePayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .input("$.jsonObject", "$")
                                       .output("$.testAttr", "$"))
                          .done());

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then incident is created
        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_COMPLETING);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedRecord followUpEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) followUpEvent.value()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedRecord incidentResolvedEvent = receiveFirstIncidentEvent(Intent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Could not apply output mappings: Task was completed without payload")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentForOutputMappingAndNoTaskCompletePayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("failingTask", t -> t.taskType("external")
                                       .output("$.testAttr", "$"))
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then incident is created
        final SubscribedRecord incidentEvent = receiveFirstIncidentCommand(Intent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Could not apply output mappings: Task was completed without payload");
    }

    @Test
    public void shouldResolveIncidentForOutputMappingAndNoTaskCompletePayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .output("$.testAttr", "$"))
                          .done());

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then incident is created
        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_COMPLETING);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedRecord followUpEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) followUpEvent.value()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedRecord incidentResolvedEvent = receiveFirstIncidentEvent(Intent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Could not apply output mappings: Task was completed without payload")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentIfExclusiveGatewayHasNoMatchingCondition()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                          .startEvent()
                          .exclusiveGateway("xor")
                          .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                          .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 12));

        // then incident is created
        final SubscribedRecord incidentEvent = receiveFirstIncidentCommand(Intent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.CONDITION_ERROR.name())
                                         .containsEntry("errorMessage", "All conditions evaluated to false and no default flow is set.")
                                         .containsEntry("activityId", "xor");
    }

    @Test
    public void shouldCreateIncidentIfConditionFailsToEvaluate()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                          .startEvent()
                          .exclusiveGateway("xor")
                          .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                          .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

        // then incident is created
        final SubscribedRecord incidentEvent = receiveFirstIncidentCommand(Intent.CREATE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value()).containsEntry("errorType", ErrorType.CONDITION_ERROR.name())
                                         .containsEntry("errorMessage", "Cannot compare values of different types: STRING and INTEGER")
                                         .containsEntry("activityId", "xor");
    }

    @Test
    public void shouldResolveIncidentForFailedCondition() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                              .startEvent()
                              .exclusiveGateway("xor")
                              .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                              .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

        // then incident is created
        receiveFirstIncidentEvent(Intent.CREATED);

        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.GATEWAY_ACTIVATED);

        // when correct payload is used
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 7).byteArray());

        // then
        final List<SubscribedRecord> records = testClient
                .receiveRecords()
                .limit(r -> r.valueType() == ValueType.WORKFLOW_INSTANCE && r.intent() == Intent.COMPLETED)
                .collect(Collectors.toList());

        // RESOLVE triggers GATEWAY_ACTIVATED, SEQUENCE_FLOW_TAKEN and RESOLVED
        assertThat(records).extracting(r -> r.recordType(), r -> r.valueType(), r -> r.intent())
            .containsSubsequence(
                tuple(RecordType.COMMAND, ValueType.INCIDENT, Intent.RESOLVE),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.GATEWAY_ACTIVATED),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.SEQUENCE_FLOW_TAKEN),
                tuple(RecordType.EVENT, ValueType.INCIDENT, Intent.RESOLVED));

        // SEQUENCE_FLOW_TAKEN triggers the rest of the process
        assertThat(records).extracting(r -> r.recordType(), r -> r.valueType(), r -> r.intent())
            .containsSubsequence(
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.SEQUENCE_FLOW_TAKEN),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.END_EVENT_OCCURRED),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.COMPLETED));
    }

    @Test
    public void shouldResolveIncidentForFailedConditionAfterUploadingWrongPayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                          .startEvent()
                          .exclusiveGateway("xor")
                          .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                          .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("workflow", asMsgPack("foo", "bar"));

        // then incident is created
        receiveFirstIncidentEvent(Intent.CREATED);

        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.GATEWAY_ACTIVATED);

        // when not correct payload is used
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 10).byteArray());

        // then
        List<SubscribedRecord> records = testClient
            .receiveRecords()
            .limit(r -> r.valueType() == ValueType.INCIDENT && r.intent() == Intent.RESOLVE_FAILED)
            .collect(Collectors.toList());

        assertThat(records).extracting(r -> r.recordType(), r -> r.valueType(), r -> r.intent())
            .containsSubsequence(
                tuple(RecordType.COMMAND, ValueType.INCIDENT, Intent.RESOLVE),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.GATEWAY_ACTIVATED),
                tuple(RecordType.EVENT, ValueType.INCIDENT, Intent.RESOLVE_FAILED));

        // when correct payload is used
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 7).byteArray());

        // then
        records = testClient
            .receiveRecords()
            .skipUntil(r -> r.valueType() == ValueType.INCIDENT && r.intent() == Intent.RESOLVE_FAILED)
            .limit(r -> r.valueType() == ValueType.WORKFLOW_INSTANCE && r.intent() == Intent.COMPLETED)
            .collect(Collectors.toList());

        // RESOLVE triggers GATEWAY_ACTIVATED, SEQUENCE_FLOW_TAKEN and RESOLVED
        assertThat(records).extracting(r -> r.recordType(), r -> r.valueType(), r -> r.intent())
            .containsSubsequence(
                tuple(RecordType.COMMAND, ValueType.INCIDENT, Intent.RESOLVE),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.GATEWAY_ACTIVATED),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.SEQUENCE_FLOW_TAKEN),
                tuple(RecordType.EVENT, ValueType.INCIDENT, Intent.RESOLVED));

        // SEQUENCE_FLOW_TAKEN triggers the rest of the process
        assertThat(records).extracting(r -> r.recordType(), r -> r.valueType(), r -> r.intent())
            .containsSubsequence(
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.SEQUENCE_FLOW_TAKEN),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.END_EVENT_OCCURRED),
                tuple(RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, Intent.COMPLETED));
    }

    @Test
    public void shouldResolveIncidentForExclusiveGatewayWithoutMatchingCondition() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("workflow")
                          .startEvent()
                          .exclusiveGateway("xor")
                          .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                              .endEvent()
                          .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                              .endEvent()
                              .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("workflow", asMsgPack("foo", 12));

        // then incident is created
        receiveFirstIncidentEvent(Intent.CREATED);

        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.GATEWAY_ACTIVATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), asMsgPack("foo", 7).byteArray());

        // then
        receiveFirstIncidentEvent(Intent.RESOLVED);
        receiveFirstWorkflowInstanceEvent(Intent.COMPLETED);
    }

    @Test
    public void shouldFailToResolveIncident() throws Exception
    {
        // given
        final WorkflowDefinition modelInstance = Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("failingTask", t -> t.taskType("external")
                             .input("$.foo", "$.foo")
                             .input("$.bar", "$.bar"))
               .done();

        testClient.deploy(modelInstance);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final  SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);
        assertThat(incidentEvent.value()).containsEntry("errorMessage", "No data found for query $.foo.");

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord resolveFailedEvent = receiveFirstIncidentEvent(Intent.RESOLVE_FAILED);
        assertThat(resolveFailedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(resolveFailedEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.bar.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldResolveIncidentAfterPreviousResolvingFailed() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        updatePayload(workflowInstanceKey, failureEvent.key(), MsgPackHelper.EMTPY_OBJECT);

        receiveFirstIncidentEvent(Intent.RESOLVE_FAILED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord incidentResolvedEvent = receiveFirstIncidentEvent(Intent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldResolveMultipleIncidents() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        // create and resolve an first incident
        long workflowInstanceKey = testClient.createWorkflowInstance("process");
        SubscribedRecord failureEvent = receiveFirstWorkflowInstanceEvent(workflowInstanceKey, Intent.ACTIVITY_READY);
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // create a second incident
        workflowInstanceKey = testClient.createWorkflowInstance("process");
        failureEvent = receiveFirstWorkflowInstanceEvent(workflowInstanceKey, Intent.ACTIVITY_READY);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(workflowInstanceKey, Intent.CREATED);

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedRecord incidentResolvedEvent = receiveFirstIncidentEvent(workflowInstanceKey, Intent.RESOLVED);
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfActivityTerminated()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedRecord incidentCreatedEvent = receiveFirstIncidentEvent(Intent.CREATED);

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.DELETED);

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.value().get("activityInstanceKey"));
    }

    @Test
    public void shouldProcessIncidentsAfterMultipleTerminations()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        // create and cancel instance with incident
        long workflowInstanceKey = testClient.createWorkflowInstance("process");
        cancelWorkflowInstance(workflowInstanceKey);

        // create and cancel instance without incident
        workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);
        cancelWorkflowInstance(workflowInstanceKey);

        // create another instance which creates an incident
        workflowInstanceKey = testClient.createWorkflowInstance("process");
        final SubscribedRecord incidentCreatedEvent = receiveFirstIncidentEvent(workflowInstanceKey, Intent.CREATED);

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(workflowInstanceKey, Intent.DELETED);

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.value().get("activityInstanceKey"));
    }

    @Test
    public void shouldCreateIncidentIfTaskHasNoRetriesLeft()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        // when
        failTaskWithNoRetriesLeft();

        // then
        final SubscribedRecord activityEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_ACTIVATED);
        final SubscribedRecord failedEvent = receiveFirstTaskEvent(Intent.FAILED);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("failureEventPosition", failedEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("taskKey", failedEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfTaskRetriesIncreased()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        failTaskWithNoRetriesLeft();

        // when
        updateTaskRetries();

        // then
        final SubscribedRecord taskEvent = receiveFirstTaskEvent(Intent.FAILED);
        final SubscribedRecord activityEvent = receiveFirstWorkflowInstanceEvent(Intent.ACTIVITY_ACTIVATED);
        SubscribedRecord incidentEvent = receiveFirstIncidentCommand(Intent.DELETE);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("taskKey", taskEvent.key());

        incidentEvent = receiveFirstIncidentEvent(Intent.DELETED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("taskKey", taskEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfTaskIsCanceled()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        failTaskWithNoRetriesLeft();

        final SubscribedRecord incidentCreatedEvent = receiveFirstIncidentEvent(Intent.CREATED);

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedRecord taskEvent = receiveFirstTaskEvent(Intent.FAILED);
        SubscribedRecord incidentEvent = receiveFirstIncidentCommand(Intent.DELETE);

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.value().get("activityInstanceKey"))
            .containsEntry("taskKey", taskEvent.key());

        incidentEvent = receiveFirstIncidentEvent(Intent.DELETED);

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.value().get("activityInstanceKey"))
            .containsEntry("taskKey", taskEvent.key());
    }

    @Test
    public void shouldCreateIncidentIfStandaloneTaskHasNoRetriesLeft()
    {
        // given
        createStandaloneTask();

        // when
        failTaskWithNoRetriesLeft();

        // then
        final SubscribedRecord failedEvent = receiveFirstTaskEvent(Intent.FAILED);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.CREATED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("failureEventPosition", failedEvent.position())
            .containsEntry("bpmnProcessId", "")
            .containsEntry("workflowInstanceKey", -1)
            .containsEntry("activityId", "")
            .containsEntry("activityInstanceKey", -1)
            .containsEntry("taskKey", failedEvent.key());
    }

    @Test
    public void shouldDeleteStandaloneIncidentIfTaskRetriesIncreased()
    {
        // given
        createStandaloneTask();

        failTaskWithNoRetriesLeft();

        // when
        updateTaskRetries();

        // then
        final SubscribedRecord taskEvent = receiveFirstTaskEvent(Intent.FAILED);
        final SubscribedRecord incidentEvent = receiveFirstIncidentEvent(Intent.DELETED);

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "")
            .containsEntry("workflowInstanceKey", -1)
            .containsEntry("activityId", "")
            .containsEntry("activityInstanceKey", -1)
            .containsEntry("taskKey", taskEvent.key());
    }

    private SubscribedRecord receiveFirstIncidentEvent(Intent intent)
    {
        return testClient.receiveEvents()
            .ofTypeIncident()
            .withIntent(intent)
            .getFirst();
    }

    private SubscribedRecord receiveFirstIncidentEvent(long wfInstanceKey, Intent intent)
    {
        return testClient.receiveEvents()
            .ofTypeIncident()
            .withIntent(intent)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == wfInstanceKey)
            .findFirst()
            .get();
    }

    private SubscribedRecord receiveFirstIncidentCommand(Intent intent)
    {
        return testClient.receiveCommands()
            .ofTypeIncident()
            .withIntent(intent)
            .getFirst();
    }

    private SubscribedRecord receiveFirstWorkflowInstanceEvent(Intent intent)
    {
        return testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(intent)
            .getFirst();
    }

    private SubscribedRecord receiveFirstWorkflowInstanceEvent(long wfInstanceKey, Intent intent)
    {
        return testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(intent)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == wfInstanceKey)
            .findFirst()
            .get();
    }

    private SubscribedRecord receiveFirstTaskEvent(Intent intent)
    {
        return testClient.receiveEvents()
            .ofTypeTask()
            .withIntent(intent)
            .getFirst();
    }


    private void failTaskWithNoRetriesLeft()
    {
        apiRule.openTaskSubscription("test").await();

        final SubscribedRecord taskEvent = receiveFirstTaskEvent(Intent.LOCKED);

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .key(taskEvent.key())
            .type(ValueType.TASK, Intent.FAIL)
            .command()
                .put("retries", 0)
                .put("type", "failingTask")
                .put("lockOwner", taskEvent.value().get("lockOwner"))
                .put("headers", taskEvent.value().get("headers"))
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.FAILED);
    }

    private void createStandaloneTask()
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .type(ValueType.TASK, Intent.CREATE)
            .command()
                .put("type", "test")
                .put("retries", 3)
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.CREATED);
    }

    private void updateTaskRetries()
    {
        final SubscribedRecord taskEvent = receiveFirstTaskEvent(Intent.FAILED);

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .key(taskEvent.key())
            .type(ValueType.TASK, Intent.UPDATE_RETRIES)
            .command()
                .put("retries", 1)
                .put("type", "test")
                .put("lockOwner", taskEvent.value().get("lockOwner"))
                .put("headers", taskEvent.value().get("headers"))
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.RETRIES_UPDATED);
    }

    private void updatePayload(final long workflowInstanceKey, final long activityInstanceKey, byte[] payload)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, Intent.UPDATE_PAYLOAD)
            .key(activityInstanceKey)
            .command()
                .put("workflowInstanceKey", workflowInstanceKey)
                .put("payload", payload)
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.PAYLOAD_UPDATED);
    }


    private void updatePayload(long workflowInstanceKey, SubscribedRecord activityInstanceEvent, String payload) throws IOException
    {
        updatePayload(workflowInstanceKey, activityInstanceEvent.key(), MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(payload)));
    }

    private void cancelWorkflowInstance(final long workflowInstanceKey)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .key(workflowInstanceKey)
            .type(ValueType.WORKFLOW_INSTANCE, Intent.CANCEL)
            .command()
                .done()
            .sendAndAwait();

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.CANCELED);
    }

}
