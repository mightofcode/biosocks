package com.mocyx.biosocks.nio.tunnel;


import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.bio.protocol.TunnelMsgType;
import com.mocyx.biosocks.nio.ByteBuffUtil;
import com.mocyx.biosocks.util.EncodeUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author Administrator
 */
@Slf4j
public class TunnelMsgDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        in.markReaderIndex();
        if (in.readableBytes() < 2) {
            return;
        }
        int size = in.readShort();
        if (in.readableBytes() < size) {
            in.resetReaderIndex();
            return;
        } else {
            byte[] arr = new byte[size];
            in.readBytes(arr);
            EncodeUtil.simpleXorEncrypt(arr, 0, arr.length);
            ByteBuf buf = Unpooled.wrappedBuffer(arr);

            int type = buf.readShort();

            if (type == TunnelMsgType.REQ_CONNECT_DOMAIN.getV()) {
                TunnelRequest request = new TunnelRequest();
                request.setType((short) type);
                request.setDomain(ByteBuffUtil.readString(buf));
                request.setPort(buf.readInt());
                out.add(request);
                log.debug("REQ_CONNECT_DOMAIN {}", JSON.toJSONString(request));
            } else if (type == TunnelMsgType.RES_CONNECT_SUCCESS.getV()) {
                TunnelResponse response = new TunnelResponse();
                response.setType((short) type);
                out.add(response);
                log.debug("RES_CONNECT_SUCCESS {}", JSON.toJSONString(response));
            } else if (type == TunnelMsgType.RES_CONNECT_FAIL.getV()) {
                TunnelResponse response = new TunnelResponse();
                response.setType((short) type);
                out.add(response);
                log.debug("RES_CONNECT_FAIL {}", JSON.toJSONString(response));
            } else {
                log.error("error type", type);
            }

        }
    }
}