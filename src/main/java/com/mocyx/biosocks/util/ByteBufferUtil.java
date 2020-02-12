package com.mocyx.biosocks.util;

import com.mocyx.biosocks.exception.ProxyException;
import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author mocyx
 */
@Slf4j
public class ByteBufferUtil {

    static public String readString(ByteBuffer buffer, int len) {
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        return new String(bytes);
    }

    static public int readPort(ByteBuffer buffer) {
        byte[] shortBytes = new byte[2];
        buffer.get(shortBytes);

        int v1 = (shortBytes[0] & 0xff);
        int v2 = (shortBytes[1] & 0xff);

        return (v1 << 8 | v2);
    }

    static public void writeSmallString(ByteBuffer buffer, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buffer.put((byte) bytes.length);
        buffer.put(bytes);
    }

    static public void writeLargeString(ByteBuffer buffer, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
    }

    static public void writeDataArr(ByteBuffer buffer, byte[] data) {
        if (data == null) {
            buffer.putInt(0);
        }
        buffer.putInt(data.length);
        buffer.put(data);
    }

    static public byte[] readDataArr(ByteBuffer buffer) {
        int len = buffer.getInt();
        if (len == 0) {
            return null;
        }
        byte[] data = new byte[len];
        buffer.get(data);
        return data;
    }

    static public String readLargeString(ByteBuffer buffer) {
        int len = buffer.getShort();
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        return new String(bytes);
    }

    static public void writeAddr(ByteBuffer buffer, InetSocketAddress address) {
        ByteBufferUtil.writeLargeString(buffer, address.getHostString());
        ByteBufferUtil.writePort(buffer, address.getPort());
    }

    static public InetSocketAddress readAddr(ByteBuffer buffer) {
        String name = ByteBufferUtil.readLargeString(buffer);
        int port = ByteBufferUtil.readPort(buffer);
        return new InetSocketAddress(name, port);
    }

    static public void writePort(ByteBuffer buffer, int port) {
        byte b1 = (byte) (port >> 8 & 0xff);
        byte b0 = (byte) (port & 0xff);
        buffer.put(b1);
        buffer.put(b0);
    }

    static public InetAddress readIpAddr(ByteBuffer buffer) {
        try {
            byte[] bytes = new byte[4];
            buffer.get(bytes);
            return Inet4Address.getByAddress(bytes);
        } catch (Exception e) {
            log.error("readIpAddr fail {}", e.getMessage(), e);
            throw new ProxyException(e.getMessage());
        }
    }
}
