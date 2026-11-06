package com.paymentprocessor.startup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

final class PackagedService implements AutoCloseable {
    private final Process process;
    private final Path portFile;
    private final Path log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();
    private URI base;

    PackagedService(String module, Path temporaryDirectory, String broker, String... settings) throws IOException {
        Path jar = Path.of(System.getProperty(module + ".jar")).toAbsolutePath();
        assertThat(jar).isRegularFile();
        log = Files.createDirectories(Path.of(System.getProperty("startup.logs"))).resolve(module + ".log");
        portFile = temporaryDirectory.resolve(module + ".port");
        String java = ProcessHandle.current().info().command().orElseThrow();
        var command = new ArrayList<>(List.of(java, "-Xmx256m", "-DPORTFILE=" + portFile,
                "-jar", jar.toString(), "--server.address=127.0.0.1", "--server.port=0",
                "--context.listener.classes=org.springframework.boot.web.context.WebServerPortFileWriter",
                "--management.endpoint.health.probes.enabled=true", "--spring.kafka.bootstrap-servers=" + broker));
        command.addAll(List.of(settings));
        process = new ProcessBuilder(command).directory(jar.getParent().toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        System.out.println("Starting " + jar.getFileName() + "; output in " + log);
    }

    void awaitReadiness() throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (System.nanoTime() < deadline) {
            assertThat(process.isAlive()).as("application exited during startup; see %s", log).isTrue();
            String port = Files.exists(portFile) ? Files.readString(portFile).trim() : "";
            if (!port.isEmpty()) {
                base = URI.create("http://127.0.0.1:" + port);
                try {
                    var response = get("/actuator/health/readiness");
                    if (response.statusCode() == 200 && "UP".equals(json.readTree(response.body()).path("status").asText())) {
                        System.out.println("Readiness UP on " + base);
                        return;
                    }
                } catch (ConnectException | HttpTimeoutException starting) {
                    // The port can be allocated before HTTP requests are served.
                }
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Readiness timed out; see " + log);
    }

    JsonNode getJson(String path) throws Exception {
        var response = get(path);
        assertThat(response.statusCode()).as("%s returned %s", path, response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(5)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void close() throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("packaged service stopped").isTrue();
        }
    }
}
