package dev.restate.sdk.blocking.gen;

import com.google.common.html.HtmlEscapers;
import com.google.protobuf.DescriptorProtos;
import java.util.*;
import java.util.function.Predicate;

public final class CodeGenUtils {
  private CodeGenUtils() {}

  // java keywords from: https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.9
  static final List<CharSequence> JAVA_KEYWORDS =
      Arrays.asList(
          "abstract",
          "assert",
          "boolean",
          "break",
          "byte",
          "case",
          "catch",
          "char",
          "class",
          "const",
          "continue",
          "default",
          "do",
          "double",
          "else",
          "enum",
          "extends",
          "final",
          "finally",
          "float",
          "for",
          "goto",
          "if",
          "implements",
          "import",
          "instanceof",
          "int",
          "interface",
          "long",
          "native",
          "new",
          "package",
          "private",
          "protected",
          "public",
          "return",
          "short",
          "static",
          "strictfp",
          "super",
          "switch",
          "synchronized",
          "this",
          "throw",
          "throws",
          "transient",
          "try",
          "void",
          "volatile",
          "while",
          // additional ones added by us
          "true",
          "false");

  static final String SERVICE_INDENDATION = "    ";
  static final String METHOD_INDENDATION = "        ";

  /**
   * Adjust a method name prefix identifier to follow the JavaBean spec: - decapitalize the first
   * letter - remove embedded underscores & capitalize the following letter
   *
   * <p>Finally, if the result is a reserved java keyword, append an underscore.
   *
   * @param word method name
   * @return lower name
   */
  static String mixedLower(String word) {
    StringBuffer w = new StringBuffer();
    w.append(Character.toLowerCase(word.charAt(0)));

    boolean afterUnderscore = false;

    for (int i = 1; i < word.length(); ++i) {
      char c = word.charAt(i);

      if (c == '_') {
        afterUnderscore = true;
      } else {
        if (afterUnderscore) {
          w.append(Character.toUpperCase(c));
        } else {
          w.append(c);
        }
        afterUnderscore = false;
      }
    }

    if (JAVA_KEYWORDS.contains(w)) {
      w.append('_');
    }

    return w.toString();
  }

  static String firstUppercase(String word) {
    if (word.length() == 1) {
      return String.valueOf(Character.toUpperCase(word.charAt(0)));
    }
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }

  static boolean isGoogleProtobufEmpty(String ty) {
    return ty.equals(".google.protobuf.Empty");
  }

  static Predicate<DescriptorProtos.SourceCodeInfo.Location> locationPathMatcher(int... matcher) {
    return location -> {
      if (location.getPathCount() != matcher.length) {
        return false;
      }
      for (int i = 0; i < matcher.length; i++) {
        if (location.getPath(i) != matcher[i]) {
          return false;
        }
      }
      return true;
    };
  }

  static String getComments(DescriptorProtos.SourceCodeInfo.Location location) {
    return location.getLeadingComments().isEmpty()
        ? location.getTrailingComments()
        : location.getLeadingComments();
  }

  static String getJavadoc(String comments, boolean isDeprecated, String prefix) {
    StringBuilder builder =
        new StringBuilder(prefix).append("/**\n").append(prefix).append(" * <pre>\n");
    Arrays.stream(HtmlEscapers.htmlEscaper().escape(comments).split("\n"))
        .forEach(line -> builder.append(prefix).append(" * ").append(line).append("\n"));
    builder.append(prefix).append(" * </pre>\n");
    if (isDeprecated) {
      builder.append(prefix).append(" * @deprecated\n");
    }
    builder.append(prefix).append(" */");
    return builder.toString();
  }
}
