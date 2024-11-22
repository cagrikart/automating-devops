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

    public void mergePullRequest(String githubRepo, int pullNumber) {
        String pullUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + githubRepo + "/pulls/" + pullNumber;
        String mergeUrl = pullUrl + "/merge";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

        ResponseEntity<String> prResponse = restTemplate.exchange(pullUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        if (!prResponse.getStatusCode().is2xxSuccessful() || !prResponse.getBody().contains("\"state\":\"open\"")) {
            System.out.println("PR is not approved or not open: " + prResponse.getBody());
            return;
        }

        HttpEntity<String> mergeEntity = new HttpEntity<>(headers);
        ResponseEntity<String> mergeResponse = restTemplate.exchange(mergeUrl, HttpMethod.PUT, mergeEntity, String.class);

        if (mergeResponse.getStatusCode().is2xxSuccessful()) {
            System.out.println("PR merged successfully.");
        } else {
            System.out.println("Failed to merge PR: " + mergeResponse.getBody());
        }
    }

    public void createTagAndRelease(String tagName, String commitSha, String targetBranch, String releaseNotes) {
        String tagUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/git/refs";
        String releaseUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/releases";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

        ResponseEntity<String> tagsResponse = restTemplate.exchange(tagUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        if (tagsResponse.getStatusCode().is2xxSuccessful() && Objects.requireNonNull(tagsResponse.getBody()).contains("\"refs/tags/" + tagName + "\"")) {
            throw new RuntimeException("Tag already exists: " + tagName);
        }


        if (tagName == null || tagName.isEmpty()) {
            tagName = getNextMinorVersion(Objects.requireNonNull(tagsResponse.getBody()));
        }

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

    private String getNextMinorVersion(String tagsResponse) {
        String latestTag = "1.0.1-c10-sit";

        String[] tags = tagsResponse.split("\n");
        for (String tag : tags) {
            if (tag.contains("refs/tags/")) {
                String version = tag.substring(tag.lastIndexOf("/") + 1).replace("\"", "");
                if (version.matches("v\\d+\\.\\d+\\.\\d+")) {
                    if (compareVersions(version, latestTag) > 0) {
                        latestTag = version;
                    }
                }
            }
        }

        String[] versionParts = latestTag.substring(1).split("\\.");
        int major = Integer.parseInt(versionParts[0]);
        int minor = Integer.parseInt(versionParts[1]) + 1;
        int patch = 0;

        return "v" + major + "." + minor + "." + patch;
    }

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.substring(1).split("\\.");
        String[] parts2 = v2.substring(1).split("\\.");
        for (int i = 0; i < parts1.length; i++) {
            int part1 = Integer.parseInt(parts1[i]);
            int part2 = Integer.parseInt(parts2[i]);
            if (part1 != part2) {
                return part1 - part2;
            }
        }
        return 0;
    }


}
