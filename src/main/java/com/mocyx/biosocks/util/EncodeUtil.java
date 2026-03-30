package com.mocyx.biosocks.util;

/**
 * @author Administrator
 */
public class EncodeUtil {
    private static String secret = "default";
    private static volatile byte secretByte = (byte) secret.hashCode();

    public synchronized static String setSecret(String secret) {
        EncodeUtil.secret = secret == null ? "default" : secret;
        secretByte = (byte) EncodeUtil.secret.hashCode();
        return EncodeUtil.secret;
    }

    public static void simpleXorEncrypt(byte[] data, int off, int len) {
        if(Global.ENABLE_ENCRYPTION){
            byte v = secretByte;
            for (int i = off; i < len + off; i++) {
                data[i] ^= v;
            }
        }
    }
}
