package com.mocyx.biosocks.entity;

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
public class JSONConfigDto {
    private String server;
    private Integer serverPort;
    private String client;
    private Integer clientPort;
}
