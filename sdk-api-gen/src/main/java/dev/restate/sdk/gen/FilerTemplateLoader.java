// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen;

import com.github.jknack.handlebars.io.AbstractTemplateLoader;
import com.github.jknack.handlebars.io.TemplateSource;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.processing.Filer;
import javax.tools.StandardLocation;

/**
 * We need this because the built-in ClassLoaderTemplateLoader is not reliable in the annotation
 * processor context
 */
class FilerTemplateLoader extends AbstractTemplateLoader {
  private final Filer filer;

  public FilerTemplateLoader(Filer filer) {
    this.filer = filer;
  }

  @Override
  public TemplateSource sourceAt(String location) {
    Path path = Paths.get(location);
    return new TemplateSource() {
      @Override
      public String content(Charset charset) throws IOException {
        try {
          return filer
              .getResource(
                  StandardLocation.ANNOTATION_PROCESSOR_PATH,
                  path.getParent().toString().replace('/', '.'),
                  path.getFileName().toString())
              .getCharContent(true)
              .toString();
        } catch (java.lang.IllegalArgumentException | IOException filerException) {
          // in vscode/cursor this happens https://github.com/restatedev/sdk-java/issues/516
          // so fallback to using the classloader
          try (var stream = getClass().getResourceAsStream("/" + location)) {
            if (stream == null) {
              throw filerException;
            }
            return new String(stream.readAllBytes(), charset);
          } catch (Exception classLoaderException) {
            throw filerException; // throw the original exception to retain the original behaviour
          }
        }
      }

      @Override
      public String filename() {
        return "/" + location;
      }

      @Override
      public long lastModified() {
        return 0;
      }
    };
  }
}
