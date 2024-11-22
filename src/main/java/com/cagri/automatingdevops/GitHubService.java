package com.cagri.automatingdevops;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


@Service
public class GitHubService {

    @Value("${github.token}")
    private String gitHubToken;

    @Value("${github.api.url}")
    private String gitHubApiUrl;

    @Value("${github.owner}")
    private String gitHubOwner;

    @Value("${github.repo}")
    private String gitHubRepo;

    private final RestTemplate restTemplate;

    public GitHubService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public void mergePullRequest(String githubRepo,int pullNumber) {
        String url = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + githubRepo + "/pulls/" + pullNumber + "/merge";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            System.out.println("PR merged successfully.");
        } else {
            System.out.println("Failed to merge PR: " + response.getBody());
        }
    }

    public void createTagAndRelease(String tagName, String commitSha, String targetBranch, String releaseNotes) {
        String tagListUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/tags";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<Void> listEntity = new HttpEntity<>(headers);

        // Mevcut taglerin kontrolü
        ResponseEntity<String> tagListResponse = restTemplate.exchange(tagListUrl, HttpMethod.GET, listEntity, String.class);
        if (tagListResponse.getStatusCode().is2xxSuccessful()) {
            // Gelen yanıtı parse ederek mevcut tagleri kontrol et
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode tagList = objectMapper.readTree(tagListResponse.getBody());
                for (JsonNode tag : tagList) {
                    String existingTagName = tag.get("name").asText();
                    if (existingTagName.equals(tagName)) {
                        throw new IllegalArgumentException("Tag with name '" + tagName + "' already exists.");
                    }
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse tag list response: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to retrieve tags: " + tagListResponse.getBody());
        }

        // Yeni tag oluşturma ve release işlemleri
        String tagUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/git/refs";

        Map<String, String> tagBody = new HashMap<>();
        tagBody.put("ref", "refs/tags/" + tagName);
        tagBody.put("sha", commitSha);

        HttpEntity<Map<String, String>> tagEntity = new HttpEntity<>(tagBody, headers);

        ResponseEntity<String> tagResponse = restTemplate.exchange(tagUrl, HttpMethod.POST, tagEntity, String.class);
        if (tagResponse.getStatusCode().is2xxSuccessful()) {
            System.out.println("Tag created successfully: " + tagName);
        } else {
            System.out.println("Failed to create tag: " + tagResponse.getBody());
            return;
        }

        String releaseUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/releases";

        Map<String, Object> releaseBody = new HashMap<>();
        releaseBody.put("tag_name", tagName);
        releaseBody.put("target_commitish", targetBranch);
        releaseBody.put("name", tagName);
        releaseBody.put("body", releaseNotes);

        HttpEntity<Map<String, Object>> releaseEntity = new HttpEntity<>(releaseBody, headers);

        ResponseEntity<String> releaseResponse = restTemplate.exchange(releaseUrl, HttpMethod.POST, releaseEntity, String.class);

        if (releaseResponse.getStatusCode().is2xxSuccessful()) {
            System.out.println("Release created successfully: " + tagName);
        } else {
            System.out.println("Failed to create release: " + releaseResponse.getBody());
        }
    }





}
