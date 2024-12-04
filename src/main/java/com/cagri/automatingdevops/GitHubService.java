package com.cagri.automatingdevops;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final ReleaseResponseRepository releaseResponseRepository;


    public GitHubService(RestTemplateBuilder restTemplateBuilder,ReleaseResponseRepository   releaseResponseRepository) {
        this.restTemplate = restTemplateBuilder.build();
        this.releaseResponseRepository = releaseResponseRepository;
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

    public ReleaseResponse createTagAndRelease(String targetBranch, String gitHubRepo, String customVersion, String  crId, String defectId) throws JsonProcessingException {
        String tagName;
        HttpHeaders headers = new HttpHeaders();
        String url = "https://api.github.com/user";

        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper objectMapper = new ObjectMapper();

        ResponseEntity<String> gitLogin = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

        JsonNode jsonNode = objectMapper.readTree(gitLogin.getBody());
        String developerFullName = jsonNode.get("login").asText();
        String branchUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/branches/" + targetBranch;
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<Void> listEntity = new HttpEntity<>(headers);

        // Branch bilgisi alma
        String commitSha;
        ResponseEntity<String> branchResponse = restTemplate.exchange(branchUrl, HttpMethod.GET, listEntity, String.class);
        if (branchResponse.getStatusCode().is2xxSuccessful()) {
             objectMapper = new ObjectMapper();
            try {
                JsonNode branchInfo = objectMapper.readTree(branchResponse.getBody());
                commitSha = branchInfo.get("commit").get("sha").asText();
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse branch info: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to retrieve branch info: " + branchResponse.getBody());
        }

        // Tag bilgisi alma
        String tagListUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/tags";
        ResponseEntity<String> tagListResponse = restTemplate.exchange(tagListUrl, HttpMethod.GET, listEntity, String.class);
        String latestTagSha = null;
        String latestTagName = null;

        if (tagListResponse.getStatusCode().is2xxSuccessful()) {
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
                    tagName = "1.0.0-" + customVersion + "-" + targetBranch;
                } else {
                    int newPatchVersion = highestPatchVersion + 1;
                    tagName = "1.0." + newPatchVersion + "-" + customVersion + "-" + targetBranch;
                }

            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse tag list response: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to retrieve tags: " + tagListResponse.getBody());
        }

        // Farklılıkları bul ve release notları oluştur
        String releaseNotes = "";
        if (latestTagSha == null) {
            releaseNotes = "Initial release for branch: " + targetBranch;
        } else {
            String compareUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/compare/" + latestTagSha + "..." + commitSha;
            ResponseEntity<String> compareResponse = restTemplate.exchange(compareUrl, HttpMethod.GET, listEntity, String.class);
            if (compareResponse.getStatusCode().is2xxSuccessful()) {
                try {
                    JsonNode compareInfo = objectMapper.readTree(compareResponse.getBody());
                    if (compareInfo.has("commits")) {
                        JsonNode commits = compareInfo.get("commits");
                        StringBuilder notesBuilder = new StringBuilder("Changes between " + latestTagName + " and " + tagName + ":\n");
                        for (JsonNode commit : commits) {
                            notesBuilder.append("- ").append(commit.get("commit").get("message").asText()).append("\n");
                        }
                        releaseNotes = notesBuilder.toString()+"CR : "+crId+" "+"  "+"Defect: "+defectId;
                    } else {
                        releaseNotes = "No commits found between tags.";
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to parse compare info: " + e.getMessage());
                }
            } else {
                throw new RuntimeException("Failed to compare commits: " + compareResponse.getBody());
            }
        }

        String tagUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/git/refs";
        Map<String, String> tagBody = new HashMap<>();
        tagBody.put("ref", "refs/tags/" + tagName);
        tagBody.put("sha", commitSha);

        HttpEntity<Map<String, String>> tagEntity = new HttpEntity<>(tagBody, headers);
        ResponseEntity<String> tagResponse = restTemplate.exchange(tagUrl, HttpMethod.POST, tagEntity, String.class);
        if (!tagResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create tag: " + tagResponse.getBody());
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timeStamp = LocalDateTime.now().format(formatter);

        // Yeni release oluşturma
        String releaseUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/releases";
        Map<String, Object> releaseBody = new HashMap<>();
        releaseBody.put("tag_name", tagName);
        releaseBody.put("target_commitish", targetBranch);
        releaseBody.put("name", tagName);
        releaseBody.put("body", releaseNotes);

        HttpEntity<Map<String, Object>> releaseEntity = new HttpEntity<>(releaseBody, headers);
        ResponseEntity<String> releaseResponse = restTemplate.exchange(releaseUrl, HttpMethod.POST, releaseEntity, String.class);
        if (!releaseResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create release: " + releaseResponse.getBody());
        }

        // Release bilgilerini döndür
        JsonNode releaseInfo = objectMapper.readTree(releaseResponse.getBody());
        String releaseLink = releaseInfo.get("html_url").asText();

        ReleaseResponse response = new ReleaseResponse();
        response.setTargetBranch(targetBranch);
        response.setTagName(tagName);
        response.setReleaseName(tagName);
        response.setReleaseTagUrl(releaseLink);
        response.setReleaseNotes(releaseNotes);
        response.setDeveloperFullName(developerFullName);
        response.setGitHubRepo(gitHubRepo);
        response.setDate(timeStamp);
        response.setCrId(crId);
        response.setDefectId(defectId);





        releaseResponseRepository.save(response);

        return response;
    }

}
