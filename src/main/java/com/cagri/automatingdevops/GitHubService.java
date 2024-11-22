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

    public void createTagAndRelease(String tagName, String commitSha, String targetBranch,  String releaseNotes) {
        String tagUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/git/refs";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

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
