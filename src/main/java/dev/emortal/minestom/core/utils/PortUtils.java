package dev.emortal.minestom.core.utils;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;

public final class PortUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortUtils.class);

    /**
     * @param port    Port to check
     * @param address Address to check
     * @return True if the port is used, false if not.
     */
    public static boolean isPortUsed(@NotNull String address, int port) {
        try (var socket = new Socket(address, port)) {
            socket.setSoTimeout(10);
            return true;
        } catch (ConnectException exception) {
            return false;
        } catch (IOException exception) {
            LOGGER.error("Error while checking if port is used", exception);
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

    private PortUtils() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}
