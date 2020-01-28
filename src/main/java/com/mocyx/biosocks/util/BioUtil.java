package com.mocyx.biosocks.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

@Slf4j
public class BioUtil {

    public static int write(SocketChannel channel, ByteBuffer byteBuffer) throws IOException {
        int len = channel.write(byteBuffer);
        log.debug("write {} {} ", len, channel);
        return len;
    }

    public static int read(SocketChannel channel, ByteBuffer byteBuffer) throws IOException {
        int len = channel.read(byteBuffer);
        log.debug("read {} {} ", len, channel);
        return len;
    }

}
