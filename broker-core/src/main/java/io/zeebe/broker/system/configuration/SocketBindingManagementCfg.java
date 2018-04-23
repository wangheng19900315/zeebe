package io.zeebe.broker.system.configuration;

public class SocketBindingManagementCfg extends SocketBindingCfg
{
    private String receiveBufferSize = "8MB";

    public SocketBindingManagementCfg()
    {
        port = 51016;
    }

    @Override
    public void applyDefaults(NetworkCfg networkCfg)
    {
        super.applyDefaults(networkCfg);
    }

    public String getReceiveBufferSize()
    {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(String receiveBufferSize)
    {
        this.receiveBufferSize = receiveBufferSize;
    }
}
