package com.mocyx.biosocks;

import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.bio.BioClient;
import com.mocyx.biosocks.bio.BioServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
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


    @Autowired
    private BioClient client;
    @Autowired
    private BioServer server;

    @Autowired
    private UdpServer udpServer;


    private void loadConfig(String path) {
        try {
            String str = FileUtils.readFileToString(new File(path), "utf-8");
            Global.config = JSON.parseObject(str, ConfigDto.class);
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
                loadConfig("client.json");

                client.run();

            } else if (Objects.equals(args[0], "server")) {
                loadConfig("server.json");

                Thread t = new Thread(udpServer);
                t.start();
                Thread ts = new Thread(server);
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
