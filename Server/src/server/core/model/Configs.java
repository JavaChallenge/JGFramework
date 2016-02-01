package server.core.model;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 * Created by Hadi on 2/21/2015 10:58 AM.
 */
public class Configs {

    public static class OutputHandlerConfig {
        public final boolean sendToUI;
        public final int timeInterval;
        public final boolean sendToFile;
        public final String filePath;
        public final int bufferSize;

        public OutputHandlerConfig(boolean sendToUI, int timeInterval,
                                   boolean sendToFile, String filePath,
                                   int bufferSize) {
            this.sendToUI = sendToUI;
            this.timeInterval = timeInterval;
            this.sendToFile = sendToFile;
            this.filePath = filePath;
            this.bufferSize = bufferSize;
        }
    }

    public static class TimeConfig {
        public final long clientResponseTime;
        public final long simulateTimeout;
        public final long turnTimeout;

        public TimeConfig(long clientResponseTime, long simulateTimeout, long turnTimeout) {
            this.clientResponseTime = clientResponseTime;
            this.simulateTimeout = simulateTimeout;
            this.turnTimeout = turnTimeout;
        }
    }

    public static class ClientConfig {
        public final int port;

        public ClientConfig(int port) {
            this.port = port;
        }
    }

    public static class TerminalConfig {
        public final String token;
        public final int port;

        public TerminalConfig(String token, int port) {
            this.token = token;
            this.port = port;
        }
    }

    public static class UIConfig {
        public final boolean enable;
        public final String token;
        public final int port;

        public UIConfig(boolean enable, String token, int port) {
            this.enable = enable;
            this.token = token;
            this.port = port;
        }
    }

    public static final Charset DEFAULT_ENCODING = Charset.forName("UTF-8");

    private static Configs configs;

    public static Configs getConfigs() {
        return configs;
    }

    public static void load(String path) throws IOException {
        load(path, Charset.forName("UTF-8"));
    }

    public static void load(String path, Charset encoding) throws IOException {
        File configFile = new File(path);
        String config = new String(Files.readAllBytes(configFile.toPath()), encoding);
        configs = new Gson().fromJson(config, Configs.class);
    }


    public final OutputHandlerConfig outputHandler;
    public final TimeConfig turnTimeout;
    public final ClientConfig client;
    public final TerminalConfig terminal;
    public final UIConfig ui;

    public Configs(OutputHandlerConfig outputHandlerConfig,
                   TimeConfig turnTimeout, ClientConfig client,
                   TerminalConfig terminal, UIConfig ui) {
        this.outputHandler = outputHandlerConfig;
        this.turnTimeout = turnTimeout;
        this.client = client;
        this.terminal = terminal;
        this.ui = ui;
    }

}
