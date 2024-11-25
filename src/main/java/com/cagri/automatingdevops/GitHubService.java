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

    public ReleaseResponse createTagAndRelease(String targetBranch) throws JsonProcessingException {
        String tagName;
        String branchUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/branches/" + targetBranch;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<Void> listEntity = new HttpEntity<>(headers);

        // Branch'in son commit SHA'sını al
        String commitSha;
        ResponseEntity<String> branchResponse = restTemplate.exchange(branchUrl, HttpMethod.GET, listEntity, String.class);
        if (branchResponse.getStatusCode().is2xxSuccessful()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode branchInfo = objectMapper.readTree(branchResponse.getBody());
                commitSha = branchInfo.get("commit").get("sha").asText();
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse branch info: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to retrieve branch info: " + branchResponse.getBody());
        }

        // Mevcut taglerin kontrolü ve en son tag'i bulma
        String tagListUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/tags";

        ResponseEntity<String> tagListResponse = restTemplate.exchange(tagListUrl, HttpMethod.GET, listEntity, String.class);
        String latestTagSha = null; // En son tag'in SHA'sı
        String latestTagName = null; // En son tag'in adı
        if (tagListResponse.getStatusCode().is2xxSuccessful()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode tagList = objectMapper.readTree(tagListResponse.getBody());
                int highestPatchVersion = -1;

                for (JsonNode tag : tagList) {
                    String existingTagName = tag.get("name").asText();
                    if (existingTagName.endsWith("-" + targetBranch)) {
                        String[] parts = existingTagName.split("-");
                        if (parts.length >= 3) {
                            String versionPart = parts[0];
                            if (versionPart.matches("\\d+\\.\\d+\\.\\d+")) {
                                String[] versionNumbers = versionPart.split("\\.");
                                int patch = Integer.parseInt(versionNumbers[2]);
                                if (patch > highestPatchVersion) {
                                    highestPatchVersion = patch;
                                    latestTagSha = tag.get("commit").get("sha").asText();
                                    latestTagName = existingTagName;
                                }
                            }
                        }
                    }
                }

                if (highestPatchVersion == -1) {
                    throw new RuntimeException("No valid tag found for branch: " + targetBranch);
                }

                int newPatchVersion = highestPatchVersion + 1;
                tagName = "1.0." + newPatchVersion + "-c10-" + targetBranch;

            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse tag list response: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to retrieve tags: " + tagListResponse.getBody());
        }

        // Sadece en son tag ile yeni tag arasındaki farkları al
        if (latestTagSha == null) {
            throw new RuntimeException("No previous tag found for branch: " + targetBranch);
        }

        String compareUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/compare/" + latestTagSha + "..." + commitSha;
        ResponseEntity<String> compareResponse = restTemplate.exchange(compareUrl, HttpMethod.GET, listEntity, String.class);
        String releaseNotes = "";
        if (compareResponse.getStatusCode().is2xxSuccessful()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode compareInfo = objectMapper.readTree(compareResponse.getBody());
                JsonNode commits = compareInfo.get("commits");
                StringBuilder notesBuilder = new StringBuilder("Changes between " + latestTagName + " and " + tagName + ":\n");
                for (JsonNode commit : commits) {
                    notesBuilder.append("- ").append(commit.get("commit").get("message").asText()).append("\n");
                }
                releaseNotes = notesBuilder.toString();
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse compare info: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to compare commits: " + compareResponse.getBody());
        }

        // Yeni tag oluşturma
        String tagUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/git/refs";

        Map<String, String> tagBody = new HashMap<>();
        tagBody.put("ref", "refs/tags/" + tagName);
        tagBody.put("sha", commitSha);

        HttpEntity<Map<String, String>> tagEntity = new HttpEntity<>(tagBody, headers);

        ResponseEntity<String> tagResponse = restTemplate.exchange(tagUrl, HttpMethod.POST, tagEntity, String.class);
        if (!tagResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create tag: " + tagResponse.getBody());
        }

        // Release oluşturma
        String releaseUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/releases";

        Map<String, Object> releaseBody = new HashMap<>();
        releaseBody.put("tag_name", tagName);
        releaseBody.put("target_commitish", targetBranch);
        releaseBody.put("name", tagName);
        releaseBody.put("body", releaseNotes);

        HttpEntity<Map<String, Object>> releaseEntity = new HttpEntity<>(releaseBody, headers);

        ResponseEntity<String> releaseResponse = restTemplate.exchange(releaseUrl, HttpMethod.POST, releaseEntity, String.class);


            ObjectMapper objectMapper = new ObjectMapper();
                JsonNode releaseInfo = objectMapper.readTree(releaseResponse.getBody());
                String releaseLink = releaseInfo.get("html_url").asText();

                // Response nesnesi oluştur
                ReleaseResponse response = new ReleaseResponse();
                response.setTargetBranch(targetBranch);
                response.setTagName(tagName);
                response.setReleaseName(tagName);
                response.setReleaseTagUrl(releaseLink);
                response.setReleaseNotes(releaseNotes);

                return response; // ReleaseResponse döndür
    }

}
