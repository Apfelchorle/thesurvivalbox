package org.thesandbox.core.util;

/**
 * @param id          "core", "coreprotect"
 * @param repo        "repo/name"
 * @param githubToken fine grained token
 * @param jarName     "PLUGINEXAMPLE.jar"
 */
public record UpdateTarget(String id, String repo, String githubToken, String jarName) {

}