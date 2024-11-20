package com.cagri.automatingdevops;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;


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

    public void mergePullRequest(int pullNumber) {
        String url = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/pulls/" + pullNumber + "/merge";
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

    public void createReleaseWithTargetBranch(String tagName, String releaseName, String releaseBody, String targetBranch) {
        // GitHub Release API Endpoint
        String releaseUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/releases";

        // HTTP Headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

        // Request Body
        Map<String, Object> releaseBodyMap = new HashMap<>();
        releaseBodyMap.put("tag_name", tagName); // Yeni tag
        releaseBodyMap.put("name", releaseName); // Release adı
        releaseBodyMap.put("body", releaseBody); // Release açıklamaları
        releaseBodyMap.put("target_commitish", targetBranch); // Target branch (ör. sit)
        releaseBodyMap.put("draft", false); // Taslak olarak mı kaydedilsin
        releaseBodyMap.put("prerelease", false); // Ön sürüm mü

        HttpEntity<Map<String, Object>> releaseEntity = new HttpEntity<>(releaseBodyMap, headers);

        // API Çağrısı
        ResponseEntity<String> releaseResponse = restTemplate.exchange(releaseUrl, HttpMethod.POST, releaseEntity, String.class);

        if (releaseResponse.getStatusCode().is2xxSuccessful()) {
            System.out.println("Release created successfully for target branch: " + targetBranch);
        } else {
            System.out.println("Failed to create release: " + releaseResponse.getBody());
        }
    }

}
