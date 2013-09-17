package org.uli.logging;

import org.junit.Test;
import static org.junit.Assert.*;

public class EchoServerTest {

    @Test
    public void testEcho() {
        EchoServer echoServer = new EchoServer();
        String echo = echoServer.echo("UliWasHere!");
        assertEquals("UliWasHere!", echo);
    }
}
