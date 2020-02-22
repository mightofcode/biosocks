package com.mocyx.biosocks.nio;

import io.netty.channel.socket.SocketChannel;
import lombok.Data;

/**
 * @author Administrator
 */
@Data
public class TunnelDto {
    private SocketChannel local;
    private SocketChannel remote;
    private String remoteAddr;
    private int remotePort;

}
