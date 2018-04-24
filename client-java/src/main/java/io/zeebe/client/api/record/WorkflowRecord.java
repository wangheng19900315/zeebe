package io.zeebe.client.api.record;

public interface WorkflowRecord extends Record
{
    /**
     * @return the BPMN process id of the workflow.
     */
    String getBpmnProcessId();

    /**
     * @return the version of the deployed workflow this instance belongs to.
     */
    int getVersion();

    /**
     * @return the XML representation of the workflow.
     */
    String getBpmnXml();

    /**
     * @return the key of the deployment this workflow belongs to.
     */
    long getDeploymentKey();
}
