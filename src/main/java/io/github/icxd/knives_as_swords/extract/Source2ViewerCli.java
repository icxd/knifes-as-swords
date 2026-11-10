package io.github.icxd.knives_as_swords.extract;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs the external Source2Viewer-CLI executable. */
public final class Source2ViewerCli {
	private static final Pattern VERSION_LINE = Pattern.compile("(?m)^Version:\\s*(\\S+)");
	/** Only the end of the output is kept; that is where errors and the summary are. */
	private static final int MAX_OUTPUT_BYTES = 64 * 1024;

	public record Result(int exitCode, String output) {
	}

	private final Path executable;

	public Source2ViewerCli(Path executable) {
		this.executable = executable.toAbsolutePath();
	}

	public Path executable() {
		return executable;
	}

	/** The tool's reported version, e.g. {@code 20.1.7467+9e2e6c7}. Fails if this is not a working CLI. */
	public String version() throws IOException, InterruptedException {
		Result result = run(executable.getParent(), Duration.ofSeconds(30), "--version");
		Matcher matcher = VERSION_LINE.matcher(result.output());
		if (result.exitCode() != 0 || !matcher.find()) {
			throw new IOException("Not a working Source2Viewer-CLI: " + executable + " (exit code "
					+ result.exitCode() + ")\n" + result.output());
		}
		return matcher.group(1);
	}

	/**
	 * @param workingDirectory where the tool runs; it appends failures to an {@code exceptions.txt} there
	 */
	public Result run(Path workingDirectory, Duration timeout, String... arguments)
			throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add(executable.toString());
		command.addAll(List.of(arguments));

		// Output goes to a file rather than a pipe, so the tool can never block on a full pipe.
		Path log = Files.createTempFile("knives_as_swords-cli-", ".log");
		try {
			Process process = new ProcessBuilder(command)
					.directory(workingDirectory.toFile())
					.redirectErrorStream(true)
					.redirectOutput(log.toFile())
					.start();
			try {
				process.getOutputStream().close();
				if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
					throw new IOException("Source2Viewer-CLI did not finish within " + timeout.toSeconds() + " s");
				}
				return new Result(process.exitValue(), readTail(log));
			} finally {
				// Also reached on timeout and interruption; a no-op once the process has exited.
				process.destroyForcibly();
			}
		} finally {
			Files.deleteIfExists(log);
		}
	}

	private static String readTail(Path file) throws IOException {
		try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
			long length = in.length();
			int size = (int) Math.min(length, MAX_OUTPUT_BYTES);
			byte[] bytes = new byte[size];
			in.seek(length - size);
			in.readFully(bytes);
			return new String(bytes, StandardCharsets.UTF_8).strip();
		}
	}
}
