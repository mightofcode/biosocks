package com.mocyx.biosocks.nio.tunnel;

import lombok.Data;

@Data
public class TunnelRequest implements TunnelMsg{
    short type;
    String domain;
    int port;
}
