// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine.ffm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Extracts the bundled native {@code restate-sdk-shared-core} library from the classpath and {@code
 * System.load}s it.
 *
 * <p>The library is shipped as a classpath resource at {@code
 * dev/restate/sdk/core/native/<classifier>/librestate_sdk_core.<ext>}, where {@code classifier}
 * encodes the OS/arch/libc and {@code ext} is the platform's shared-library extension.
 *
 * <p><b>Symbol resolution ordering.</b> The jextract-generated {@code SharedCoreNative} resolves
 * symbols through a {@code static final SymbolLookup SYMBOL_LOOKUP =
 * SymbolLookup.loaderLookup().or( Linker.nativeLinker().defaultLookup())}. {@code loaderLookup()}
 * only sees libraries that were loaded (via {@link System#load(String)} / {@link
 * System#loadLibrary(String)}) by the same classloader <em>before</em> the lookup is created — i.e.
 * before {@code SharedCoreNative} is class-initialized. We therefore perform the extraction +
 * {@code System.load} from {@link #ensureLoaded()}, which callers must invoke before ever touching
 * {@code SharedCoreNative}.
 */
public final class NativeLibraryLoader {

  private NativeLibraryLoader() {}

  private static final String RESOURCE_PREFIX = "dev/restate/sdk/core/native/";
  private static final String LIB_BASENAME = "librestate_sdk_core";

  /** Set to true once the native library has been successfully loaded into this classloader. */
  private static volatile boolean loaded = false;

  /**
   * Extracts and {@link System#load}s the native library exactly once. Idempotent and thread-safe.
   * Must be called before {@code SharedCoreNative} is class-initialized so {@code loaderLookup()}
   * can resolve the {@code vm_*} symbols.
   */
  public static synchronized void ensureLoaded() {
    if (loaded) {
      return;
    }
    String classifier = detectClassifier();
    String fileName = LIB_BASENAME + libExtension();
    String resource = RESOURCE_PREFIX + classifier + "/" + fileName;

    Path extracted = extractToTempFile(resource, fileName);
    System.load(extracted.toAbsolutePath().toString());
    loaded = true;
  }

  private static Path extractToTempFile(String resource, String fileName) {
    ClassLoader cl = NativeLibraryLoader.class.getClassLoader();
    try (InputStream in =
        cl != null
            ? cl.getResourceAsStream(resource)
            : ClassLoader.getSystemResourceAsStream(resource)) {
      if (in == null) {
        throw new UnsatisfiedLinkError(
            "Cannot find bundled native library on the classpath: " + resource);
      }
      Path tempDir = Files.createTempDirectory("restate-shared-core");
      tempDir.toFile().deleteOnExit();
      Path target = tempDir.resolve(fileName);
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      target.toFile().deleteOnExit();
      return target;
    } catch (IOException e) {
      throw new UnsatisfiedLinkError(
          "Failed to extract native library " + resource + ": " + e.getMessage());
    }
  }

  /**
   * Builds the resource classifier from {@code os.name} / {@code os.arch}. On Linux the libc flavor
   * (gnu vs musl) is appended. Only {@code linux-x86_64} is currently shipped, but the structure
   * supports the other platforms.
   */
  static String detectClassifier() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String osArch = normalizeArch(System.getProperty("os.arch", "").toLowerCase(Locale.ROOT));

    if (osName.contains("linux")) {
      return "linux-" + osArch + (isMusl() ? "-musl" : "");
    } else if (osName.contains("mac") || osName.contains("darwin")) {
      return "darwin-" + osArch;
    } else if (osName.contains("win")) {
      return "windows-" + osArch;
    }
    throw new UnsatisfiedLinkError("Unsupported operating system: " + osName);
  }

  private static String normalizeArch(String arch) {
    return switch (arch) {
      case "x86_64", "amd64" -> "x86_64";
      case "aarch64", "arm64" -> "aarch64";
      default -> arch;
    };
  }

  private static String libExtension() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (osName.contains("mac") || osName.contains("darwin")) {
      return ".dylib";
    } else if (osName.contains("win")) {
      return ".dll";
    }
    return ".so";
  }

  /**
   * Best-effort detection of a musl-based Linux (e.g. Alpine). We probe {@code
   * /lib/ld-musl-*.so.1}; absence implies glibc. Cheap and only consulted on Linux.
   */
  private static boolean isMusl() {
    try {
      Path libDir = Path.of("/lib");
      if (Files.isDirectory(libDir)) {
        try (var entries = Files.list(libDir)) {
          if (entries.anyMatch(p -> p.getFileName().toString().startsWith("ld-musl-"))) {
            return true;
          }
        }
      }
    } catch (IOException | RuntimeException ignored) {
      // fall through to glibc
    }
    return false;
  }
}
