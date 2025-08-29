package com.example.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class WebhookSolverApplication implements CommandLineRunner {

    private final RestTemplate restTemplate;
    private final Environment env;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebhookSolverApplication(RestTemplate restTemplate, Environment env) {
        this.restTemplate = restTemplate;
        this.env = env;
    }

    public static void main(String[] args) {
        SpringApplication.run(WebhookSolverApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Override
    public void run(String... args) throws Exception {
        String name = env.getProperty("app.name", "John Doe");
        String regNo = env.getProperty("app.regNo", "REG12347");
        String email = env.getProperty("app.email", "john@example.com");
        String finalQuery = env.getProperty("app.finalQuery", null);

        if (finalQuery == null || finalQuery.isBlank()) {
            try (InputStream is = new ClassPathResource("finalQuery.sql").getInputStream()) {
                finalQuery = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (Exception ignored) {}
        }

        if (finalQuery == null || finalQuery.isBlank()) {
            System.err.println("ERROR: finalQuery not provided.");
            return;
        }

        // Step 1: Call generateWebhook
        String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";
        ObjectNode genReq = mapper.createObjectNode();
        genReq.put("name", name);
        genReq.put("regNo", regNo);
        genReq.put("email", email);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> genEntity = new HttpEntity<>(mapper.writeValueAsString(genReq), h);

        ResponseEntity<JsonNode> genResp = restTemplate.postForEntity(generateUrl, genEntity, JsonNode.class);
        if (!genResp.getStatusCode().is2xxSuccessful() || genResp.getBody() == null) {
            System.err.println("Failed to generate webhook. Status: " + genResp.getStatusCodeValue());
            return;
        }

        JsonNode body = genResp.getBody();
        String webhook = getFirst(body, "webhook", "webhookUrl", "webhook_url");
        String accessToken = getFirst(body, "accessToken", "access_token", "token");

        if (webhook == null) {
            System.err.println("Webhook not found in response.");
            return;
        }

        Files.writeString(Path.of("submitted-finalQuery.sql"), finalQuery, StandardCharsets.UTF_8);

        // Step 2: Submit SQL query
        ObjectNode submit = mapper.createObjectNode();
        submit.put("finalQuery", finalQuery);

        HttpHeaders submitHeaders = new HttpHeaders();
        submitHeaders.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null && !accessToken.isBlank()) {
            submitHeaders.set("Authorization", accessToken);
        }
        HttpEntity<String> submitEntity = new HttpEntity<>(mapper.writeValueAsString(submit), submitHeaders);

        ResponseEntity<String> submitResp = restTemplate.postForEntity(webhook, submitEntity, String.class);
        System.out.println("Submit status: " + submitResp.getStatusCodeValue());
        System.out.println("Submit body: " + (submitResp.getBody() == null ? "<empty>" : submitResp.getBody()));
    }

    private static String getFirst(JsonNode n, String... keys) {
        for (String k : keys) if (n.has(k) && !n.get(k).isNull()) return n.get(k).asText();
        return null;
    }
}
