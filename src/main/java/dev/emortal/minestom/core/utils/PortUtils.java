package dev.emortal.minestom.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;

public class PortUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortUtils.class);

    /**
     * @param port    Port to check
     * @param address Address to check
     * @return True if the port is used, false if not.
     */
    public static boolean isPortUsed(String address, int port) {
        try (Socket socket = new Socket(address, port)) {
            socket.setSoTimeout(10);
            return true;
        } catch (ConnectException e) {
            return false;
        } catch (IOException e) {
            LOGGER.error("Error while checking if port is used", e);
            return false;
        }
    }

    /**
     * @param port Port to check
     * @return True if the port is used, false if not.
     */
    public static boolean isPortUsed(int port) {
        return isPortUsed("localhost", port);
    }
}
