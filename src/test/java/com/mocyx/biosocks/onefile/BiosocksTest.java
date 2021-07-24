package com.mocyx.biosocks.onefile;


import com.mocyx.biosocks.util.ConfigDto;
import com.mocyx.biosocks.nio.NioClient;
import com.mocyx.biosocks.nio.NioServer;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


/**
 * curl http://www.baidu.com/
 * curl --socks5-hostname localhost:9713 http://www.baidu.com/
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class BiosocksTest {

    private Thread startClient() {
        ConfigDto configDto = new ConfigDto();
        configDto.setClientPort(9713);
        configDto.setClient("0.0.0.0");
        configDto.setServer("127.0.0.1");
        configDto.setServerPort(9714);
        configDto.setSecret("123456");
        NioClient client = new NioClient(configDto);
        Thread t = new Thread(client);
        t.start();
        return t;
    }

    private Thread startServer() {
        ConfigDto configDto = new ConfigDto();
        configDto.setClientPort(9713);
        configDto.setClient("0.0.0.0");
        configDto.setServer("0.0.0.0");
        configDto.setServerPort(9714);
        configDto.setSecret("123456");
        NioServer server = new NioServer(configDto);
        Thread t = new Thread(server);
        t.start();
        return t;
    }

    @Test
    @SneakyThrows
    public void testBiosocks() {
        startClient();
        Thread t = startServer();
        t.join();
        System.currentTimeMillis();
    }

    @Test
    @SneakyThrows
    public void testClient() {
        ConfigDto configDto = new ConfigDto();
        configDto.setClientPort(9201);
        configDto.setClient("0.0.0.0");
        configDto.setServer("47.243.103.58");
        configDto.setServerPort(9501);
        configDto.setSecret("123456");
        NioClient client = new NioClient(configDto);
        Thread t = new Thread(client);
        t.start();
        t.join();
        System.currentTimeMillis();
    }


}
