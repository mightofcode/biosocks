package com.mocyx.biosocks.nio;

import io.netty.util.AttributeKey;

/**
 * @author Administrator
 */
public class NioUtil {
    public static final AttributeKey<TunnelDto> TUNNEL_KEY = AttributeKey.valueOf("socks.tunnel");
}
