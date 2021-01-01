package com.mocyx.biosocks.domain.entity;

import lombok.Data;

@Data
public class ProxyStat {
    private String remote;
    private Long remotePort;
    private Long byteUp = 0L;
    private Long byteDown = 0L;
    private Long upInSec = 0L;
    private Long downInSec = 0L;
    private Boolean closed = false;
}
