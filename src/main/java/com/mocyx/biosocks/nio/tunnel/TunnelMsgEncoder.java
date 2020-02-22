package com.mocyx.biosocks.nio.tunnel;


import com.mocyx.biosocks.nio.ByteBuffUtil;
import com.mocyx.biosocks.util.EncodeUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @author Administrator
 */
public class TunnelMsgEncoder extends MessageToByteEncoder<TunnelMsg> {

    final static ThreadLocal<ByteBuf> buffers = ThreadLocal.withInitial(() -> ByteBufAllocator.DEFAULT.buffer(4096));

    @Override
    protected void encode(ChannelHandlerContext ctx, TunnelMsg msg, ByteBuf out) throws Exception {
        if (msg instanceof TunnelRequest) {

            TunnelRequest request = (TunnelRequest) msg;
            ByteBuf tmpBuf = buffers.get();
            tmpBuf.clear();
            tmpBuf.writeShort(request.type);
            ByteBuffUtil.writeString(tmpBuf, request.getDomain());
            tmpBuf.writeInt(request.port);

            out.writeShort(tmpBuf.writerIndex());

            byte[] data = new byte[tmpBuf.readableBytes()];
            tmpBuf.readBytes(data);
            EncodeUtil.simpleXorEncrypt(data,0,data.length);
            out.writeBytes(data);

            //out.writeBytes(tmpBuf);

        } else if (msg instanceof TunnelResponse) {
            TunnelResponse response = (TunnelResponse) msg;
            ByteBuf tmpBuf = buffers.get();
            tmpBuf.clear();

            tmpBuf.writeShort(response.type);

            out.writeShort(tmpBuf.writerIndex());
            byte[] data = new byte[tmpBuf.readableBytes()];
            tmpBuf.readBytes(data);
            EncodeUtil.simpleXorEncrypt(data,0,data.length);
            out.writeBytes(data);
            //out.writeBytes(tmpBuf);
        }
    }
}
