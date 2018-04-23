package io.zeebe.broker.system.configuration;

public class SocketBindingClientApiCfg extends SocketBindingCfg
{
    private String controlMessageBufferSize = "8MB";

    public SocketBindingClientApiCfg()
    {
        port = 51015;
    }

    public String getControlMessageBufferSize()
    {
        return controlMessageBufferSize;
    }

    public void setControlMessageBufferSize(String controlMessageBufferSize)
    {
        this.controlMessageBufferSize = controlMessageBufferSize;
    }
}
