package me.blueat.logmaker.plugins.maker;

import com.google.common.net.InetAddresses;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IPMakerTest {

    @Test
    void getData() {
        // Given
        IPMaker ipMaker = new IPMaker("testMaker", "ip");
        ipMaker.getThread().start();

        // When
        String data = ipMaker.getData();

        // Then
        assertNotNull(data);
        assertTrue(InetAddresses.isInetAddress(data));

        ipMaker.getThread().interrupt();
    }

    @Test
    void isThread() {
        // Given
        IPMaker ipMaker = new IPMaker("testMaker", "ip");

        // When & Then
        assertTrue(ipMaker.isThread());
    }
}
