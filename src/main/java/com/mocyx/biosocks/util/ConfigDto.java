package com.mocyx.biosocks.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Administrator
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigDto {
    private volatile String server;
    private volatile Integer serverPort;
    private volatile String client;
    private volatile Integer clientPort;
    private volatile String secret = "default";
}
