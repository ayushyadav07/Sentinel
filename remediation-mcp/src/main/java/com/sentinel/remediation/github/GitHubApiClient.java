package com.sentinel.remediation.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class GitHubApiClient {

    private static final String API_BASE = "https://api.github.com";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestClient client(String token) {
        return RestClient.builder()
                .baseUrl(API_BASE)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public String getDefaultBranchHeadSha(String owner, String repo, String branch, String token) {
        JsonNode ref = client(token).get()
                .uri("/repos/{owner}/{repo}/git/ref/heads/{branch}", owner, repo, branch)
                .retrieve()
                .body(JsonNode.class);
        return ref.path("object").path("sha").asText();
    }

    public void createBranch(String owner, String repo, String newBranch, String fromSha, String token) {
        client(token).post()
                .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
                .body(Map.of("ref", "refs/heads/" + newBranch, "sha", fromSha))
                .retrieve()
                .toBodilessEntity();
    }

    public FileContent getFileContent(String owner, String repo, String path, String ref, String token) {
        try {
            path = path.replaceFirst("^/+", "");
            JsonNode node = client(token).get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}", owner, repo, path, ref)
                    .retrieve()
                    .body(JsonNode.class);
            String encoded = node.path("content").asText().replace("\n", "");
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            return new FileContent(decoded, node.path("sha").asText());
        } catch (Exception e) {
            return null;
        }
    }

    public void updateFile(String owner, String repo, String path, String newContent,
                            String previousSha, String branch, String commitMessage, String token) {
        path = path.replaceFirst("^/+", "");
        String encoded = Base64.getEncoder().encodeToString(newContent.getBytes(StandardCharsets.UTF_8));
        client(token).put()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .body(Map.of(
                        "message", commitMessage,
                        "content", encoded,
                        "sha", previousSha,
                        "branch", branch
                ))
                .retrieve()
                .toBodilessEntity();
    }

    public String openPullRequest(String owner, String repo, String title, String body,
                                   String headBranch, String baseBranch, List<String> labels, String token) {
        JsonNode pr = client(token).post()
                .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                .body(Map.of("title", title, "body", body, "head", headBranch, "base", baseBranch))
                .retrieve()
                .body(JsonNode.class);

        int prNumber = pr.path("number").asInt();
        if (labels != null && !labels.isEmpty()) {
            client(token).post()
                    .uri("/repos/{owner}/{repo}/issues/{number}/labels", owner, repo, prNumber)
                    .body(Map.of("labels", labels))
                    .retrieve()
                    .toBodilessEntity();
        }
        return pr.path("html_url").asText();
    }

    public String openIssue(String owner, String repo, String title, String body,
                             List<String> labels, String token) {
        JsonNode issue = client(token).post()
                .uri("/repos/{owner}/{repo}/issues", owner, repo)
                .body(Map.of("title", title, "body", body, "labels", labels == null ? List.of() : labels))
                .retrieve()
                .body(JsonNode.class);
        return issue.path("html_url").asText();
    }

    public boolean existingOpenItemWithLabel(String owner, String repo, String label, String token) {
        String baseQuery =
                "repo:" + owner + "/" + repo +
                        " label:" + label +
                        " state:open";

        boolean issueExists = searchGitHubIssues(
                baseQuery + " is:issue",
                token
        );

        if (issueExists) {
            return true;
        }

        return searchGitHubIssues(
                baseQuery + " is:pull-request",
                token
        );
    }

    private boolean searchGitHubIssues(String query, String token) {
        JsonNode result = client(token).get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/issues")
                        .queryParam("q", query)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        return result.path("total_count").asInt(0) > 0;
    }

    public record FileContent(String content, String sha) {}
}
