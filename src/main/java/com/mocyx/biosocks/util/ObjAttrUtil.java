package com.mocyx.biosocks.util;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ObjAttrUtil {
    private Map<Object, Map<String, Object>> objAttrs = new HashMap<>();

    public synchronized Object getAttr(Object obj, String k) {
        Map<String, Object> map = objAttrs.get(obj);
        if (map == null) {
            return null;
        }
        return map.get(k);
    }

    public synchronized void setAttr(Object obj, String k, Object value) {
        Map<String, Object> map = objAttrs.get(obj);
        if (map == null) {
            objAttrs.put(obj, new HashMap<>());
            map = objAttrs.get(obj);
        }
        map.put(k, value);
    }
    public synchronized void delAttr(Object obj, String k, Object value) {
        Map<String, Object> map = objAttrs.get(obj);
        if (map == null) {
            objAttrs.put(obj, new HashMap<>());
            map = objAttrs.get(obj);
        }
        map.remove(k);
    }

    public synchronized void delObj(Object obj) {
        objAttrs.remove(obj);
    }

}