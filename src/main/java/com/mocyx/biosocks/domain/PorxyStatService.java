package com.mocyx.biosocks.domain;

import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.domain.entity.ProxyStat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class PorxyStatService {

    public PorxyStatService() {
        Thread t = new Thread(new Worker());
        t.start();
        Thread t1 = new Thread(new Printer());
        t1.start();
    }

    Map<String, ProxyStat> stats = new HashMap<>();

    private void updateData(NetMessage message) {
        synchronized (this) {
            String k = String.format("%s %s", message.getDomain(), message.getPort());
            if (!stats.containsKey(k)) {
                ProxyStat s = new ProxyStat();
                s.setRemote(message.domain);
                s.setRemotePort(message.port);
                stats.put(k, s);
            }
            ProxyStat s = stats.get(k);
            s.setUpInSec(message.byteUp + s.getUpInSec());
            s.setDownInSec(message.byteDown + s.getDownInSec());
            s.setByteUp(message.byteUp + s.getByteUp());
            s.setByteDown(message.byteDown + s.getByteDown());
            if (Objects.equals(message.getType(), "close")) {
                s.setClosed(true);
            }

        }
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    NetMessage message = queue.take();
                    updateData(message);
                    log.debug("msg {}", JSON.toJSONString(message));
                    System.currentTimeMillis();
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    private class Printer implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                    synchronized (PorxyStatService.this) {
                        List<ProxyStat> datas = new ArrayList<>(stats.values());
                        datas.sort(new Comparator<ProxyStat>() {
                            @Override
                            public int compare(ProxyStat o1, ProxyStat o2) {
                                String k1 = String.format("%s %s", o1.getRemote(), o1.getRemotePort());
                                String k2 = String.format("%s %s", o1.getRemote(), o1.getRemotePort());
                                return k1.compareTo(k2);
                            }
                        });
                        //
                        StringBuilder sb = new StringBuilder();
                        sb.append("\ndump start\n");
                        for (ProxyStat s : datas) {
                            String line = String.format("%s %s %sB %sB %sB/s %sB/s\n",
                                    s.getRemote(), s.getRemotePort(), s.getByteUp(),
                                    s.getByteDown(), s.getUpInSec(), s.getDownInSec());
                            sb.append(line);
                            s.setDownInSec(0L);
                            s.setUpInSec(0L);
                        }
                        sb.append("dump end");
                        log.info(sb.toString());
                        //
                        Iterator<Map.Entry<String, ProxyStat>> it = stats.entrySet().iterator();
                        while (it.hasNext()) {
                            Map.Entry<String, ProxyStat> pair = it.next();
                            if (pair.getValue().getClosed()) {
                                it.remove();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Data
    public static class NetMessage {
        private String domain;
        private Long port;
        private Long byteDown = 0L;
        private Long byteUp = 0L;
        private String type;
    }

    private BlockingQueue<NetMessage> queue = new ArrayBlockingQueue<NetMessage>(2000);

    public void onOpen(String domain, Long port) {
        NetMessage netMessage = new NetMessage();
        netMessage.setType("open");
        netMessage.setDomain(domain);
        netMessage.setPort(port);
        netMessage.setByteDown(0L);
        netMessage.setByteUp(0L);
        queue.offer(netMessage);
    }

    public void onUp(String domain, Long port, Long size) {
        NetMessage netMessage = new NetMessage();
        netMessage.setType("up");
        netMessage.setDomain(domain);
        netMessage.setPort(port);
        netMessage.setByteDown(0L);
        netMessage.setByteUp(size);
        queue.offer(netMessage);
    }

    public void onDown(String domain, Long port, Long size) {
        NetMessage netMessage = new NetMessage();
        netMessage.setType("down");
        netMessage.setDomain(domain);
        netMessage.setPort(port);
        netMessage.setByteDown(size);
        netMessage.setByteUp(0L);
        queue.offer(netMessage);
    }

    public void onClose(String domain, Long port) {
        NetMessage netMessage = new NetMessage();
        netMessage.setType("close");
        netMessage.setDomain(domain);
        netMessage.setPort(port);
        netMessage.setByteDown(0L);
        netMessage.setByteUp(0L);
        queue.offer(netMessage);
    }
}
