package com.mocyx.biosocks.protocol;

import com.mocyx.biosocks.util.ByteBufferUtil;
import com.mocyx.biosocks.util.EncodeUtil;
import lombok.Data;
import lombok.Getter;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * @author Administrator
 */
@Data
public class TunnelUdpProtocol {
    public enum TunnelUdpMsgType {
        REQ_SEND((short) 1),
        RES_RECV((short) 129),
        ;
        @Getter
        short v;

        TunnelUdpMsgType(short v) {
            this.v = v;
        }
    }

    @Data
    public static class TunnelUdpRequest {
        short type;
        InetSocketAddress source;
        InetSocketAddress remote;
        byte[] data;

        public void write(ByteBuffer buffer) {
            int oldPos = buffer.position();
            buffer.putShort((short) 0);
            buffer.putShort(type);

            if (type == TunnelUdpMsgType.REQ_SEND.v) {
                ByteBufferUtil.writeAddr(buffer, source);
                ByteBufferUtil.writeAddr(buffer, remote);
                ByteBufferUtil.writeDataArr(buffer, data);
            } else {
                throw new RuntimeException("type error");
            }

            short len = (short) ((buffer.position() - oldPos) - 2);
            EncodeUtil.simpleXorEncrypt(buffer.array(), oldPos + 2, len);
            buffer.putShort(oldPos, len);
        }

        public static TunnelUdpRequest tryRead(ByteBuffer buffer) {
            if (buffer.remaining() < 2) {
                return null;
            }
            buffer.mark();
            int dataLen = buffer.getShort();

            if (buffer.remaining() < dataLen) {
                buffer.reset();
                return null;
            }

            EncodeUtil.simpleXorEncrypt(buffer.array(), buffer.position(), dataLen);

            TunnelUdpRequest request = new TunnelUdpRequest();
            request.type = buffer.getShort();

            if (request.type == TunnelUdpMsgType.REQ_SEND.v) {

                request.source = ByteBufferUtil.readAddr(buffer);
                request.remote = ByteBufferUtil.readAddr(buffer);
                request.data = ByteBufferUtil.readDataArr(buffer);

            } else {
                throw new RuntimeException("type error");
            }
            return request;
        }

    }

    @Data
    public static class TunnelUdpResponse {
        short type;
        InetSocketAddress source;
        InetSocketAddress remote;
        byte[] data;

        public void write(ByteBuffer buffer) {
            int oldPos = buffer.position();
            buffer.putShort((short) 0);
            buffer.putShort(type);

            if (type == TunnelUdpMsgType.RES_RECV.v) {
                ByteBufferUtil.writeAddr(buffer, source);
                ByteBufferUtil.writeAddr(buffer, remote);
                ByteBufferUtil.writeDataArr(buffer, data);
            } else {
                throw new RuntimeException("type error");
            }

            short len = (short) ((buffer.position() - oldPos) - 2);
            EncodeUtil.simpleXorEncrypt(buffer.array(), oldPos + 2, len);
            buffer.putShort(oldPos, len);
        }

        public static TunnelUdpResponse tryRead(ByteBuffer buffer) {
            if (buffer.remaining() < 2) {
                return null;
            }
            buffer.mark();
            int dataLen = buffer.getShort();

            if (buffer.remaining() < dataLen) {
                buffer.reset();
                return null;
            }

            EncodeUtil.simpleXorEncrypt(buffer.array(), buffer.position(), dataLen);

            TunnelUdpResponse request = new TunnelUdpResponse();
            request.type = buffer.getShort();

            if (request.type == TunnelUdpMsgType.RES_RECV.v) {

                request.source = ByteBufferUtil.readAddr(buffer);
                request.remote = ByteBufferUtil.readAddr(buffer);
                request.data = ByteBufferUtil.readDataArr(buffer);

            } else {
                throw new RuntimeException("type error");
            }
            return request;
        }
    }
}