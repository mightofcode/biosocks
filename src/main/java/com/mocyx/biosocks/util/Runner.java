package com.mocyx.biosocks.util;

import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.nio.NioClient;
import com.mocyx.biosocks.nio.NioServer;
import com.mocyx.biosocks.udp.UdpServer;
import com.mocyx.biosocks.util.ConfigDto;
import com.mocyx.biosocks.util.EncodeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Objects;

/**
 * @author Administrator
 */
@Component
@Slf4j
public class Runner implements CommandLineRunner {

    public static ConfigDto loadConfig(String path) {
        try {
            String str = FileUtils.readFileToString(new File(path), "utf-8");
            ConfigDto config = JSON.parseObject(str, ConfigDto.class);
            return config;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(0);
        }
        return null;
    }
    public static void saveConfig(String path,ConfigDto configDto) {
        try {
            FileUtils.writeStringToFile(new File(path),JSON.toJSONString(configDto));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(0);
        }
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            return;
        }
        try {
            if (Objects.equals(args[0], "client")) {
                ConfigDto configDto = loadConfig("client.json");
                EncodeUtil.setSecret(configDto.getSecret());
                NioClient client = new NioClient(configDto);
                Thread t = new Thread(client);
                t.start();
                t.join();
            }
            else if (Objects.equals(args[0], "server")) {
                ConfigDto configDto = loadConfig("server.json");
                EncodeUtil.setSecret(configDto.getSecret());
                Thread t = new Thread(new UdpServer(configDto));
                t.start();
                Thread ts = new Thread(new NioServer(configDto));
                ts.start();
                ts.join();
                t.join();
            }
        } catch (Exception e) {
            log.error("error {} ", e.getMessage(), e);
            System.exit(0);
        }


    }
}
