package ua.rivne.electro.config;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for HTTP proxies.
 * Loads proxy list and settings from proxy.conf file.
 */
public class ProxyConfig {

    private final List<ProxyEntry> proxies = new ArrayList<>();
    private int timeoutSeconds = 15;
    private boolean notifyAdminOnFailure = true;

    /**
     * Represents a single proxy entry.
     */
    public static class ProxyEntry {
        private final String host;
        private final int port;
        private final String username;
        private final String password;

        public ProxyEntry(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public boolean hasAuth() { return username != null && !username.isEmpty(); }

        public Proxy toSocksProxy() {
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
        }

        public Proxy toHttpProxy() {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        }

        /**
         * @deprecated Use toSocksProxy() or toHttpProxy() instead
         */
        public Proxy toProxy() {
            return toSocksProxy();
        }

        /**
         * Returns Base64-encoded credentials for Proxy-Authorization header.
         * Format: "Basic base64(username:password)"
         */
        public String getAuthHeader() {
            if (!hasAuth()) return null;
            String credentials = username + ":" + password;
            return "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        }

        /**
         * Sets up global authenticator for this proxy.
         * Must be called before making HTTP request through this proxy.
         */
        public void setupAuthenticator() {
            if (hasAuth()) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                });
            }
        }

        /**
         * Clears the global authenticator.
         */
        public static void clearAuthenticator() {
            Authenticator.setDefault(null);
        }

        @Override
        public String toString() {
            return host + ":" + port + (hasAuth() ? " (with auth)" : "");
        }
    }

    /**
     * Loads proxy configuration from proxy.conf resource file.
     */
    public static ProxyConfig load() {
        ProxyConfig config = new ProxyConfig();

        try (InputStream is = ProxyConfig.class.getResourceAsStream("/proxy.conf")) {
            if (is == null) {
                System.out.println("📡 No proxy.conf found, using direct connection only");
                return config;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    // Skip empty lines and comments
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // Parse settings
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String key = parts[0].trim().toLowerCase();
                        String value = parts[1].trim();

                        switch (key) {
                            case "timeout_seconds":
                                try {
                                    config.timeoutSeconds = Integer.parseInt(value);
                                } catch (NumberFormatException e) {
                                    System.err.println("⚠️ Invalid timeout_seconds: " + value);
                                }
                                break;
                            case "notify_admin_on_failure":
                                config.notifyAdminOnFailure = Boolean.parseBoolean(value);
                                break;
                        }
                        continue;
                    }

                    // Parse proxy entry: host:port or host:port:username:password
                    ProxyEntry entry = parseProxyLine(line);
                    if (entry != null) {
                        config.proxies.add(entry);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load proxy.conf: " + e.getMessage());
        }

        System.out.println("📡 Loaded " + config.proxies.size() + " proxies, timeout: " + config.timeoutSeconds + "s");
        return config;
    }

    /**
     * Parses a proxy line: host:port or host:port:username:password
     */
    private static ProxyEntry parseProxyLine(String line) {
        try {
            String[] parts = line.split(":");
            if (parts.length < 2) {
                System.err.println("⚠️ Invalid proxy format: " + line);
                return null;
            }

            String host = parts[0].trim();
            int port = Integer.parseInt(parts[1].trim());
            String username = parts.length > 2 ? parts[2].trim() : null;
            String password = parts.length > 3 ? parts[3].trim() : null;

            return new ProxyEntry(host, port, username, password);
        } catch (NumberFormatException e) {
            System.err.println("⚠️ Invalid proxy port: " + line);
            return null;
        }
    }

    public List<ProxyEntry> getProxies() { return proxies; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getTimeoutMillis() { return timeoutSeconds * 1000; }
    public boolean isNotifyAdminOnFailure() { return notifyAdminOnFailure; }
    public boolean hasProxies() { return !proxies.isEmpty(); }
}

