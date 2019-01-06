package org.runestar.launcher;

import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipFile;

public final class RuneStar {

    public static final String TITLE = "RuneStar";

    public static final String REPO_NAME = "RuneStar/client";

    public static final Path DIRECTORY;
    static {
        try {
            DIRECTORY = Utils.getJarLocation(RuneStar.class).getParent();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static final Path DL_DIR = DIRECTORY.resolve("dl");

    public static final Path CLIENT_JAR = DL_DIR.resolve("client.jar");

    public static final Path CLIENT_JAR_PRM = DL_DIR.resolve("client.jar.prm");

    public static final Path CLIENT_JAR_SOURCE = DL_DIR.resolve("client.jar.source");

    public static final Path USER_PRM = DIRECTORY.resolve("client.prm");

    public static final Image ICON;
    static {
        try {
            ICON = ImageIO.read(RuneStar.class.getResource("icon.png"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static LaunchFrame frame = null;

    private RuneStar() {}

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        frame = new LaunchFrame();

        try {
            updateJar();
            launchJar();

            Thread.sleep(2500);
            frame.dispose();
        } catch (Exception e) {
            frame.setStatus("Error");
            frame.log(Utils.stackTraceToString(e));
        }
    }

    private static void updateJar() throws Exception {
        Files.createDirectories(DL_DIR);

        String sourceString = Utils.readTextFile(CLIENT_JAR_SOURCE);
        URI sourceUrl = null;
        if (sourceString != null) {
            sourceUrl = new URI(sourceString);
        }

        frame.log("Connecting to GitHub");
        GitHub github = GitHub.connectAnonymously();

        frame.log("Using repository " + REPO_NAME);
        GHRepository repo = github.getRepository(REPO_NAME);

        frame.log("Finding latest release");
        GHRelease latestRelease = repo.listReleases().iterator().next();

        List<GHAsset> assets = latestRelease.getAssets();
        if (assets.size() != 1) throw new Exception("Release contains more than 1 asset!");
        URI downloadUrl = new URI(assets.get(0).getBrowserDownloadUrl());
        frame.log("Latest release URL is " + downloadUrl);

        if (!downloadUrl.equals(sourceUrl) || !Utils.verifyJar(CLIENT_JAR)) {
            frame.log("Downloading " + downloadUrl + " to " + CLIENT_JAR);
            Utils.downloadFile(downloadUrl.toURL(), CLIENT_JAR);
            frame.log("Writing " + downloadUrl + " to " + CLIENT_JAR_SOURCE);
            Utils.writeTextFile(CLIENT_JAR_SOURCE, downloadUrl.toString());
            frame.log("Copying contents of .prm in " + CLIENT_JAR + " to " + CLIENT_JAR_SOURCE);
            try (
                    ZipFile jar = new ZipFile(CLIENT_JAR.toFile());
                    InputStream jarPrm = jar.getInputStream(jar.getEntry(".prm"))
            ) {
               Files.copy(jarPrm, CLIENT_JAR_PRM, StandardCopyOption.REPLACE_EXISTING) ;
            }
        }
    }

    private static void launchJar() throws IOException {
        if (Files.notExists(USER_PRM )) {
            frame.log("File " + USER_PRM + " is missing!");
            Files.createFile(USER_PRM);
        }

        ProcessBuilder cmd = Utils.jarRunCommand(
                CLIENT_JAR,
                "-Duser.home=" + DIRECTORY.toString(),
                '@' + CLIENT_JAR_PRM.toString(),
                '@' + USER_PRM.toString()
        );
        frame.log("Using command: " + cmd.command());
        cmd.start();
    }
}