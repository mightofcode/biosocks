package com.mocyx.biosocks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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


    @Override
    public void run(String... args) {

        if (args.length == 0) {
            return;
        }

        try {
            if (Objects.equals(args[0], "client")) {
                client.run();
            } else if (Objects.equals(args[0], "server")) {
                server.run();
            }
        } catch (Exception e) {
            log.error("error {} ", e.getMessage(), e);
            System.exit(0);
        }


    }
}
