package com.mocyx.biosocks.mocnetty;

import com.mocyx.mocnetty.MocServerBootstrap;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

/**
 *
 */
public class MocNettyTest {


    @Test
    public void main() {

        MocServerBootstrap serverBootstrap=new MocServerBootstrap();
        serverBootstrap.start(new InetSocketAddress("0.0.0.0",9030));

    }


}
