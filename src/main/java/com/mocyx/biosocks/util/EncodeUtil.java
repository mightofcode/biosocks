package com.mocyx.biosocks.util;

public class EncodeUtil {

    public static void simpleXorEncrypt(byte[] data, int off, int len, String secret) {
        byte v = (byte) (secret.hashCode() % 256);
        for (int i = off; i < len+off; i++) {
            data[i] ^= v;
        }
    }

//    public static void simpleXorDecrypt(byte[] data, int off, int len, String secret) {
//        byte v = (byte) (secret.hashCode() % 256);
//        for (int i = off; i < off+len; i++) {
//            data[i] ^= v;
//        }
//    }
}
