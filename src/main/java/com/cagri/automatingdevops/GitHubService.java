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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class GitHubService {


    @Value("${github.api.url}")
    private String gitHubApiUrl;

    @Value("${github.owner}")
    private String gitHubOwner;


    private final RestTemplate restTemplate;
    private final ReleaseResponseRepository releaseResponseRepository;


    public GitHubService(RestTemplateBuilder restTemplateBuilder,ReleaseResponseRepository   releaseResponseRepository) {
        this.restTemplate = restTemplateBuilder.build();
        this.releaseResponseRepository = releaseResponseRepository;
    }
    private HttpHeaders createHeaders(String gitHubToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");
        return headers;
    }
    public void mergePullRequest(String githubRepo,int pullNumber,String gitHubToken) {
        String url = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + githubRepo + "/pulls/" + pullNumber + "/merge";

        HttpHeaders headers = createHeaders(gitHubToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            System.out.println("PR merged successfully.");
        } else {
            System.out.println("Failed to merge PR: " + response.getBody());
        }
    }
    public List<ReleaseResponse> createTagsAndReleases(String targetBranch, List<String> gitHubRepos,String crId, String defectId,String token) throws Exception {
        List<ReleaseResponse> releaseResponses = new ArrayList<>();

        for (String gitHubRepo : gitHubRepos) {
            try {
                ReleaseResponse response = createTagAndRelease(targetBranch, gitHubRepo, crId, defectId,token);
                releaseResponses.add(response);
            } catch (Exception e) {
                System.err.println("Error processing repo " + gitHubRepo + ": " + e.getMessage());
            }
        }

        return releaseResponses;
    }
    public ReleaseResponse createTagAndRelease(String targetBranch, String gitHubRepo,String crId, String defectId, String gitHubToken) throws JsonProcessingException {
        HttpHeaders headers = createHeaders(gitHubToken);
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper objectMapper = new ObjectMapper();

        String gitHubOwner = fetchGitHubOwner(headers, restTemplate, objectMapper);

        String commitSha = fetchBranchCommitSha(headers, restTemplate, objectMapper, gitHubOwner, gitHubRepo, targetBranch);

        String tagName = generateNewTagName(headers, restTemplate, objectMapper, gitHubOwner, gitHubRepo, targetBranch);
        String latestTagSha = fetchLatestTagSha(headers, restTemplate, objectMapper, gitHubOwner, gitHubRepo, targetBranch);

        String releaseNotes = generateReleaseNotes(headers, restTemplate, objectMapper, gitHubOwner, gitHubRepo, latestTagSha, commitSha, tagName, crId, defectId,targetBranch);

        createTag(headers, restTemplate, gitHubOwner, gitHubRepo, tagName, commitSha);

        String releaseLink = createRelease(headers, restTemplate, gitHubOwner, gitHubRepo, targetBranch, tagName, releaseNotes);

        return buildReleaseResponse(gitHubOwner, gitHubRepo, targetBranch, tagName, releaseLink, releaseNotes, crId, defectId);
    }



    private String fetchGitHubOwner(HttpHeaders headers, RestTemplate restTemplate, ObjectMapper objectMapper) throws JsonProcessingException {
        String url = "https://api.github.com/user";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        return jsonNode.get("login").asText();
    }

    private String fetchBranchCommitSha(HttpHeaders headers, RestTemplate restTemplate, ObjectMapper objectMapper, String gitHubOwner, String gitHubRepo, String targetBranch) {
        String branchUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/branches/" + targetBranch;
        ResponseEntity<String> response = restTemplate.exchange(branchUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                JsonNode branchInfo = objectMapper.readTree(response.getBody());
                return branchInfo.get("commit").get("sha").asText();
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse branch info: " + e.getMessage());
            }
        }
        throw new RuntimeException("Failed to retrieve branch info: " + response.getBody());
    }

    private String generateNewTagName(HttpHeaders headers, RestTemplate restTemplate, ObjectMapper objectMapper, String gitHubOwner, String gitHubRepo, String targetBranch) {
        String tagListUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/tags";
        ResponseEntity<String> response = restTemplate.exchange(tagListUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                JsonNode tagList = objectMapper.readTree(response.getBody());
                int highestPatchVersion = -1;
                for (JsonNode tag : tagList) {
                    String existingTagName = tag.get("name").asText();
                    if (existingTagName.endsWith("-" + targetBranch)) {
                        highestPatchVersion = Math.max(highestPatchVersion, extractPatchVersion(existingTagName));
                    }
                }
                int newPatchVersion = highestPatchVersion + 1;
                return "1.0." + newPatchVersion +  "-" + targetBranch;
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse tag list response: " + e.getMessage());
            }
        }
        throw new RuntimeException("Failed to retrieve tags: " + response.getBody());
    }

    private int extractPatchVersion(String tagName) {
        String[] parts = tagName.split("-");
        if (parts.length >= 3 && parts[0].matches("\\d+\\.\\d+\\.\\d+")) {
            return Integer.parseInt(parts[0].split("\\.")[2]);
        }
        return -1;
    }

    private String fetchLatestTagSha(HttpHeaders headers, RestTemplate restTemplate, ObjectMapper objectMapper, String gitHubOwner, String gitHubRepo, String targetBranch) {
        String tagListUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/tags";
        ResponseEntity<String> response = restTemplate.exchange(tagListUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                JsonNode tagList = objectMapper.readTree(response.getBody());
                for (JsonNode tag : tagList) {
                    if (tag.get("name").asText().endsWith("-" + targetBranch)) {
                        return tag.get("commit").get("sha").asText();
                    }
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse tag list response: " + e.getMessage());
            }
        }
        return null;
    }

    private String generateReleaseNotes(HttpHeaders headers, RestTemplate restTemplate, ObjectMapper objectMapper, String gitHubOwner, String gitHubRepo, String latestTagSha, String commitSha, String tagName, String crId, String defectId,String targetBranch) {
        if (latestTagSha == null) {
            return "Initial release for branch: " + targetBranch;
        }

        String compareUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/compare/" + latestTagSha + "..." + commitSha;
        ResponseEntity<String> response = restTemplate.exchange(compareUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                JsonNode compareInfo = objectMapper.readTree(response.getBody());
                if (compareInfo.has("commits")) {
                    StringBuilder notesBuilder = new StringBuilder("Changes between " + latestTagSha + " and " + tagName + ":\n");
                    for (JsonNode commit : compareInfo.get("commits")) {
                        notesBuilder.append("- ").append(commit.get("commit").get("message").asText()).append("\n");
                    }
                    notesBuilder.append("CR: ").append(crId).append("  Defect: ").append(defectId);
                    return notesBuilder.toString();
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse compare info: " + e.getMessage());
            }
        }
        throw new RuntimeException("Failed to compare commits: " + response.getBody());
    }

    private void createTag(HttpHeaders headers, RestTemplate restTemplate, String gitHubOwner, String gitHubRepo, String tagName, String commitSha) {
        String tagUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/git/refs";
        Map<String, String> tagBody = Map.of("ref", "refs/tags/" + tagName, "sha", commitSha);
        ResponseEntity<String> response = restTemplate.exchange(tagUrl, HttpMethod.POST, new HttpEntity<>(tagBody, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create tag: " + response.getBody());
        }
    }

    private String createRelease(HttpHeaders headers, RestTemplate restTemplate, String gitHubOwner, String gitHubRepo, String targetBranch, String tagName, String releaseNotes) {
        String releaseUrl = gitHubApiUrl + "/repos/" + gitHubOwner + "/" + gitHubRepo + "/releases";
        Map<String, Object> releaseBody = Map.of(
                "tag_name", tagName,
                "target_commitish", targetBranch,
                "name", tagName,
                "body", releaseNotes
                                                );
        ResponseEntity<String> response = restTemplate.exchange(releaseUrl, HttpMethod.POST, new HttpEntity<>(releaseBody, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create release: " + response.getBody());
        }
        try {
            JsonNode releaseInfo = new ObjectMapper().readTree(response.getBody());
            return releaseInfo.get("html_url").asText();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse release response: " + e.getMessage());
        }
    }
    public ReleaseResponse saveRelease(ReleaseResponse releaseResponse) {
        return releaseResponseRepository.save(releaseResponse);
    }
    private ReleaseResponse buildReleaseResponse(String gitHubOwner, String gitHubRepo, String targetBranch, String tagName, String releaseLink, String releaseNotes, String crId, String defectId) {
        ReleaseResponse response = new ReleaseResponse();
        response.setTargetBranch(targetBranch);
        response.setTagName(tagName);
        response.setReleaseName(tagName);
        response.setReleaseTagUrl(releaseLink);
        response.setReleaseNotes(releaseNotes);
        response.setDeveloperFullName(gitHubOwner);
        response.setGitHubRepo(gitHubRepo);
        response.setDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        response.setCrId(crId);
        response.setDefectId(defectId);
        saveRelease(response);

        return response;
    }

}
