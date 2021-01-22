package com.mocyx.biosocks.bio.protocol;


import com.mocyx.biosocks.util.ByteBufferUtil;
import com.mocyx.biosocks.util.EncodeUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

/**
 * @author Administrator
 */
@Slf4j
public class TunnelProtocol {


    @Data
    public static class TunnelRequest {
        short type;
        String domain;
        int port;

        public void write(ByteBuffer buffer) {
            int oldPos = buffer.position();
            buffer.putShort((short) 0);
            buffer.putShort(type);
            if (type == TunnelMsgType.REQ_CONNECT_DOMAIN.getV()) {
                ByteBufferUtil.writeSmallString(buffer, domain);
                buffer.putInt(port);
            }
            short len = (short) ((buffer.position() - oldPos) - 2);
            EncodeUtil.simpleXorEncrypt(buffer.array(), oldPos + 2, len);
            buffer.putShort(oldPos, len);
        }

        public static TunnelRequest tryRead(ByteBuffer buffer) {
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

            TunnelRequest request = new TunnelRequest();
            request.type = buffer.getShort();

            if (request.type == TunnelMsgType.REQ_CONNECT_DOMAIN.getV()) {
                request.domain = ByteBufferUtil.readSmallString(buffer);
                request.port = buffer.getInt();
            } else {
                buffer.reset();
                return null;
            }
            return request;
        }
    }

    @Data
    public static class TunnelResponse {
        short type;

        public void write(ByteBuffer buffer) {
            int oldPos = buffer.position();
            buffer.putShort((short) 0);
            buffer.putShort(type);


            int len = (short) (buffer.position() - oldPos - 2);
            EncodeUtil.simpleXorEncrypt(buffer.array(), oldPos + 2, len);

            buffer.putShort(oldPos, (short) len);

        }

        public static TunnelResponse tryRead(ByteBuffer buffer) {
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

            TunnelResponse response = new TunnelResponse();
            response.type = buffer.getShort();
            return response;
        }
    }
}






