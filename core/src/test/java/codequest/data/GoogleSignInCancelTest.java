package codequest.data;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cancelling a Google sign-in must end the wait for the browser at once and must not leave a listener open. */
class GoogleSignInCancelTest {

    @Test
    void cancellingClosesTheListenerSoTheWaitEnds() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            GoogleSignIn.Cancel cancel = new GoogleSignIn.Cancel();
            cancel.attach(server);
            assertFalse(cancel.isCancelled());
            Thread waiter = new Thread(() -> {
                try {
                    server.accept(); // what the sign-in does while the browser is open
                } catch (Exception expected) {
                    // closed by cancel
                }
            });
            waiter.start();
            Thread.sleep(100);
            cancel.cancel();
            waiter.join(2000);
            assertFalse(waiter.isAlive(), "the wait should end as soon as it is cancelled");
            assertTrue(cancel.isCancelled());
            assertTrue(server.isClosed());
            assertThrows(SocketException.class, server::accept);
        }
    }

    @Test
    void cancellingBeforeTheListenerExistsClosesItAsSoonAsItAppears() throws Exception {
        GoogleSignIn.Cancel cancel = new GoogleSignIn.Cancel();
        cancel.cancel();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            cancel.attach(server);
            assertTrue(server.isClosed());
        }
    }

    @Test
    void anAttemptReportsWhetherItWasCancelled() {
        DatabaseManager.GoogleAttempt attempt = new DatabaseManager.GoogleAttempt();
        assertFalse(attempt.cancelled());
        attempt.cancel();
        assertTrue(attempt.cancelled());
    }
}
