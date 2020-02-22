package com.mocyx.biosocks.util;

/**
 * @author Administrator
 */
public class EncodeUtil {
    private static String secret = "default";

    public synchronized static String setSecret(String secret) {
        return secret;
    }

    public static void simpleXorEncrypt(byte[] data, int off, int len) {
//        byte v = (byte) (secret.hashCode() % 256);
//        for (int i = off; i < len + off; i++) {
//            data[i] ^= v;
//        }
    }
}
