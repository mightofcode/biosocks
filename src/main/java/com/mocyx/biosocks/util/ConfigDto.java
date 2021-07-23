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
    private String server;
    private Integer serverPort;
    private String client;
    private Integer clientPort;
    private String secret = "default";
}
