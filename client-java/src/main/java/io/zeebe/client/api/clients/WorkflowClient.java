package io.zeebe.client.api.clients;

import io.zeebe.client.api.commands.*;

/**
 * A client with access to all workflow-related operations:
 * <li>deploy a workflow
 * <li>create a workflow instance
 * <li>cancel a workflow instance
 * <li>update the payload of a workflow instance
 */
public interface WorkflowClient
{

    /**
     * Command to deploy new workflows.
     *
     * <pre>
     * workflowClient
     *  .newDeployCommand()
     *  .addResourceFile("~/wf/workflow1.bpmn")
     *  .addResourceFile("~/wf/workflow2.bpmn")
     *  .send();
     * </pre>
     *
     * @return a builder for the command
     */
    DeployWorkflowCommandStep1 newDeployCommand();

    /**
     * Command to create/start a new instance of a workflow.
     *
     * <pre>
     * workflowClient
     *  .newCreateInstanceCommand()
     *  .bpmnProcessId("my-process")
     *  .latestVersion()
     *  .payload(json)
     *  .send();
     * </pre>
     *
     * @return a builder for the command
     */
    CreateWorkflowInstanceCommandStep1 newCreateInstanceCommand();

    /**
     * Command to cancel a workflow instance.
     *
     * <pre>
     * workflowClient
     *  .newCancelInstanceCommand()
     *  .event(workflowInstanceEvent)
     *  .send();
     * </pre>
     *
     * The workflow instance is specified by the given event. The event must be
     * the latest event of the workflow instance to ensure that the command is
     * based on the latest state of the workflow instance. If it's not the
     * latest one then the command is rejected.
     *
     * @return a builder for the command
     */
    CancelWorkflowInstanceCommandStep1 newCancelInstanceCommand();

    /**
     * Command to update the payload of a workflow instance.
     *
     * <pre>
     * workflowClient
     *  .newUpdatePayloadCommand()
     *  .event(workflowInstanceEvent)
     *  .payload(json)
     *  .send();
     * </pre>
     *
     * The workflow instance is specified by the given event. The event must be
     * the latest event of the workflow instance to ensure that the command is
     * based on the latest state of the workflow instance. If it's not the
     * latest one then the command is rejected.
     * <p>
     * If the workflow instance failed because of a payload-related incident
     * then it will try to resolve the incident with the given payload.
     *
     * @return a builder for the command
     */
    UpdatePayloadWorkflowInstanceCommandStep1 newUpdatePayloadCommand();
}
