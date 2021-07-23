package com.mocyx.biosocks.protocol;

import com.mocyx.biosocks.util.ByteBufferUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * @author Administrator
 */
@Slf4j
public class SocksProtocol {

    public enum Socks5State {
        shake,
        connect,
        transfer
    }


    @Data
    public static class SocksShakeRequestDto {
        byte ver;
        byte nmethods;
        byte[] methods;

        public static SocksShakeRequestDto tryRead(ByteBuffer buffer) {
            if (buffer.remaining() < 2) {
                return null;
            }
            buffer.mark();
            SocksShakeRequestDto res = new SocksShakeRequestDto();
            res.ver = buffer.get();
            res.nmethods = buffer.get();
            if (buffer.remaining() < res.nmethods) {
                buffer.reset();
                return null;
            }
            res.methods = new byte[res.nmethods];
            buffer.get(res.methods);
            return res;
        }
    }

    @Data
    public static class SocksShakeResponseDto {
        byte ver;
        byte method;

        public void write(ByteBuffer buffer) {
            buffer.put(ver);
            buffer.put(method);
        }
    }

    @Data
    public static class SocksConnectRequestDto {
        byte ver;
        byte cmd;
        byte rsv;
        byte atyp;
        /**
         * atyp=0x1
         */
        InetAddress addr;
        /**
         * atyp=0x3
         */
        String domain;
        short port;

        public static SocksConnectRequestDto tryRead(ByteBuffer buffer) {
            if (buffer.remaining() < 4) {
                return null;
            }
            buffer.mark();
            SocksConnectRequestDto res = new SocksConnectRequestDto();
            res.ver = buffer.get();
            res.cmd = buffer.get();
            res.rsv = buffer.get();
            res.atyp = buffer.get();
            if (res.atyp == 0x01) {
                if (buffer.remaining() < 6) {
                    buffer.reset();
                    return null;
                }
                res.addr = ByteBufferUtil.readIpAddr(buffer);
                res.port = (short) ByteBufferUtil.readPort(buffer);

            } else if (res.atyp == 0x03) {
                if (buffer.remaining() < 1) {
                    buffer.reset();
                    return null;
                }
                int len = (((int) buffer.get()) & 0xff);
                if (buffer.remaining() < len + 2) {
                    buffer.reset();
                    return null;
                }
                res.domain = ByteBufferUtil.readString(buffer, len);
                res.port = (short) ByteBufferUtil.readPort(buffer);
            } else {
                throw new RuntimeException("atyp error");
            }
            return res;

        }

    }

    @Data
    public static class SocksConnectResponseDto {
        byte ver;
        byte cmd;
        byte rsv;
        byte atyp;
        /**
         * atyp=0x1
         */
        InetAddress addr;
        /**
         * atyp=0x3
         */
        String domain;
        short port;

        public void write(ByteBuffer buffer) {
            buffer.put(ver);
            buffer.put(cmd);
            buffer.put(rsv);
            buffer.put(atyp);

            if (atyp == 0x01) {
                buffer.put(addr.getAddress());
                ByteBufferUtil.writePort(buffer, port);
            } else if (atyp == 0x03) {
                ByteBufferUtil.writeSmallString(buffer, domain);
                ByteBufferUtil.writePort(buffer, port);
            }

        }
    }


}
