package io.github.icxd.knives_as_swords.extract;

import io.github.icxd.knives_as_swords.util.FileUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads the pinned extractor release, verifies its SHA-256, and unpacks it into the mod's
 * local tools folder. Nothing here runs unless the player has agreed to the download.
 */
public final class ExtractorInstaller {
	private static final long MAX_DOWNLOAD_BYTES = 256L * 1024 * 1024;
	private static final long MAX_UNPACKED_BYTES = 1024L * 1024 * 1024;
	/** Written last, so an interrupted install is never mistaken for a complete one. */
	private static final String COMPLETE_MARKER = ".complete";

	/** Opens the download; replaceable so tests need no network. */
	@FunctionalInterface
	public interface Source {
		InputStream open(URI uri) throws IOException, InterruptedException;
	}

	private final Path installDir;
	private final URI uri;
	private final String sha256;
	private final String executableName;
	private final Source source;

	public ExtractorInstaller(Path installDir, URI uri, String sha256, String executableName, Source source) {
		this.installDir = installDir.toAbsolutePath().normalize();
		this.uri = uri;
		this.sha256 = sha256;
		this.executableName = executableName;
		this.source = source;
	}

	public static ExtractorInstaller forRelease(Path toolsDir, ExtractorRelease release) {
		Path installDir = toolsDir.resolve("source2viewer-cli-" + ExtractorRelease.VERSION + "-" + release.platform());
		return new ExtractorInstaller(installDir, release.downloadUri(), release.sha256(), release.executableName(),
				ExtractorInstaller::openHttp);
	}

	/** The extractor executable, if a complete install with the pinned hash is present. */
	public Optional<Path> installed() {
		Path executable = installDir.resolve(executableName);
		Path marker = installDir.resolve(COMPLETE_MARKER);
		try {
			if (Files.isRegularFile(executable) && Files.isRegularFile(marker)
					&& Files.readString(marker, StandardCharsets.UTF_8).trim().equalsIgnoreCase(sha256)) {
				return Optional.of(executable);
			}
		} catch (IOException e) {
			// Treat an unreadable marker as "not installed".
		}
		return Optional.empty();
	}

	/** Downloads, verifies and unpacks the extractor; returns its executable. */
	public Path install() throws IOException, InterruptedException {
		Path parent = installDir.getParent();
		Files.createDirectories(parent);
		Path archive = Files.createTempFile(parent, "extractor-", ".zip.part");
		try {
			String actual = download(archive);
			if (!actual.equalsIgnoreCase(sha256)) {
				throw new IOException("Extractor download failed verification: expected SHA-256 " + sha256
						+ " but got " + actual + " from " + uri);
			}

			FileUtil.deleteRecursively(installDir);
			Files.createDirectories(installDir);
			unpack(archive);

			Path executable = installDir.resolve(executableName);
			if (!Files.isRegularFile(executable)) {
				throw new IOException("Extractor archive does not contain " + executableName);
			}
			// No-op on Windows; needed on POSIX because zip entries carry no mode bits here.
			executable.toFile().setExecutable(true);

			Files.writeString(installDir.resolve(COMPLETE_MARKER), sha256, StandardCharsets.UTF_8);
			return executable;
		} finally {
			Files.deleteIfExists(archive);
		}
	}

	private String download(Path target) throws IOException, InterruptedException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}

		long total = 0;
		byte[] buffer = new byte[1 << 16];
		try (InputStream in = source.open(uri); OutputStream out = Files.newOutputStream(target)) {
			int read;
			while ((read = in.read(buffer)) >= 0) {
				total += read;
				if (total > MAX_DOWNLOAD_BYTES) {
					throw new IOException("Extractor download is larger than expected; aborting");
				}
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}
				digest.update(buffer, 0, read);
				out.write(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private void unpack(Path archive) throws IOException {
		long total = 0;
		byte[] buffer = new byte[1 << 16];
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				Path target = installDir.resolve(entry.getName()).normalize();
				if (!target.startsWith(installDir)) {
					throw new IOException("Extractor archive has an entry outside its folder: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}

				Files.createDirectories(target.getParent());
				try (OutputStream out = Files.newOutputStream(target)) {
					int read;
					while ((read = zip.read(buffer)) >= 0) {
						total += read;
						if (total > MAX_UNPACKED_BYTES) {
							throw new IOException("Extractor archive unpacks to more than expected; aborting");
						}
						out.write(buffer, 0, read);
					}
				}
			}
		}
	}

	private static InputStream openHttp(URI uri) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			response.body().close();
			throw new IOException("Extractor download failed: HTTP " + response.statusCode() + " from " + uri);
		}
		return response.body();
	}
}
