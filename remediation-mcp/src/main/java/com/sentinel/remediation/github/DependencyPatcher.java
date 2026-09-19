package com.sentinel.remediation.github;

import com.sentinel.common.model.Vulnerability;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DependencyPatcher {

    public java.util.Optional<String> patchMavenPom(String pomXml, Vulnerability vulnerability) {
        String artifactId = shortArtifactId(vulnerability.pkgName());

        Pattern block = Pattern.compile(
                "(<dependency>\\s*(?:(?!</dependency>).)*?<artifactId>\\s*" + Pattern.quote(artifactId) +
                        "\\s*</artifactId>(?:(?!</dependency>).)*?<version>)\\s*" +
                        Pattern.quote(vulnerability.installedVersion()) + "\\s*(</version>)",
                Pattern.DOTALL
        );

        Matcher matcher = block.matcher(pomXml);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String replacement = Matcher.quoteReplacement(matcher.group(1)) + vulnerability.fixedVersion()
                + Matcher.quoteReplacement(matcher.group(2));
        String patched = matcher.replaceFirst(replacement);

        if (block.matcher(patched).find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(patched);
    }

    public java.util.Optional<String> patchGradleBuild(String buildGradle, Vulnerability vulnerability) {
        String artifactId = shortArtifactId(vulnerability.pkgName());

        Pattern coordinate = Pattern.compile(
                "([\"']" + Pattern.quote(groupPrefix(vulnerability.pkgName())) + ":" +
                        Pattern.quote(artifactId) + ":)" + Pattern.quote(vulnerability.installedVersion()) + "([\"'])"
        );

        Matcher matcher = coordinate.matcher(buildGradle);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String replacement = Matcher.quoteReplacement(matcher.group(1)) + vulnerability.fixedVersion()
                + Matcher.quoteReplacement(matcher.group(2));
        return java.util.Optional.of(matcher.replaceFirst(replacement));
    }

    public java.util.Optional<String> patchRequirementsTxt(String requirementsTxt, Vulnerability vulnerability) {
        String packageName = normalizePackageName(vulnerability.pkgName());

        Pattern dependency = Pattern.compile(
                "(?m)^([ \\t]*" + Pattern.quote(packageName) +
                        "(?:\\[[^\\]]+\\])?[ \\t]*(?:==|===)[ \\t]*)" +
                        Pattern.quote(vulnerability.installedVersion()) + "([ \\t]*(?:#.*)?)$",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = dependency.matcher(requirementsTxt);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String replacement = Matcher.quoteReplacement(matcher.group(1))
                + vulnerability.fixedVersion()
                + Matcher.quoteReplacement(matcher.group(2));
        return java.util.Optional.of(matcher.replaceFirst(replacement));
    }

    public java.util.Optional<String> patchPackageJson(String packageJson, Vulnerability vulnerability) {
        String packageName = vulnerability.pkgName();

        Pattern dependency = Pattern.compile(
                "(\"" + Pattern.quote(packageName) + "\"\\s*:\\s*\")" +
                        Pattern.quote(vulnerability.installedVersion()) + "(\")"
        );

        Matcher matcher = dependency.matcher(packageJson);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String replacement = Matcher.quoteReplacement(matcher.group(1))
                + vulnerability.fixedVersion()
                + Matcher.quoteReplacement(matcher.group(2));
        String patched = matcher.replaceFirst(replacement);

        if (dependency.matcher(patched).find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(patched);
    }

    public java.util.Optional<String> patchGoMod(String goMod, Vulnerability vulnerability) {
        String module = vulnerability.pkgName();

        Pattern dependency = Pattern.compile(
                "(?m)^([ \\t]*" + Pattern.quote(module) +
                        "[ \\t]+)" + Pattern.quote(vulnerability.installedVersion()) + "([ \\t]*(?://.*)?)$"
        );

        Matcher matcher = dependency.matcher(goMod);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String replacement = Matcher.quoteReplacement(matcher.group(1))
                + vulnerability.fixedVersion()
                + Matcher.quoteReplacement(matcher.group(2));
        return java.util.Optional.of(matcher.replaceFirst(replacement));
    }

    public java.util.Optional<String> patchGemfile(String gemfile, Vulnerability vulnerability) {
        String packageName = vulnerability.pkgName();

        Pattern dependency = Pattern.compile(
                "(?m)^([ \\t]*gem[ \\t]+[\"']" + Pattern.quote(packageName) +
                        "[\"'][ \\t]*,[ \\t]*[\"'])(?:~>|>=|=)?[ \\t]*" +
                        Pattern.quote(vulnerability.installedVersion()) +
                        "([\"'][ \\t]*(?:#.*)?)$"
        );

        Matcher matcher = dependency.matcher(gemfile);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String replacement = Matcher.quoteReplacement(matcher.group(1))
                + vulnerability.fixedVersion()
                + Matcher.quoteReplacement(matcher.group(2));
        return java.util.Optional.of(matcher.replaceFirst(replacement));
    }

    public java.util.Optional<String> patchGemfileLock(
            String gemfileLock,
            Vulnerability vulnerability) {

        String packageName = vulnerability.pkgName();

        Pattern dependency = Pattern.compile(
                "(?m)^([ \\t]*" + Pattern.quote(packageName) +
                        "[ \\t]*\\()" +
                        Pattern.quote(vulnerability.installedVersion()) +
                        "(\\))$"
        );

        Matcher matcher = dependency.matcher(gemfileLock);

        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String replacement =
                Matcher.quoteReplacement(matcher.group(1))
                        + vulnerability.fixedVersion()
                        + Matcher.quoteReplacement(matcher.group(2));

        String patched = matcher.replaceFirst(replacement);

        if (dependency.matcher(patched).find()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(patched);
    }

    public java.util.Optional<String> patchComposerJson(
            String composerJson,
            Vulnerability vulnerability) {

        String packageName = vulnerability.pkgName();

        Pattern dependency = Pattern.compile(
                "(\"" + Pattern.quote(packageName) +
                        "\"\\s*:\\s*\")" +
                        Pattern.quote(vulnerability.installedVersion()) +
                        "(\")"
        );

        Matcher matcher = dependency.matcher(composerJson);

        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String replacement =
                Matcher.quoteReplacement(matcher.group(1))
                        + vulnerability.fixedVersion()
                        + Matcher.quoteReplacement(matcher.group(2));

        String patched = matcher.replaceFirst(replacement);

        if (dependency.matcher(patched).find()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(patched);
    }

    public java.util.Optional<String> patchComposerLock(
            String composerLock,
            Vulnerability vulnerability) {

        String packageName = vulnerability.pkgName();

        Pattern dependency = Pattern.compile(
                "(\"name\"\\s*:\\s*\"" + Pattern.quote(packageName) +
                        "\"\\s*,\\s*\"version\"\\s*:\\s*\")" +
                        Pattern.quote(vulnerability.installedVersion()) +
                        "(\")",
                Pattern.DOTALL
        );

        Matcher matcher = dependency.matcher(composerLock);

        if (!matcher.find()) {
            return java.util.Optional.empty();
        }

        String replacement =
                Matcher.quoteReplacement(matcher.group(1))
                        + vulnerability.fixedVersion()
                        + Matcher.quoteReplacement(matcher.group(2));

        String patched = matcher.replaceFirst(replacement);

        if (dependency.matcher(patched).find()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(patched);
    }


    private String shortArtifactId(String pkgName) {
        int idx = pkgName.lastIndexOf(':');
        return idx >= 0 ? pkgName.substring(idx + 1) : pkgName;
    }

    private String groupPrefix(String pkgName) {
        int idx = pkgName.lastIndexOf(':');
        return idx >= 0 ? pkgName.substring(0, idx) : pkgName;
    }

    private String normalizePackageName(String pkgName) {
        int idx = pkgName.lastIndexOf('/');
        return idx >= 0 ? pkgName.substring(idx + 1) : pkgName;
    }
}
