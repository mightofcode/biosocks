package com.mocyx.biosocks.protocol;


import lombok.Getter;

/**
 * @author Administrator
 */
public enum TunnelMsgType {
    REQ_CONNECT_DOMAIN(1),
    RES_CONNECT_SUCCESS(129),
    RES_CONNECT_FAIL(130),
    ;

    @Getter
    int v;

    TunnelMsgType(int v) {
        this.v = v;
    }
}

