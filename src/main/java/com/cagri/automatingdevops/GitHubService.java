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
        String url = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + githubRepo + "/pulls/" + pullNumber;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> pullResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (pullResponse.getStatusCode().is2xxSuccessful()) {
            String responseBody = pullResponse.getBody();
            if (responseBody != null && responseBody.contains("\"state\":\"approved\"")) {
                String mergeUrl = url + "/merge";
                ResponseEntity<String> mergeResponse = restTemplate.exchange(mergeUrl, HttpMethod.PUT, entity, String.class);

                if (mergeResponse.getStatusCode().is2xxSuccessful()) {
                    System.out.println("PR merged successfully.");
                } else {
                    System.out.println("Failed to merge PR: " + mergeResponse.getBody());
                }
            } else {
                System.out.println("PR is not approved. Merge operation cannot proceed.");
            }
        } else {
            System.out.println("Failed to fetch PR details: " + pullResponse.getBody());
        }
    }

    public void createTagAndRelease(String tagName, String commitSha, String targetBranch, String releaseNotes) {
        // Eğer tagName verilmemişse otomatik oluştur
        if (tagName == null || tagName.isEmpty()) {
            tagName = generateNextTag();
        }

        String tagUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/git/refs";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");

        // Mevcut tag var mı kontrol et
        ResponseEntity<String> existingTagsResponse = restTemplate.exchange(tagUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (existingTagsResponse.getStatusCode().is2xxSuccessful()) {
            String tagsResponseBody = existingTagsResponse.getBody();
            if (tagsResponseBody != null && tagsResponseBody.contains("\"ref\":\"refs/tags/" + tagName + "\"")) {
                throw new IllegalStateException("Tag with name " + tagName + " already exists.");
            }
        }

        // Tag oluştur
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

        // Release oluştur
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

    private String generateNextTag() {
        String tagUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/tags";
        ResponseEntity<String> tagsResponse = restTemplate.exchange(tagUrl, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        if (tagsResponse.getStatusCode().is2xxSuccessful()) {
            String tagsBody = tagsResponse.getBody();
            if (tagsBody != null) {
                // En büyük tag'i bul ve bir artır
                String latestTag = findLatestTag(tagsBody);
                return incrementMinorVersion(latestTag);
            }
        }

        // Default olarak ilk tag
        return "v1.0.0";
    }

    private String findLatestTag(String tagsBody) {
        // Örnek bir tag çıkarıcı (detaylı işleme gerekebilir)
        return "v1.0.0"; // Gerçek tag'i döndürecek şekilde geliştirilmeli
    }

    private String incrementMinorVersion(String tag) {
        String[] parts = tag.replace("v", "").split("\\.");
        if (parts.length >= 2) {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]) + 1;
            return "v" + major + "." + minor + ".0";
        }
        return "v1.0.0"; // Eğer parsing başarısız olursa
    }

}
